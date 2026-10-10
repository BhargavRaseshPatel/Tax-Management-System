package com.propertytax.report;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Builds each report as CSV straight from PostgreSQL: one read-only query per report type, written out by a single
 * generic CSV writer. Reports are staff-only and may include officer email addresses.
 */
@Component
public class ReportGenerator {

	private static final Map<ReportJob.Type, String> QUERIES = Map.of(
			ReportJob.Type.TAX_ROLL_SUMMARY, """
					SELECT p.roll_number, p.street_address, p.city, p.property_class,
					       a.assessed_value, t.account_number,
					       COALESCE(b.billed, 0) AS billed, COALESCE(b.paid, 0) AS paid,
					       COALESCE(b.billed, 0) - COALESCE(b.paid, 0) AS outstanding
					FROM properties p
					LEFT JOIN assessments a ON a.property_id = p.id AND a.tax_year = ?
					LEFT JOIN tax_accounts t ON t.property_id = p.id
					LEFT JOIN LATERAL (
					    SELECT SUM(tb.amount) AS billed,
					           (SELECT COALESCE(SUM(py.amount), 0) FROM payments py
					            JOIN tax_bills pb ON pb.id = py.tax_bill_id
					            WHERE pb.tax_account_id = t.id AND pb.tax_year = ? AND pb.status <> 'CANCELLED'
					              AND py.status = 'RECORDED') AS paid
					    FROM tax_bills tb
					    WHERE tb.tax_account_id = t.id AND tb.tax_year = ? AND tb.status <> 'CANCELLED') b ON true
					ORDER BY p.roll_number""",
			ReportJob.Type.OUTSTANDING_BALANCES, """
					SELECT b.bill_number, b.bill_type, t.account_number, p.roll_number, p.street_address, b.due_date,
					       b.amount, COALESCE(pay.paid, 0) AS paid, b.amount - COALESCE(pay.paid, 0) AS outstanding
					FROM tax_bills b
					JOIN tax_accounts t ON t.id = b.tax_account_id
					JOIN properties p ON p.id = t.property_id
					LEFT JOIN (SELECT tax_bill_id, SUM(amount) AS paid FROM payments
					           WHERE status = 'RECORDED' GROUP BY tax_bill_id) pay ON pay.tax_bill_id = b.id
					WHERE b.tax_year = ? AND b.status IN ('ISSUED', 'PARTIALLY_PAID')
					ORDER BY b.due_date, b.bill_number""",
			ReportJob.Type.PAYMENTS_RECONCILIATION, """
					SELECT py.reference_number, py.payment_date, py.method, py.amount, py.status,
					       b.bill_number, t.account_number, u.email AS recorded_by
					FROM payments py
					JOIN tax_bills b ON b.id = py.tax_bill_id
					JOIN tax_accounts t ON t.id = b.tax_account_id
					JOIN users u ON u.id = py.recorded_by
					WHERE b.tax_year = ?
					ORDER BY py.payment_date, py.id""");

	private final JdbcTemplate jdbc;

	ReportGenerator(JdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	public byte[] generate(ReportJob.Type type, int taxYear) {
		var sql = QUERIES.get(type);
		// Every "?" in a report query is the tax year.
		var parameters = sql.chars().filter(c -> c == '?').mapToObj(c -> (Object) taxYear).toArray();
		var csv = jdbc.query(sql, rows -> {
			var meta = rows.getMetaData();
			var out = new StringBuilder();
			for (int i = 1; i <= meta.getColumnCount(); i++) {
				out.append(i == 1 ? "" : ",").append(cell(meta.getColumnLabel(i)));
			}
			out.append("\r\n");
			while (rows.next()) {
				for (int i = 1; i <= meta.getColumnCount(); i++) {
					out.append(i == 1 ? "" : ",").append(cell(rows.getString(i)));
				}
				out.append("\r\n");
			}
			return out.toString();
		}, parameters);
		return csv.getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * One CSV field (RFC 4180 quoting). Text starting with = + - @ gets a leading apostrophe so a spreadsheet
	 * shows it as text instead of running it as a formula (addresses and names are typed in by people).
	 */
	static String cell(String value) {
		if (value == null) {
			return "";
		}
		var text = !value.isEmpty() && "=+-@".indexOf(value.charAt(0)) >= 0 && !value.matches("-?\\d+(\\.\\d+)?")
				? "'" + value : value;
		return text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r")
				? "\"" + text.replace("\"", "\"\"") + "\"" : text;
	}

}
