package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import dev.onepieceapi.contentservice.domain.bounty.BountyKnowledge;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.atChapter;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.previews;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Bounty previewed at N (UF-CNT-23): Ace's poster, standing until Marineford, held by a
 * character met at its first appearance; Luffy's chain of posters, each replaced only
 * once the reader meets the next one.
 */
class BountyPreviewerTest {

	private static final UUID ACE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");

	private static final UUID ACE_550M = UUID.fromString("00000000-0000-0000-0000-000000000550");

	private static final UUID LUFFY_30M = UUID.fromString("00000000-0000-0000-0000-000000000030");

	private static final UUID LUFFY_100M = UUID.fromString("00000000-0000-0000-0000-000000000100");

	private static final UUID LUFFY_300M = UUID.fromString("00000000-0000-0000-0000-000000000300");

	private static final Bounty ACE_POSTER = Bounty.builder()
		.posterName("Portgas D. Ace")
		.amount(550_000_000L)
		.inactive(true)
		.holderContentId(ACE)
		.firstAppearance(atChapter(552))
		.initialKnowledge(new BountyKnowledge(false))
		.reveals(List.of(new Reveal<>(atChapter(574), Set.of(BountyField.INACTIVE), BountyKnowledge.NONE, Map.of())))
		.build();

	private ReaderPreviews previews;

	private BountyLinks links;

	private ContentDefinition<Bounty, BountyVersionEntity> bounties;

	private BountyPreviewer previewer;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.previews = previews();
		this.links = mock(BountyLinks.class);
		this.bounties = mock(ContentDefinition.class);
		ContentDefinition<Character, CharacterVersionEntity> characters = mock(ContentDefinition.class);
		doReturn(Map.of(ACE, Character.builder().romaji("portgas-d-ace").firstAppearance(atChapter(154)).build()))
			.when(this.previews)
			.online(eq(characters), any());
		this.previewer = new BountyPreviewer(this.previews, this.links, mock(CharacterLinks.class), this.bounties,
				characters);
	}

	@Test
	void aBountyIsInactiveOnlyOnceTheReaderLearnsIt() {
		doReturn(Map.of()).when(this.previews).online(eq(this.bounties), any());

		Preview<Bounty> beforeMarineford = this.previewer.previewOf(ACE_550M, ACE_POSTER,
				ReaderPosition.atChapter(560));
		Preview<Bounty> afterMarineford = this.previewer.previewOf(ACE_550M, ACE_POSTER, ReaderPosition.atChapter(600));

		assertThat(beforeMarineford.body().inactive()).isFalse();
		assertThat(beforeMarineford.body().holderContentId()).isEqualTo(ACE);
		assertThat(beforeMarineford.body().initialKnowledge()).isEqualTo(BountyKnowledge.NONE);
		assertThat(beforeMarineford.namesAt()).containsKey(ACE);
		assertThat(afterMarineford.body().inactive()).isTrue();
		assertThat(this.previewer.previewOf(ACE_550M, ACE_POSTER, ReaderPosition.atChapter(551)).visible()).isFalse();
	}

	@Test
	void theBountyItReplacesIsHiddenAndFlaggedWhileTheReaderHasNotMetIt() {
		doReturn(Map.of(LUFFY_30M, poster(500))).when(this.previews).online(eq(this.bounties), any());
		Bounty hundredMillions = poster(435).toBuilder().previousContentId(LUFFY_30M).build();

		Preview<Bounty> preview = this.previewer.previewOf(LUFFY_100M, hundredMillions, ReaderPosition.atChapter(436));

		assertThat(preview.body().previousContentId()).isNull();
		assertThat(preview.hiddenEntries())
			.containsExactly(new HiddenEntry("previous", HiddenReason.TARGET_NOT_VISIBLE, 500));
	}

	@Test
	void theChainAtNHoldsTheBountiesMetAndIsReplacedOnlyOnceTheNextIsMet() {
		Bounty hundredMillions = poster(435).toBuilder().previousContentId(LUFFY_30M).build();
		when(this.links.chainOf(eq(LUFFY_100M), eq(LUFFY_30M), any()))
			.thenReturn(new BountyChain(List.of(LUFFY_300M, LUFFY_100M, LUFFY_30M), LUFFY_300M));
		doReturn(Map.of(LUFFY_30M, poster(96), LUFFY_100M, hundredMillions, LUFFY_300M, poster(801)))
			.when(this.previews)
			.online(eq(this.bounties), any());

		BountyChain atEnies = this.previewer.chainAt(LUFFY_100M,
				this.previewer.previewOf(LUFFY_100M, hundredMillions, ReaderPosition.atChapter(500)));
		BountyChain atWano = this.previewer.chainAt(LUFFY_100M,
				this.previewer.previewOf(LUFFY_100M, hundredMillions, ReaderPosition.atChapter(900)));

		assertThat(atEnies).isEqualTo(new BountyChain(List.of(LUFFY_100M, LUFFY_30M), null));
		assertThat(atWano).isEqualTo(new BountyChain(List.of(LUFFY_300M, LUFFY_100M, LUFFY_30M), LUFFY_300M));
	}

	private static Bounty poster(int firstChapter) {
		return Bounty.builder().posterName("Monkey D. Luffy").firstAppearance(atChapter(firstChapter)).build();
	}

}
