package com.roucoux.cairn.application.exception;

import com.roucoux.cairn.application.csv.ImportFileRejectedException;
import com.roucoux.cairn.application.csv.LineError;
import com.roucoux.cairn.domain.exception.business.BusinessException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.technical.TechnicalException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain errors to RFC 9457 problem details by exception family: a missing resource becomes
 * 404, other business-rule violations become 422, technical failures of an outbound dependency
 * become 502. (Authentication and authorization failures — 401/403 — are handled by Spring
 * Security's filter chain, not here.)
 */
@RestControllerAdvice
class ApiExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    ProblemDetail handleNotFound(NotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, exception.getMessage());
        problem.setTitle("Resource not found");
        return problem;
    }

    /**
     * A rejected import carries more than a message: RFC 9457 lets the reasons ride along as an
     * extension member, so the caller can fix every line in one pass instead of rediscovering them one
     * deploy at a time.
     */
    @ExceptionHandler(ImportFileRejectedException.class)
    ProblemDetail handleImportRejected(ImportFileRejectedException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Import rejected");
        problem.setProperty(
                "errors",
                exception.errors().stream().map(ApiExceptionHandler::asMember).toList());
        return problem;
    }

    /**
     * A code and the offending token, never a sentence: the caller owns the wording and can
     * translate it. {@code value} is omitted rather than sent null when the failure has no token.
     */
    private static Map<String, Object> asMember(LineError error) {
        Map<String, Object> member = new LinkedHashMap<>();
        member.put("line", error.line());
        member.put("code", error.code().name());
        if (error.value() != null) {
            member.put("value", error.value());
        }
        return member;
    }

    /** A unique index turning a row down means the same instrument, account or quote is already there. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail handleConflict(DataIntegrityViolationException exception) {
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Already exists");
        return problem;
    }

    @ExceptionHandler(BusinessException.class)
    ProblemDetail handleBusiness(BusinessException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Business rule violated");
        return problem;
    }

    @ExceptionHandler(TechnicalException.class)
    ProblemDetail handleTechnical(TechnicalException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, exception.getMessage());
        problem.setTitle("Upstream dependency failed");
        return problem;
    }

    @ExceptionHandler(LastPasskeyException.class)
    ProblemDetail handleLastPasskey(LastPasskeyException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }
}
