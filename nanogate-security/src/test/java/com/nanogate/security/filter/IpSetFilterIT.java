package com.nanogate.security.filter;

import com.nanogate.security.NanoGateSecurityTestApp;
import com.nanogate.security.SecurityConstants;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = {NanoGateSecurityTestApp.class, IpSetFilterIT.TestConfig.class})
@TestPropertySource(properties = {
        "nanogate.security.ip-header=X-Forwarded-For",
        "nanogate.security.ip-sets[0].name=allow-only-50",
        "nanogate.security.ip-sets[0].type=ALLOW",
        "nanogate.security.ip-sets[0].ips[0]=192.168.1.50",
        "nanogate.security.ip-sets[1].name=block-evil",
        "nanogate.security.ip-sets[1].type=BLOCK",
        "nanogate.security.ip-sets[1].ips[0]=10.0.0.1"
})
class IpSetFilterIT {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder().build();

    @Configuration
    static class TestConfig {
        @Bean
        public FilterRegistrationBean<Filter> mockRouteFilter() {
            FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
            registrationBean.setFilter(new Filter() {
                @Override
                public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
                    HttpServletRequest httpRequest = (HttpServletRequest) request;
                    String testIpSet = httpRequest.getHeader("X-Test-IpSet");
                    if (testIpSet != null && !testIpSet.isEmpty()) {
                        request.setAttribute(SecurityConstants.IP_SET_ATTRIBUTE, testIpSet);
                    }
                    chain.doFilter(request, response);
                }
            });
            registrationBean.setOrder(1); // Before IpSetFilter (Order 10)
            registrationBean.addUrlPatterns("/*");
            return registrationBean;
        }
    }

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    @Test
    void testAllowIpSet_AllowedIp_Returns200() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/test"))
                .header("X-Test-IpSet", "allow-only-50")
                .header("X-Forwarded-For", "192.168.1.50, 10.0.0.2")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void testAllowIpSet_BlockedIp_Returns403() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/test"))
                .header("X-Test-IpSet", "allow-only-50")
                .header("X-Forwarded-For", "192.168.1.51")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void testBlockIpSet_BlockedIp_Returns403() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/test"))
                .header("X-Test-IpSet", "block-evil")
                .header("X-Forwarded-For", "10.0.0.1")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(403, response.statusCode());
    }

    @Test
    void testBlockIpSet_AllowedIp_Returns200() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/test"))
                .header("X-Test-IpSet", "block-evil")
                .header("X-Forwarded-For", "192.168.1.100")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("OK", response.body());
    }

    @Test
    void testNoIpSetConfigured_Returns200() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/test"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
    }
}
