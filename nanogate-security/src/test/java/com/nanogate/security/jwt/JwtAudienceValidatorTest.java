package com.nanogate.security.jwt;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JwtAudienceValidatorTest {

    @Test
    void shouldSuccessWhenAudienceMatches() {
        JwtAudienceValidator validator = new JwtAudienceValidator(List.of("app-1", "app-2"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of("app-1"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertFalse(result.hasErrors());
    }

    @Test
    void shouldSuccessWhenTokenHasMultipleAudiencesAndOneMatches() {
        JwtAudienceValidator validator = new JwtAudienceValidator(List.of("app-2"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(Arrays.asList("app-1", "app-2", "app-3"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertFalse(result.hasErrors());
    }

    @Test
    void shouldFailWhenAudienceDoesNotMatch() {
        JwtAudienceValidator validator = new JwtAudienceValidator(List.of("trusted-app"));
        Jwt jwt = mock(Jwt.class);
        when(jwt.getAudience()).thenReturn(List.of("malicious-app"));

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertTrue(result.hasErrors());
        assertEquals("invalid_token", result.getErrors().iterator().next().getErrorCode());
    }

    @Test
    void shouldSuccessWhenWhitelistIsEmpty() {
        JwtAudienceValidator validator = new JwtAudienceValidator(Collections.emptyList());
        Jwt jwt = mock(Jwt.class);

        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertFalse(result.hasErrors());
    }
}
