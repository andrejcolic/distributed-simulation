package rs.ac.bg.etf.kdp.common;

/**
 * Invalid job configuration (Test 6). Carries a clear message that becomes the
 * {@code Failed} status — the error is <b>not</b> silently swallowed.
 */
public class ConfigException extends Exception {
    private static final long serialVersionUID = 1L;

    public ConfigException(String message) {
        super(message);
    }
}
