package com.nanogate.routing.config;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.CorsProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * A custom configuration source that resolves CORS properties dynamically
 * by cascading from Route -> BackendSet -> Global configuration.
 */
@Component
public class NanoGateCorsConfigurationSource implements CorsConfigurationSource {

    private final NanoGateRouteProperties routeProperties;

    public NanoGateCorsConfigurationSource(NanoGateRouteProperties routeProperties) {
        this.routeProperties = routeProperties;
    }

    @Override
    public CorsConfiguration getCorsConfiguration(HttpServletRequest request) {
        Route route = (Route) request.getAttribute("NANO_ROUTE");
        
        CorsProperties corsProperties = resolveCorsProperties(route);

        if (corsProperties == null || Boolean.FALSE.equals(corsProperties.getEnabled())) {
            return null; // Null means no CORS handling for this request
        }

        return buildCorsConfiguration(corsProperties);
    }

    private CorsProperties resolveCorsProperties(Route route) {
        if (route == null) {
            return routeProperties.getDefaultCors();
        }

        if (route.getCors() != null) {
            return route.getCors();
        }

        if (route.getBackendSet() != null) {
            BackendSet backendSet = routeProperties.getBackendSet(route.getBackendSet());
            if (backendSet != null && backendSet.getCors() != null) {
                return backendSet.getCors();
            }
        }

        return routeProperties.getDefaultCors();
    }

    private CorsConfiguration buildCorsConfiguration(CorsProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        
        if (properties.getAllowedOrigins() != null) {
            config.setAllowedOrigins(properties.getAllowedOrigins());
        }
        if (properties.getAllowedMethods() != null) {
            config.setAllowedMethods(properties.getAllowedMethods());
        }
        if (properties.getAllowedHeaders() != null) {
            config.setAllowedHeaders(properties.getAllowedHeaders());
        }
        if (properties.getExposedHeaders() != null) {
            config.setExposedHeaders(properties.getExposedHeaders());
        }
        if (properties.getAllowCredentials() != null) {
            config.setAllowCredentials(properties.getAllowCredentials());
        }
        if (properties.getMaxAge() != null) {
            config.setMaxAge(properties.getMaxAge());
        }

        return config;
    }
}
