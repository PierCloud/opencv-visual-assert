package io.senti.testing.visual;

public class VisualAssertException extends RuntimeException {

    public VisualAssertException(String message) {
        super(message);
    }

    public VisualAssertException(String message, Throwable cause) {
        super(message, cause);
    }
}
