package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.readertime.ImageKind;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** What submitting asks of the gallery of an item (workflow 3.5.1), written once. */
class GalleriedTest {

	private static final StoryPoint FIRST_APPEARANCE = StoryPoint.ofChapter(UUID.randomUUID());

	private static final StoryPoint LATER = StoryPoint.ofChapter(UUID.randomUUID());

	private static final List<String> LANGUAGES = List.of("en", "it");

	private static final Map<String, String> CAPTIONED = Map.of("en", "Look", "it", "Aspetto");

	@Test
	void anAppearanceDiscoveredAtTheFirstAppearanceOpensTheGalleryEvenWithAnEnd() {
		var ended = new GalleryImage("a", ImageKind.ASPETTO, new StoryWindow(FIRST_APPEARANCE, LATER), CAPTIONED);

		assertThat(item(ended).missingGallery(LANGUAGES)).isEmpty();
	}

	@Test
	void aFormOrALaterOrUndiscoveredAppearanceDoesNotOpenTheGallery() {
		var form = new GalleryImage("a", ImageKind.FORMA, StoryWindow.from(FIRST_APPEARANCE), CAPTIONED);
		var later = new GalleryImage("b", ImageKind.ASPETTO, StoryWindow.from(LATER), CAPTIONED);
		var undiscovered = new GalleryImage("c", ImageKind.ASPETTO, StoryWindow.NONE, CAPTIONED);

		assertThat(item(form, later, undiscovered).missingGallery(LANGUAGES)).containsExactly("gallery");
		assertThat(item().missingGallery(LANGUAGES)).containsExactly("gallery");
	}

	@Test
	void eachImageMissesTheCaptionsItIsNotWrittenInByItsIndexBeforeTheGallery() {
		var open = new GalleryImage("a", ImageKind.ASPETTO, StoryWindow.from(FIRST_APPEARANCE),
				Map.of("it", "Aspetto"));
		var form = new GalleryImage("b", ImageKind.FORMA, StoryWindow.from(LATER), Map.of());

		assertThat(item(open, form).missingGallery(LANGUAGES)).containsExactly("gallery[0].caption[en]",
				"gallery[1].caption[en]", "gallery[1].caption[it]");
	}

	@Test
	void theWindowsTheLanguagesAndTheImagesAreThoseOfEveryImage() {
		var item = item(new GalleryImage("b", ImageKind.ASPETTO, StoryWindow.from(FIRST_APPEARANCE), Map.of("it", "x")),
				new GalleryImage(null, ImageKind.FORMA, StoryWindow.from(LATER), Map.of("fr", "y")),
				new GalleryImage("a", ImageKind.FORMA, StoryWindow.NONE, Map.of()));

		assertThat(item.galleryWindows()).extracting(NamedWindow::field)
			.containsExactly("gallery[0]", "gallery[1]", "gallery[2]");
		assertThat(item.galleryLanguages()).containsExactly("fr", "it");
		assertThat(item.galleryImageIds()).containsExactly("a", "b");
	}

	private static Item item(GalleryImage... gallery) {
		return new Item(Optional.of(FIRST_APPEARANCE), Arrays.asList(gallery));
	}

	private record Item(Optional<StoryPoint> origin, List<GalleryImage> gallery) implements Galleried {

		@Override
		public List<NamedWindow> windows() {
			return galleryWindows();
		}

	}

}
