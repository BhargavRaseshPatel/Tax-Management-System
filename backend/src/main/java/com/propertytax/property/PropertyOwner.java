package com.propertytax.property;

import java.math.BigDecimal;
import java.time.Instant;

import com.propertytax.owner.Owner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

/** Join entity for the properties <-> owners many-to-many; carries the ownership share. */
@Entity
@Table(name = "property_owners")
public class PropertyOwner {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "property_id")
	private Property property;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "owner_id")
	private Owner owner;

	private BigDecimal ownershipPercent;

	@CreationTimestamp
	@Column(updatable = false)
	private Instant createdAt;

	protected PropertyOwner() {}

	public PropertyOwner(Property property, Owner owner, BigDecimal ownershipPercent) {
		this.property = property;
		this.owner = owner;
		this.ownershipPercent = ownershipPercent;
	}

	public Long getId() { return id; }
	public Property getProperty() { return property; }
	public Owner getOwner() { return owner; }
	public BigDecimal getOwnershipPercent() { return ownershipPercent; }
	public Instant getCreatedAt() { return createdAt; }

}
