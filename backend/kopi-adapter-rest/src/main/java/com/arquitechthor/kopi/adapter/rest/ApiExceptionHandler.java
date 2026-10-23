package com.arquitechthor.kopi.adapter.rest;

import com.arquitechthor.kopi.application.game.GameNotFoundException;
import com.arquitechthor.kopi.application.game.InvalidUciException;
import com.arquitechthor.kopi.application.game.VersionMismatchException;
import com.arquitechthor.kopi.application.port.BotUnavailableException;
import com.arquitechthor.kopi.application.port.ConcurrentGameUpdateException;
import com.arquitechthor.kopi.domain.game.GameNotInProgressException;
import com.arquitechthor.kopi.domain.game.GameRuleException;
import com.arquitechthor.kopi.domain.game.IllegalMoveException;
import com.arquitechthor.kopi.domain.game.NotYourTurnException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/// Turns errors into `ProblemDetail` responses (RFC 9457, design §10.1).
///
/// Each problem has a stable `code` property for programs (`ILLEGAL_MOVE`)
/// and a `title` and `detail` for people, in the language of
/// `Accept-Language` (ADR-011: the domain gives codes, this adapter gives
/// words).
///
/// | Code                    | Status |
/// |-------------------------|--------|
/// | `INVALID_REQUEST`       | 400    |
/// | `INVALID_UCI`           | 400    |
/// | `GAME_NOT_FOUND`        | 404    |
/// | `GAME_NOT_IN_PROGRESS`  | 409    |
/// | `NOT_YOUR_TURN`         | 409    |
/// | `VERSION_MISMATCH`      | 412    |
/// | `ILLEGAL_MOVE`          | 422    |
/// | `PRECONDITION_REQUIRED` | 428    |
/// | `BOT_UNAVAILABLE`       | 503    |
@RestControllerAdvice(basePackageClasses = RestAdapterConfiguration.class)
class ApiExceptionHandler {

    private final MessageSource messages;

    ApiExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    @ExceptionHandler
    ProblemDetail invalidUci(InvalidUciException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_UCI", request, e.text());
    }

    @ExceptionHandler
    ProblemDetail invalidRequest(InvalidRequestException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail unreadableBody(HttpMessageNotReadableException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request, "the body is missing or is not valid JSON");
    }

    @ExceptionHandler
    ProblemDetail badParameter(MethodArgumentTypeMismatchException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request, "bad value for " + e.getName());
    }

    /// Out-of-range values in a query, such as `?size=1000`.
    @ExceptionHandler
    ProblemDetail invalidArgument(IllegalArgumentException e, HttpServletRequest request) {
        return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", request, e.getMessage());
    }

    @ExceptionHandler
    ProblemDetail notFound(GameNotFoundException e, HttpServletRequest request) {
        return problem(HttpStatus.NOT_FOUND, "GAME_NOT_FOUND", request, e.gameId());
    }

    @ExceptionHandler
    ProblemDetail gameRule(GameRuleException e, HttpServletRequest request) {
        // Exhaustive over the sealed family: a new rule violation needs a
        // status and a code here before it compiles.
        return switch (e) {
            case GameNotInProgressException _ ->
                problem(HttpStatus.CONFLICT, "GAME_NOT_IN_PROGRESS", request, e.gameId());
            case NotYourTurnException _ -> problem(HttpStatus.CONFLICT, "NOT_YOUR_TURN", request, e.gameId());
            case IllegalMoveException illegal ->
                problem(HttpStatus.UNPROCESSABLE_CONTENT, "ILLEGAL_MOVE", request, illegal.uci());
        };
    }

    @ExceptionHandler
    ProblemDetail versionMismatch(VersionMismatchException e, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH", request, e.gameId());
    }

    /// Someone saved between our load and our save: for the client, the
    /// same as sending an old version.
    @ExceptionHandler
    ProblemDetail concurrentUpdate(ConcurrentGameUpdateException e, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_FAILED, "VERSION_MISMATCH", request, e.gameId());
    }

    @ExceptionHandler
    ProblemDetail missingVersion(Versions.MissingVersionException e, HttpServletRequest request) {
        return problem(HttpStatus.PRECONDITION_REQUIRED, "PRECONDITION_REQUIRED", request);
    }

    @ExceptionHandler
    ProblemDetail botUnavailable(BotUnavailableException e, HttpServletRequest request) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "BOT_UNAVAILABLE", request, e.profile());
    }

    private ProblemDetail problem(HttpStatus status, String code, HttpServletRequest request, Object... arguments) {
        var locale = ApiLocale.of(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        var problem = ProblemDetail.forStatusAndDetail(status,
                messages.getMessage("rest.problem." + code + ".detail", arguments, locale));
        problem.setTitle(messages.getMessage("rest.problem." + code + ".title", null, locale));
        problem.setProperty("code", code);
        return problem;
    }
}
