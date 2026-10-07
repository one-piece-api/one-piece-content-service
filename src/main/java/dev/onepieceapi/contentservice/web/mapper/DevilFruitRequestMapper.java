package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitListRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTranslationRequest;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Devil Fruits to the domain. */
@UtilityClass
public class DevilFruitRequestMapper {

	/** The relation of a fruit to its type, as {@code ContentFilter} names it. */
	private static final String TYPE_RELATION = "type";

	/** What the request says, as typed: tidying it up is left to the domain. */
	public DevilFruit toDomain(DevilFruitRequest request) {
		Map<String, DevilFruitTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new DevilFruit(request.romaji(), request.type(), translations);
	}

	/** The common filters, then the type when the list is narrowed to one. */
	public ContentFilter toFilter(DevilFruitListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		return request.type() == null ? filter : filter.relatedTo(TYPE_RELATION, request.type());
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static DevilFruitTranslation toDomain(DevilFruitTranslationRequest translation) {
		return translation == null ? new DevilFruitTranslation(null, null, null, null) : new DevilFruitTranslation(
				translation.name(), translation.description(), translation.advantages(), translation.disadvantages());
	}

}
