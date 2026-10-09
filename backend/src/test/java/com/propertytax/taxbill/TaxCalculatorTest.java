package com.propertytax.taxbill;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring, no database. Fictional values. */
class TaxCalculatorTest {

	static BigDecimal d(String v) {
		return new BigDecimal(v);
	}

	@Test
	void annualTaxIsValueTimesRateRoundedToCents() {
		assertThat(TaxCalculator.annualTax(d("650000.00"), d("0.00750000"))).isEqualTo(d("4875.00"));
		// 650000 × 0.00751234 = 4883.021 → 4883.02
		assertThat(TaxCalculator.annualTax(d("650000.00"), d("0.00751234"))).isEqualTo(d("4883.02"));
	}

	@Test
	void halfCentRoundsUp() {
		// 100.10 × 0.05 = 5.005 → 5.01 (half-up, not banker's rounding)
		assertThat(TaxCalculator.annualTax(d("100.10"), d("0.05"))).isEqualTo(d("5.01"));
		// 4883.03 × 0.5 = 2441.515 → 2441.52
		assertThat(TaxCalculator.interimAmount(d("4883.03"))).isEqualTo(d("2441.52"));
	}

	@Test
	void zeroAssessmentGivesZeroTax() {
		assertThat(TaxCalculator.annualTax(d("0.00"), d("0.0075"))).isEqualTo(d("0.00"));
	}

	@Test
	void finalIsAnnualMinusInterimAndCanGoNegative() {
		assertThat(TaxCalculator.finalAmount(d("4875.00"), d("2325.00"))).isEqualTo(d("2550.00"));
		assertThat(TaxCalculator.finalAmount(d("4875.00"), d("0.00"))).isEqualTo(d("4875.00"));
		assertThat(TaxCalculator.finalAmount(d("2000.00"), d("2325.00"))).isNegative();
	}

}
