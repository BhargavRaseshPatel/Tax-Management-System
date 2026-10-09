package com.propertytax.user;

import com.propertytax.owner.Owner;
import com.propertytax.owner.OwnerRepository;
import com.propertytax.security.SecurityConfig;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "1. Users")
@RestController
class MeController {

	/** The signed-in user; ownerId is set when the login is linked to an owner record (residents). */
	record MeResponse(Long id, String email, String fullName, User.Role role, Long ownerId) {}

	private final UserRepository users;
	private final OwnerRepository owners;

	MeController(UserRepository users, OwnerRepository owners) {
		this.users = users;
		this.owners = owners;
	}

	@GetMapping("/api/me")
	@Transactional(readOnly = true)
	MeResponse me(Authentication auth) {
		var user = users.require(SecurityConfig.userId(auth));
		var ownerId = owners.findByUserId(user.getId()).map(Owner::getId).orElse(null);
		return new MeResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), ownerId);
	}

}
