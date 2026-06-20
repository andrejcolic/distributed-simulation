package rs.ac.bg.etf.kdp.common.msg;

import java.io.Serializable;

/**
 * Marker for all protocol messages. Everything is serialized over
 * {@code ObjectOutputStream}/{@code ObjectInputStream}.
 */
public interface Message extends Serializable {
}
