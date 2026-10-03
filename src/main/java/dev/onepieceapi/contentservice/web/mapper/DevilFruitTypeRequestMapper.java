package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeTranslationRequest;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Devil Fruit Types to the domain. */
@UtilityClass
public class DevilFruitTypeRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public DevilFruitType toDomain(DevilFruitTypeRequest request) {
		Map<String, DevilFruitTypeTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new DevilFruitType(request.romaji(), translations);
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static DevilFruitTypeTranslation toDomain(DevilFruitTypeTranslationRequest translation) {
		return translation == null ? new DevilFruitTypeTranslation(null, null)
				: new DevilFruitTypeTranslation(translation.name(), translation.description());
	}

}
