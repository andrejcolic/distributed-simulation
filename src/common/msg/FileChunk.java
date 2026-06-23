package common.msg;

/**
 * One chunk of a large file, streamed so the whole file is never held in memory (Test 7).
 * A chunk whose {@code data} is {@code null} marks the end of the file.
 */
public final class FileChunk implements Message {

    /** Chunk bytes, or {@code null} to signal end-of-file. */
    public final byte[] data;

    public FileChunk(byte[] data) {
        this.data = data;
    }
}
