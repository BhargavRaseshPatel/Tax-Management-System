package com.propertytax.security;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * LOCAL DEVELOPMENT ONLY (DEV_AUTH=true): stands in for Amazon Cognito so the app runs without an AWS account.
 * Signs Cognito-shaped JWTs with an RSA key generated at startup, so every token dies with the process.
 * Never enable in a deployed environment: anyone could mint a token for any user.
 */
@Component
@ConditionalOnProperty(name = "app.dev-auth", havingValue = "true")
public class DevTokenIssuer {

	static final String ISSUER = "civictax-dev";
	static final Duration LIFETIME = Duration.ofHours(8);
	private static final Logger log = LoggerFactory.getLogger(DevTokenIssuer.class);

	public record IssuedToken(String accessToken, Instant expiresAt) {}

	private final RSAKey key = generateKey();
	private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));

	DevTokenIssuer() {
		refuseOnEcs(System.getenv("ECS_CONTAINER_METADATA_URI_V4"));
		log.warn("DEV_AUTH is enabled: local token issuer active. Never enable this outside local development.");
	}

	/** ECS sets that variable in every task. If DEV_AUTH ever reaches a deployed task, stop instead of serving. */
	static void refuseOnEcs(String ecsMetadataUri) {
		if (ecsMetadataUri != null && !ecsMetadataUri.isBlank()) {
			throw new IllegalStateException("DEV_AUTH must never be enabled on ECS: it lets anyone sign in as any user");
		}
	}

	public IssuedToken issue(User user) {
		var now = Instant.now();
		var claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(UUID.nameUUIDFromBytes(user.getEmail().getBytes()).toString()) // Cognito subs are UUIDs
				.issuedAt(now)
				.expiresAt(now.plus(LIFETIME))
				.claim("email", user.getEmail())
				.claim("token_use", "id")
				.claim("cognito:groups", List.of(user.getRole().name())) // informational; the role is read from the DB
				.build();
		return new IssuedToken(encoder.encode(JwtEncoderParameters.from(claims)).getTokenValue(), claims.getExpiresAt());
	}

	/** Takes precedence over Cognito's decoder: Boot only auto-configures one when none exists. */
	@Bean
	JwtDecoder devJwtDecoder() throws Exception {
		var decoder = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
		decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		return decoder;
	}

	/** An empty database gets one admin so the first token can be issued (the seed script signs in as this user). */
	@Bean
	ApplicationRunner devBootstrapAdmin(UserRepository users) {
		return args -> {
			if (users.count() == 0) {
				users.save(new User("s.chen@civictax.demo", "Sarah Chen", User.Role.ADMIN));
				log.info("Empty database: created dev bootstrap admin s.chen@civictax.demo");
			}
		};
	}

	private static RSAKey generateKey() {
		try {
			var generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			var pair = generator.generateKeyPair();
			return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
					.privateKey((RSAPrivateKey) pair.getPrivate())
					.keyID(UUID.randomUUID().toString())
					.build();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
