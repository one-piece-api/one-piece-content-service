package dev.onepieceapi.contentservice.service.image;

import dev.onepieceapi.contentservice.domain.image.GalleryUploads;
import dev.onepieceapi.contentservice.domain.readertime.Galleried;
import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.service.exception.ImageRefusedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * The images of a gallery as a draft is saved (workflow 3.5.1; reveals plan R5), over
 * {@link ContentImages}: each upload checked against the entity's profile and stored,
 * each image kept checked to be one the draft already shows - a save never points to an
 * image of another version it may not see. Called inside the transaction that saves the
 * version, so the images and the version are written together or not at all.
 */
@Component
@RequiredArgsConstructor
public class GalleryImages {

	private static final String IMAGE_ID_FIELD = "%s.imageId";

	private static final String IMAGE_FIELD = "%s.image";

	private static final String NOT_OF_THIS_DRAFT = "must be an image of this draft, or an upload";

	private final ContentImages images;

	/**
	 * The gallery as it will be saved: every upload stored, every other image one of
	 * {@code current}.
	 * @param current the images the draft shows now; empty for a new content
	 */
	public List<GalleryImage> apply(List<GalleryImage> written, GalleryUploads uploads, Collection<String> current,
			EntityType entityType) {
		List<FieldViolation> violations = new ArrayList<>();
		List<GalleryImage> saved = new ArrayList<>();
		for (int index = 0; index < written.size(); index++) {
			GalleryImage image = written.get(index);
			String field = Galleried.entryField(index);
			var upload = uploads.of(index);
			if (upload.isPresent()) {
				saved.add(image.withImageId(store(upload.get(), field, entityType)));
			}
			else if (image.imageId() == null || !current.contains(image.imageId())) {
				violations.add(new FieldViolation(IMAGE_ID_FIELD.formatted(field), NOT_OF_THIS_DRAFT));
			}
			else {
				saved.add(image);
			}
		}
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
		return saved;
	}

	/**
	 * Deletes every image the version stopped showing, unless another version still shows
	 * it. Called once the version is saved.
	 */
	public void release(Collection<String> previous, Collection<String> now) {
		previous.stream().filter(id -> !now.contains(id)).forEach(id -> this.images.release(id, null));
	}

	/** An image refused by the profile names the entry that brought it. */
	private String store(byte[] upload, String field, EntityType entityType) {
		try {
			return this.images.store(upload, entityType);
		}
		catch (ImageRefusedException refused) {
			throw refused.atField(IMAGE_FIELD.formatted(field));
		}
	}

}
