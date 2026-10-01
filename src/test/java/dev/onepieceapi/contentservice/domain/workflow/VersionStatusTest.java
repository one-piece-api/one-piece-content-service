package dev.onepieceapi.contentservice.domain.workflow;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.DRAFT;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.IN_REVIEW;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.PUBLISHED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.READY_TO_PUBLISH;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.REJECTED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.RETIRED;
import static dev.onepieceapi.contentservice.domain.workflow.VersionStatus.SUPERSEDED;
import static org.assertj.core.api.Assertions.assertThat;

/** The two facts a status knows, docs/user-flows/content-editorial-workflow.md 4.1. */
class VersionStatusTest {

	@Test
	void theOpenStatusesAreTheOnesStillMovingThroughTheWorkflow() {
		assertThat(Arrays.stream(VersionStatus.values()).filter(VersionStatus::isOpen)).containsExactlyInAnyOrder(DRAFT,
				IN_REVIEW, REJECTED, READY_TO_PUBLISH);
	}

	@Test
	void aVersionHasBeenOnlineOnlyIfItIsOrWasPublished() {
		assertThat(Arrays.stream(VersionStatus.values()).filter(VersionStatus::everPublished))
			.containsExactlyInAnyOrder(PUBLISHED, RETIRED, SUPERSEDED);
	}

	@Test
	void anOpenVersionHasNeverBeenOnline() {
		assertThat(Arrays.stream(VersionStatus.values()).filter(VersionStatus::isOpen))
			.noneMatch(VersionStatus::everPublished);
	}

}
