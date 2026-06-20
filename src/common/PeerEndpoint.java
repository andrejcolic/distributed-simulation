package rs.ac.bg.etf.kdp.common;

import java.io.Serializable;

/**
 * Network address (host + port) of a worker's peer listener, used so workers can connect to
 * each other and exchange simulation events directly (peer-to-peer routing).
 */
public class PeerEndpoint implements Serializable {
    private static final long serialVersionUID = 1L;

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
