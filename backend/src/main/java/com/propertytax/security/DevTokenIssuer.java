package com.propertytax.security;

import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jwt.JWTParser;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.SupplierJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Password-less sign-in (DEV_AUTH=true). Locally it stands in for Amazon Cognito so the app runs without an AWS
 * account. Signs Cognito-shaped JWTs with an RSA key generated at startup, so every token dies with the process.
 *
 * Deployed, it only starts when PUBLIC_DEMO=true as well, and then works alongside Cognito as the "demo account"
 * buttons: it issues tokens for the fictional seed accounts only, never for a real (Cognito) account.
 * Anyone who can open the site can use those accounts, so a public demo must hold fictional data only.
 */
@Component
@ConditionalOnProperty(name = "app.dev-auth", havingValue = "true")
public class DevTokenIssuer {

	static final String ISSUER = "civictax-dev";
	static final Duration LIFETIME = Duration.ofHours(8);
	private static final Logger log = LoggerFactory.getLogger(DevTokenIssuer.class);

	public record IssuedToken(String accessToken, Instant expiresAt) {}

	/** Email domains of the fictional accounts created by scripts/seed-demo-data.sh. */
	static final Set<String> DEMO_DOMAINS = Set.of("civictax.demo", "example.ca");

	// ponytail: the key lives in this one process, so a public demo needs exactly one API task (ApiDesiredCount=1)
	// and every demo session ends at a redeploy. More tasks: load a shared key from Secrets Manager.
	private final RSAKey key = generateKey();
	private final JwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
	private final boolean publicDemo;

	DevTokenIssuer(@Value("${app.public-demo:false}") boolean publicDemo) {
		refuseOnEcs(System.getenv("ECS_CONTAINER_METADATA_URI_V4"), publicDemo);
		this.publicDemo = publicDemo;
		log.warn(publicDemo
				? "PUBLIC_DEMO is enabled: anyone can sign in as a fictional demo account without a password."
				: "DEV_AUTH is enabled: local token issuer active. Never enable this outside local development.");
	}

	/** ECS sets that variable in every task. DEV_AUTH on its own must never serve there: stop instead. */
	static void refuseOnEcs(String ecsMetadataUri, boolean publicDemo) {
		if (ecsMetadataUri != null && !ecsMetadataUri.isBlank() && !publicDemo) {
			throw new IllegalStateException("DEV_AUTH must never be enabled on ECS: it lets anyone sign in as any user"
					+ " (a deliberate public demo also sets PUBLIC_DEMO=true)");
		}
	}

	/** Public demo: only the fictional seed accounts can be entered without a password. Locally: anyone. */
	boolean maySignIn(User user) {
		return maySignIn(publicDemo, user.getEmail());
	}

	static boolean maySignIn(boolean publicDemo, String email) {
		return !publicDemo || DEMO_DOMAINS.contains(email.substring(email.indexOf('@') + 1));
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

	/**
	 * Takes precedence over Boot's Cognito decoder (Boot only auto-configures one when none exists). With a Cognito
	 * issuer configured as well (public demo), each token goes to the decoder for its issuer; both check the
	 * signature, issuer and expiry themselves, so reading the issuer first decides nothing about validity.
	 */
	@Bean
	JwtDecoder devJwtDecoder(@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:}") String cognitoIssuer,
			@Value("${spring.security.oauth2.resourceserver.jwt.audiences:}") List<String> audiences) throws Exception {
		var dev = NimbusJwtDecoder.withPublicKey(key.toRSAPublicKey()).build();
		dev.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		if (cognitoIssuer.isBlank()) {
			return dev;
		}
		JwtDecoder cognito = new SupplierJwtDecoder(() -> { // lazy: Cognito's keys are fetched on first use
			var decoder = NimbusJwtDecoder.withIssuerLocation(cognitoIssuer).build();
			OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<List<String>>("aud",
					aud -> audiences.isEmpty() || (aud != null && !Collections.disjoint(aud, audiences)));
			decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
					JwtValidators.createDefaultWithIssuer(cognitoIssuer), audience));
			return decoder;
		});
		return token -> isDevToken(token) ? dev.decode(token) : cognito.decode(token);
	}

	static boolean isDevToken(String token) {
		try {
			return ISSUER.equals(JWTParser.parse(token).getJWTClaimsSet().getIssuer());
		} catch (java.text.ParseException e) {
			return false; // not a JWT at all: the Cognito decoder rejects it properly
		}
	}

	/** Guarantees one admin so the first token can be issued (the seed script signs in as this user). */
	@Bean
	ApplicationRunner devBootstrapAdmin(UserRepository users) {
		return args -> {
			if (users.findByEmail("s.chen@civictax.demo").isEmpty()) {
				users.save(new User("s.chen@civictax.demo", "Sarah Chen", User.Role.ADMIN));
				log.info("Created demo admin s.chen@civictax.demo");
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
