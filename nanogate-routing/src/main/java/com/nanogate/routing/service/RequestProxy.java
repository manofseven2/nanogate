package com.nanogate.routing.service;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Enumeration;

/**
 * Service responsible for proxying the incoming HttpServletRequest to a target URI.
 * Uses Java's HttpClient for efficient, synchronous (with virtual threads) request forwarding.
 */
@Service
public class RequestProxy {

    private final HttpClient httpClient;

    public RequestProxy() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1) // Use HTTP/1.1 for now, can be upgraded later
                .connectTimeout(Duration.ofSeconds(10)) // Connection timeout
                .build();
    }

    /**
     * Proxies the incoming request to the specified target URI.
     *
     * @param request The original HttpServletRequest.
     * @param response The original HttpServletResponse.
     * @param targetUri The URI of the backend service to forward the request to.
     * @throws IOException If an I/O error occurs during proxying.
     * @throws InterruptedException If the operation is interrupted.
     */
    public void proxyRequest(HttpServletRequest request, HttpServletResponse response, URI targetUri)
            throws IOException, InterruptedException {

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

        // Copy headers (excluding hop-by-hop headers like Connection, Keep-Alive, Proxy-Authenticate, Proxy-Authorization, TE, Trailers, Transfer-Encoding, Upgrade)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            // Exclude headers that are managed by the client/server or are hop-by-hop
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
            // Exclude hop-by-hop headers from the response as well
            if (!isHopByHopHeader(name)) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });

        response.getOutputStream().write(backendResponse.body());
    }

    private boolean isHopByHopHeader(String headerName) {
        // List of HTTP/1.1 hop-by-hop headers
        // https://www.w3.org/Protocols/rfc2616/rfc2616-sec13.html#sec13.5.1
        String lowerCaseHeaderName = headerName.toLowerCase();
        return lowerCaseHeaderName.equals("connection") ||
               lowerCaseHeaderName.equals("keep-alive") ||
               lowerCaseHeaderName.equals("proxy-authenticate") ||
               lowerCaseHeaderName.equals("proxy-authorization") ||
               lowerCaseHeaderName.equals("te") || // Transfer-Encoding
               lowerCaseHeaderName.equals("trailers") ||
               lowerCaseHeaderName.equals("transfer-encoding") ||
               lowerCaseHeaderName.equals("upgrade");
    }
}