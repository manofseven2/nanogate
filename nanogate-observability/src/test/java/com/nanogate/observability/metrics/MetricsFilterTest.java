package com.nanogate.observability.metrics;

import com.nanogate.routing.metrics.MetricAttribute;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RouteLocator;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricsFilterTest {

    @Mock
    private RouteLocator routeLocator;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private MeterRegistry meterRegistry;
    private MetricsFilter metricsFilter;
    private Map<String, Object> attributeMap;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsFilter = new MetricsFilter(meterRegistry, routeLocator);
        attributeMap = new HashMap<>();

        // Mock the set/getAttribute methods to use our local map
        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            Object value = invocation.getArgument(1);
            attributeMap.put(key, value);
            return null;
        }).when(request).setAttribute(anyString(), any());

        doAnswer(invocation -> {
            String key = invocation.getArgument(0);
            return attributeMap.get(key);
        }).when(request).getAttribute(anyString());
    }

    @Test
    void doFilter_RecordsAllMetricsFromAttributes() throws Exception {
        Route route = new Route();
        route.setPath("/api/test/**");

        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(response.getStatus()).thenReturn(200);

        // Simulate the orchestrator setting the attributes during the filter chain execution
        doAnswer(invocation -> {
            attributeMap.put(MetricAttribute.OVERHEAD_DURATION_NANOS.name(), 100_000_000L); // 100ms
            attributeMap.put(MetricAttribute.BACKEND_DURATION_NANOS.name(), 300_000_000L); // 300ms
            return null;
        }).when(filterChain).doFilter(request, response);

        metricsFilter.doFilter(request, response, filterChain);

        assertThat(meterRegistry.get("nanogate.requests.total").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("nanogate.request.overhead").timer().count()).isEqualTo(1);
        assertThat(meterRegistry.get("nanogate.backend.response").timer().count()).isEqualTo(1);

        assertThat(meterRegistry.get("nanogate.request.overhead").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(100);
        assertThat(meterRegistry.get("nanogate.backend.response").timer().totalTime(TimeUnit.MILLISECONDS)).isEqualTo(300);
        assertThat(meterRegistry.get("nanogate.requests.total").timer().totalTime(TimeUnit.NANOSECONDS)).isGreaterThan(0);
    }
}
