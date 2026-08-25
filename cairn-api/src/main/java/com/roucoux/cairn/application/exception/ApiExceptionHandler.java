package com.roucoux.cairn.application.exception;

import com.roucoux.cairn.domain.exception.business.BusinessException;
import com.roucoux.cairn.domain.exception.technical.TechnicalException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain errors to RFC 9457 problem details by exception family: business-rule violations
 * become 422, technical failures of an outbound dependency become 502. (Authentication and
 * authorization failures — 401/403 — are handled by Spring Security's filter chain, not here.)
 */
@RestControllerAdvice
class ApiExceptionHandler {

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
}
