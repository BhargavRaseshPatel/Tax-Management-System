package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public final class TaxBillDtos {

	private TaxBillDtos() {}

	public record TaxBillResponse(Long id, String billNumber, Long taxAccountId, Long assessmentId, Long taxRateId,
			Integer taxYear, TaxBill.Type billType, BigDecimal amount, LocalDate issueDate, LocalDate dueDate,
			TaxBill.Status status, Instant createdAt, Instant updatedAt) {

		static TaxBillResponse from(TaxBill b) {
			return new TaxBillResponse(b.getId(), b.getBillNumber(), b.getTaxAccount().getId(),
					b.getAssessment().getId(), b.getTaxRate().getId(), b.getTaxYear(), b.getBillType(), b.getAmount(),
					b.getIssueDate(), b.getDueDate(), b.getStatus(), b.getCreatedAt(), b.getUpdatedAt());
		}
	}

}
