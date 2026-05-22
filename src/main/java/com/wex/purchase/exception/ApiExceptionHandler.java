package com.wex.purchase.exception;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LogManager.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(PurchaseNotFoundException.class)
    ProblemDetail handleNotFound(PurchaseNotFoundException ex) {
        log.warn("Purchase not found: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Purchase Not Found");
        problem.setType(URI.create("about:blank#purchase-not-found"));
        return problem;
    }

    @ExceptionHandler(CurrencyConversionException.class)
    ProblemDetail handleConversion(CurrencyConversionException ex) {
        log.warn("Currency conversion failed: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
        problem.setTitle("Currency Conversion Failed");
        problem.setType(URI.create("about:blank#currency-conversion-failed"));
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .findFirst()
                .orElse("Validation failed");

        log.warn("Validation failed: {}", detail);
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        problem.setTitle("Validation Error");
        problem.setType(URI.create("about:blank#validation-error"));
        return problem;
    }
}
