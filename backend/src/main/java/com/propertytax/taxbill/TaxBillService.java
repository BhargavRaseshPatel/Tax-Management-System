package com.propertytax.taxbill;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

import com.propertytax.assessment.Assessment;
import com.propertytax.assessment.AssessmentRepository;
import com.propertytax.common.ApiErrors;
import com.propertytax.taxaccount.TaxAccount;
import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBillDtos.AccountBalanceResponse;
import com.propertytax.taxbill.TaxBillDtos.GenerateBillRequest;
import com.propertytax.taxbill.TaxBillDtos.TaxBillResponse;
import com.propertytax.taxrate.TaxRate;
import com.propertytax.taxrate.TaxRateRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxBillService {

	private static final BigDecimal ZERO = new BigDecimal("0.00");

	private final TaxBillRepository taxBills;
	private final TaxAccountRepository taxAccounts;
	private final AssessmentRepository assessments;
	private final TaxRateRepository taxRates;

	TaxBillService(TaxBillRepository taxBills, TaxAccountRepository taxAccounts, AssessmentRepository assessments,
			TaxRateRepository taxRates) {
		this.taxBills = taxBills;
		this.taxAccounts = taxAccounts;
		this.assessments = assessments;
		this.taxRates = taxRates;
	}

	/**
	 * INTERIM: 50% of the previous year's annual tax (previous year's assessment × rate). Must precede the final.
	 * FINAL: this year's annual tax minus the live interim bill, if any.
	 * The account row is locked so concurrent requests can't both pass the ordering checks.
	 */
	public TaxBillResponse generate(Long accountId, GenerateBillRequest r) {
		if (r.dueDate().isBefore(r.issueDate())) {
			throw ApiErrors.badRequest("dueDate must not be before issueDate");
		}
		var account = taxAccounts.findByIdForUpdate(accountId)
				.orElseThrow(() -> ApiErrors.notFound("Tax account", accountId));
		if (account.getStatus() != TaxAccount.Status.ACTIVE) {
			throw ApiErrors.conflict("Tax account " + accountId + " is " + account.getStatus());
		}
		var liveBills = taxBills.findByTaxAccountIdAndTaxYearAndStatusNot(accountId, r.taxYear(), TaxBill.Status.CANCELLED);
		if (liveBills.stream().anyMatch(b -> b.getBillType() == r.billType())) {
			throw ApiErrors.conflict("A live " + r.billType() + " bill for " + r.taxYear() + " already exists");
		}

		var bill = switch (r.billType()) {
			case INTERIM -> interim(account, r, liveBills);
			case FINAL -> finalBill(account, r, liveBills);
		};
		bill.applyPaidTotal(ZERO); // a zero-amount bill is immediately PAID
		return TaxBillResponse.from(taxBills.save(bill), ZERO);
	}

	private TaxBill interim(TaxAccount account, GenerateBillRequest r, List<TaxBill> liveBills) {
		if (!liveBills.isEmpty()) {
			throw ApiErrors.conflict("An interim bill can't be issued after the final bill for " + r.taxYear());
		}
		int previousYear = r.taxYear() - 1;
		var assessment = assessmentFor(account, previousYear);
		var rate = rateFor(account, previousYear);
		var amount = TaxCalculator.interimAmount(TaxCalculator.annualTax(assessment.getAssessedValue(), rate.getRate()));
		return newBill(account, assessment, rate, r, amount);
	}

	private TaxBill finalBill(TaxAccount account, GenerateBillRequest r, List<TaxBill> liveBills) {
		var assessment = assessmentFor(account, r.taxYear());
		var rate = rateFor(account, r.taxYear());
		var annual = TaxCalculator.annualTax(assessment.getAssessedValue(), rate.getRate());
		var interimBilled = liveBills.stream().map(TaxBill::getAmount).reduce(ZERO, BigDecimal::add);
		var amount = TaxCalculator.finalAmount(annual, interimBilled);
		if (amount.signum() < 0) {
			// ponytail: no refunds/credits in scope; add a credit flow if interim overbilling must be supported
			throw ApiErrors.conflict("Interim billed " + interimBilled + " exceeds the " + r.taxYear()
					+ " annual tax " + annual + "; refunds are out of scope");
		}
		return newBill(account, assessment, rate, r, amount);
	}

	private TaxBill newBill(TaxAccount account, Assessment assessment, TaxRate rate, GenerateBillRequest r,
			BigDecimal amount) {
		// Unique even when a cancelled bill is reissued; safe because the account row is locked.
		long sequence = taxBills.countByTaxAccountIdAndTaxYearAndBillType(account.getId(), r.taxYear(), r.billType()) + 1;
		var billNumber = "%s-%d-%s-%d".formatted(account.getAccountNumber(), r.taxYear(),
				r.billType().name().charAt(0), sequence);
		return new TaxBill(billNumber, account, assessment, rate, r.taxYear(), r.billType(), amount, r.issueDate(),
				r.dueDate());
	}

	private Assessment assessmentFor(TaxAccount account, int year) {
		var propertyId = account.getProperty().getId();
		return assessments.findByPropertyIdAndTaxYear(propertyId, year)
				.orElseThrow(() -> ApiErrors.conflict("No " + year + " assessment for property " + propertyId));
	}

	private TaxRate rateFor(TaxAccount account, int year) {
		var propertyClass = account.getProperty().getPropertyClass();
		return taxRates.findByTaxYearAndPropertyClass(year, propertyClass)
				.orElseThrow(() -> ApiErrors.conflict("No " + year + " tax rate for " + propertyClass));
	}

	/** Only a bill with no RECORDED payments can be cancelled (reverse the payments first). */
	public TaxBillResponse cancel(Long billId) {
		var bill = taxBills.requireForUpdate(billId);
		if (bill.getStatus() == TaxBill.Status.CANCELLED) {
			throw ApiErrors.conflict("Tax bill " + billId + " is already cancelled");
		}
		var paid = taxBills.paidAmount(billId);
		if (paid.signum() > 0) {
			throw ApiErrors.conflict("Tax bill " + billId + " has recorded payments; reverse them first");
		}
		bill.cancel();
		taxBills.flush();
		return TaxBillResponse.from(bill, paid);
	}

	@Transactional(readOnly = true)
	public List<TaxBillResponse> forAccount(Long taxAccountId) {
		taxAccounts.require(taxAccountId);
		var paid = taxBills.paidTotalsForAccount(taxAccountId).stream()
				.collect(Collectors.toMap(TaxBillRepository.PaidTotal::billId, TaxBillRepository.PaidTotal::paid));
		return taxBills.findByTaxAccountIdOrderByTaxYearDescBillTypeAsc(taxAccountId).stream()
				.map(b -> TaxBillResponse.from(b, paid.getOrDefault(b.getId(), ZERO))).toList();
	}

	@Transactional(readOnly = true)
	public TaxBillResponse get(Long id) {
		return TaxBillResponse.from(taxBills.require(id), taxBills.paidAmount(id));
	}

	@Transactional(readOnly = true)
	public AccountBalanceResponse balance(Long taxAccountId) {
		taxAccounts.require(taxAccountId);
		var billed = taxBills.totalBilled(taxAccountId);
		var paid = taxBills.paidTotalsForAccount(taxAccountId).stream().map(TaxBillRepository.PaidTotal::paid)
				.reduce(ZERO, BigDecimal::add);
		return new AccountBalanceResponse(taxAccountId, billed, paid, billed.subtract(paid));
	}

}
