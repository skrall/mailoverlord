package org.mailoverlord.server.config;

import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns a rejected request body into a problem detail that says what was wrong with it.
 *
 * <p>Without this, Spring Boot answers a body that fails its constraints with
 * {@code {"detail": "Invalid request content."}} and nothing else. The constraints know exactly
 * which field was wrong and why — {@code MessageReleaseRequest} carries messages like
 * "overrideToAddresses must name at least one address when overrideTo is true" — and all of that
 * is discarded. A caller is left with a 400 that says the request was invalid, which is the same
 * unhelpful answer the hand-written guards replaced in #13, arrived at by a different route.
 *
 * <p>Boot used to attach the field errors to the problem detail and no longer does, so this
 * exists to put them back. It is here rather than in the controller on purpose: validation is
 * declared on the DTOs, and only the shape of the failure is decided in one place.
 *
 * <p>The {@code errors} property carries the field name and message but deliberately not the
 * rejected value. Rejecting an oversized batch echoes the whole batch back, so a request naming
 * hundreds of thousands of ids would answer with an error document nearly as large as itself.
 *
 * <p>Ordered ahead of Boot's own problem detail handling, which also claims
 * {@code MethodArgumentNotValidException} and answers it with a detail of "Invalid request
 * content." Two advices can handle the same exception and the first one wins, so without this the
 * messages below would never reach a caller.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ValidationProblemDetailAdvice {

    private static final Logger logger = LoggerFactory.getLogger(ValidationProblemDetailAdvice.class);

    /**
     * Names a rejected field and what is wrong with it, in the body of the problem detail.
     *
     * @param field the request field that failed
     * @param message why it failed, already phrased for a caller to act on
     */
    public record FieldProblem(String field, String message) {
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail onInvalidRequestBody(MethodArgumentNotValidException e) {
        List<FieldProblem> problems = e.getBindingResult().getAllErrors().stream()
                .map(error -> new FieldProblem(fieldOf(error), error.getDefaultMessage()))
                .toList();

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                summarise(problems));
        problem.setTitle(HttpStatus.BAD_REQUEST.getReasonPhrase());
        problem.setProperty("errors", problems);
        return problem;
    }

    /**
     * Joins the failures into one sentence, so a client that reads only {@code detail} — which is
     * all the UI does — still learns what to change.
     *
     * <p>Each message names its own field already, so no field names are prepended here: doing so
     * would read "messageIds: messageIds must contain at least one message id."
     */
    private static String summarise(List<FieldProblem> problems) {
        String detail = problems.stream()
                .map(FieldProblem::message)
                .collect(Collectors.joining(" "));
        logger.debug("Rejected a request body: {}", detail);
        return detail;
    }

    /**
     * The field a violation belongs to.
     *
     * <p>Class-level constraints report no field of their own. {@code UsableOverrides} attaches
     * each violation to the address it is about, so there is normally a name; the fallback covers
     * a violation that has none.
     */
    private static String fieldOf(ObjectError error) {
        return error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
    }
}