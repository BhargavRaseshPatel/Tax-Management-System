package com.propertytax.user;

import com.propertytax.user.UserDtos.CreateUserRequest;
import com.propertytax.user.UserDtos.UpdateUserRequest;
import com.propertytax.user.UserDtos.UserResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

	private final UserRepository users;

	UserService(UserRepository users) {
		this.users = users;
	}

	public UserResponse create(CreateUserRequest r) {
		return UserResponse.from(users.save(new User(r.email(), r.fullName(), r.role())));
	}

	@Transactional(readOnly = true)
	public PagedModel<UserResponse> list(Pageable pageable) {
		return new PagedModel<>(users.findAll(pageable).map(UserResponse::from));
	}

	@Transactional(readOnly = true)
	public UserResponse get(Long id) {
		return UserResponse.from(users.require(id));
	}

	public UserResponse update(Long id, UpdateUserRequest r) {
		var user = users.require(id);
		user.update(r.fullName(), r.role(), r.active());
		users.flush(); // so updatedAt in the response is current
		return UserResponse.from(user);
	}

}
