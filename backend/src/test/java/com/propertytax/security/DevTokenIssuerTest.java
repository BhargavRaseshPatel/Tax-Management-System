package com.propertytax.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DevTokenIssuerTest {

	@Test
	void refusesToStartInsideAnEcsTask() {
		assertThatThrownBy(() -> DevTokenIssuer.refuseOnEcs("http://169.254.170.2/v4/abc"))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("DEV_AUTH must never be enabled on ECS");
	}

	@Test
	void startsNormallyAnywhereElse() {
		assertThatCode(() -> DevTokenIssuer.refuseOnEcs(null)).doesNotThrowAnyException();
		assertThatCode(() -> DevTokenIssuer.refuseOnEcs("")).doesNotThrowAnyException();
	}

}
