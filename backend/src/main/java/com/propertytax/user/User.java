package com.propertytax.user;

import java.util.Locale;

import com.propertytax.common.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

	public enum Role { RESIDENT, TAX_OFFICER, ADMIN }

	private String email;
	private String fullName;
	@Enumerated(EnumType.STRING)
	private Role role;
	private boolean active = true;

	protected User() {}

	public User(String email, String fullName, Role role) {
		this.email = email.toLowerCase(Locale.ROOT); // schema requires lowercase
		this.fullName = fullName;
		this.role = role;
	}

	public void update(String fullName, Role role, boolean active) {
		this.fullName = fullName;
		this.role = role;
		this.active = active;
	}

	public String getEmail() { return email; }
	public String getFullName() { return fullName; }
	public Role getRole() { return role; }
	public boolean isActive() { return active; }

}
