package com.roucoux.cairn.application.exception;

import com.roucoux.cairn.domain.exception.business.BusinessException;
import com.roucoux.cairn.domain.exception.business.NotFoundException;
import com.roucoux.cairn.domain.exception.business.PortfolioImportRejectedException;
import com.roucoux.cairn.domain.exception.technical.TechnicalException;
import com.roucoux.cairn.domain.model.ImportError;
import java.util.Map;
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
     * Declared before the {@link BusinessException} family it belongs to, because a rejected import
     * carries more than a message: RFC 9457 lets the reasons ride along as an extension member, so
     * the caller can fix every row in one pass instead of rediscovering them one deploy at a time.
     */
    @ExceptionHandler(PortfolioImportRejectedException.class)
    ProblemDetail handleImportRejected(PortfolioImportRejectedException exception) {
        ProblemDetail problem =
                ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, exception.getMessage());
        problem.setTitle("Import rejected");
        problem.setProperty(
                "errors",
                exception.errors().stream()
                        .map(error -> Map.of("line", lineOf(error), "message", error.message()))
                        .toList());
        return problem;
    }

    /** The domain counts data rows from zero; a person reading the file counts every line from one. */
    private static int lineOf(ImportError error) {
        return error.rowIndex() + 2;
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
