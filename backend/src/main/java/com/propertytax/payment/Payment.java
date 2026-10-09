package com.propertytax.payment;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.propertytax.common.BaseEntity;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A simulated payment record. Never a real financial transaction. */
@Entity
@Table(name = "payments")
public class Payment extends BaseEntity {

	public enum Method { CHEQUE, BANK_TRANSFER, PRE_AUTHORIZED, COUNTER }

	public enum Status { RECORDED, REVERSED }

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tax_bill_id")
	private TaxBill taxBill;

	private BigDecimal amount;
	private LocalDate paymentDate;

	@Enumerated(EnumType.STRING)
	private Method method;

	private String referenceNumber;

	@Enumerated(EnumType.STRING)
	private Status status = Status.RECORDED;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "recorded_by")
	private User recordedBy;

	protected Payment() {}

	public Payment(TaxBill taxBill, BigDecimal amount, LocalDate paymentDate, Method method, String referenceNumber,
			User recordedBy) {
		this.taxBill = taxBill;
		this.amount = amount;
		this.paymentDate = paymentDate;
		this.method = method;
		this.referenceNumber = referenceNumber;
		this.recordedBy = recordedBy;
	}

	public void reverse() {
		status = Status.REVERSED;
	}

	public TaxBill getTaxBill() { return taxBill; }
	public BigDecimal getAmount() { return amount; }
	public LocalDate getPaymentDate() { return paymentDate; }
	public Method getMethod() { return method; }
	public String getReferenceNumber() { return referenceNumber; }
	public Status getStatus() { return status; }
	public User getRecordedBy() { return recordedBy; }

}
