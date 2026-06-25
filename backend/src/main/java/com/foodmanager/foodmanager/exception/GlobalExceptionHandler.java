package com.foodmanager.foodmanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice // 400 n shi
public class GlobalExceptionHandler {
    @ExceptionHandler(DuplicateEntityException.class)
    public ProblemDetail handleDuplicateEntity(DuplicateEntityException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(InvalidCredentialsException.class) // 401 on bad login
    public ProblemDetail handleInvalidCredentials(InvalidCredentialsException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
    }
}