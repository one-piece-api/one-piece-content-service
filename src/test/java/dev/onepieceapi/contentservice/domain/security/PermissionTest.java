package dev.onepieceapi.contentservice.domain.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionTest {

	@Test
	void aPermissionIsFoundByTheStringCarriedOnTheToken() {
		assertThat(Permission.of("content:read")).contains(Permission.CONTENT_READ);
		assertThat(Permission.of("languages:manage")).contains(Permission.LANGUAGES_MANAGE);
	}

	@Test
	void aPermissionOfAnotherServiceIsNotOneOfOurs() {
		assertThat(Permission.of("users:read")).isEmpty();
		assertThat(Permission.of("CONTENT_READ")).isEmpty();
	}

	@Test
	void theAuthorityIsTheStringWithThePermissionPrefix() {
		assertThat(Permission.CONTENT_WRITE.value()).isEqualTo("content:write");
		assertThat(Permission.CONTENT_WRITE.authority()).isEqualTo("PERMISSION_content:write");
	}

}
