package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterKnowledge;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.AliasRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterKnowledgeRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterListRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterRevealRequest;
import dev.onepieceapi.contentservice.web.dto.request.CharacterTranslationRequest;
import dev.onepieceapi.contentservice.web.dto.request.EpithetRequest;
import dev.onepieceapi.contentservice.web.dto.request.FruitUseRequest;
import dev.onepieceapi.contentservice.web.dto.request.MembershipPeriodRequest;
import dev.onepieceapi.contentservice.web.dto.request.MembershipRequest;
import dev.onepieceapi.contentservice.web.dto.request.RelativeRequest;
import dev.onepieceapi.contentservice.web.dto.request.SpeciesEntryRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/** From the requests about Characters to the domain. */
@UtilityClass
public class CharacterRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Character toDomain(CharacterRequest request) {
		Map<String, CharacterTranslation> translations = new TreeMap<>();
		Optional.ofNullable(request.translations())
			.orElseGet(Map::of)
			.forEach((language, translation) -> translations.put(language, toDomain(translation)));
		return Character.builder()
			.romaji(request.romaji())
			.japaneseName(request.japaneseName())
			.translations(translations)
			.status(request.status())
			.gender(request.gender())
			.birthday(request.birthday())
			.epithets(entries(request.epithets(), CharacterRequestMapper::toDomain))
			.aliases(entries(request.aliases(), CharacterRequestMapper::toDomain))
			.species(entries(request.species(), CharacterRequestMapper::toDomain))
			.affiliations(entries(request.affiliations(), CharacterRequestMapper::toDomain))
			.devilFruits(entries(request.devilFruits(), CharacterRequestMapper::toDomain))
			.relatives(entries(request.relatives(), CharacterRequestMapper::toDomain))
			.firstAppearance(FirstAppearanceMapper.toDomain(request.firstAppearance()))
			.description(ParagraphRequestMapper.toDomain(request.description()))
			.initialKnowledge(Optional.ofNullable(request.initialKnowledge())
				.map(CharacterRequestMapper::toDomain)
				.orElse(CharacterKnowledge.NONE))
			.reveals(RevealRequestMapper.each(request.reveals(), CharacterRequestMapper::toDomain))
			.build();
	}

	private static CharacterKnowledge toDomain(CharacterKnowledgeRequest knowledge) {
		return new CharacterKnowledge(knowledge.romaji(), knowledge.japaneseName(), knowledge.status(),
				RevealRequestMapper.names(knowledge.translations(), CharacterTranslationRequest::name));
	}

	private static Reveal<CharacterField, CharacterKnowledge> toDomain(CharacterRevealRequest reveal) {
		var values = new CharacterKnowledge(reveal.romaji(), reveal.japaneseName(), reveal.status(),
				RevealRequestMapper.names(reveal.translations(), CharacterTranslationRequest::name));
		return RevealRequestMapper.toDomain(reveal, values);
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

	/** A list left out is an empty one; each entry mapped in its order. */
	private static <R, D> List<D> entries(List<R> requests, Function<R, D> toDomain) {
		return Optional.ofNullable(requests).orElseGet(List::of).stream().map(toDomain).toList();
	}

	private static Alias toDomain(AliasRequest alias) {
		return new Alias(alias.alias(), FirstAppearanceMapper.toWindow(alias));
	}

	private static SpeciesEntry toDomain(SpeciesEntryRequest species) {
		return new SpeciesEntry(species.species(), FirstAppearanceMapper.toWindow(species));
	}

	private static Relative toDomain(RelativeRequest relative) {
		return new Relative(relative.relative(), relative.kind(), FirstAppearanceMapper.toWindow(relative));
	}

	private static FruitUse toDomain(FruitUseRequest use) {
		return new FruitUse(use.devilFruit(), FirstAppearanceMapper.toWindow(use));
	}

	private static Membership toDomain(MembershipRequest membership) {
		return new Membership(membership.affiliation(),
				entries(membership.periods(), CharacterRequestMapper::toDomain));
	}

	private static MembershipPeriod toDomain(MembershipPeriodRequest period) {
		return new MembershipPeriod(FirstAppearanceMapper.toWindow(period), written(period.role()),
				period.marineRank());
	}

	/** A language sent with nothing in it is a language with nothing written. */
	private static CharacterTranslation toDomain(CharacterTranslationRequest translation) {
		return translation == null ? new CharacterTranslation(null) : new CharacterTranslation(translation.name());
	}

	private static Epithet toDomain(EpithetRequest epithet) {
		return new Epithet(written(epithet.text()), FirstAppearanceMapper.toWindow(epithet));
	}

	/** A language sent as null is a language with nothing written. */
	private static Map<String, String> written(Map<String, String> values) {
		Map<String, String> written = new HashMap<>();
		Optional.ofNullable(values).orElseGet(Map::of).forEach((language, value) -> {
			if (value != null) {
				written.put(language, value);
			}
		});
		return written;
	}

}
