package com.appfuxion_notification_platform.backend.api.correlation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationContextInterceptorTest {

    @AfterEach
    void clean() {
        MDC.clear();
        CorrelationContextHolder.clear();
    }

    @Test
    void preHandle_shouldUseIncomingHeaderAndPopulateContext() {
        CorrelationContextInterceptor interceptor = new CorrelationContextInterceptor("X-Correlation-Id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("X-Correlation-Id", "corr-123");

        interceptor.preHandle(request, response, new Object());

        assertEquals("corr-123", CorrelationContextHolder.requireCorrelationId());
        assertEquals("corr-123", response.getHeader("X-Correlation-Id"));
        assertEquals("corr-123", MDC.get("correlationId"));
    }

    @Test
    void preHandle_shouldGenerateCorrelationIdWhenHeaderMissing() {
        CorrelationContextInterceptor interceptor = new CorrelationContextInterceptor("X-Correlation-Id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());
        String generated = CorrelationContextHolder.requireCorrelationId();

        assertNotNull(generated);
        assertFalse(generated.isBlank());
        assertEquals(generated, response.getHeader("X-Correlation-Id"));
        assertEquals(generated, MDC.get("correlationId"));

        interceptor.afterCompletion(request, response, new Object(), null);
        assertNull(MDC.get("correlationId"));
    }
}
