package org.apache.river.impl.security.dos;

public class IsolationException extends Exception {

    private static final long serialVersionUID = 1L;

    public IsolationException() {
        super();
    }

    public IsolationException(String message) {
        super(message);
    }

    public IsolationException(String message, Throwable cause) {
        super(message, cause);
    }

    public IsolationException(Throwable cause) {
        super(cause);
    }
}
