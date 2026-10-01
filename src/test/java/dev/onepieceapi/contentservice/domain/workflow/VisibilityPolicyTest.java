package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_PUBLISH;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_READ;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_REVIEW;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_WRITE;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.ARCHIVED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The visibility table of docs/user-flows/content-editorial-workflow.md 4.3, checked over
 * every combination of permissions and then for each default role.
 */
class VisibilityPolicyTest {

	private static final Set<VersionStatus> FROM_READY_ON = Set.of(READY_TO_PUBLISH, PUBLISHED, ARCHIVED, RETIRED,
			SUPERSEDED);

	@ParameterizedTest
	@MethodSource("everyPermissionCombination")
	void eachStatusIsVisibleExactlyToThePermissionsOfTheTable(Set<Permission> permissions) {
		var visible = VisibilityPolicy.visibleStatuses(permissions);

		boolean writes = permissions.contains(CONTENT_WRITE);
		boolean reviews = permissions.contains(CONTENT_REVIEW);
		boolean reads = permissions.contains(CONTENT_READ);
		assertThat(visible.contains(DRAFT)).isEqualTo(writes);
		assertThat(visible.contains(REJECTED)).isEqualTo(writes);
		assertThat(visible.contains(IN_REVIEW)).isEqualTo(writes || reviews);
		FROM_READY_ON.forEach(status -> assertThat(visible.contains(status)).as("%s", status).isEqualTo(reads));
	}

	@Test
	void anEditorSeesEverything() {
		assertThat(VisibilityPolicy.visibleStatuses(Set.of(CONTENT_READ, CONTENT_WRITE)))
			.containsExactlyInAnyOrder(VersionStatus.values());
	}

	@Test
	void aReviewerSeesNoDraftAndNoRejectedVersion() {
		assertThat(VisibilityPolicy.visibleStatuses(Set.of(CONTENT_READ, CONTENT_REVIEW)))
			.containsExactlyInAnyOrder(IN_REVIEW, READY_TO_PUBLISH, PUBLISHED, ARCHIVED, RETIRED, SUPERSEDED);
	}

	@Test
	void aPublisherSeesNothingBeforeReadyToPublish() {
		assertThat(VisibilityPolicy.visibleStatuses(Set.of(CONTENT_READ, CONTENT_PUBLISH)))
			.containsExactlyInAnyOrderElementsOf(FROM_READY_ON);
	}

	@Test
	void noPermissionSeesNothing() {
		assertThat(VisibilityPolicy.visibleStatuses(Set.of())).isEmpty();
	}

	/** All 2^n subsets of {@link Permission}, each bit of the counter picking one. */
	private static Stream<Set<Permission>> everyPermissionCombination() {
		var all = Permission.values();
		return IntStream.range(0, 1 << all.length).mapToObj(mask -> {
			var subset = EnumSet.noneOf(Permission.class);
			IntStream.range(0, all.length).filter(bit -> (mask & (1 << bit)) != 0).forEach(bit -> subset.add(all[bit]));
			return subset;
		});
	}

}
