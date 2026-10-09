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

	// Resident data-ownership checks (used by SecurityConfig). A resident owns a record when their login is linked
	// to an owner of the property the record belongs to. Unknown ids simply return false.

	@Query("select count(o) > 0 from Owner o where o.id = :ownerId and o.user.id = :userId")
	boolean userIsOwner(Long userId, Long ownerId);

	@Query("select count(po) > 0 from PropertyOwner po where po.property.id = :propertyId and po.owner.user.id = :userId")
	boolean userOwnsProperty(Long userId, Long propertyId);

	@Query("""
			select count(po) > 0 from PropertyOwner po, Assessment a
			where a.id = :assessmentId and a.property = po.property and po.owner.user.id = :userId""")
	boolean userOwnsAssessment(Long userId, Long assessmentId);

	@Query("""
			select count(po) > 0 from PropertyOwner po, TaxAccount t
			where t.id = :accountId and t.property = po.property and po.owner.user.id = :userId""")
	boolean userOwnsAccount(Long userId, Long accountId);

	@Query("""
			select count(po) > 0 from PropertyOwner po, TaxBill b
			where b.id = :billId and b.taxAccount.property = po.property and po.owner.user.id = :userId""")
	boolean userOwnsBill(Long userId, Long billId);

	@Query("""
			select count(po) > 0 from PropertyOwner po, Payment p
			where p.id = :paymentId and p.taxBill.taxAccount.property = po.property and po.owner.user.id = :userId""")
	boolean userOwnsPayment(Long userId, Long paymentId);

}
