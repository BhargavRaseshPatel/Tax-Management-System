package com.propertytax.taxbill;

import java.util.List;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxBillRepository extends JpaRepository<TaxBill, Long> {

	List<TaxBill> findByTaxAccountIdOrderByTaxYearDescBillTypeAsc(Long taxAccountId);

	default TaxBill require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax bill", id));
	}

}
