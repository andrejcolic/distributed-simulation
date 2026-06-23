package common;

/**
 * Invalid job configuration (Test 6). Carries a clear message that becomes the
 * {@code Failed} status — the error is <b>not</b> silently swallowed.
 */
public class ConfigException extends Exception {

    public ConfigException(String message) {
        super(message);
    }
}
