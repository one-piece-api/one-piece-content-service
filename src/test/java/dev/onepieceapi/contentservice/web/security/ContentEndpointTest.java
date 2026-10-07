package dev.onepieceapi.contentservice.web.security;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.web.ApiPaths;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** Every section gets every content endpoint, with its permission. */
class ContentEndpointTest {

	@Test
	void everyEndpointOfEverySectionHasAPermission() {
		for (String section : ApiPaths.CONTENT_SECTIONS) {
			assertThat(SecuredEndpoint.requiredPermission(HttpMethod.GET, section)).contains(Permission.CONTENT_READ);
			assertThat(SecuredEndpoint.requiredPermission(HttpMethod.POST, section)).contains(Permission.CONTENT_WRITE);
			assertThat(SecuredEndpoint.requiredPermission(HttpMethod.POST, section + ApiPaths.CONTENT_VERSION_CLAIM))
				.contains(Permission.CONTENT_REVIEW);
			assertThat(SecuredEndpoint.requiredPermission(HttpMethod.POST, section + ApiPaths.CONTENT_VERSION_PUBLISH))
				.contains(Permission.CONTENT_PUBLISH);
			assertThat(SecuredEndpoint.requiredPermission(HttpMethod.POST, section + ApiPaths.CONTENT_VERSION_RETIRE))
				.contains(Permission.CONTENT_RETIRE);
		}
	}

	@Test
	void aPathOutsideTheSectionsIsNotAContentEndpoint() {
		assertThat(ContentEndpoint.requiredPermission(HttpMethod.GET, "/elsewhere" + ApiPaths.CONTENT_BY_ID)).isEmpty();
		assertThat(ContentEndpoint.requiredPermission(HttpMethod.PATCH, ApiPaths.DEVIL_FRUIT_TYPES)).isEmpty();
	}

	@Test
	void theDevilFruitSectionHasTheEndpointsEverySectionHas() {
		assertThat(ApiPaths.CONTENT_SECTIONS).contains(ApiPaths.DEVIL_FRUITS);
		assertThat(SecuredEndpoint.requiredPermission(HttpMethod.GET, ApiPaths.DEVIL_FRUITS))
			.contains(Permission.CONTENT_READ);
		assertThat(SecuredEndpoint.requiredPermission(HttpMethod.PUT, ApiPaths.DEVIL_FRUITS + ApiPaths.CONTENT_VERSION))
			.contains(Permission.CONTENT_WRITE);
	}

	@Test
	void theTypesAFruitMayBeLinkedToTakeContentWriteAndAreNotInTheSections() {
		assertThat(SecuredEndpoint.requiredPermission(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPES_LINKABLE))
			.contains(Permission.CONTENT_WRITE);
		assertThat(ContentEndpoint.requiredPermission(HttpMethod.GET, ApiPaths.DEVIL_FRUIT_TYPES_LINKABLE)).isEmpty();
	}

}
