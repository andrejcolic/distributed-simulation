package worker;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.atomic.AtomicLong;

import common.Connection;
import common.msg.PeerMessages;
import sleep.simulation.Event;
import sleep.simulation.SimBuffer;

// SimBuffer that routes events across workers; processing is gated by a conservative safe time.
@SuppressWarnings({"rawtypes", "unchecked"})
public final class DistributedSimBuffer implements SimBuffer {

    private final PriorityQueue<Event> queue = new PriorityQueue<>();
    private final Map<Long, Integer> routing;
    private final int selfIndex;
    private final Map<Integer, Connection> peers = new HashMap<>();

    private final AtomicLong sent = new AtomicLong(0);
    private final AtomicLong received = new AtomicLong(0);

    private volatile long safeTime = Long.MIN_VALUE;
    private volatile boolean terminated = false;
    private volatile boolean peerFailed = false;

    // Optimistic mode: peer-bound events are held until the barrier marks their time safe.
    private boolean optimistic = false;
    private final PriorityQueue<Event> heldRemote = new PriorityQueue<>();

    public DistributedSimBuffer(Map<Long, Integer> routing, int selfIndex) {
        this.routing = routing;
        this.selfIndex = selfIndex;
    }

    public void setPeer(int index, Connection connection) {
        peers.put(index, connection);
    }

    public void setOptimistic() {
        this.optimistic = true;
    }

    /* SimBuffer */

    @Override
    public void putEvent(Event event) {
        List<Event> one = new LinkedList<>();
        one.add(event);
        putEvents(one);
    }

    @Override
    public void putEvents(List events) {
        Map<Integer, List<Object>> remote = null;
        synchronized (this) {
            for (Object o : events) {
                Event e = (Event) o;
                int owner = ownerOf(e);
                if (owner == selfIndex) {
                    queue.add(e);
                } else if (optimistic) {
                    heldRemote.add(e);
                } else {
                    if (remote == null) {
                        remote = new HashMap<>();
                    }
                    remote.computeIfAbsent(owner, k -> new ArrayList<>()).add(e);
                }
            }
            notifyAll();
        }
        if (remote != null) {
            for (Map.Entry<Integer, List<Object>> entry : remote.entrySet()) {
                sendRemote(entry.getKey(), entry.getValue());
            }
        }
    }

    @Override
    public synchronized Event getEvent() {
        while (queue.isEmpty() && !terminated) {
            try {
                wait();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return queue.poll();
    }

    @Override
    public synchronized List getEvents() {
        List<Event> list = new LinkedList<>();
        Event e = getEvent();
        if (e != null) {
            list.add(e);
        }
        return list;
    }

    @Override
    public synchronized boolean isEmpty() {
        return queue.isEmpty();
    }

    @Override
    public synchronized long getMinrank() {
        long min = queue.isEmpty() ? Long.MAX_VALUE : queue.peek().getlTime();
        if (!heldRemote.isEmpty()) {
            min = Math.min(min, heldRemote.peek().getlTime());
        }
        return min;
    }

    /* conservative control */

    // Next event within the safe time, else null.
    public synchronized Event pollProcessable() {
        if (terminated || queue.isEmpty()) {
            return null;
        }
        Event e = queue.peek();
        if (e.getlTime() <= safeTime) {
            return queue.poll();
        }
        return null;
    }

    /* optimistic control */

    // Next event with lTime below the limit (eager processing, ignoring the safe time), else null.
    public synchronized Event pollBelow(long limit) {
        if (terminated || queue.isEmpty() || queue.peek().getlTime() >= limit) {
            return null;
        }
        return queue.poll();
    }

    // Sends held peer-bound events whose time is now safe.
    public void releaseRemoteUpTo(long safe) {
        List<Event> due = new ArrayList<>();
        synchronized (this) {
            while (!heldRemote.isEmpty() && heldRemote.peek().getlTime() <= safe) {
                due.add(heldRemote.poll());
            }
        }
        if (due.isEmpty()) {
            return;
        }
        Map<Integer, List<Object>> byOwner = new HashMap<>();
        for (Event e : due) {
            byOwner.computeIfAbsent(ownerOf(e), k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<Integer, List<Object>> entry : byOwner.entrySet()) {
            sendRemote(entry.getKey(), entry.getValue());
        }
    }

    public void setSafeTime(long safeTime) {
        this.safeTime = safeTime;
    }

    public void setTerminated() {
        terminated = true;
        synchronized (this) {
            notifyAll();
        }
    }

    public boolean isTerminated() {
        return terminated;
    }

    public long sentCount() {
        return sent.get();
    }

    public long receivedCount() {
        return received.get();
    }

    // True if a peer became unreachable (its worker died).
    public boolean peerFailed() {
        return peerFailed;
    }

    public void receiveEvents(List<Object> events) {
        synchronized (this) {
            for (Object o : events) {
                queue.add((Event) o);
            }
            notifyAll();
        }
        received.addAndGet(events.size());
    }

    /* internals */

    private int ownerOf(Event e) {
        Integer owner = routing.get(e.getDstID());
        return owner == null ? selfIndex : owner; // unknown destination treated as local
    }

    private void sendRemote(int owner, List<Object> batch) {
        Connection conn = peers.get(owner);
        if (conn == null) {
            synchronized (this) {
                for (Object o : batch) {
                    queue.add((Event) o);
                }
            }
            return;
        }
        try {
            conn.send(new PeerMessages.RouteEvents(batch));
            sent.addAndGet(batch.size());
        } catch (IOException ex) {
            // Peer unreachable (worker died): stay silent and let the server restart the job.
            peerFailed = true;
            setTerminated();
        }
    }
}
