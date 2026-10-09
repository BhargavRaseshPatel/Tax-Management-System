package com.propertytax;

import com.propertytax.security.DevTokenIssuer;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;

/** Real bearer tokens from the dev issuer, for one shared test user per role (created on first use). */
final class TestAuth {

	private TestAuth() {}

	static String bearer(UserRepository users, DevTokenIssuer issuer, User.Role role) {
		return bearer(users, issuer, role, "test-" + role.name().toLowerCase().replace('_', '-') + "@demo.test");
	}

	static String bearer(UserRepository users, DevTokenIssuer issuer, User.Role role, String email) {
		var user = users.findByEmail(email).orElseGet(() -> users.save(new User(email, "Test " + role, role)));
		return "Bearer " + issuer.issue(user).accessToken();
	}

}
