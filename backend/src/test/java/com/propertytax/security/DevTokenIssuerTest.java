package com.propertytax.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import com.propertytax.user.User;
import org.junit.jupiter.api.Test;

class DevTokenIssuerTest {

	@Test
	void refusesToStartInsideAnEcsTask() {
		assertThatThrownBy(() -> DevTokenIssuer.refuseOnEcs("http://169.254.170.2/v4/abc", false))
				.isInstanceOf(IllegalStateException.class).hasMessageContaining("DEV_AUTH must never be enabled on ECS");
	}

	@Test
	void startsNormallyAnywhereElse() {
		assertThatCode(() -> DevTokenIssuer.refuseOnEcs(null, false)).doesNotThrowAnyException();
		assertThatCode(() -> DevTokenIssuer.refuseOnEcs("", false)).doesNotThrowAnyException();
	}

	@Test
	void startsOnEcsOnlyAsADeliberatePublicDemo() {
		assertThatCode(() -> DevTokenIssuer.refuseOnEcs("http://169.254.170.2/v4/abc", true)).doesNotThrowAnyException();
	}

	@Test
	void publicDemoSignsInFictionalAccountsOnly() {
		assertThat(DevTokenIssuer.maySignIn(true, "s.chen@civictax.demo")).isTrue();
		assertThat(DevTokenIssuer.maySignIn(true, "elena.rostova@example.ca")).isTrue();
		assertThat(DevTokenIssuer.maySignIn(true, "real.person@gmail.com")).isFalse();
		assertThat(DevTokenIssuer.maySignIn(true, "civictax.demo@gmail.com")).isFalse();
		assertThat(DevTokenIssuer.maySignIn(true, "attacker@civictax.demo.evil.org")).isFalse();
	}

	@Test
	void localDevelopmentSignsInAnyone() {
		assertThat(DevTokenIssuer.maySignIn(false, "real.person@gmail.com")).isTrue();
	}

	@Test
	void onlyItsOwnTokensGoToTheDevDecoder() {
		var issuer = new DevTokenIssuer(false);
		var own = issuer.issue(new User("s.chen@civictax.demo", "Sarah Chen", User.Role.ADMIN)).accessToken();
		// An unsigned token from someone else's issuer, built here so no token-shaped string sits in the source
		var foreign = base64Url("{\"alg\":\"none\"}") + "." + base64Url("{\"iss\":\"https://cognito-idp.example/pool\"}") + ".";
		assertThat(DevTokenIssuer.isDevToken(own)).isTrue();
		assertThat(DevTokenIssuer.isDevToken(foreign)).isFalse();
		assertThat(DevTokenIssuer.isDevToken("not-a-token")).isFalse();
	}

	private static String base64Url(String json) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
	}

}
