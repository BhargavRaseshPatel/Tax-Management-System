package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.propertytax.assessment.Assessment;
import com.propertytax.common.BaseEntity;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxrate.TaxRate;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "tax_bills")
public class TaxBill extends BaseEntity {

	public enum Type { INTERIM, FINAL }

	public enum Status { ISSUED, PARTIALLY_PAID, PAID, CANCELLED }

	private String billNumber;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tax_account_id")
	private TaxAccount taxAccount;

	/** The exact assessment and rate used to compute {@link #amount} (audit trail). */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "assessment_id")
	private Assessment assessment;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tax_rate_id")
	private TaxRate taxRate;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer taxYear;

	@Enumerated(EnumType.STRING)
	private Type billType;

	private BigDecimal amount;
	private LocalDate issueDate;
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	private Status status = Status.ISSUED;

	protected TaxBill() {}

	public TaxBill(String billNumber, TaxAccount taxAccount, Assessment assessment, TaxRate taxRate, int taxYear,
			Type billType, BigDecimal amount, LocalDate issueDate, LocalDate dueDate) {
		this.billNumber = billNumber;
		this.taxAccount = taxAccount;
		this.assessment = assessment;
		this.taxRate = taxRate;
		this.taxYear = taxYear;
		this.billType = billType;
		this.amount = amount;
		this.issueDate = issueDate;
		this.dueDate = dueDate;
	}

	/** Status follows the total of RECORDED payments. A zero-amount bill is immediately PAID. */
	public void applyPaidTotal(BigDecimal paid) {
		if (paid.compareTo(amount) >= 0) {
			status = Status.PAID;
		} else if (paid.signum() > 0) {
			status = Status.PARTIALLY_PAID;
		} else {
			status = Status.ISSUED;
		}
	}

	public void cancel() {
		status = Status.CANCELLED;
	}

	public String getBillNumber() { return billNumber; }
	public TaxAccount getTaxAccount() { return taxAccount; }
	public Assessment getAssessment() { return assessment; }
	public TaxRate getTaxRate() { return taxRate; }
	public Integer getTaxYear() { return taxYear; }
	public Type getBillType() { return billType; }
	public BigDecimal getAmount() { return amount; }
	public LocalDate getIssueDate() { return issueDate; }
	public LocalDate getDueDate() { return dueDate; }
	public Status getStatus() { return status; }

}
