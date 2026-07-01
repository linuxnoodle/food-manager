package com.foodmanager.foodmanager.exception;

public class FoodNotFoundException extends RuntimeException {
    public FoodNotFoundException(String code) {
        super("food not found: " + code);
    }
}
