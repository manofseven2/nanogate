package com.nanogate.routing.security;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.config.RouteRegistry;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.InMemoryRouteLocator;
import com.nanogate.security.model.GlobalSecuritySettings;
import com.nanogate.security.model.SecurityProperties;
import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Implementation of RouteSecurityResolver that uses the RouteLocator and RouteRegistry.
 */
@Service
public class DefaultRouteSecurityResolver implements RouteSecurityResolver {

    private final InMemoryRouteLocator routeLocator;
    private final RouteRegistry routeRegistry;

    public DefaultRouteSecurityResolver(InMemoryRouteLocator routeLocator, RouteRegistry routeRegistry) {
        this.routeLocator = routeLocator;
        this.routeRegistry = routeRegistry;
    }

    @Override
    public ResolvedSecurityPolicy resolvePolicy(HttpServletRequest request) {
        Optional<Route> route = Optional.ofNullable((Route) request.getAttribute("nanogate.matched_route"));
        if (route.isEmpty()) {
            // Fallback for cases where RouteResolutionFilter hasn't run yet
            route = routeLocator.findRoute(request);
        }

        NanoGateRouteProperties globalProps = routeRegistry.get();
        GlobalSecuritySettings globalSecurity = globalProps.getSecurity();
        BackendSet backendSet = route.isPresent() ? globalProps.getBackendSet(route.get().getBackendSet()) : null;
        
        SecurityProperties bsSecurity = backendSet != null ? backendSet.getSecurity() : null;
        SecurityProperties routeSecurity = route.isPresent() ? route.get().getSecurity() : null;

        boolean enabled = resolveBoolean(routeSecurity, bsSecurity, globalSecurity);
        List<String> scopes = resolveList(routeSecurity, bsSecurity, globalSecurity, SecurityProperties::getRequiredScopes);
        List<String> roles = resolveList(routeSecurity, bsSecurity, globalSecurity, SecurityProperties::getRequiredRoles);
        Map<String, String> forwardClaims = resolveMap(routeSecurity, bsSecurity, globalSecurity);

        return new ResolvedSecurityPolicy(
            enabled, 
            scopes, 
            roles, 
            forwardClaims, 
            route.map(Route::getId).orElse(null)
        );
    }

    private boolean resolveBoolean(SecurityProperties route, SecurityProperties bs, SecurityProperties global) {
        if (route != null && route.getEnabled() != null) return route.getEnabled();
        if (bs != null && bs.getEnabled() != null) return bs.getEnabled();
        if (global != null && global.getEnabled() != null) return global.getEnabled();
        return false; 
    }

    private List<String> resolveList(SecurityProperties route, SecurityProperties bs, SecurityProperties global, 
                                     java.util.function.Function<SecurityProperties, List<String>> extractor) {
        List<String> val = route != null ? extractor.apply(route) : null;
        if (val == null) val = bs != null ? extractor.apply(bs) : null;
        if (val == null) val = global != null ? extractor.apply(global) : null;
        return val != null ? val : Collections.emptyList();
    }

    private Map<String, String> resolveMap(SecurityProperties route, SecurityProperties bs, SecurityProperties global) {
        Map<String, String> result = new HashMap<>();
        if (global != null && global.getForwardClaims() != null) result.putAll(global.getForwardClaims());
        if (bs != null && bs.getForwardClaims() != null) result.putAll(bs.getForwardClaims());
        if (route != null && route.getForwardClaims() != null) result.putAll(route.getForwardClaims());
        return result;
    }
}
