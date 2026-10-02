package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTypeResponse;
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
	public ContentSummaryResponse<DevilFruitTypeNamesResponse> toSummaryResponse(
			ContentSummary<DevilFruitType> summary) {
		return ContentResponseMapper.toSummaryResponse(summary, DevilFruitTypeResponseMapper::toNamesResponse);
	}

	/** Everything the version says. */
	public DevilFruitTypeResponse toResponse(DevilFruitType devilFruitType) {
		Map<String, DevilFruitTypeTranslationResponse> translations = new TreeMap<>();
		devilFruitType.translations()
			.forEach((language, translation) -> translations.put(language, toTranslationResponse(translation)));
		return new DevilFruitTypeResponse(devilFruitType.romaji(), translations);
	}

	/** What a list row shows: the names, for the languages that have one. */
	public DevilFruitTypeNamesResponse toNamesResponse(DevilFruitType devilFruitType) {
		Map<String, String> names = new TreeMap<>();
		devilFruitType.translations().forEach((language, translation) -> {
			if (translation.name() != null) {
				names.put(language, translation.name());
			}
		});
		return new DevilFruitTypeNamesResponse(devilFruitType.romaji(), names);
	}

	private static DevilFruitTypeTranslationResponse toTranslationResponse(DevilFruitTypeTranslation translation) {
		return new DevilFruitTypeTranslationResponse(translation.name(), translation.description());
	}

}
