package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategoryTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeSubcategoryRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeSubcategoryTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.DevilFruitTypeTranslationRequest;
import lombok.experimental.UtilityClass;

import java.util.List;
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
		List<DevilFruitTypeSubcategory> subcategories = Optional.ofNullable(request.subcategories())
			.orElseGet(List::of)
			.stream()
			.map(DevilFruitTypeRequestMapper::toDomain)
			.toList();
		return new DevilFruitType(request.romaji(), translations, subcategories);
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static DevilFruitTypeTranslation toDomain(DevilFruitTypeTranslationRequest translation) {
		return translation == null ? new DevilFruitTypeTranslation(null, null, null, null)
				: new DevilFruitTypeTranslation(translation.name(), translation.description(), translation.advantages(),
						translation.disadvantages());
	}

	/** A subcategory sent as nothing at all is a new one with nothing written. */
	private static DevilFruitTypeSubcategory toDomain(DevilFruitTypeSubcategoryRequest subcategory) {
		if (subcategory == null) {
			return new DevilFruitTypeSubcategory(null, Map.of());
		}
		Map<String, DevilFruitTypeSubcategoryTranslation> translations = new TreeMap<>();
		Optional.ofNullable(subcategory.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new DevilFruitTypeSubcategory(subcategory.id(), translations);
	}

	private static DevilFruitTypeSubcategoryTranslation toDomain(
			DevilFruitTypeSubcategoryTranslationRequest translation) {
		return translation == null ? new DevilFruitTypeSubcategoryTranslation(null, null)
				: new DevilFruitTypeSubcategoryTranslation(translation.name(), translation.description());
	}

}
