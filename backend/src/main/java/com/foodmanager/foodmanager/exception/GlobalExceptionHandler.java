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

    // food endpoints

    @ExceptionHandler(FoodNotFoundException.class)
    public ProblemDetail handleFoodNotFound(FoodNotFoundException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(InvalidSearchQueryException.class) // 400 on bad tag / no filters
    public ProblemDetail handleInvalidSearchQuery(InvalidSearchQueryException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(UpstreamRateLimitedException.class) // 503 — OFF 429, frontend must back off
    public ProblemDetail handleUpstreamRateLimited(UpstreamRateLimitedException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
    }

    @ExceptionHandler(UpstreamTimeoutException.class) // 504
    public ProblemDetail handleUpstreamTimeout(UpstreamTimeoutException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.GATEWAY_TIMEOUT, e.getMessage());
    }

    @ExceptionHandler(UpstreamException.class) // 502 fallback for the other Upstream* subtypes
    public ProblemDetail handleUpstream(UpstreamException e){
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, e.getMessage());
    }
}