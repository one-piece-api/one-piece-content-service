package dev.onepieceapi.contentservice.service.exception;

import dev.onepieceapi.exception.DomainException;

import java.util.Map;

/**
 * Raised when an uploaded image breaks its entity's profile (implementation plan of the
 * Devil Fruit, D6). One code per reason and the numbers that explain it in the details,
 * next to {@code field: "image"}, so a client can tell the uploader what to change in
 * their own language.
 */
public class ImageRefusedException extends DomainException {

	private static final String FIELD = "image";

	public ImageRefusedException(ContentErrorCode code, String message, Map<String, ?> numbers) {
		super(code, message);
		withDetail("field", FIELD);
		numbers.forEach(this::withDetail);
	}

	public static ImageRefusedException formatUnsupported() {
		return new ImageRefusedException(ContentErrorCode.IMAGE_FORMAT_UNSUPPORTED, "The image must be a static PNG",
				Map.of());
	}

}
