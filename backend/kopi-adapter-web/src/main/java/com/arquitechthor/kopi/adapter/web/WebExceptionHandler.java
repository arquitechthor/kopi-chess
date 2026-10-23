package com.arquitechthor.kopi.adapter.web;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/// A game that does not exist, or a URL that cannot name one, gets a
/// friendly "not found" page in the visitor's language.
@ControllerAdvice(basePackageClasses = WebAdapterConfiguration.class)
class WebExceptionHandler {

    @ExceptionHandler({GameNotFoundException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    String notFound() {
        return "not-found";
    }
}
