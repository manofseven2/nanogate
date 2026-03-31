package com.nanogate.routing.service;

import com.nanogate.routing.model.HttpClientProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Enumeration;

/**
 * Service responsible for proxying the incoming HttpServletRequest to a target URI.
 * It uses a provided HttpClient instance to perform the request forwarding.
 */
@Service
public class RequestProxy {

    private final HttpClientProvider httpClientProvider;

    public RequestProxy(HttpClientProvider httpClientProvider) {
        this.httpClientProvider = httpClientProvider;
    }

    /**
     * Proxies the incoming request to the specified target URI using a client with the given properties.
     *
     * @param request The original HttpServletRequest.
     * @param response The original HttpServletResponse.
     * @param targetUri The URI of the backend service to forward the request to.
     * @param clientProperties The resolved HTTP client properties for this request.
     * @throws IOException If an I/O error occurs during proxying.
     * @throws InterruptedException If the operation is interrupted.
     */
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response, URI targetUri, HttpClientProperties clientProperties)
            throws IOException, InterruptedException {

        // Get a cached HttpClient instance for this request's configuration
        HttpClient httpClient = httpClientProvider.getClient(clientProperties);

        // 1. Build the HttpRequest for the backend
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder();

        // Copy method
        requestBuilder.method(request.getMethod(), HttpRequest.BodyPublishers.ofInputStream(() -> {
            try {
                return request.getInputStream();
            } catch (IOException e) {
                throw new RuntimeException("Failed to get request input stream", e);
            }
        }));

        // Construct the new target URI, preserving query parameters
        String newPath = targetUri.getPath() + request.getRequestURI().substring(request.getContextPath().length());
        URI finalTargetUri = URI.create(targetUri.getScheme() + "://" + targetUri.getAuthority() + newPath + (request.getQueryString() != null ? "?" + request.getQueryString() : ""));
        requestBuilder.uri(finalTargetUri);

        // Apply response timeout if specified
        if (clientProperties.getResponseTimeout() != null) {
            requestBuilder.timeout(clientProperties.getResponseTimeout());
        }

        // Copy headers (excluding hop-by-hop headers)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!isHopByHopHeader(headerName)) {
                Enumeration<String> headerValues = request.getHeaders(headerName);
                while (headerValues.hasMoreElements()) {
                    requestBuilder.header(headerName, headerValues.nextElement());
                }
            }
        }

        // 2. Send the request
        HttpResponse<byte[]> backendResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofByteArray());

        // 3. Copy backend response to original HttpServletResponse
        response.setStatus(backendResponse.statusCode());

        backendResponse.headers().map().forEach((name, values) -> {
            if (!isHopByHopHeader(name)) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });

        response.getOutputStream().write(backendResponse.body());
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