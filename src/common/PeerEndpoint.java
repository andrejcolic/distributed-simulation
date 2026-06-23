package common;

import java.io.Serializable;

// Address of a worker's peer listener, so workers can exchange events directly.
public class PeerEndpoint implements Serializable {

    public final String host;
    public final int port;

    public PeerEndpoint(String host, int port) {
        this.host = host;
        this.port = port;
    }

    @Override
    public String toString() {
        return host + ":" + port;
    }
}
