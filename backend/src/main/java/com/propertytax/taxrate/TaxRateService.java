package com.propertytax.taxrate;

import java.util.List;

import com.propertytax.taxrate.TaxRateDtos.TaxRateRequest;
import com.propertytax.taxrate.TaxRateDtos.TaxRateResponse;
import com.propertytax.taxrate.TaxRateDtos.TaxRateUpdateRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxRateService {

	private final TaxRateRepository taxRates;

	TaxRateService(TaxRateRepository taxRates) {
		this.taxRates = taxRates;
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

	public TaxRateResponse update(Long id, TaxRateUpdateRequest r) {
		var rate = taxRates.require(id);
		rate.update(r.rate(), r.description());
		taxRates.flush();
		return TaxRateResponse.from(rate);
	}

}
