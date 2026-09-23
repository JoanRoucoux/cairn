package com.roucoux.cairn.adapter.client.config;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

class TransientFailureRetryInterceptor implements ClientHttpRequestInterceptor {

    private static final List<Duration> DEFAULT_BACKOFF = List.of(Duration.ofSeconds(1), Duration.ofSeconds(3));
    private static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(5);

    private final List<Duration> backoff;
    private final Sleeper sleeper;

    TransientFailureRetryInterceptor() {
        this(DEFAULT_BACKOFF, Sleeper.THREAD);
    }

    TransientFailureRetryInterceptor(List<Duration> backoff, Sleeper sleeper) {
        this.backoff = List.copyOf(backoff);
        this.sleeper = sleeper;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        for (int attempt = 0; ; attempt++) {
            boolean lastAttempt = attempt == backoff.size();
            ClientHttpResponse response;
            try {
                response = execution.execute(request, body);
            } catch (HttpTimeoutException timeout) {
                throw timeout;
            } catch (IOException failure) {
                if (lastAttempt) {
                    throw failure;
                }
                sleeper.sleep(backoff.get(attempt));
                continue;
            }
            Optional<Duration> wait = lastAttempt ? Optional.empty() : waitBeforeRetry(response, attempt);
            if (wait.isEmpty()) {
                return response;
            }
            response.close();
            sleeper.sleep(wait.get());
        }
    }

    private Optional<Duration> waitBeforeRetry(ClientHttpResponse response, int attempt) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        if (status.is5xxServerError()) {
            return Optional.of(backoff.get(attempt));
        }
        if (status.isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            Duration wait = retryAfter(response.getHeaders()).orElse(backoff.get(attempt));
            return wait.compareTo(MAX_RETRY_AFTER) <= 0 ? Optional.of(wait) : Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<Duration> retryAfter(HttpHeaders headers) {
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(Duration.ofSeconds(Long.parseLong(value.strip())));
        } catch (NumberFormatException httpDate) {
            return Optional.empty();
        }
    }

    @FunctionalInterface
    interface Sleeper {

        Sleeper THREAD = duration -> {
            try {
                Thread.sleep(duration);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("interrupted while waiting to retry");
            }
        };

        void sleep(Duration duration) throws IOException;
    }
}
