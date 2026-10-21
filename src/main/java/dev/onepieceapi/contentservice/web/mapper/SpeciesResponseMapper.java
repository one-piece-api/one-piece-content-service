package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesResponse;
import dev.onepieceapi.contentservice.web.dto.response.SpeciesTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From Species to their response bodies: what a version says is mapped here, the workflow
 * around it by {@link ContentResponseMapper}. The chapter and the episode it first
 * appears in are given already looked up, so the mapper reads no database.
 */
@UtilityClass
public class SpeciesResponseMapper {

	/** A version of a Species as its caller meets it, with everything it says. */
	public VersionResponse<SpeciesResponse> toVersionResponse(VersionAccess<Species> access,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access, species -> toResponse(species, storyItems, images));
	}

	/** A row of the list of Species: the romaji and the names. */
	public ContentSummaryResponse<SpeciesNamesResponse> toSummaryResponse(ContentSummary<Species> summary) {
		return ContentResponseMapper.toSummaryResponse(summary,
				species -> new SpeciesNamesResponse(species.romaji(), species.names()));
	}

	/** Everything the version says. */
	public SpeciesResponse toResponse(Species species, Map<UUID, NumberedReference> storyItems,
			ImageResponseMapper images) {
		Map<String, SpeciesTranslationResponse> translations = new TreeMap<>();
		species.translations().forEach((language, translation) -> translations.put(language, toResponse(translation)));
		return new SpeciesResponse(species.romaji(), translations, images.toResponse(species.imageId()),
				FirstAppearanceMapper.toResponse(species.firstAppearance(), storyItems));
	}

	/** A species a character may point to. */
	public SpeciesReferenceResponse toResponse(SpeciesReference reference) {
		return new SpeciesReferenceResponse(reference.id(), reference.romaji(), reference.names());
	}

	private static SpeciesTranslationResponse toResponse(SpeciesTranslation translation) {
		return new SpeciesTranslationResponse(translation.name(), translation.description());
	}

}
