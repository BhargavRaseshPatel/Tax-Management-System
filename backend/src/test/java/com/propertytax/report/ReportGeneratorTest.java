package com.propertytax.report;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** CSV field rules: RFC 4180 quoting and spreadsheet-formula neutralising. */
class ReportGeneratorTest {

	@Test
	void plainValuesAreUnchangedAndNullIsEmpty() {
		assertThat(ReportGenerator.cell("742 Evergreen Terr")).isEqualTo("742 Evergreen Terr");
		assertThat(ReportGenerator.cell("1000.00")).isEqualTo("1000.00");
		assertThat(ReportGenerator.cell(null)).isEmpty();
	}

	@Test
	void commasQuotesAndNewlinesAreQuoted() {
		assertThat(ReportGenerator.cell("18 King St S, Suite 402")).isEqualTo("\"18 King St S, Suite 402\"");
		assertThat(ReportGenerator.cell("The \"Annex\"")).isEqualTo("\"The \"\"Annex\"\"\"");
		assertThat(ReportGenerator.cell("line1\nline2")).isEqualTo("\"line1\nline2\"");
	}

	@Test
	void textThatASpreadsheetWouldRunAsAFormulaIsNeutralised() {
		assertThat(ReportGenerator.cell("=HYPERLINK(\"http://evil\",\"x\")")).startsWith("\"'=HYPERLINK");
		assertThat(ReportGenerator.cell("+1 555 0100")).isEqualTo("'+1 555 0100");
		assertThat(ReportGenerator.cell("@cmd")).isEqualTo("'@cmd");
		assertThat(ReportGenerator.cell("-250.00")).isEqualTo("-250.00"); // a real negative number stays a number
	}

}
