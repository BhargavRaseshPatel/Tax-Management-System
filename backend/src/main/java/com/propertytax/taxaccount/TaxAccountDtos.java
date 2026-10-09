package com.propertytax.taxaccount;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

public final class TaxAccountDtos {

	private TaxAccountDtos() {}

	public record TaxAccountRequest(@NotNull Long propertyId) {}

	public record TaxAccountResponse(Long id, String accountNumber, Long propertyId, TaxAccount.Status status,
			Instant createdAt, Instant updatedAt) {

		static TaxAccountResponse from(TaxAccount a) {
			return new TaxAccountResponse(a.getId(), a.getAccountNumber(), a.getProperty().getId(), a.getStatus(),
					a.getCreatedAt(), a.getUpdatedAt());
		}
	}

}
