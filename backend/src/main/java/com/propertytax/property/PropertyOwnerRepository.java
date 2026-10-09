package com.propertytax.property;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PropertyOwnerRepository extends JpaRepository<PropertyOwner, Long> {

	/** An owner's properties, fetched in the same query (no lazy load per row). */
	@EntityGraph(attributePaths = "property")
	List<PropertyOwner> findByOwnerId(Long ownerId);

	/** A property's owners, fetched in the same query. */
	@EntityGraph(attributePaths = "owner")
	List<PropertyOwner> findByPropertyIdOrderById(Long propertyId);

	Optional<PropertyOwner> findByPropertyIdAndOwnerId(Long propertyId, Long ownerId);

	@Query("select coalesce(sum(po.ownershipPercent), 0) from PropertyOwner po where po.property.id = :propertyId")
	BigDecimal totalOwnershipPercent(Long propertyId);

}
