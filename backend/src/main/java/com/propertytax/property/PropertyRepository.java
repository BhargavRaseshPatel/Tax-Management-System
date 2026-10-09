package com.propertytax.property;

import java.util.Optional;

import com.propertytax.common.ApiErrors;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PropertyRepository extends JpaRepository<Property, Long> {

	/** Case-insensitive match on roll number or street address. */
	@Query("""
			select p from Property p
			where lower(p.rollNumber) like lower(concat('%', :q, '%'))
			   or lower(p.streetAddress) like lower(concat('%', :q, '%'))
			""")
	Page<Property> search(String q, Pageable pageable);

	/** Row lock: serializes concurrent changes to one property's ownership shares. */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Property p where p.id = :id")
	Optional<Property> findByIdForUpdate(Long id);

	default Property require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Property", id));
	}

}
