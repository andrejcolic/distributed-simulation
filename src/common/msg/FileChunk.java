package rs.ac.bg.etf.kdp.common.msg;

/**
 * A file chunk for streaming result transfer (Test 7 — large files).
 * The whole file is never held in memory: it is sent / received chunk by chunk.
 */
public final class FileChunk implements Message {
    private static final long serialVersionUID = 1L;

    /** Buffer; only the first {@link #length} bytes are valid. */
    public final byte[] data;
    public final int length;
    public final boolean last;

    public FileChunk(byte[] data, int length, boolean last) {
        this.data = data;
        this.length = length;
        this.last = last;
    }
}
