package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.CharacterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpithetRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** From the requests about Characters to the domain. */
@UtilityClass
public class CharacterRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Character toDomain(CharacterRequest request) {
		Map<String, CharacterTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		List<Epithet> epithets = Optional.ofNullable(request.epithets())
			.orElseGet(List::of)
			.stream()
			.map(CharacterRequestMapper::toDomain)
			.toList();
		return Character.builder()
			.romaji(request.romaji())
			.japaneseName(request.japaneseName())
			.translations(translations)
			.status(request.status())
			.gender(request.gender())
			.birthday(request.birthday())
			.epithets(epithets)
			.aliases(request.aliases())
			.speciesIds(request.species())
			.firstAppearance(FirstAppearanceMapper.toDomain(request.firstAppearance()))
			.build();
	}

	/**
	 * What the save does to the draft's portrait (implementation plan of the Character,
	 * D7): replaced by an upload, removed when asked, kept otherwise; both at once is
	 * refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(CharacterRequest request, MultipartFile upload) {
		return ImageRequestMapper.toImageChange(request.removesImage(), upload);
	}

	/** The common filters, then the status and the species when the list is narrowed. */
	public ContentFilter toFilter(CharacterListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		if (request.characterStatus() != null) {
			filter = filter.withAttribute(Character.STATUS_ATTRIBUTE, request.characterStatus());
		}
		return request.species() == null ? filter
				: filter.withAttribute(Character.SPECIES_ATTRIBUTE, request.species());
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static CharacterTranslation toDomain(CharacterTranslationRequest translation) {
		return translation == null ? new CharacterTranslation(null, null)
				: new CharacterTranslation(translation.name(), translation.description());
	}

	/** A language sent as null is a language with nothing written. */
	private static Epithet toDomain(EpithetRequest epithet) {
		Map<String, String> text = new HashMap<>();
		Optional.ofNullable(epithet.text()).orElseGet(Map::of).forEach((language, value) -> {
			if (value != null) {
				text.put(language, value);
			}
		});
		return new Epithet(text);
	}

}
