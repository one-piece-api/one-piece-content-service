package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.persistence.entity.ImageEntity;
import dev.onepieceapi.contentservice.persistence.repository.ImageRepository;

import java.time.Instant;
import java.util.HexFormat;

/**
 * Seeds stored images for the tests of what shows one - a character's gallery - whose
 * rows must point to an image that exists. The bytes are not a real picture: nothing
 * reads them back.
 */
public final class StoredImages {

	private static final Instant EARLIER = Instant.parse("2026-10-10T10:00:00Z");

	private StoredImages() {
	}

	/**
	 * An image with an id made of this one character, a hex digit, e.g. {@code 'a'}; its
	 * id.
	 */
	public static String image(ImageRepository images, char idCharacter) {
		String id = String.valueOf(idCharacter).repeat(64);
		images.save(ImageEntity.builder()
			.id(id)
			.contentType("image/webp")
			.width(320)
			.height(400)
			.sizeBytes(4)
			.bytes(HexFormat.of().parseHex("52494646"))
			.createdAt(EARLIER)
			.build());
		return id;
	}

	/** The portrait every complete character shows. */
	public static String portrait(ImageRepository images) {
		return image(images, 'f');
	}

}
