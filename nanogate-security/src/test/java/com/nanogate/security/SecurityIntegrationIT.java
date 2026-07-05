package com.nanogate.security;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.nanogate.security.service.RouteSecurityResolver;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("it")
@WireMockTest(httpPort = 8089)
class SecurityIntegrationIT {

    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @MockitoBean
    private RouteSecurityResolver routeSecurityResolver;

    private static RSAKey rsaKey;
    private static String validJwt;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://localhost:8089/jwks.json");
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class TestConfig {
        @org.springframework.context.annotation.Bean
        public org.springframework.security.oauth2.jwt.JwtDecoder jwtDecoder() {
            return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withJwkSetUri("http://localhost:8089/jwks.json").build();
        }

        @org.springframework.web.bind.annotation.RestController
        static class DummyController {
            @org.springframework.web.bind.annotation.GetMapping("/api/secured")
            public String secured() {
                return "ok";
            }
        }
    }

    @BeforeAll
    static void generateKeys() throws Exception {
        rsaKey = new RSAKeyGenerator(2048)
                .keyID("test-key-id")
                .generate();

        JWKSet jwkSet = new JWKSet(rsaKey);
        
        // Setup valid JWT
        RSASSASigner signer = new RSASSASigner(rsaKey);
        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject("alice")
                .issuer("http://localhost:8089")
                .expirationTime(new Date(new Date().getTime() + 60 * 1000))
                .claim("scp", List.of("read", "write"))
                .claim("roles", List.of("admin"))
                .build();

        SignedJWT signedJWT = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(rsaKey.getKeyID()).build(),
                claimsSet);

        signedJWT.sign(signer);
        validJwt = signedJWT.serialize();
    }

    @BeforeEach
    void setUp(WireMockRuntimeInfo wmRuntimeInfo) {
        // Host the JWKS on WireMock
        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get("/jwks.json")
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(new JWKSet(rsaKey.toPublicJWK()).toString())));

        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(context.getBean(com.nanogate.security.filter.JwtSecurityFilter.class))
                .build();

        // Default mock behavior: enable security for /api/secured
        when(routeSecurityResolver.resolvePolicy(any())).thenAnswer(invocation -> {
            jakarta.servlet.http.HttpServletRequest req = invocation.getArgument(0);
            if (req.getRequestURI().startsWith("/api/secured")) {
                return new RouteSecurityResolver.ResolvedSecurityPolicy(
                        true,
                        List.of("read"),
                        List.of("admin"),
                        Collections.emptyMap(),
                        "test-route"
                );
            }
            return new RouteSecurityResolver.ResolvedSecurityPolicy(
                    false,
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyMap(),
                    null
            );
        });
    }

    @Test
    void shouldRejectRequestWithoutToken() throws Exception {
        mockMvc.perform(get("/api/secured"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/api/secured")
                        .header("Authorization", "Bearer invalid-junk"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAcceptValidToken() throws Exception {
        mockMvc.perform(get("/api/secured")
                        .header("Authorization", "Bearer " + validJwt))
                .andExpect(status().isOk());
    }
}
