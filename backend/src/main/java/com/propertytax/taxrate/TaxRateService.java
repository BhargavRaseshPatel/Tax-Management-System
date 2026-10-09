package com.propertytax.taxrate;

import java.util.List;

import com.propertytax.common.ApiErrors;
import com.propertytax.taxbill.TaxBill;
import com.propertytax.taxbill.TaxBillRepository;
import com.propertytax.taxrate.TaxRateDtos.TaxRateRequest;
import com.propertytax.taxrate.TaxRateDtos.TaxRateResponse;
import com.propertytax.taxrate.TaxRateDtos.TaxRateUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxRateService {

	private final TaxRateRepository taxRates;
	private final TaxBillRepository taxBills;

	TaxRateService(TaxRateRepository taxRates, TaxBillRepository taxBills) {
		this.taxRates = taxRates;
		this.taxBills = taxBills;
	}

	public TaxRateResponse create(TaxRateRequest r) {
		return TaxRateResponse.from(taxRates.save(new TaxRate(r.taxYear(), r.propertyClass(), r.rate(), r.description())));
	}

	/** Small table (years x 3 classes), so no pagination. */
	@Transactional(readOnly = true)
	public List<TaxRateResponse> list(Integer taxYear) {
		var rates = taxYear == null ? taxRates.findAllByOrderByTaxYearDescPropertyClass()
				: taxRates.findByTaxYearOrderByPropertyClass(taxYear);
		return rates.stream().map(TaxRateResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public TaxRateResponse get(Long id) {
		return TaxRateResponse.from(taxRates.require(id));
	}

	/** A rate that priced a live bill is frozen; cancel the bill first to correct it. */
	public TaxRateResponse update(Long id, TaxRateUpdateRequest r) {
		var rate = taxRates.require(id);
		if (taxBills.existsByTaxRateIdAndStatusNot(id, TaxBill.Status.CANCELLED)) {
			throw ApiErrors.conflict("Tax rate " + id + " is used by a live tax bill and can't be changed");
		}
		rate.update(r.rate(), r.description());
		taxRates.flush();
		return TaxRateResponse.from(rate);
	}

}
