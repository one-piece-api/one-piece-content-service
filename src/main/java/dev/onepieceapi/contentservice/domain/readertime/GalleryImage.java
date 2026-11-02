package dev.onepieceapi.contentservice.domain.readertime;

import dev.onepieceapi.contentservice.domain.workflow.Text;
import dev.onepieceapi.readertime.ImageKind;

import java.util.Map;

/**
 * One image of a gallery (docs/user-flows/content-editorial-workflow.md 3.5.1): an
 * appearance or a form of the character, with its window on the reader axis and a caption
 * per language. Part of the item's version, in the order shown. An image past its end is
 * a past appearance, still shown as such.
 *
 * @param imageId the stored image; null only for an upload still to be stored
 * @param kind {@code ASPETTO} may be the main portrait, {@code FORMA} never
 * @param caption per language code, for the languages it is written in
 */
public record GalleryImage(String imageId, ImageKind kind, StoryWindow window,
		Map<String, String> caption) implements Windowed {

	public static final int CAPTION_MAX_LENGTH = 100;

	public GalleryImage {
		window = StoryWindow.orNone(window);
		caption = caption == null ? Map.of() : Map.copyOf(caption);
	}

	/**
	 * Every caption without the space around it; a language left blank is not written.
	 */
	public GalleryImage normalized() {
		return new GalleryImage(this.imageId, this.kind, this.window, Text.written(this.caption));
	}

	/** The same entry showing another stored image. */
	public GalleryImage withImageId(String storedImageId) {
		return new GalleryImage(storedImageId, this.kind, this.window, this.caption);
	}

	/** What its caption says in this language; null when nothing is written in it. */
	public String captionIn(String language) {
		return this.caption.get(language);
	}

	/** An appearance: the only kind that may be the main portrait. */
	public boolean isAppearance() {
		return this.kind == ImageKind.ASPETTO;
	}

}
