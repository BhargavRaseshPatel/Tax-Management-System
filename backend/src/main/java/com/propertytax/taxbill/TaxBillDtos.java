package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public final class TaxBillDtos {

	private TaxBillDtos() {}

	/** The amount is never supplied by the client; it is calculated from the assessment and rate. */
	public record GenerateBillRequest(
			@NotNull @Min(2000) @Max(2100) Integer taxYear,
			@NotNull TaxBill.Type billType,
			@NotNull LocalDate issueDate,
			@NotNull LocalDate dueDate) {}

	public record TaxBillResponse(Long id, String billNumber, Long taxAccountId, Long assessmentId, Long taxRateId,
			Integer taxYear, TaxBill.Type billType, BigDecimal amount, BigDecimal paidAmount,
			BigDecimal outstandingAmount, LocalDate issueDate, LocalDate dueDate, TaxBill.Status status,
			Instant documentGeneratedAt, Instant createdAt, Instant updatedAt) {

		static TaxBillResponse from(TaxBill b, BigDecimal paid) {
			var outstanding = b.getStatus() == TaxBill.Status.CANCELLED ? BigDecimal.ZERO.setScale(2)
					: b.getAmount().subtract(paid);
			return new TaxBillResponse(b.getId(), b.getBillNumber(), b.getTaxAccount().getId(),
					b.getAssessment().getId(), b.getTaxRate().getId(), b.getTaxYear(), b.getBillType(), b.getAmount(),
					paid, outstanding, b.getIssueDate(), b.getDueDate(), b.getStatus(), b.getDocumentGeneratedAt(),
					b.getCreatedAt(), b.getUpdatedAt());
		}
	}

	/** Outstanding = live (non-cancelled) bills − RECORDED payments. Derived, never stored. */
	public record AccountBalanceResponse(Long taxAccountId, BigDecimal totalBilled, BigDecimal totalPaid,
			BigDecimal outstanding) {}

}
