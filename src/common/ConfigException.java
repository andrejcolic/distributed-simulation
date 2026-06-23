package common;

// Invalid job configuration; its message becomes the job's Failed reason.
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }
}
