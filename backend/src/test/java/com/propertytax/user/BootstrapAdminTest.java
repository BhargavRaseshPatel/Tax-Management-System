package com.propertytax.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BootstrapAdminTest {

	UserRepository users = mock(UserRepository.class);

	@Test
	void emptyTablePlusConfiguredEmailCreatesOneAdmin() {
		when(users.count()).thenReturn(0L);

		new BootstrapAdmin(users, "First.Admin@Example.org").run(null);

		var saved = ArgumentCaptor.forClass(User.class);
		verify(users).save(saved.capture());
		assertThat(saved.getValue().getEmail()).isEqualTo("first.admin@example.org");
		assertThat(saved.getValue().getRole()).isEqualTo(User.Role.ADMIN);
	}

	@Test
	void doesNothingWhenUsersExistOrNoEmailIsConfigured() {
		when(users.count()).thenReturn(3L);
		new BootstrapAdmin(users, "late@example.org").run(null);

		when(users.count()).thenReturn(0L);
		new BootstrapAdmin(users, "").run(null);

		verify(users, never()).save(any());
	}

}
