package com.nanogate.security.model;

import java.util.List;

/**
 * Global security settings needed for JWT validation infrastructure.
 */
public class GlobalSecuritySettings extends SecurityProperties {
    private String issuerUri;
    private String jwksUri;
    private List<String> audiences;

    public String getIssuerUri() { return issuerUri; }
    public void setIssuerUri(String issuerUri) { this.issuerUri = issuerUri; }

    public String getJwksUri() { return jwksUri; }
    public void setJwksUri(String jwksUri) { this.jwksUri = jwksUri; }

    public List<String> getAudiences() { return audiences; }
    public void setAudiences(List<String> audiences) { this.audiences = audiences; }
}
