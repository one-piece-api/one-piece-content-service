package dev.onepieceapi.contentservice.domain.readertime;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.IntStream;

/**
 * The capability of an item with an image gallery
 * (docs/user-flows/content-editorial-workflow.md 3.5.1): enabled on the Character only,
 * every other entity keeps one fixed image. Its windows follow the rules of every point
 * ({@code ReaderAxisRules}); what it needs to be submitted is written here once. The main
 * portrait is derived by the library {@code one-piece-reader-time}, never stored.
 */
public interface Galleried extends WindowedItem {

	int MAX_IMAGES = 20;

	/** The list as a request names it. */
	String GALLERY_FIELD = "gallery";

	/** An image's caption in a language, e.g. {@code gallery[3].caption[en]}. */
	String CAPTION_FIELD = "gallery[%d].caption[%s]";

	/** Its images, in the order shown. */
	List<GalleryImage> gallery();

	/** One of its images as a request names it, e.g. {@code gallery[3]}. */
	static String entryField(int index) {
		return GALLERY_FIELD + "[" + index + "]";
	}

	/** The window of each image, e.g. {@code gallery[3]}. */
	default List<NamedWindow> galleryWindows() {
		List<GalleryImage> images = gallery();
		return IntStream.range(0, images.size())
			.mapToObj(index -> new NamedWindow(entryField(index), images.get(index).window()))
			.toList();
	}

	/** Every language a caption is written in. */
	default Set<String> galleryLanguages() {
		Set<String> languages = new TreeSet<>();
		gallery().forEach(image -> languages.addAll(image.caption().keySet()));
		return languages;
	}

	/** The stored images it shows, each once. */
	default Set<String> galleryImageIds() {
		Set<String> ids = new TreeSet<>();
		gallery().stream().map(GalleryImage::imageId).filter(Objects::nonNull).forEach(ids::add);
		return ids;
	}

	/**
	 * What submitting asks of the gallery (3.5.1): every caption in every language, and
	 * an appearance discovered at the first appearance - the same point - so the reader
	 * meeting the item sees them.
	 */
	default List<String> missingGallery(Collection<String> languages) {
		List<String> missing = new ArrayList<>();
		List<GalleryImage> images = gallery();
		for (int index = 0; index < images.size(); index++) {
			GalleryImage image = images.get(index);
			int position = index;
			languages.stream()
				.filter(language -> image.captionIn(language) == null)
				.forEach(language -> missing.add(CAPTION_FIELD.formatted(position, language)));
		}
		if (images.stream().noneMatch(this::opensTheGallery)) {
			missing.add(GALLERY_FIELD);
		}
		return missing;
	}

	/** An appearance discovered where the reader meets the item. */
	private boolean opensTheGallery(GalleryImage image) {
		StoryPoint discovered = image.window().discovered();
		return image.isAppearance() && !discovered.isMissing() && origin().map(discovered::equals).orElse(Boolean.TRUE);
	}

}
