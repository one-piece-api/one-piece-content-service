package dev.onepieceapi.contentservice.domain.dashboard;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCountTest {

	@Test
	void everyStatusButSupersededHasATileInWorkflowOrder() {
		assertThat(StatusCount.tracked()).containsExactly(VersionStatus.DRAFT, VersionStatus.IN_REVIEW,
				VersionStatus.REJECTED, VersionStatus.READY_TO_PUBLISH, VersionStatus.PUBLISHED, VersionStatus.ARCHIVED,
				VersionStatus.RETIRED);
	}

}
