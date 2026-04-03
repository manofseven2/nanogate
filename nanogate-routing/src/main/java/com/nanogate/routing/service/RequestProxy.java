package com.nanogate.routing.service;

import com.nanogate.routing.model.HttpClientProperties;
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
        String queryString = request.getQueryString();
        String finalPath = targetUri.getPath() + request.getRequestURI().substring(request.getContextPath().length());
        URI finalTargetUri = URI.create(targetUri.getScheme() + "://" + targetUri.getAuthority() + finalPath + (queryString != null ? "?" + queryString : ""));
        requestBuilder.uri(finalTargetUri);

        // Apply response timeout if specified
        if (clientProperties.getResponseTimeout() != null) {
            requestBuilder.timeout(clientProperties.getResponseTimeout());
        }

        // Copy headers (excluding hop-by-hop headers and Host header)
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

        // 2. Send the request and expect an InputStream for the body (Streaming)
        HttpResponse<InputStream> backendResponse = httpClient.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofInputStream());

        // 3. Copy backend response headers to original HttpServletResponse FIRST
        response.setStatus(backendResponse.statusCode());

        backendResponse.headers().map().forEach((name, values) -> {
            if (!isHopByHopHeader(name) && !name.equalsIgnoreCase("content-length") && !name.equalsIgnoreCase("transfer-encoding")) {
                values.forEach(value -> response.addHeader(name, value));
            }
        });

        // 4. Stream the body to the client
        // Using transferTo() which inherently uses an 8192-byte buffer under the hood in the JDK.
        // This blocks the Virtual Thread if the client is slow, naturally propagating TCP backpressure
        // to the backend without causing OutOfMemoryErrors on the gateway.
        try (InputStream bodyStream = backendResponse.body()) {
            if (bodyStream != null) {
                bodyStream.transferTo(response.getOutputStream());
                // Flush the output stream at the end to ensure all remaining bytes are sent
                response.getOutputStream().flush();
            }
        } catch (IOException e) {
            // Client likely disconnected or pipe broke during streaming.
            // Virtual thread terminates cleanly.
            throw new IOException("Error while streaming response to client", e);
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
