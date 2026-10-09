package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Simplified demo formula: Assessed Value × Applicable Tax Rate = Tax Amount.
 * All results are rounded to cents, half-up. Inputs are validated upstream (non-negative, 0 < rate < 1).
 */
public final class TaxCalculator {

	/** Interim bill = this share of the previous year's annual tax. */
	static final BigDecimal INTERIM_SHARE = new BigDecimal("0.50");

	private TaxCalculator() {}

	public static BigDecimal annualTax(BigDecimal assessedValue, BigDecimal rate) {
		return cents(assessedValue.multiply(rate));
	}

	public static BigDecimal interimAmount(BigDecimal previousYearAnnualTax) {
		return cents(previousYearAnnualTax.multiply(INTERIM_SHARE));
	}

	/** May be negative when the interim exceeded this year's tax; the caller decides what to do. */
	public static BigDecimal finalAmount(BigDecimal annualTax, BigDecimal interimBilled) {
		return cents(annualTax.subtract(interimBilled));
	}

	private static BigDecimal cents(BigDecimal value) {
		return value.setScale(2, RoundingMode.HALF_UP);
	}

}
