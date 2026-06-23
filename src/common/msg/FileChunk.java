package common.msg;

// One chunk of a streamed file; data == null marks end-of-file.
public final class FileChunk implements Message {

    public final byte[] data;

    public FileChunk(byte[] data) {
        this.data = data;
    }
}
