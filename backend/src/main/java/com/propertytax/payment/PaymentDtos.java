package com.propertytax.payment;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class PaymentDtos {

	private PaymentDtos() {}

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
