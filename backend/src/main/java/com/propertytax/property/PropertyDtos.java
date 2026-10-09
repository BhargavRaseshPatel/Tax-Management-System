package com.propertytax.property;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class PropertyDtos {

	private PropertyDtos() {}

	public record PropertyRequest(
			@NotBlank @Size(max = 20) String rollNumber,
			@NotBlank @Size(max = 255) String streetAddress,
			@NotBlank @Size(max = 100) String city,
			@NotNull @Pattern(regexp = "^[A-Z][0-9][A-Z] [0-9][A-Z][0-9]$", message = "must match format A1A 1A1")
			String postalCode,
			@NotNull PropertyClass propertyClass) {}

	public record PropertyResponse(Long id, String rollNumber, String streetAddress, String city, String postalCode,
			PropertyClass propertyClass, Instant createdAt, Instant updatedAt) {

		static PropertyResponse from(Property p) {
			return new PropertyResponse(p.getId(), p.getRollNumber(), p.getStreetAddress(), p.getCity(),
					p.getPostalCode(), p.getPropertyClass(), p.getCreatedAt(), p.getUpdatedAt());
		}
	}

	public record OwnershipRequest(
			@NotNull Long ownerId,
			@NotNull @DecimalMin("0.01") @DecimalMax("100.00") @Digits(integer = 3, fraction = 2)
			BigDecimal ownershipPercent) {}

	public record OwnershipResponse(Long propertyId, Long ownerId, String ownerName, BigDecimal ownershipPercent) {

		static OwnershipResponse from(PropertyOwner po) {
			return new OwnershipResponse(po.getProperty().getId(), po.getOwner().getId(), po.getOwner().getFullName(),
					po.getOwnershipPercent());
		}
	}

}
