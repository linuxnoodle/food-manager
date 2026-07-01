package com.foodmanager.foodmanager.exception;

public class InvalidSearchQueryException extends RuntimeException {
    public InvalidSearchQueryException(String msg) {
        super(msg);
    }
}
