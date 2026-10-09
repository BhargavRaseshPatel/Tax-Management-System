package com.propertytax.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;

public final class PaymentDtos {

	private PaymentDtos() {}

	/**
	 * Records a simulated payment; no money moves. referenceNumber must be unique (duplicate guard).
	 * recordedByUserId is temporary: Stage 8 takes the recorder from the authenticated JWT instead.
	 */
	public record RecordPaymentRequest(
			@NotNull @DecimalMin("0.01") @Digits(integer = 12, fraction = 2) BigDecimal amount,
			@NotNull @PastOrPresent LocalDate paymentDate,
			@NotNull Payment.Method method,
			@NotBlank @Size(max = 50) String referenceNumber,
			@NotNull Long recordedByUserId) {}

	public record PaymentResponse(Long id, Long taxBillId, BigDecimal amount, LocalDate paymentDate,
			Payment.Method method, String referenceNumber, Payment.Status status, Long recordedById,
			Instant createdAt, Instant updatedAt) {

		static PaymentResponse from(Payment p) {
			return new PaymentResponse(p.getId(), p.getTaxBill().getId(), p.getAmount(), p.getPaymentDate(),
					p.getMethod(), p.getReferenceNumber(), p.getStatus(), p.getRecordedBy().getId(),
					p.getCreatedAt(), p.getUpdatedAt());
		}
	}

}
