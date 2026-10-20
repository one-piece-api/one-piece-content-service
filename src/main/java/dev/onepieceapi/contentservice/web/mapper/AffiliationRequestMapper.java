package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.affiliation.AffiliationTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.web.dto.request.AffiliationRequest;
import dev.onepieceapi.contentservice.web.dto.request.AffiliationTranslationRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Affiliations to the domain. */
@UtilityClass
public class AffiliationRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Affiliation toDomain(AffiliationRequest request) {
		Map<String, AffiliationTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new Affiliation(request.romaji(), translations, null,
				FirstAppearanceMapper.toDomain(request.firstAppearance()));
	}

	/**
	 * What the save does to the draft's emblem (implementation plan of the Affiliation,
	 * D5): replaced by an upload, removed when asked, kept otherwise; both at once is
	 * refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(AffiliationRequest request, MultipartFile upload) {
		return ImageRequestMapper.toImageChange(request.removesImage(), upload);
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static AffiliationTranslation toDomain(AffiliationTranslationRequest translation) {
		return translation == null ? new AffiliationTranslation(null, null)
				: new AffiliationTranslation(translation.name(), translation.description());
	}

}
