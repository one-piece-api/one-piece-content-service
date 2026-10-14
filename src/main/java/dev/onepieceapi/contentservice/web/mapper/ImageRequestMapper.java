package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;

/**
 * What a save does to the image of a draft, read from its request (implementation plan of
 * the Devil Fruit, D5), for every entity that has one: a Devil Fruit's image, a volume's
 * cover.
 */
@UtilityClass
public class ImageRequestMapper {

	private static final String IMAGE_FIELD = "image";

	private static final String BOTH_IMAGE_CHANGES = "must not be sent together with removeImage";

	/**
	 * Replaced by an upload, removed when asked, kept otherwise; both at once is refused.
	 * @param removes whether the request asks to remove the image
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(boolean removes, MultipartFile upload) {
		boolean uploaded = upload != null && !upload.isEmpty();
		if (uploaded && removes) {
			throw new ValueInvalidException(List.of(new FieldViolation(IMAGE_FIELD, BOTH_IMAGE_CHANGES)));
		}
		if (uploaded) {
			return ImageChange.replaceWith(bytesOf(upload));
		}
		return removes ? ImageChange.REMOVE : ImageChange.KEEP;
	}

	private static byte[] bytesOf(MultipartFile upload) {
		try {
			return upload.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
