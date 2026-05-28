package com.foodmanager.foodmanager.exception;

public class DuplicateEntityException extends RuntimeException {
    public DuplicateEntityException(String msg){
        super(msg);
    }
}
