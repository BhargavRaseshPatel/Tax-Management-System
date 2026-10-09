package com.propertytax.owner;

import com.propertytax.common.ApiErrors;
import com.propertytax.owner.OwnerDtos.OwnerRequest;
import com.propertytax.owner.OwnerDtos.OwnerResponse;
import com.propertytax.user.User;
import com.propertytax.user.UserRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class OwnerService {

	private final OwnerRepository owners;
	private final UserRepository users;

	OwnerService(OwnerRepository owners, UserRepository users) {
		this.owners = owners;
		this.users = users;
	}

	public OwnerResponse create(OwnerRequest r) {
		var owner = new Owner(residentLogin(r.userId()), r.fullName(), r.email(), r.phone(), r.mailingAddress());
		return OwnerResponse.from(owners.save(owner));
	}

	@Transactional(readOnly = true)
	public PagedModel<OwnerResponse> list(String name, Pageable pageable) {
		var page = name == null || name.isBlank()
				? owners.findAll(pageable)
				: owners.findByFullNameContainingIgnoreCase(name.strip(), pageable);
		return new PagedModel<>(page.map(OwnerResponse::from));
	}

	@Transactional(readOnly = true)
	public OwnerResponse get(Long id) {
		return OwnerResponse.from(owners.require(id));
	}

	public OwnerResponse update(Long id, OwnerRequest r) {
		var owner = owners.require(id);
		owner.update(residentLogin(r.userId()), r.fullName(), r.email(), r.phone(), r.mailingAddress());
		owners.flush();
		return OwnerResponse.from(owner);
	}

	/** Only a RESIDENT login may represent an owner; it drives resident data-ownership checks later. */
	private User residentLogin(Long userId) {
		if (userId == null) {
			return null;
		}
		var user = users.require(userId);
		if (user.getRole() != User.Role.RESIDENT) {
			throw ApiErrors.badRequest("User " + userId + " is not a RESIDENT and cannot be linked to an owner");
		}
		return user;
	}

}
