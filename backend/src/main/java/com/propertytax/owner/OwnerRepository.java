package com.propertytax.owner;

import com.propertytax.common.ApiErrors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnerRepository extends JpaRepository<Owner, Long> {

	Page<Owner> findByFullNameContainingIgnoreCase(String name, Pageable pageable);

	default Owner require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Owner", id));
	}

}
