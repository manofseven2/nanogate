package com.nanogate.security.config;

import com.nanogate.security.jwt.JwtAudienceValidator;
import com.nanogate.security.model.GlobalSecuritySettings;
import com.nanogate.security.service.GlobalSecurityProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;

import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnProperty(prefix = "nanogate.routing.security", name = "enabled", havingValue = "true")
public class SecurityAutoConfiguration {

    private final GlobalSecurityProvider securityProvider;

    public SecurityAutoConfiguration(GlobalSecurityProvider securityProvider) {
        this.securityProvider = securityProvider;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        GlobalSecuritySettings settings = securityProvider.getSettings();
        
        String issuerUri = settings.getIssuerUri();
        String jwksUri = settings.getJwksUri();

        NimbusJwtDecoder jwtDecoder;
        if (jwksUri != null && !jwksUri.isBlank()) {
            jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        } else if (issuerUri != null && !issuerUri.isBlank()) {
            jwtDecoder = NimbusJwtDecoder.withIssuerLocation(issuerUri).build();
        } else {
            return token -> { throw new JwtException("Security enabled but no issuer or JWKS URI configured."); };
        }

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        
        if (issuerUri != null && !issuerUri.isBlank()) {
            validators.add(new JwtIssuerValidator(issuerUri));
        }

        if (settings.getAudiences() != null && !settings.getAudiences().isEmpty()) {
            validators.add(new JwtAudienceValidator(settings.getAudiences()));
        }

        jwtDecoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        
        return jwtDecoder;
    }
}
