package com.propertytax.assessment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class AssessmentDtos {

	private AssessmentDtos() {}

	public record AssessmentRequest(
			@NotNull @Min(2000) @Max(2100) Integer taxYear,
			@NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal assessedValue,
			@NotNull LocalDate assessmentDate) {}

	public record AssessmentResponse(Long id, Long propertyId, Integer taxYear, BigDecimal assessedValue,
			LocalDate assessmentDate, Instant createdAt, Instant updatedAt) {

		static AssessmentResponse from(Assessment a) {
			return new AssessmentResponse(a.getId(), a.getProperty().getId(), a.getTaxYear(), a.getAssessedValue(),
					a.getAssessmentDate(), a.getCreatedAt(), a.getUpdatedAt());
		}
	}

}
