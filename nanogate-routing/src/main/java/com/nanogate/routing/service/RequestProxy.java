package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.resilience.service.CircuitBreakerProvider;
import com.nanogate.routing.model.HeaderTransformProperties;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RequestProxy {

    private static final Logger log = LoggerFactory.getLogger(RequestProxy.class);
    private static final Pattern HEADER_PLACEHOLDER_PATTERN = Pattern.compile("\\{header\\.([^}]+)}");

    private final HttpClientProvider httpClientProvider;
    private final CircuitBreakerProvider circuitBreakerProvider;

    public RequestProxy(HttpClientProvider httpClientProvider, CircuitBreakerProvider circuitBreakerProvider) {
        this.httpClientProvider = httpClientProvider;
        this.circuitBreakerProvider = circuitBreakerProvider;
    }

    public CompletableFuture<HttpResponse<InputStream>> prepareRequest(HttpServletRequest request, URI targetUri,
                                                                         HttpClientProperties clientProperties, ResilienceProperties resilienceProperties, Route route) {

        HttpClient httpClient = httpClientProvider.getClient(clientProperties);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        requestBuilder.method(request.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return request.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Failed to get request input stream", e);
            }
        }));

        String transformedPath = transformPath(request.getRequestURI().substring(request.getContextPath().length()), route);
        String finalPath = targetUri.getPath() + transformedPath;
        String queryString = request.getQueryString();
        URI finalTargetUri = URI.create(targetUri.getScheme() + "://" + targetUri.getAuthority() + finalPath + (queryString != null ? "?" + queryString : ""));
        requestBuilder.uri(finalTargetUri);

        if (clientProperties.getResponseTimeout() != null) {
            requestBuilder.timeout(clientProperties.getResponseTimeout());
        }

        applyRequestHeaderTransforms(request, requestBuilder, route.getRequestHeaders());

        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(targetUri, resilienceProperties);
        HttpRequest httpRequest = requestBuilder.build();

        return circuitBreaker.executeCompletionStage(
                () -> httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
        ).toCompletableFuture();
    }

    private String transformPath(String path, Route route) {
        String modifiedPath = path;
        if (route.getStripPrefix() != null && route.getStripPrefix() > 0) {
            String[] segments = path.split("/");
            if (segments.length > route.getStripPrefix()) {
                modifiedPath = "/" + Arrays.stream(segments, route.getStripPrefix() + 1, segments.length)
                                          .collect(Collectors.joining("/"));
                log.debug("Stripped prefix from path. Original: '{}', New: '{}'", path, modifiedPath);
            }
        }
        if (StringUtils.hasText(route.getRewritePath()) && StringUtils.hasText(route.getRewriteReplacement())) {
            modifiedPath = modifiedPath.replaceAll(route.getRewritePath(), route.getRewriteReplacement());
            log.debug("Rewrote path. Original: '{}', New: '{}'", path, modifiedPath);
        }
        return modifiedPath;
    }

    private void applyRequestHeaderTransforms(HttpServletRequest request, HttpRequest.Builder requestBuilder, HeaderTransformProperties transforms) {
        List<String> toRemove = (transforms != null && transforms.remove() != null)
                ? transforms.remove().stream().map(String::toLowerCase).toList()
                : Collections.emptyList();
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (toRemove.contains(headerName.toLowerCase())) {
                log.debug("Removing request header: {}", headerName);
                continue;
            }
            // Do not copy restricted headers. Let the HTTP client manage them.
            if (!isHopByHopHeader(headerName) && !headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    requestBuilder.header(headerName, headerValues.nextElement());
                }
            }
        }
        if (transforms != null && transforms.add() != null) {
            transforms.add().forEach((key, value) -> {
                String resolvedValue = resolveHeaderPlaceholder(value, request);
                log.debug("Adding/overwriting request header: {}={}", key, resolvedValue);
                requestBuilder.setHeader(key, resolvedValue);
            });
        }
    }

    private String resolveHeaderPlaceholder(String value, HttpServletRequest request) {
        if (value == null || !value.contains("{")) return value;
        Matcher matcher = HEADER_PLACEHOLDER_PATTERN.matcher(value);
        return matcher.replaceAll(matchResult -> {
            String headerName = matchResult.group(1);
            String headerValue = request.getHeader(headerName);
            log.debug("Resolved placeholder '{{}}' to value '{}'", matchResult.group(0), headerValue);
            return headerValue != null ? headerValue : "";
        });
    }

    private boolean isHopByHopHeader(String headerName) {
        String lowerCaseHeaderName = headerName.toLowerCase();
        return lowerCaseHeaderName.equals("connection") || "keep-alive".equals(lowerCaseHeaderName) ||
               "proxy-authenticate".equals(lowerCaseHeaderName) || "proxy-authorization".equals(lowerCaseHeaderName) ||
               "te".equals(lowerCaseHeaderName) || "trailers".equals(lowerCaseHeaderName) ||
               "transfer-encoding".equals(lowerCaseHeaderName) || "upgrade".equals(lowerCaseHeaderName);
    }
}
