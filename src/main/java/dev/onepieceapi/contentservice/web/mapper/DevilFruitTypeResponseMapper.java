package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeSubcategoryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeSubcategoryTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;

/**
 * From Devil Fruit Types to their response bodies: what a version says is mapped here,
 * the workflow around it by {@link ContentResponseMapper}.
 */
@UtilityClass
public class DevilFruitTypeResponseMapper {

	/**
	 * A version of a Devil Fruit Type as its caller meets it, with everything it says.
	 */
	public VersionResponse<DevilFruitTypeResponse> toVersionResponse(VersionAccess<DevilFruitType> access) {
		return ContentResponseMapper.toVersionResponse(access, DevilFruitTypeResponseMapper::toResponse);
	}

	/** A row of the list of Devil Fruit Types. */
	public ContentSummaryResponse<DevilFruitTypeNamesResponse> toSummaryResponse(ContentSummary<DevilFruitType> summary,
			long devilFruitCount) {
		return ContentResponseMapper.toSummaryResponse(summary,
				devilFruitType -> toNamesResponse(devilFruitType, devilFruitCount));
	}

	/** Everything the version says. */
	public DevilFruitTypeResponse toResponse(DevilFruitType devilFruitType) {
		Map<String, DevilFruitTypeTranslationResponse> translations = new TreeMap<>();
		devilFruitType.translations()
			.forEach((language, translation) -> translations.put(language, toTranslationResponse(translation)));
		return new DevilFruitTypeResponse(devilFruitType.romaji(), translations,
				devilFruitType.subcategories()
					.stream()
					.map(DevilFruitTypeResponseMapper::toSubcategoryResponse)
					.toList());
	}

	/**
	 * What a list row shows: the names, for the languages that have one, and its fruits.
	 */
	public DevilFruitTypeNamesResponse toNamesResponse(DevilFruitType devilFruitType, long devilFruitCount) {
		return new DevilFruitTypeNamesResponse(devilFruitType.romaji(), devilFruitType.names(), devilFruitCount);
	}

	private static DevilFruitTypeTranslationResponse toTranslationResponse(DevilFruitTypeTranslation translation) {
		return new DevilFruitTypeTranslationResponse(translation.name(), translation.description(),
				translation.advantages(), translation.disadvantages());
	}

	private static DevilFruitTypeSubcategoryResponse toSubcategoryResponse(DevilFruitTypeSubcategory subcategory) {
		Map<String, DevilFruitTypeSubcategoryTranslationResponse> translations = new TreeMap<>();
		subcategory.translations()
			.forEach((language, translation) -> translations.put(language,
					new DevilFruitTypeSubcategoryTranslationResponse(translation.name(), translation.description())));
		return new DevilFruitTypeSubcategoryResponse(subcategory.id(), translations);
	}

}
