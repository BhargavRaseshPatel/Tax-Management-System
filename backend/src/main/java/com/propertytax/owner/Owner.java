package com.propertytax.owner;

import com.propertytax.common.BaseEntity;
import com.propertytax.user.User;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "owners")
public class Owner extends BaseEntity {

	/** Optional resident login for this owner. */
	@OneToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	private String fullName;
	private String email;
	private String phone;
	private String mailingAddress;

	protected Owner() {}

	public Owner(User user, String fullName, String email, String phone, String mailingAddress) {
		this.user = user;
		this.fullName = fullName;
		this.email = email;
		this.phone = phone;
		this.mailingAddress = mailingAddress;
	}

	public User getUser() { return user; }
	public String getFullName() { return fullName; }
	public String getEmail() { return email; }
	public String getPhone() { return phone; }
	public String getMailingAddress() { return mailingAddress; }

}
