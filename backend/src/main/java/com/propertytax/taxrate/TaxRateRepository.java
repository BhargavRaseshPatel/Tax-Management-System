package com.propertytax.taxrate;

import java.util.List;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

	List<TaxRate> findByTaxYearOrderByPropertyClass(Integer taxYear);

	List<TaxRate> findAllByOrderByTaxYearDescPropertyClass();

	default TaxRate require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax rate", id));
	}

}
