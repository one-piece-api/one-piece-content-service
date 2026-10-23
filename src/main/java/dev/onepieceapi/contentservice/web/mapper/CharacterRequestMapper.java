package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.CharacterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpithetRequest;
import dev.onepieceapi.contentservice.web.dto.request.FruitUseRequest;
import dev.onepieceapi.contentservice.web.dto.request.MembershipRequest;
import dev.onepieceapi.contentservice.web.dto.request.RelativeRequest;
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
			.affiliations(Optional.ofNullable(request.affiliations())
				.orElseGet(List::of)
				.stream()
				.map(CharacterRequestMapper::toDomain)
				.toList())
			.devilFruits(Optional.ofNullable(request.devilFruits())
				.orElseGet(List::of)
				.stream()
				.map(CharacterRequestMapper::toDomain)
				.toList())
			.relatives(Optional.ofNullable(request.relatives())
				.orElseGet(List::of)
				.stream()
				.map(CharacterRequestMapper::toDomain)
				.toList())
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

	/**
	 * The common filters, then those of the character itself when the list is narrowed.
	 */
	public ContentFilter toFilter(CharacterListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		if (request.characterStatus() != null) {
			filter = filter.withAttribute(Character.STATUS_ATTRIBUTE, request.characterStatus());
		}
		if (request.species() != null) {
			filter = filter.withAttribute(Character.SPECIES_ATTRIBUTE, request.species());
		}
		if (request.affiliation() != null) {
			filter = filter.withAttribute(Character.AFFILIATION_ATTRIBUTE, request.affiliation());
		}
		if (request.affiliationKind() != null) {
			filter = filter.withAttribute(Character.AFFILIATION_KIND_ATTRIBUTE, request.affiliationKind());
		}
		return request.devilFruit() == null ? filter
				: filter.withAttribute(Character.DEVIL_FRUIT_ATTRIBUTE, request.devilFruit());
	}

	private static Relative toDomain(RelativeRequest relative) {
		return new Relative(relative.relative(), relative.kind());
	}

	private static FruitUse toDomain(FruitUseRequest use) {
		return new FruitUse(use.devilFruit(), use.isFormer());
	}

	/** A language sent as null is a language with nothing written. */
	private static Membership toDomain(MembershipRequest membership) {
		Map<String, String> role = new HashMap<>();
		Optional.ofNullable(membership.role()).orElseGet(Map::of).forEach((language, value) -> {
			if (value != null) {
				role.put(language, value);
			}
		});
		return new Membership(membership.affiliation(), role, membership.marineRank(), membership.isFormer());
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
