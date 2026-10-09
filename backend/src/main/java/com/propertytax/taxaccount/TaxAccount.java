package com.propertytax.taxaccount;

import com.propertytax.common.BaseEntity;
import com.propertytax.property.Property;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/** One per property. Outstanding balance is derived from bills and payments, never stored. */
@Entity
@Table(name = "tax_accounts")
public class TaxAccount extends BaseEntity {

	public enum Status { ACTIVE, CLOSED }

	private String accountNumber;

	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id")
	private Property property;

	@Enumerated(EnumType.STRING)
	private Status status = Status.ACTIVE;

	protected TaxAccount() {}

	public TaxAccount(String accountNumber, Property property) {
		this.accountNumber = accountNumber;
		this.property = property;
	}

	public String getAccountNumber() { return accountNumber; }
	public Property getProperty() { return property; }
	public Status getStatus() { return status; }

}
