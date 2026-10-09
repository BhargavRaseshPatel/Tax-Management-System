package com.propertytax.taxrate;

import java.util.List;
import java.util.Optional;

import com.propertytax.common.ApiErrors;
import com.propertytax.property.PropertyClass;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxRateRepository extends JpaRepository<TaxRate, Long> {

	List<TaxRate> findByTaxYearOrderByPropertyClass(Integer taxYear);

	List<TaxRate> findAllByOrderByTaxYearDescPropertyClass();

	Optional<TaxRate> findByTaxYearAndPropertyClass(Integer taxYear, PropertyClass propertyClass);

	default TaxRate require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax rate", id));
	}

}
