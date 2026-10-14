package com.propertytax.security;

import java.util.List;
import java.util.Locale;

import com.propertytax.common.ApiErrors;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Password-less sign-in: pick a user, get a token. Local development, or the demo accounts of a public demo. */
@Tag(name = "0. Dev sign-in", description = "DEV_AUTH=true: local development, or the demo accounts of a public demo")
@RestController
@RequestMapping("/api/dev")
@ConditionalOnProperty(name = "app.dev-auth", havingValue = "true")
class DevAuthController {

	record DevUser(Long id, String email, String fullName, User.Role role) {
		static DevUser from(User u) {
			return new DevUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
		}
	}

	record TokenRequest(@NotBlank @Email String email) {}

	record TokenResponse(String accessToken, java.time.Instant expiresAt, DevUser user) {}

	private final UserRepository users;
	private final DevTokenIssuer issuer;

	DevAuthController(UserRepository users, DevTokenIssuer issuer) {
		this.users = users;
		this.issuer = issuer;
	}

	/** Active accounts for the sign-in picker. */
	@GetMapping("/users")
	List<DevUser> users() {
		return users.findAll(Sort.by("id")).stream().filter(User::isActive).filter(issuer::maySignIn)
				.map(DevUser::from).toList();
	}

	@PostMapping("/token")
	TokenResponse token(@Valid @RequestBody TokenRequest request) {
		var user = users.findByEmail(request.email().toLowerCase(Locale.ROOT))
				.filter(User::isActive)
				.filter(issuer::maySignIn) // a public demo never issues a token for a real account
				.orElseThrow(() -> ApiErrors.notFound("Active user", request.email()));
		var token = issuer.issue(user);
		return new TokenResponse(token.accessToken(), token.expiresAt(), DevUser.from(user));
	}

}
