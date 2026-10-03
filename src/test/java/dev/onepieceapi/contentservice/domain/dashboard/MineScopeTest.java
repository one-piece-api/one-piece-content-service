package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** What "mine" means per status, and for whom (UF-CNT-19). */
class MineScopeTest {

	private static final Set<Permission> EVERYTHING = EnumSet.allOf(Permission.class);

	@Test
	void anEditorsDraftsAreTheOnesTheyWrote() {
		assertThat(MineScope.of(VersionStatus.DRAFT, Set.of(Permission.CONTENT_WRITE))).contains(MineScope.AUTHORED);
	}

	@Test
	void aReviewersReviewsAreTheOnesTheyHold() {
		assertThat(MineScope.of(VersionStatus.IN_REVIEW, Set.of(Permission.CONTENT_REVIEW)))
			.contains(MineScope.CLAIMED);
	}

	@Test
	void whoMayNotClaimHasNoReviewOfTheirOwn() {
		assertThat(MineScope.of(VersionStatus.IN_REVIEW, Set.of(Permission.CONTENT_WRITE, Permission.CONTENT_ADMIN)))
			.isEmpty();
	}

	@Test
	void whoMayNotWriteHasNoDraftOfTheirOwn() {
		assertThat(MineScope.of(VersionStatus.DRAFT, Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)))
			.isEmpty();
	}

	@ParameterizedTest
	@EnumSource(value = VersionStatus.class, names = { "DRAFT", "IN_REVIEW" }, mode = EnumSource.Mode.EXCLUDE)
	void noOtherStatusHasAMineWhateverThePermissions(VersionStatus status) {
		assertThat(MineScope.of(status, EVERYTHING)).isEmpty();
	}

}
