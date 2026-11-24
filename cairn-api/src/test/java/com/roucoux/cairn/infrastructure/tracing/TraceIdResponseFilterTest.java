package com.roucoux.cairn.infrastructure.tracing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceIdResponseFilterTest {

    private final Tracer tracer = mock(Tracer.class);

    @Test
    void answersWithTheCurrentTraceId() throws Exception {
        Span span = mock(Span.class);
        TraceContext context = mock(TraceContext.class);
        when(tracer.currentSpan()).thenReturn(span);
        when(span.context()).thenReturn(context);
        when(context.traceId()).thenReturn("4bf92f3577b34da6a3ce929d0e0e4736");
        MockHttpServletResponse response = new MockHttpServletResponse();

        new TraceIdResponseFilter(tracer).doFilter(new MockHttpServletRequest(), response, new MockFilterChain());

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo("4bf92f3577b34da6a3ce929d0e0e4736");
    }

    @Test
    void addsNoHeaderOutsideASpan() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        new TraceIdResponseFilter(tracer).doFilter(new MockHttpServletRequest(), response, chain);

        assertThat(response.getHeader("X-Trace-Id")).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void runsRightAfterTheObservationFilter() {
        var registration = new TracingConfig().traceIdResponseFilter(tracer);

        assertThat(registration.getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
        assertThat(registration.getFilter()).isInstanceOf(TraceIdResponseFilter.class);
    }
}
