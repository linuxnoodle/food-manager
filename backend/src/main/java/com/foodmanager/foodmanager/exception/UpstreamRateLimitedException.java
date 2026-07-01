package com.foodmanager.foodmanager.exception;

/**
 * OFF returned 429 (or hit our own self-imposed cap). Maps to 503 in the handler
 * so the frontend can back off — IP bans are manual to undo, so we never retry
 * automatically into a rate-limited state.
 */
public class UpstreamRateLimitedException extends UpstreamException {
    public UpstreamRateLimitedException(String msg) {
        super(msg);
    }
}
