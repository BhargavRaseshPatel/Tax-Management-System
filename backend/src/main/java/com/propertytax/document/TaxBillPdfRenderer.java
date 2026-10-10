package com.propertytax.document;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;

import com.propertytax.property.PropertyOwner;
import com.propertytax.taxbill.TaxBill;
import org.openpdf.text.Document;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

/**
 * Renders a tax bill as issued: what was billed and why. It deliberately shows no payment status, which changes
 * over time, so the stored PDF never goes stale. Every page is marked as a fictional demo document.
 */
@Component
public class TaxBillPdfRenderer {

	private static final Font TITLE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
	private static final Font HEADING = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
	private static final Font BODY = FontFactory.getFont(FontFactory.HELVETICA, 10);
	private static final Font SMALL = FontFactory.getFont(FontFactory.HELVETICA_OBLIQUE, 8);

	/** The bill's account, property, assessment and rate must be loadable (call inside a transaction). */
	public byte[] render(TaxBill bill, List<PropertyOwner> owners) {
		var property = bill.getTaxAccount().getProperty();
		var out = new ByteArrayOutputStream();
		var document = new Document(PageSize.LETTER, 54, 54, 54, 54);
		PdfWriter.getInstance(document, out);
		document.open();

		document.add(new Paragraph("CivicTax - Property Tax Bill", TITLE));
		document.add(new Paragraph("DEMONSTRATION DOCUMENT - fictional data, not a real tax bill or payment request.", SMALL));
		document.add(new Paragraph(" "));

		document.add(section("Bill", new String[][] {
				{ "Bill number", bill.getBillNumber() },
				{ "Type", bill.getBillType() + " " + bill.getTaxYear() },
				{ "Issue date", bill.getIssueDate().toString() },
				{ "Due date", bill.getDueDate().toString() },
				{ "Tax account", bill.getTaxAccount().getAccountNumber() } }));

		document.add(section("Property", new String[][] {
				{ "Roll number", property.getRollNumber() },
				{ "Address", property.getStreetAddress() + ", " + property.getCity() + " " + property.getPostalCode() },
				{ "Class", property.getPropertyClass().name() },
				{ "Owner(s)", owners.isEmpty() ? "-" : String.join("; ", owners.stream()
						.map(o -> o.getOwner().getFullName() + " (" + o.getOwnershipPercent().stripTrailingZeros().toPlainString() + "%)")
						.toList()) } }));

		document.add(section("Calculation basis", new String[][] {
				{ "Assessed value (" + bill.getAssessment().getTaxYear() + ")", money(bill.getAssessment().getAssessedValue()) },
				{ "Tax rate (" + bill.getTaxRate().getTaxYear() + ")", bill.getTaxRate().getRate().toPlainString() },
				{ "Method", bill.getBillType() == TaxBill.Type.INTERIM
						? "Interim: 50% of the previous year's annual tax"
						: "Final: annual tax (assessed value x rate) less any interim bill" } }));

		var total = new Paragraph("Amount billed: " + money(bill.getAmount()), TITLE);
		total.setAlignment(Element.ALIGN_RIGHT);
		total.setSpacingBefore(12);
		document.add(total);

		document.close();
		return out.toByteArray();
	}

	private static PdfPTable section(String heading, String[][] rows) {
		var table = new PdfPTable(new float[] { 1, 2 });
		table.setWidthPercentage(100);
		table.setSpacingBefore(10);
		var head = new PdfPCell(new Phrase(heading, HEADING));
		head.setColspan(2);
		head.setPadding(6);
		table.addCell(head);
		for (var row : rows) {
			table.addCell(cell(row[0]));
			table.addCell(cell(row[1]));
		}
		return table;
	}

	private static PdfPCell cell(String text) {
		var cell = new PdfPCell(new Phrase(text, BODY));
		cell.setPadding(5);
		return cell;
	}

	private static String money(BigDecimal amount) {
		return NumberFormat.getCurrencyInstance(Locale.CANADA).format(amount);
	}

}
