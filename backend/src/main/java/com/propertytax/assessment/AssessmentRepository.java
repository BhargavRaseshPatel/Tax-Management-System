package com.propertytax.assessment;

import java.util.List;

import com.propertytax.common.ApiErrors;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssessmentRepository extends JpaRepository<Assessment, Long> {

	List<Assessment> findByPropertyIdOrderByTaxYearDesc(Long propertyId);

	default Assessment require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Assessment", id));
	}

}
