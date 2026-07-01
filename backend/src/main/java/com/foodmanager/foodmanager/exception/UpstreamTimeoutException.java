package com.foodmanager.foodmanager.exception;

/**
 * OFF didn't respond within the configured timeout. Maps to 504 in the handler.
 */
public class UpstreamTimeoutException extends UpstreamException {
    public UpstreamTimeoutException(String msg) {
        super(msg);
    }

    public UpstreamTimeoutException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
