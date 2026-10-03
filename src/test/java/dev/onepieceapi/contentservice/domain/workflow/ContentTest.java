package dev.onepieceapi.contentservice.domain.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContentTest {

	@Test
	void theOnlineVersionIsThePublishedOne() {
		var content = new Content<>(UUID.randomUUID(), List.of(version(1, VersionStatus.SUPERSEDED),
				version(2, VersionStatus.PUBLISHED), version(3, VersionStatus.DRAFT)));

		assertThat(content.onlineVersionNumber()).contains(2);
	}

	@Test
	void aContentWithNothingPublishedHasNoOnlineVersion() {
		var content = new Content<>(UUID.randomUUID(),
				List.of(version(1, VersionStatus.RETIRED), version(2, VersionStatus.DRAFT)));

		assertThat(content.onlineVersionNumber()).isEmpty();
	}

	private static VersionAccess<String> version(int number, VersionStatus status) {
		var version = Version.<String>builder().number(number).status(status).body("anything").build();
		return new VersionAccess<>(version, Set.of());
	}

}
