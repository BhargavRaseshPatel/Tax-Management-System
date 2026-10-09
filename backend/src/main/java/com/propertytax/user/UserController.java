package com.propertytax.user;

import com.propertytax.user.UserDtos.CreateUserRequest;
import com.propertytax.user.UserDtos.UpdateUserRequest;
import com.propertytax.user.UserDtos.UserResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Users are deactivated (active=false), never deleted: payments reference who recorded them. */
@Tag(name = "1. Users")
@RestController
@RequestMapping("/api/users")
class UserController {

	private final UserService service;

	UserController(UserService service) {
		this.service = service;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	UserResponse create(@Valid @RequestBody CreateUserRequest request) {
		return service.create(request);
	}

	@GetMapping
	PagedModel<UserResponse> list(@ParameterObject @PageableDefault(sort = "id") Pageable pageable) {
		return service.list(pageable);
	}

	@GetMapping("/{id}")
	UserResponse get(@PathVariable Long id) {
		return service.get(id);
	}

	@PutMapping("/{id}")
	UserResponse update(@PathVariable Long id, @Valid @RequestBody UpdateUserRequest request) {
		return service.update(id, request);
	}

}
