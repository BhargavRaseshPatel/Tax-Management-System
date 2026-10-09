package com.propertytax.taxbill;

import java.util.List;

import com.propertytax.taxaccount.TaxAccountRepository;
import com.propertytax.taxbill.TaxBillDtos.TaxBillResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side only. Bill generation (interim/final, calculation, statuses) arrives in Stage 5. */
@Service
@Transactional(readOnly = true)
public class TaxBillService {

	private final TaxBillRepository taxBills;
	private final TaxAccountRepository taxAccounts;

	TaxBillService(TaxBillRepository taxBills, TaxAccountRepository taxAccounts) {
		this.taxBills = taxBills;
		this.taxAccounts = taxAccounts;
	}

	public List<TaxBillResponse> forAccount(Long taxAccountId) {
		taxAccounts.require(taxAccountId);
		return taxBills.findByTaxAccountIdOrderByTaxYearDescBillTypeAsc(taxAccountId).stream()
				.map(TaxBillResponse::from).toList();
	}

	public TaxBillResponse get(Long id) {
		return TaxBillResponse.from(taxBills.require(id));
	}

}
