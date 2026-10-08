package dev.onepieceapi.contentservice.service.image;

import dev.onepieceapi.contentservice.config.ImageProperties;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.image.ImageProfile;
import dev.onepieceapi.contentservice.domain.image.ImageStore;
import dev.onepieceapi.contentservice.domain.image.NormalizedImage;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.service.validation.ImageValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The images of the versions of any entity (implementation plan of the Devil Fruit, D5,
 * D6) - a Facade over checking, normalizing and storing them, called inside the
 * transaction that saves the version: the image and the version are written together or
 * not at all, and an image no version uses any more is deleted in that same transaction,
 * with no job sweeping orphans later.
 */
@Component
@RequiredArgsConstructor
public class ContentImages {

	private final ImageValidator validator;

	private final ImageNormalizer normalizer;

	private final ImageStore store;

	private final ImageProperties properties;

	/**
	 * The image a version will have after the change, stored if it is a new one.
	 * @param current the image it has now; null for none or a new content
	 */
	public String apply(ImageChange change, String current, EntityType entityType) {
		return switch (change) {
			case ImageChange.Keep keep -> current;
			case ImageChange.Remove remove -> null;
			case ImageChange.Replace(byte[] upload) -> store(upload, profileOf(entityType));
		};
	}

	/**
	 * Deletes the image a version stopped using, unless another version still uses it.
	 * Called once the version is saved, so it is no longer counted among the users.
	 * @param now the image the version has now; null for none
	 */
	public void release(String previous, String now) {
		if (previous != null && !previous.equals(now)) {
			this.store.deleteIfUnreferenced(previous);
		}
	}

	private String store(byte[] upload, ImageProfile profile) {
		NormalizedImage image = this.normalizer.normalize(this.validator.validate(upload, profile), profile);
		this.store.save(image);
		return image.id();
	}

	private ImageProfile profileOf(EntityType entityType) {
		return this.properties.profileOf(entityType)
			.orElseThrow(() -> new IllegalStateException("No image profile configured for " + entityType));
	}

}
