package com.propertytax.user;

import java.util.Optional;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	default User require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("User", id));
	}

}
