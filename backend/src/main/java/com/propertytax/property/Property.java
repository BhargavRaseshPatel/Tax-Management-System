package com.propertytax.property;

import com.propertytax.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "properties")
public class Property extends BaseEntity {

	private String rollNumber;
	private String streetAddress;
	private String city;
	private String postalCode;
	@Enumerated(EnumType.STRING)
	private PropertyClass propertyClass;

	protected Property() {}

	public Property(String rollNumber, String streetAddress, String city, String postalCode, PropertyClass propertyClass) {
		this.rollNumber = rollNumber;
		this.streetAddress = streetAddress;
		this.city = city;
		this.postalCode = postalCode;
		this.propertyClass = propertyClass;
	}

	public String getRollNumber() { return rollNumber; }
	public String getStreetAddress() { return streetAddress; }
	public String getCity() { return city; }
	public String getPostalCode() { return postalCode; }
	public PropertyClass getPropertyClass() { return propertyClass; }

}
