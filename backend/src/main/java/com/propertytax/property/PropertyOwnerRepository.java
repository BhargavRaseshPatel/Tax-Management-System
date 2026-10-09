package com.propertytax.property;

import java.util.List;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PropertyOwnerRepository extends JpaRepository<PropertyOwner, Long> {

	/** An owner's properties, fetched in the same query (no lazy load per row). */
	@EntityGraph(attributePaths = "property")
	List<PropertyOwner> findByOwnerId(Long ownerId);

}
