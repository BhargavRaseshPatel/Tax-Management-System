package com.propertytax.assessment;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.propertytax.common.BaseEntity;
import com.propertytax.property.Property;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "assessments")
public class Assessment extends BaseEntity {

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id")
	private Property property;

	@JdbcTypeCode(SqlTypes.SMALLINT)
	private Integer taxYear;

	private BigDecimal assessedValue;
	private LocalDate assessmentDate;

	protected Assessment() {}

	public Assessment(Property property, int taxYear, BigDecimal assessedValue, LocalDate assessmentDate) {
		this.property = property;
		this.taxYear = taxYear;
		this.assessedValue = assessedValue;
		this.assessmentDate = assessmentDate;
	}

	public Property getProperty() { return property; }
	public Integer getTaxYear() { return taxYear; }
	public BigDecimal getAssessedValue() { return assessedValue; }
	public LocalDate getAssessmentDate() { return assessmentDate; }

}
