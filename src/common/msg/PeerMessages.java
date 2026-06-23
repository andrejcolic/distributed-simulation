package common.msg;

import java.util.List;

/**
 * Messages for the Worker &harr; Worker (peer) channel: a hello that identifies the connecting
 * worker for a job, and batches of routed simulation events.
 */
public final class PeerMessages {

    private PeerMessages() {
    }

    /** First message on a peer connection: which job and which worker is connecting. */
    public static final class PeerHello implements Message {
        public final String jobId;
        public final int fromIndex;

        public PeerHello(String jobId, int fromIndex) {
            this.jobId = jobId;
            this.fromIndex = fromIndex;
        }
    }

    /**
     * A batch of simulation events routed to this peer. Events are carried as {@code Object}
     * (they are {@code sleep.simulation.Event}, which is {@code Serializable}).
     */
    public static final class RouteEvents implements Message {
        public final List<Object> events;

        public RouteEvents(List<Object> events) {
            this.events = events;
        }
    }
}
