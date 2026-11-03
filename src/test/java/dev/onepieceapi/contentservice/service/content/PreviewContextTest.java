package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.readertime.HiddenEntry;
import dev.onepieceapi.contentservice.domain.readertime.HiddenReason;
import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.readertime.ImageKind;
import dev.onepieceapi.readertime.ReaderPosition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.at;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.atChapter;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.atEpisode;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.chapters;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.context;
import static dev.onepieceapi.contentservice.service.content.PreviewFixtures.fromChapter;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The rules of a preview at N on this service's points (workflow 3.8, UF-CNT-23): what
 * the reader has met, how a point reads there, which entries, paragraphs and portrait are
 * seen, and which hidden entries are flagged to the editor.
 */
class PreviewContextTest {

	private static final UUID FISHMEN = UUID.fromString("00000000-0000-0000-0000-00000000f15e");

	private static final UUID GIANTS = UUID.fromString("00000000-0000-0000-0000-0000000091a7");

	private static final UUID OFFLINE = UUID.fromString("00000000-0000-0000-0000-0000000000ff");

	@Test
	void anItemIsMetFromItsFirstAppearanceOnTheAxisRead() {
		assertThat(context(ReaderPosition.atChapter(4), Map.of()).hasMet(atChapter(5))).isFalse();
		assertThat(context(ReaderPosition.atChapter(5), Map.of()).hasMet(atChapter(5))).isTrue();
		assertThat(context(ReaderPosition.atEpisode(500), Map.of()).hasMet(atChapter(5))).isFalse();
		assertThat(context(ReaderPosition.atEpisode(3), Map.of()).hasMet(at(5, 3))).isTrue();
	}

	@Test
	void aPointKeepsOnlyItsPartOnTheAxisReadAndNothingBeyondN() {
		PreviewContext atChapterTen = context(ReaderPosition.atChapter(10), Map.of());

		assertThat(atChapterTen.trimmed(at(5, 3))).isEqualTo(atChapter(5));
		assertThat(atChapterTen.trimmed(at(11, 3))).isEqualTo(StoryPoint.NONE);
		assertThat(context(ReaderPosition.atEpisode(3), Map.of()).trimmed(at(5, 3))).isEqualTo(atEpisode(3));
		assertThat(atChapterTen.trimmed(chapters(2, 12))).isEqualTo(StoryWindow.from(atChapter(2)));
	}

	@Test
	void anEntryIsSeenOnceDiscoveredWithItsTargetMetAndTheUnexpectedHiddenOnesAreFlagged() {
		PreviewContext context = context(ReaderPosition.atChapter(10),
				Map.of(FISHMEN, atChapter(1), GIANTS, atChapter(20)));
		List<SpeciesEntry> entries = List.of(new SpeciesEntry(FISHMEN, fromChapter(2)),
				new SpeciesEntry(FISHMEN, StoryWindow.from(atEpisode(1))), new SpeciesEntry(FISHMEN, chapters(2, 4)),
				new SpeciesEntry(FISHMEN, fromChapter(11)), new SpeciesEntry(OFFLINE, fromChapter(2)),
				new SpeciesEntry(GIANTS, fromChapter(2)));

		List<SpeciesEntry> seen = context.entries(entries, "species", SpeciesEntry::speciesContentId,
				(entry, window) -> new SpeciesEntry(entry.speciesContentId(), window));

		assertThat(seen).containsExactly(new SpeciesEntry(FISHMEN, fromChapter(2)),
				new SpeciesEntry(FISHMEN, chapters(2, 4)));
		assertThat(context.getHiddenEntries()).containsExactly(HiddenEntry.of("species[1]", HiddenReason.NOT_ON_AXIS),
				HiddenEntry.of("species[4]", HiddenReason.TARGET_NOT_ONLINE),
				new HiddenEntry("species[5]", HiddenReason.TARGET_NOT_VISIBLE, 20));
	}

	@Test
	void theParagraphsReadAreThoseDiscoveredAndNotEndedEachTrimmed() {
		PreviewContext context = context(ReaderPosition.atChapter(10), Map.of());
		Paragraph opening = new Paragraph(Map.of("it", "Un pirata"), new StoryWindow(at(1, 1), StoryPoint.NONE));
		Paragraph former = new Paragraph(Map.of("it", "Un mozzo"), chapters(1, 5));
		Paragraph animeOnly = new Paragraph(Map.of("it", "Un filler"), StoryWindow.from(atEpisode(50)));
		Paragraph later = new Paragraph(Map.of("it", "Un imperatore"), fromChapter(1000));

		List<Paragraph> read = context.paragraphs(ParagraphSection.DESCRIPTION,
				List.of(opening, former, animeOnly, later));

		assertThat(read).containsExactly(new Paragraph(Map.of("it", "Un pirata"), fromChapter(1)));
		assertThat(context.getHiddenEntries()).extracting(HiddenEntry::field, HiddenEntry::reason)
			.containsExactly(tuple(ParagraphSection.DESCRIPTION.entryField(2), HiddenReason.NOT_ON_AXIS));
	}

	@Test
	void theMainPortraitIsTheLatestAppearanceNotEndedAndNeverAForm() {
		var child = new GalleryImage("child", ImageKind.ASPETTO, chapters(1, 5), Map.of());
		var young = new GalleryImage("young", ImageKind.ASPETTO, fromChapter(5), Map.of());
		var gear = new GalleryImage("gear", ImageKind.FORMA, fromChapter(8), Map.of());
		var adult = new GalleryImage("adult", ImageKind.ASPETTO, fromChapter(598), Map.of());
		List<GalleryImage> gallery = List.of(child, young, gear, adult);

		assertThat(context(ReaderPosition.atChapter(3), Map.of()).mainPortrait(gallery)).contains(child);
		assertThat(context(ReaderPosition.atChapter(10), Map.of()).mainPortrait(gallery)).contains(young);
		assertThat(context(ReaderPosition.atChapter(600), Map.of()).mainPortrait(gallery)).contains(adult);
		assertThat(context(ReaderPosition.atEpisode(10), Map.of()).mainPortrait(gallery)).isEmpty();
	}

}
