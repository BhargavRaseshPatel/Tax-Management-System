package com.propertytax.assessment;

import java.util.List;

import com.propertytax.assessment.AssessmentDtos.AssessmentRequest;
import com.propertytax.assessment.AssessmentDtos.AssessmentResponse;
import com.propertytax.property.PropertyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AssessmentService {

	private final AssessmentRepository assessments;
	private final PropertyRepository properties;

	AssessmentService(AssessmentRepository assessments, PropertyRepository properties) {
		this.assessments = assessments;
		this.properties = properties;
	}

	public AssessmentResponse create(Long propertyId, AssessmentRequest r) {
		var property = properties.require(propertyId);
		var assessment = new Assessment(property, r.taxYear(), r.assessedValue(), r.assessmentDate());
		return AssessmentResponse.from(assessments.save(assessment));
	}

	@Transactional(readOnly = true)
	public List<AssessmentResponse> forProperty(Long propertyId) {
		properties.require(propertyId);
		return assessments.findByPropertyIdOrderByTaxYearDesc(propertyId).stream().map(AssessmentResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public AssessmentResponse get(Long id) {
		return AssessmentResponse.from(assessments.require(id));
	}

	public AssessmentResponse update(Long id, AssessmentRequest r) {
		var assessment = assessments.require(id);
		assessment.update(r.taxYear(), r.assessedValue(), r.assessmentDate());
		assessments.flush();
		return AssessmentResponse.from(assessment);
	}

}
