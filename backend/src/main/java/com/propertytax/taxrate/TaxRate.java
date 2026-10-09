package com.propertytax.taxrate;

import java.math.BigDecimal;

import com.propertytax.common.BaseEntity;
import com.propertytax.property.PropertyClass;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Fictional demo rate for one (tax year, property class). Not an official municipal rate. */
@Entity
@Table(name = "tax_rates")
public class TaxRate extends BaseEntity {

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer taxYear;

	@Enumerated(EnumType.STRING)
	private PropertyClass propertyClass;

	private BigDecimal rate;
	private String description;

	protected TaxRate() {}

	public TaxRate(int taxYear, PropertyClass propertyClass, BigDecimal rate, String description) {
		this.taxYear = taxYear;
		this.propertyClass = propertyClass;
		this.rate = rate;
		this.description = description;
	}

	public Integer getTaxYear() { return taxYear; }
	public PropertyClass getPropertyClass() { return propertyClass; }
	public BigDecimal getRate() { return rate; }
	public String getDescription() { return description; }

}
