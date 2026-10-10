package com.propertytax.security;

import static org.springframework.http.HttpMethod.GET;

import java.util.List;
import java.util.Locale;
import java.util.function.BiPredicate;

import com.propertytax.property.PropertyOwnerRepository;
import com.propertytax.user.UserRepository;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;

/**
 * Every access rule in one place. The JWT only proves identity (its email claim); the role comes from the users
 * table, which admins manage, so a role change takes effect on the next request. Rules are evaluated top-down.
 *
 * Token source: Amazon Cognito in deployed environments (set SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_ISSUER_URI),
 * or the local dev issuer (DEV_AUTH=true). With neither configured there is no JwtDecoder and the app refuses to start.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

	private static final String TAX_OFFICER = "TAX_OFFICER";
	private static final String ADMIN = "ADMIN";

	@Bean
	SecurityFilterChain api(HttpSecurity http, PropertyOwnerRepository ownership,
			Converter<Jwt, AbstractAuthenticationToken> jwtToUser,
			@Value("${app.dev-auth:false}") boolean devAuth) throws Exception {
		http
			.csrf(csrf -> csrf.disable()) // stateless bearer tokens, no cookies → CSRF does not apply
			.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtToUser)))
			.authorizeHttpRequests(auth -> {
				auth.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
					.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
					// ponytail: API docs are public for local dev; Stage 16 disables or restricts them in production
					.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll();
				if (devAuth) {
					auth.requestMatchers("/api/dev/**").permitAll();
				}
				auth
					// Any signed-in user
					.requestMatchers(GET, "/api/me", "/api/tax-rates", "/api/tax-rates/*").authenticated()
					// Users: staff read (officers link owners to resident logins); only admins change them
					.requestMatchers(GET, "/api/users", "/api/users/*").hasAnyRole(ADMIN, TAX_OFFICER)
					.requestMatchers("/api/users/**").hasRole(ADMIN)
					// Residents may read only records tied to a property they own
					.requestMatchers(GET, "/api/owners/{id}", "/api/owners/{id}/properties")
						.access(staffOr(ownership::userIsOwner))
					.requestMatchers(GET, "/api/properties/{id}", "/api/properties/{id}/owners",
							"/api/properties/{id}/assessments", "/api/properties/{id}/tax-account")
						.access(staffOr(ownership::userOwnsProperty))
					.requestMatchers(GET, "/api/assessments/{id}").access(staffOr(ownership::userOwnsAssessment))
					.requestMatchers(GET, "/api/tax-accounts/{id}", "/api/tax-accounts/{id}/bills",
							"/api/tax-accounts/{id}/balance")
						.access(staffOr(ownership::userOwnsAccount))
					.requestMatchers(GET, "/api/tax-bills/{id}", "/api/tax-bills/{id}/payments", "/api/tax-bills/{id}/document")
						.access(staffOr(ownership::userOwnsBill))
					.requestMatchers(GET, "/api/payments/{id}").access(staffOr(ownership::userOwnsPayment))
					// Reports (request, track, download): staff only
					.requestMatchers("/api/reports", "/api/reports/**").hasAnyRole(ADMIN, TAX_OFFICER)
					// Everything else: staff read; only tax officers change tax data
					.requestMatchers(GET, "/api/**").hasAnyRole(ADMIN, TAX_OFFICER)
					.requestMatchers("/api/**").hasRole(TAX_OFFICER)
					.anyRequest().denyAll();
			});
		return http.build();
	}

	/** Staff pass; a resident passes only if they own the record named by the {id} path variable. */
	static AuthorizationManager<RequestAuthorizationContext> staffOr(BiPredicate<Long, Long> userOwns) {
		return (authentication, context) -> {
			var auth = authentication.get();
			if (hasRole(auth, TAX_OFFICER) || hasRole(auth, ADMIN)) {
				return new AuthorizationDecision(true);
			}
			try {
				long recordId = Long.parseLong(context.getVariables().get("id"));
				return new AuthorizationDecision(hasRole(auth, "RESIDENT") && userOwns.test(userId(auth), recordId));
			} catch (NumberFormatException e) {
				return new AuthorizationDecision(false);
			}
		};
	}

	private static boolean hasRole(Authentication auth, String role) {
		return auth != null && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
	}

	/** The authenticated user's id (the token's name is set to it by the converter below). */
	public static long userId(Authentication auth) {
		return Long.parseLong(auth.getName());
	} 

	/** JWT → our user: matched by email claim; unknown or deactivated users get 401. */
	@Bean
	Converter<Jwt, AbstractAuthenticationToken> jwtToUser(UserRepository users) {
		return jwt -> {
			var email = jwt.getClaimAsString("email");
			var user = email == null ? null : users.findByEmail(email.toLowerCase(Locale.ROOT)).orElse(null);
			if (user == null || !user.isActive()) {
				throw new BadCredentialsException("Unknown or deactivated user");
			}
			var authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole()));
			return new JwtAuthenticationToken(jwt, authorities, user.getId().toString());
		};
	}

}
