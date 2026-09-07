package com.astronomation.networks.math.simplex;

public class SimplexException extends RuntimeException {
    public SimplexException() {
    }

    public SimplexException(Throwable cause) {
        super(cause);
    }

    public SimplexException(String message) {
        super(message);
    }

    public SimplexException(String message, Throwable cause) {
        super(message, cause);
    }

    public SimplexException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
