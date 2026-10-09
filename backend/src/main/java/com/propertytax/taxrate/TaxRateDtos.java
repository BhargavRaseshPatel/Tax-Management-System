package com.propertytax.taxrate;

import java.math.BigDecimal;
import java.time.Instant;

import com.propertytax.property.PropertyClass;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class TaxRateDtos {

	private TaxRateDtos() {}

	/** rate is a fraction: 0.0075 means 0.75% of assessed value. */
	public record TaxRateRequest(
			@NotNull @Min(2000) @Max(2100) Integer taxYear,
			@NotNull PropertyClass propertyClass,
			@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false)
			@Digits(integer = 1, fraction = 8) BigDecimal rate,
			@Size(max = 255) String description) {}

	/** Year and class identify the rate and can't change; only the rate and description can. */
	public record TaxRateUpdateRequest(
			@NotNull @DecimalMin(value = "0", inclusive = false) @DecimalMax(value = "1", inclusive = false)
			@Digits(integer = 1, fraction = 8) BigDecimal rate,
			@Size(max = 255) String description) {}

	public record TaxRateResponse(Long id, Integer taxYear, PropertyClass propertyClass, BigDecimal rate,
			String description, Instant createdAt, Instant updatedAt) {

		static TaxRateResponse from(TaxRate t) {
			return new TaxRateResponse(t.getId(), t.getTaxYear(), t.getPropertyClass(), t.getRate(),
					t.getDescription(), t.getCreatedAt(), t.getUpdatedAt());
		}
	}

}
