package com.propertytax.taxaccount;

import com.propertytax.common.ApiErrors;
import com.propertytax.property.PropertyRepository;
import com.propertytax.taxaccount.TaxAccountDtos.TaxAccountRequest;
import com.propertytax.taxaccount.TaxAccountDtos.TaxAccountResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class TaxAccountService {

	private final TaxAccountRepository taxAccounts;
	private final PropertyRepository properties;

	TaxAccountService(TaxAccountRepository taxAccounts, PropertyRepository properties) {
		this.taxAccounts = taxAccounts;
		this.properties = properties;
	}

	/** One account per property (DB unique), so the number can be derived from the property id. */
	public TaxAccountResponse open(TaxAccountRequest r) {
		var property = properties.require(r.propertyId());
		var account = new TaxAccount("TA-%08d".formatted(property.getId()), property);
		return TaxAccountResponse.from(taxAccounts.save(account));
	}

	@Transactional(readOnly = true)
	public PagedModel<TaxAccountResponse> list(Pageable pageable) {
		return new PagedModel<>(taxAccounts.findAll(pageable).map(TaxAccountResponse::from));
	}

	@Transactional(readOnly = true)
	public TaxAccountResponse get(Long id) {
		return TaxAccountResponse.from(taxAccounts.require(id));
	}

	@Transactional(readOnly = true)
	public TaxAccountResponse forProperty(Long propertyId) {
		properties.require(propertyId);
		return taxAccounts.findByPropertyId(propertyId).map(TaxAccountResponse::from)
				.orElseThrow(() -> ApiErrors.notFound("Tax account for property", propertyId));
	}

}
