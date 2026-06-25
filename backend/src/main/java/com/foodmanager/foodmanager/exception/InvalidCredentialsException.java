package com.foodmanager.foodmanager.exception;

public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException(String msg){
        super(msg);
    }
}
