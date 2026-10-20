package dev.onepieceapi.contentservice.domain.bounty;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BountyValidityTest {

	@Test
	void inactiveOverridesBothOthers() {
		assertThat(BountyValidity.of(true, true)).isEqualTo(BountyValidity.INACTIVE);
		assertThat(BountyValidity.of(true, false)).isEqualTo(BountyValidity.INACTIVE);
	}

	@Test
	void aReplacedBountyThatStandsOtherwiseIsSuperseded() {
		assertThat(BountyValidity.of(false, true)).isEqualTo(BountyValidity.SUPERSEDED);
	}

	@Test
	void theRestIsActive() {
		assertThat(BountyValidity.of(false, false)).isEqualTo(BountyValidity.ACTIVE);
	}

}
