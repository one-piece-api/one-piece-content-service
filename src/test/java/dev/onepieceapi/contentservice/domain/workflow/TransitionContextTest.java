package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the rules know about a content once a transition has moved one of its versions.
 */
class TransitionContextTest {

	private static final User VIVI = new User(UUID.randomUUID(), "vivi", "vivi@onepiece.local");

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	@Test
	void aVersionLeavingTheOpenStatusesLeavesItsContentWithoutAnOpenOne() {
		var before = new TransitionContext(version(READY_TO_PUBLISH), true, VIVI, PUBLISHER);

		var after = before.after(version(PUBLISHED));

		assertThat(after.contentHasOpenVersion()).isFalse();
		assertThat(after.version().status()).isEqualTo(PUBLISHED);
		assertThat(after.caller()).isEqualTo(VIVI);
		assertThat(after.permissions()).isEqualTo(PUBLISHER);
	}

	@Test
	void aVersionEnteringTheOpenStatusesIsItsContentsOpenOne() {
		var before = new TransitionContext(version(ARCHIVED), false, VIVI, PUBLISHER);

		assertThat(before.after(version(READY_TO_PUBLISH)).contentHasOpenVersion()).isTrue();
	}

	@ParameterizedTest(name = "[{index}] {0} -> {1}, open version before: {2}")
	@CsvSource({ "DRAFT, IN_REVIEW, true", "IN_REVIEW, READY_TO_PUBLISH, true", "REJECTED, DRAFT, true",
			"PUBLISHED, RETIRED, true", "PUBLISHED, RETIRED, false", "SUPERSEDED, PUBLISHED, true",
			"SUPERSEDED, PUBLISHED, false" })
	void anyOtherMoveLeavesTheOpenVersionOfTheContentAsItWas(VersionStatus from, VersionStatus to,
			boolean hadOpenVersion) {
		var before = new TransitionContext(version(from), hadOpenVersion, VIVI, PUBLISHER);

		assertThat(before.after(version(to)).contentHasOpenVersion()).isEqualTo(hadOpenVersion);
	}

	private static Version<String> version(VersionStatus status) {
		return Version.<String>builder().number(1).status(status).author(VIVI).build();
	}

}
