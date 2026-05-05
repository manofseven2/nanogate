package com.nanogate.security.jwt;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collections;
import java.util.List;

/**
 * Validates that the JWT contains at least one audience from the trusted whitelist.
 */
public class JwtAudienceValidator implements OAuth2TokenValidator<Jwt> {

    private final List<String> allowedAudiences;

    public JwtAudienceValidator(List<String> allowedAudiences) {
        this.allowedAudiences = allowedAudiences != null ? allowedAudiences : Collections.emptyList();
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (allowedAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }

        List<String> tokenAudiences = jwt.getAudience();
        if (tokenAudiences != null && !Collections.disjoint(tokenAudiences, allowedAudiences)) {
            return OAuth2TokenValidatorResult.success();
        }

        OAuth2Error error = new OAuth2Error("invalid_token", 
            "The token is not intended for any of the allowed audiences: " + allowedAudiences, null);
        return OAuth2TokenValidatorResult.failure(error);
    }
}
