package com.propertytax.taxaccount;

import java.util.Optional;

import com.propertytax.common.ApiErrors;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface TaxAccountRepository extends JpaRepository<TaxAccount, Long> {

	Optional<TaxAccount> findByPropertyId(Long propertyId);

	/** Row lock: serializes bill generation for one account (interim/final ordering, bill numbering). */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select a from TaxAccount a join fetch a.property where a.id = :id")
	Optional<TaxAccount> findByIdForUpdate(Long id);

	default TaxAccount require(Long id) {
		return findById(id).orElseThrow(() -> ApiErrors.notFound("Tax account", id));
	}

}
