package com.propertytax.owner;

import java.time.Instant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class OwnerDtos {

	private OwnerDtos() {}

	/** userId is optional: links the owner to a RESIDENT login. */
	public record OwnerRequest(
			Long userId,
			@NotBlank @Size(max = 150) String fullName,
			@Email @Size(max = 255) String email,
			@Size(max = 30) String phone,
			@Size(max = 255) String mailingAddress) {}

	public record OwnerResponse(Long id, Long userId, String fullName, String email, String phone,
			String mailingAddress, Instant createdAt, Instant updatedAt) {

		static OwnerResponse from(Owner o) {
			return new OwnerResponse(o.getId(), o.getUser() == null ? null : o.getUser().getId(), o.getFullName(),
					o.getEmail(), o.getPhone(), o.getMailingAddress(), o.getCreatedAt(), o.getUpdatedAt());
		}
	}

}
