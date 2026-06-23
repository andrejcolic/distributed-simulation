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

// SimBuffer that routes events across workers. putEvents (called from Simulator.work via
// Netlist.transform) sends each event to the worker owning its dstID: local events go into a
// PriorityQueue ordered by lTime, remote events are sent to that peer; a receiver thread feeds
// incoming events back into the same queue. Processing is gated by a conservative safe time: only
// events with lTime <= safeTime run, and the barrier (see DistributedJob) advances safeTime.
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

    public DistributedSimBuffer(Map<Long, Integer> routing, int selfIndex) {
        this.routing = routing;
        this.selfIndex = selfIndex;
    }

    // Registers a peer connection (during setup, before the run starts).
    public void setPeer(int index, Connection connection) {
        peers.put(index, connection);
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
        // Blocking variant (not used by the conservative loop, which uses pollProcessable).
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
        Event e = queue.peek();
        return e == null ? Long.MAX_VALUE : e.getlTime();
    }

    /* conservative control */

    // Returns the next event if it is within the safe time, else null.
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

    // Called by a peer receiver thread when a batch of events arrives.
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
        // An unknown destination is treated as local so events are never silently dropped.
        return owner == null ? selfIndex : owner;
    }

    private void sendRemote(int owner, List<Object> batch) {
        Connection conn = peers.get(owner);
        if (conn == null) {
            // No peer link (should not happen once setup completes): keep the events local.
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
            // peer unreachable: fail this sub-job (the server restarts it)
            throw new RuntimeException("Peer " + owner + " unreachable: " + ex.getMessage(), ex);
        }
    }
}
