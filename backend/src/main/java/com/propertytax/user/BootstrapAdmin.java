package com.propertytax.user;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * A brand-new deployment has no users, so nobody could sign in to create the first one. When BOOTSTRAP_ADMIN_EMAIL
 * is set and the users table is empty, that email becomes an ADMIN; they then sign in (Cognito) and add everyone
 * else. Does nothing once any user exists, so it cannot be used to add admins later.
 */
@Component
class BootstrapAdmin implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdmin.class);

	private final UserRepository users;
	private final String email;

	BootstrapAdmin(UserRepository users, @Value("${app.bootstrap-admin-email:}") String email) {
		this.users = users;
		this.email = email;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (email.isBlank() || users.count() > 0) {
			return;
		}
		try {
			users.save(new User(email, "Administrator", User.Role.ADMIN));
			log.info("Empty users table: created bootstrap admin {}", email);
		} catch (DataIntegrityViolationException alreadyCreated) {
			// the API and worker started together on an empty database and the other one got there first
		}
	}

}
