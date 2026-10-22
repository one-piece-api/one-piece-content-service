package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationReference;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationResponse;
import dev.onepieceapi.contentservice.web.dto.response.AffiliationTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From Affiliations to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The chapter and the episode it
 * first appears in are given already looked up, so the mapper reads no database.
 */
@UtilityClass
public class AffiliationResponseMapper {

	/** A version of an Affiliation as its caller meets it, with everything it says. */
	public VersionResponse<AffiliationResponse> toVersionResponse(VersionAccess<Affiliation> access,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access,
				affiliation -> toResponse(affiliation, storyItems, images));
	}

	/**
	 * A row of the list of Affiliations: the romaji, the names and how many belong to it.
	 */
	public ContentSummaryResponse<AffiliationNamesResponse> toSummaryResponse(ContentSummary<Affiliation> summary,
			long memberCount) {
		return ContentResponseMapper.toSummaryResponse(summary,
				affiliation -> new AffiliationNamesResponse(affiliation.romaji(), affiliation.names(), memberCount));
	}

	/** An affiliation a character may point to. */
	public AffiliationReferenceResponse toResponse(AffiliationReference reference) {
		return new AffiliationReferenceResponse(reference.id(), reference.romaji(), reference.names(),
				reference.kind());
	}

	/** Everything the version says. */
	public AffiliationResponse toResponse(Affiliation affiliation, Map<UUID, NumberedReference> storyItems,
			ImageResponseMapper images) {
		Map<String, AffiliationTranslationResponse> translations = new TreeMap<>();
		affiliation.translations()
			.forEach((language, translation) -> translations.put(language, toResponse(translation)));
		return new AffiliationResponse(affiliation.romaji(), translations, images.toResponse(affiliation.imageId()),
				FirstAppearanceMapper.toResponse(affiliation.firstAppearance(), storyItems), affiliation.kind());
	}

	private static AffiliationTranslationResponse toResponse(AffiliationTranslation translation) {
		return new AffiliationTranslationResponse(translation.name(), translation.description());
	}

}
