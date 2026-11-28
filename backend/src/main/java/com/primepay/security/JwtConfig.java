package com.primepay.security;

import java.util.Base64;
import java.security.SecureRandom;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

@Configuration
public class JwtConfig {
    private static final int MINIMUM_SECRET_BYTES = 32;
    private static final Logger logger = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    SecretKey jwtSecretKey(
            @Value("${app.security.jwt.secret-base64:}") String encodedSecret,
            @Value("${app.security.jwt.allow-ephemeral-dev-secret:false}") boolean allowEphemeralDevSecret,
            Environment environment) {
        if (encodedSecret.isBlank()) {
            if (!allowEphemeralDevSecret || !environment.acceptsProfiles(Profiles.of("dev"))) {
                throw new IllegalStateException("Set JWT_SECRET_BASE64 to a securely generated secret of at least 32 bytes.");
            }
            byte[] generatedSecret = new byte[MINIMUM_SECRET_BYTES];
            new SecureRandom().nextBytes(generatedSecret);
            encodedSecret = Base64.getEncoder().encodeToString(generatedSecret);
            logger.warn("Using an ephemeral JWT secret for the dev profile; issued tokens are invalid after restart.");
        }

        final byte[] secretBytes;
        try {
            secretBytes = Base64.getDecoder().decode(encodedSecret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must be valid standard Base64.", exception);
        }

        if (secretBytes.length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET_BASE64 must decode to at least 32 bytes for HS256.");
        }

        return new SecretKeySpec(secretBytes, "HmacSHA256");
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey secretKey) {
        return NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey secretKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<SecurityContext>(secretKey));
    }
}
