package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTranslationRequest;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Devil Fruits to the domain. */
@UtilityClass
public class DevilFruitRequestMapper {

	/** The relation of a fruit to its type, as {@code ContentFilter} names it. */
	private static final String TYPE_RELATION = "type";

	private static final String IMAGE_FIELD = "image";

	private static final String BOTH_IMAGE_CHANGES = "must not be sent together with removeImage";

	/** What the request says, as typed: tidying it up is left to the domain. */
	public DevilFruit toDomain(DevilFruitRequest request) {
		Map<String, DevilFruitTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new DevilFruit(request.romaji(), request.type(), request.subcategory(), translations, null);
	}

	/**
	 * What the save does to the draft's image (implementation plan of the Devil Fruit,
	 * D5): replaced by an upload, removed when asked, kept otherwise; both at once is
	 * refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(DevilFruitRequest request, MultipartFile upload) {
		boolean uploaded = upload != null && !upload.isEmpty();
		if (uploaded && request.removesImage()) {
			throw new ValueInvalidException(List.of(new FieldViolation(IMAGE_FIELD, BOTH_IMAGE_CHANGES)));
		}
		if (uploaded) {
			return ImageChange.replaceWith(bytesOf(upload));
		}
		return request.removesImage() ? ImageChange.REMOVE : ImageChange.KEEP;
	}

	/** The common filters, then the type when the list is narrowed to one. */
	public ContentFilter toFilter(DevilFruitListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.type() == null ? filter : filter.relatedTo(TYPE_RELATION, request.type());
	}

	private static byte[] bytesOf(MultipartFile upload) {
		try {
			return upload.getBytes();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static DevilFruitTranslation toDomain(DevilFruitTranslationRequest translation) {
		return translation == null ? new DevilFruitTranslation(null, null, null, null) : new DevilFruitTranslation(
				translation.name(), translation.description(), translation.advantages(), translation.disadvantages());
	}

}
