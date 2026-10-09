package com.propertytax.property;

import java.math.BigDecimal;
import java.util.List;

import com.propertytax.common.ApiErrors;
import com.propertytax.owner.OwnerRepository;
import com.propertytax.property.PropertyDtos.OwnershipRequest;
import com.propertytax.property.PropertyDtos.OwnershipResponse;
import com.propertytax.property.PropertyDtos.PropertyRequest;
import com.propertytax.property.PropertyDtos.PropertyResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class PropertyService {

	private static final BigDecimal FULL_OWNERSHIP = new BigDecimal("100");

	private final PropertyRepository properties;
	private final PropertyOwnerRepository propertyOwners;
	private final OwnerRepository owners;

	PropertyService(PropertyRepository properties, PropertyOwnerRepository propertyOwners, OwnerRepository owners) {
		this.properties = properties;
		this.propertyOwners = propertyOwners;
		this.owners = owners;
	}

	public PropertyResponse create(PropertyRequest r) {
		var property = new Property(r.rollNumber(), r.streetAddress(), r.city(), r.postalCode(), r.propertyClass());
		return PropertyResponse.from(properties.save(property));
	}

	@Transactional(readOnly = true)
	public PagedModel<PropertyResponse> search(String q, Pageable pageable) {
		var page = q == null || q.isBlank() ? properties.findAll(pageable) : properties.search(q.strip(), pageable);
		return new PagedModel<>(page.map(PropertyResponse::from));
	}

	@Transactional(readOnly = true)
	public PropertyResponse get(Long id) {
		return PropertyResponse.from(properties.require(id));
	}

	public PropertyResponse update(Long id, PropertyRequest r) {
		var property = properties.require(id);
		property.update(r.rollNumber(), r.streetAddress(), r.city(), r.postalCode(), r.propertyClass());
		properties.flush();
		return PropertyResponse.from(property);
	}

	@Transactional(readOnly = true)
	public List<OwnershipResponse> owners(Long propertyId) {
		properties.require(propertyId);
		return propertyOwners.findByPropertyIdOrderById(propertyId).stream().map(OwnershipResponse::from).toList();
	}

	@Transactional(readOnly = true)
	public List<PropertyResponse> propertiesOfOwner(Long ownerId) {
		owners.require(ownerId);
		return propertyOwners.findByOwnerId(ownerId).stream().map(po -> PropertyResponse.from(po.getProperty())).toList();
	}

	/** Shares on one property may not exceed 100%. The row lock stops two concurrent assigns both passing the check. */
	public OwnershipResponse assignOwner(Long propertyId, OwnershipRequest r) {
		var property = properties.findByIdForUpdate(propertyId)
				.orElseThrow(() -> ApiErrors.notFound("Property", propertyId));
		var owner = owners.require(r.ownerId());
		var total = propertyOwners.totalOwnershipPercent(propertyId).add(r.ownershipPercent());
		if (total.compareTo(FULL_OWNERSHIP) > 0) {
			throw ApiErrors.conflict("Ownership shares for property " + propertyId + " would total " + total + "%");
		}
		return OwnershipResponse.from(propertyOwners.save(new PropertyOwner(property, owner, r.ownershipPercent())));
	}

	public void removeOwner(Long propertyId, Long ownerId) {
		var link = propertyOwners.findByPropertyIdAndOwnerId(propertyId, ownerId)
				.orElseThrow(() -> ApiErrors.notFound("Ownership of property " + propertyId + " by owner", ownerId));
		propertyOwners.delete(link);
	}

}
