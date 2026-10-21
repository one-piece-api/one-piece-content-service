package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.domain.species.SpeciesTranslation;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.web.dto.request.SpeciesRequest;
import dev.onepieceapi.contentservice.web.dto.request.SpeciesTranslationRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Species to the domain. */
@UtilityClass
public class SpeciesRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Species toDomain(SpeciesRequest request) {
		Map<String, SpeciesTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return new Species(request.romaji(), translations, null,
				FirstAppearanceMapper.toDomain(request.firstAppearance()));
	}

	/**
	 * What the save does to the draft's picture (implementation plan of the Species, D5):
	 * replaced by an upload, removed when asked, kept otherwise; both at once is refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(SpeciesRequest request, MultipartFile upload) {
		return ImageRequestMapper.toImageChange(request.removesImage(), upload);
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static SpeciesTranslation toDomain(SpeciesTranslationRequest translation) {
		return translation == null ? new SpeciesTranslation(null, null)
				: new SpeciesTranslation(translation.name(), translation.description());
	}

}
