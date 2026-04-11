package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.resilience.service.CircuitBreakerProvider;
import com.nanogate.routing.model.HttpClientProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Enumeration;
import java.util.concurrent.CompletionException;

/**
 * Service responsible for proxying the incoming HttpServletRequest to a target URI.
 * It uses a provided HttpClient instance to perform the request forwarding.
 */
@Service
public class RequestProxy {

    private final HttpClientProvider httpClientProvider;
    private final CircuitBreakerProvider circuitBreakerProvider;

    public RequestProxy(HttpClientProvider httpClientProvider, CircuitBreakerProvider circuitBreakerProvider) {
        this.httpClientProvider = httpClientProvider;
        this.circuitBreakerProvider = circuitBreakerProvider;
    }

    /**
     * Proxies the incoming request to the specified target URI using a client with the given properties.
     *
     * @param request The original HttpServletRequest.
     * @param response The original HttpServletResponse.
     * @param targetUri The URI of the backend service to forward the request to.
     * @param clientProperties The resolved HTTP client properties for this request.
     * @param resilienceProperties The resolved resilience properties for this request.
     * @throws IOException If an I/O error occurs during proxying.
     * @throws InterruptedException If the operation is interrupted.
     */
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response, URI targetUri,
                             HttpClientProperties clientProperties, ResilienceProperties resilienceProperties)
            throws IOException, InterruptedException {

        HttpClient httpClient = httpClientProvider.getClient(clientProperties);
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        requestBuilder.method(request.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return request.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Failed to get request input stream", e);
            }
        }));

        String queryString = request.getQueryString();
        String finalPath = targetUri.getPath() + request.getRequestURI().substring(request.getContextPath().length());
        URI finalTargetUri = URI.create(targetUri.getScheme() + "://" + targetUri.getAuthority() + finalPath + (queryString != null ? "?" + queryString : ""));
        requestBuilder.uri(finalTargetUri);

        if (clientProperties.getResponseTimeout() != null) {
            requestBuilder.timeout(clientProperties.getResponseTimeout());
        }

        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!isHopByHopHeader(headerName) && !headerName.equalsIgnoreCase("host") && !headerName.equalsIgnoreCase("content-length")) {
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    requestBuilder.header(headerName, headerValues.nextElement());
                }
            }
        }

        CircuitBreaker circuitBreaker = circuitBreakerProvider.getCircuitBreaker(targetUri, resilienceProperties);
        HttpRequest httpRequest = requestBuilder.build();

        try {
            HttpResponse<InputStream> backendResponse = circuitBreaker.executeCompletionStage(
                    () -> httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofInputStream())
            ).toCompletableFuture().join();

            response.setStatus(backendResponse.statusCode());

            backendResponse.headers().map().forEach((name, values) -> {
                if (!isHopByHopHeader(name) && !name.equalsIgnoreCase("content-length") && !name.equalsIgnoreCase("transfer-encoding")) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });

            try (InputStream bodyStream = backendResponse.body()) {
                if (bodyStream != null) {
                    bodyStream.transferTo(response.getOutputStream());
                    response.getOutputStream().flush();
                }
            }
        } catch (CompletionException e) {
            // Unwrap the CompletionException to throw the original cause
            if (e.getCause() instanceof IOException) {
                throw (IOException) e.getCause();
            } else if (e.getCause() instanceof InterruptedException) {
                throw (InterruptedException) e.getCause();
            } else {
                throw new RuntimeException(e.getCause());
            }
        }
    }

    private boolean isHopByHopHeader(String headerName) {
        String lowerCaseHeaderName = headerName.toLowerCase();
        return lowerCaseHeaderName.equals("connection") ||
               lowerCaseHeaderName.equals("keep-alive") ||
               lowerCaseHeaderName.equals("proxy-authenticate") ||
               lowerCaseHeaderName.equals("proxy-authorization") ||
               lowerCaseHeaderName.equals("te") ||
               lowerCaseHeaderName.equals("trailers") ||
               lowerCaseHeaderName.equals("transfer-encoding") ||
               lowerCaseHeaderName.equals("upgrade");
    }
}
