package com.nanogate.security.model;

import java.util.List;
import java.util.Map;

/**
 * Configuration for JWT/OAuth2 security policies.
 */
public class SecurityProperties {
    private Boolean enabled;
    private List<String> requiredScopes;
    private List<String> requiredRoles;
    private Map<String, String> forwardClaims;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public List<String> getRequiredScopes() {
        return requiredScopes;
    }

    public void setRequiredScopes(List<String> requiredScopes) {
        this.requiredScopes = requiredScopes;
    }

    public List<String> getRequiredRoles() {
        return requiredRoles;
    }

    public void setRequiredRoles(List<String> requiredRoles) {
        this.requiredRoles = requiredRoles;
    }

    public Map<String, String> getForwardClaims() {
        return forwardClaims;
    }

    public void setForwardClaims(Map<String, String> forwardClaims) {
        this.forwardClaims = forwardClaims;
    }
}
