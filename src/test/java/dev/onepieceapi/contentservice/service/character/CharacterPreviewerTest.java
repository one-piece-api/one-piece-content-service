package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterKnowledge;
import dev.onepieceapi.contentservice.domain.character.CharacterLookups;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.character.CharacterStatus;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeKind;
import dev.onepieceapi.contentservice.domain.character.RelativeOf;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.RevealedName;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
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
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.chapters;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.fromChapter;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.previews;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A Character previewed at N (UF-CNT-23): Ace, alive until Marineford, a Whitebeard
 * Pirate, user of the Mera Mera no Mi, Luffy's sworn brother from Alabasta's aftermath,
 * Sabo's before the reader meets Sabo.
 */
class CharacterPreviewerTest {

	private static final UUID ACE = UUID.fromString("00000000-0000-0000-0000-0000000000ac");

	private static final UUID LUFFY = UUID.fromString("00000000-0000-0000-0000-00000000001f");

	private static final UUID SABO = UUID.fromString("00000000-0000-0000-0000-0000000005ab");

	private static final UUID MERA = UUID.fromString("00000000-0000-0000-0000-00000000e7a0");

	private static final UUID WHITEBEARD_PIRATES = UUID.fromString("00000000-0000-0000-0000-0000000000eb");

	private static final Character PORTGAS_D_ACE = Character.builder()
		.romaji("portgas-d-ace")
		.translations(Map.of("it", new CharacterTranslation("Portgas D. Ace")))
		.status(CharacterStatus.DECEASED)
		.firstAppearance(atChapter(154))
		.devilFruits(List.of(new FruitUse(MERA, fromChapter(158))))
		.affiliations(List.of(new Membership(WHITEBEARD_PIRATES,
				List.of(new MembershipPeriod(chapters(234, 574), Map.of("it", "Comandante"), null)))))
		.relatives(List.of(new Relative(LUFFY, RelativeKind.SWORN_SIBLING, fromChapter(440)),
				new Relative(SABO, RelativeKind.SWORN_SIBLING, fromChapter(300))))
		.initialKnowledge(new CharacterKnowledge(null, null, CharacterStatus.ALIVE, Map.of()))
		.reveals(
				List.of(new Reveal<>(atChapter(574), Set.of(CharacterField.STATUS), CharacterKnowledge.NONE, Map.of())))
		.build();

	private ReaderPreviews previews;

	private CharacterReferences references;

	private CharacterPreviewer previewer;

	@BeforeEach
	@SuppressWarnings("unchecked")
	void setUp() {
		this.previews = previews();
		this.references = mock(CharacterReferences.class);
		ContentDefinition<Character, CharacterVersionEntity> characters = mock(ContentDefinition.class);
		ContentDefinition<Species, SpeciesVersionEntity> species = mock(ContentDefinition.class);
		ContentDefinition<Affiliation, AffiliationVersionEntity> affiliations = mock(ContentDefinition.class);
		ContentDefinition<DevilFruit, DevilFruitVersionEntity> fruits = mock(ContentDefinition.class);
		doReturn(Map.of()).when(this.previews).online(eq(species), any());
		doReturn(Map.of(WHITEBEARD_PIRATES, Affiliation.builder().firstAppearance(atChapter(234)).build()))
			.when(this.previews)
			.online(eq(affiliations), any());
		doReturn(Map.of(MERA, DevilFruit.builder().romaji("mera-mera-no-mi").firstAppearance(atChapter(158)).build()))
			.when(this.previews)
			.online(eq(fruits), any());
		doReturn(Map.of(LUFFY, character("monkey-d-luffy", 1), SABO, character("sabo", 583))).when(this.previews)
			.online(eq(characters), any());
		this.previewer = new CharacterPreviewer(this.previews, this.references, characters, species, affiliations,
				fruits);
	}

	@Test
	void beforeMarinefordAceIsAliveAndHisBrotherhoodIsNotKnownYet() {
		Preview<Character> preview = this.previewer.previewOf(ACE, PORTGAS_D_ACE, ReaderPosition.atChapter(400));

		Character ace = preview.body();
		assertThat(ace.status()).isEqualTo(CharacterStatus.ALIVE);
		assertThat(ace.romaji()).isEqualTo("portgas-d-ace");
		assertThat(ace.devilFruits()).containsExactly(new FruitUse(MERA, fromChapter(158)));
		assertThat(ace.affiliations()).singleElement()
			.satisfies(membership -> assertThat(membership.periods()).singleElement()
				.satisfies(period -> assertThat(period.window()).isEqualTo(fromChapter(234))));
		assertThat(ace.relatives()).isEmpty();
		assertThat(preview.hiddenEntries())
			.containsExactly(new HiddenEntry("relatives[1]", HiddenReason.TARGET_NOT_VISIBLE, 583));
		assertThat(preview.namesAt()).containsEntry(MERA, new RevealedName("mera-mera-no-mi", Map.of()));
	}

	@Test
	void fromMarinefordAceIsDeceasedAndAFormerWhitebeardPirate() {
		Character ace = this.previewer.previewOf(ACE, PORTGAS_D_ACE, ReaderPosition.atChapter(600)).body();

		assertThat(ace.status()).isEqualTo(CharacterStatus.DECEASED);
		assertThat(ace.affiliations().getFirst().periods().getFirst().window()).isEqualTo(chapters(234, 574));
		assertThat(ace.relatives()).extracting(Relative::relativeContentId).containsExactly(LUFFY, SABO);
	}

	@Test
	void aCharacterIsNotVisibleBeforeItsFirstAppearance() {
		assertThat(this.previewer.previewOf(ACE, PORTGAS_D_ACE, ReaderPosition.atChapter(153)).visible()).isFalse();
	}

	@Test
	void theCharactersNamingItAsARelativeAreThoseTheReaderHasMetWithTheEntryDiscovered() {
		var luffy = new CharacterReference(LUFFY, "monkey-d-luffy", Map.of(), null);
		var sabo = new CharacterReference(SABO, "sabo", Map.of(), null);
		when(this.references.relativesOf(ACE))
			.thenReturn(List.of(new RelativeOf(luffy, RelativeKind.SWORN_SIBLING, fromChapter(440)),
					new RelativeOf(sabo, RelativeKind.SWORN_SIBLING, fromChapter(300))));
		when(this.references.devilFruitsOf(any()))
			.thenReturn(Map.of(MERA, new DevilFruitReference(MERA, "mera-mera-no-mi", Map.of(), false)));
		Preview<Character> preview = this.previewer.previewOf(ACE, PORTGAS_D_ACE, ReaderPosition.atChapter(500));

		CharacterLookups lookups = this.previewer.lookupsAt(ACE, preview);

		assertThat(lookups.relativesOf()).extracting(relative -> relative.character().id()).containsExactly(LUFFY);
		assertThat(lookups.devilFruits()).containsKey(MERA);
	}

	private static Character character(String romaji, int firstChapter) {
		return Character.builder().romaji(romaji).firstAppearance(atChapter(firstChapter)).build();
	}

}
