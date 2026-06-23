package common.msg;

import java.util.List;

// Messages for the Worker <-> Worker (peer) channel.
public final class PeerMessages {

    private PeerMessages() {}

    // First message on a peer connection: which job and which worker is connecting.
    public static final class PeerHello implements Message {
        public final String jobId;
        public final int fromIndex;

        public PeerHello(String jobId, int fromIndex) {
            this.jobId = jobId;
            this.fromIndex = fromIndex;
        }
    }

    // A batch of routed simulation events (carried as Object = sleep.simulation.Event).
    public static final class RouteEvents implements Message {
        public final List<Object> events;

        public RouteEvents(List<Object> events) {
            this.events = events;
        }
    }
}
