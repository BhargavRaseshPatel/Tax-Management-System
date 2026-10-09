package com.propertytax.taxaccount;

import java.util.Optional;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxAccountRepository extends JpaRepository<TaxAccount, Long> {

	Optional<TaxAccount> findByPropertyId(Long propertyId);

	default TaxAccount require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax account", id));
	}

}
