package com.propertytax.user;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class UserDtos {

	private UserDtos() {}

	public record CreateUserRequest(
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(max = 150) String fullName,
			@NotNull User.Role role) {}

	public record UpdateUserRequest(
			@NotBlank @Size(max = 150) String fullName,
			@NotNull User.Role role,
			@NotNull Boolean active) {}

	public record UserResponse(Long id, String email, String fullName, User.Role role, boolean active,
			Instant createdAt, Instant updatedAt) {

		static UserResponse from(User u) {
			return new UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole(), u.isActive(),
					u.getCreatedAt(), u.getUpdatedAt());
		}
	}

}
