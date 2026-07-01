package com.foodmanager.foodmanager.exception;

/**
 * Raised when OFF is reachable but returned a 5xx. Maps to 502 in the handler.
 * On the food-detail path the service suppresses this and serves stale data
 * if any is cached; only the cache-miss path propagates it to the user.
 */
public class UpstreamException extends RuntimeException {
    public UpstreamException(String msg) {
        super(msg);
    }

    public UpstreamException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
