package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The list variants of the rules of every relation (implementation plan of the Character,
 * D14): each entry of a list is a relation of its own, reported by its index.
 */
class RelationRulesTest {

	private static final RelationDescriptor<UUID> SPECIES = RelationDescriptor.<UUID>builder()
		.name("species")
		.target(Function.identity())
		.notLinkable("must be a Species with an approved version")
		.targetNotOnline(BlockReason.RELATED_NOT_ONLINE)
		.onlineItemsLinked(BlockReason.ONLINE_ITEMS_LINKED)
		.itemsName("characters")
		.build();

	private static final UUID NINGEN = UUID.randomUUID();

	private static final UUID GYOJIN = UUID.randomUUID();

	private static final UUID MINK = UUID.randomUUID();

	private final SpeciesVersionRepository targets = mock(SpeciesVersionRepository.class);

	private final ContentRepository contents = mock(ContentRepository.class);

	@Test
	void everyEntryNotLinkableIsNamedByItsIndex() {
		when(this.targets.existsWithStatus(eq(NINGEN), anyCollection())).thenReturn(true);

		var violations = RelationRules.notLinkable(SPECIES, List.of(NINGEN, GYOJIN, MINK), "species", this.targets);

		assertThat(violations).containsExactly(
				new FieldViolation("species[1]", "must be a Species with an approved version"),
				new FieldViolation("species[2]", "must be a Species with an approved version"));
	}

	@Test
	void anEmptyListIsAlwaysLinkable() {
		assertThat(RelationRules.notLinkable(SPECIES, List.<UUID>of(), "species", this.targets)).isEmpty();
	}

	@Test
	void theFirstEntryNotOnlineBlocksGoingOnlineNamingItsIndex() {
		when(this.targets.existsWithStatus(NINGEN, Set.of(VersionStatus.PUBLISHED))).thenReturn(true);
		when(this.targets.findMostRecent(any(), any())).thenReturn(List.of());

		var block = RelationRules.targetNotOnline(VersionAction.PUBLISH, SPECIES, List.of(NINGEN, GYOJIN, MINK),
				"species", this.targets);

		assertThat(block).hasValueSatisfying(blocked -> {
			assertThat(blocked.reason()).isEqualTo(BlockReason.RELATED_NOT_ONLINE);
			assertThat(blocked.detail()).isEqualTo(Map.of("speciesIndex", 1, "speciesId", GYOJIN));
		});
	}

	@Test
	void anActionNotGoingOnlineIsNeverBlocked() {
		assertThat(
				RelationRules.targetNotOnline(VersionAction.SUBMIT, SPECIES, List.of(GYOJIN), "species", this.targets))
			.isEmpty();
	}

	@Test
	void everyEntryIsHeldBeforeGoingOnlineAndNoneOtherwise() {
		RelationRules.lockTargetBefore(VersionAction.RESTORE, SPECIES, List.of(NINGEN, GYOJIN), this.contents);
		RelationRules.lockTargetBefore(VersionAction.SUBMIT, SPECIES, List.of(MINK), this.contents);

		verify(this.contents).lockShared(NINGEN);
		verify(this.contents).lockShared(GYOJIN);
		verify(this.contents, never()).lockShared(MINK);
	}

}
