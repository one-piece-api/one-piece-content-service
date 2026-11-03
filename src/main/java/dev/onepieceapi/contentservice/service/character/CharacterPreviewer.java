package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.character.Alias;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterField;
import dev.onepieceapi.contentservice.domain.character.CharacterKnowledge;
import dev.onepieceapi.contentservice.domain.character.CharacterLookups;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.character.CharacterTranslation;
import dev.onepieceapi.contentservice.domain.character.Epithet;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.character.MembershipPeriod;
import dev.onepieceapi.contentservice.domain.character.Relative;
import dev.onepieceapi.contentservice.domain.character.RelativeOf;
import dev.onepieceapi.contentservice.domain.character.SpeciesEntry;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.readertime.GalleryImage;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.RevealedName;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.readertime.WindowedItem;
import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.persistence.entity.AffiliationVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.SpeciesVersionEntity;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitPreviewer;
import dev.onepieceapi.readertime.ReaderPosition;
import dev.onepieceapi.readertime.WindowState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * A Character as a reader at N sees it (UF-CNT-23): met from its first appearance, its
 * identity and status as known there (Ace alive before Marineford), each list with the
 * entries discovered by N whose target is met, each membership with its periods
 * discovered by N, its gallery and main portrait at N, its biography as the paragraphs
 * read there. What its entries point to is read on its version online (reveals plan R6,
 * D5): a Devil Fruit or a relative is called as the reader knows it at N.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class CharacterPreviewer implements ContentPreviewer<Character> {

	private static final String AFFILIATIONS_FIELD = "affiliations";

	private final ReaderPreviews previews;

	private final CharacterReferences references;

	private final ContentDefinition<Character, CharacterVersionEntity> characters;

	private final ContentDefinition<Species, SpeciesVersionEntity> species;

	private final ContentDefinition<Affiliation, AffiliationVersionEntity> affiliations;

	private final ContentDefinition<DevilFruit, DevilFruitVersionEntity> devilFruits;

	@Override
	public Preview<Character> previewOf(UUID contentId, Character body, ReaderPosition position) {
		Map<UUID, Species> onlineSpecies = this.previews.online(this.species,
				ids(body.species(), SpeciesEntry::speciesContentId));
		Map<UUID, Affiliation> onlineAffiliations = this.previews.online(this.affiliations,
				ids(body.affiliations(), Membership::affiliationContentId));
		Map<UUID, DevilFruit> onlineFruits = this.previews.online(this.devilFruits,
				ids(body.devilFruits(), FruitUse::devilFruitContentId));
		Map<UUID, Character> onlineRelatives = this.previews.online(this.characters,
				ids(body.relatives(), Relative::relativeContentId));
		Map<UUID, StoryPoint> targets = new HashMap<>();
		Stream.of(onlineSpecies, onlineAffiliations, onlineFruits, onlineRelatives)
			.forEach(online -> targets.putAll(ReaderPreviews.firstAppearances(online)));
		List<StoryPoint> points = pointsOf(Stream.of(List.of(body), onlineFruits.values(), onlineRelatives.values()));
		PreviewContext context = this.previews.contextFor(position, points, targets);
		if (!context.hasMet(body.firstAppearance())) {
			return Preview.notVisible(position);
		}
		Map<UUID, RevealedName> namesAt = new HashMap<>();
		onlineFruits.forEach((id, fruit) -> namesAt.put(id, DevilFruitPreviewer.nameAt(context, fruit)));
		onlineRelatives.forEach((id, relative) -> namesAt.put(id, nameAt(context, relative)));
		return context.previewOf(resolved(body, context), mainPortraitOf(body, context), namesAt);
	}

	/**
	 * What the lists of the character previewed point to, and who points to it, as the
	 * reader at N knows them: the Devil Fruits and the relatives called as known there;
	 * the characters online naming it as a relative, those whose entry is discovered and
	 * who are met at N, called as known there.
	 */
	public CharacterLookups lookupsAt(UUID contentId, Preview<Character> preview) {
		Character body = preview.body();
		Map<UUID, DevilFruitReference> fruits = new HashMap<>();
		this.references.devilFruitsOf(body)
			.forEach((id, fruit) -> fruits.put(id, renamed(fruit, preview, DevilFruitReference::named)));
		Map<UUID, CharacterReference> relatives = new HashMap<>();
		this.references.relativesOf(body)
			.forEach((id, relative) -> relatives.put(id, renamed(relative, preview, CharacterReference::named)));
		return new CharacterLookups(this.references.speciesOf(body), this.references.affiliationsOf(body), fruits,
				relatives, relativesOfAt(contentId, preview.position()));
	}

	/** What the reader at N calls the character: its romaji and its names known there. */
	public static RevealedName nameAt(PreviewContext context, Character character) {
		return context.valueAt(character, CharacterField.IDENTITY, currentOf(character))
			.map(identity -> new RevealedName(identity.romaji(), identity.names()))
			.orElseGet(() -> new RevealedName(null, Map.of()));
	}

	private Character resolved(Character body, PreviewContext context) {
		CharacterKnowledge current = currentOf(body);
		CharacterKnowledge identity = context.valueAt(body, CharacterField.IDENTITY, current)
			.orElse(CharacterKnowledge.NONE);
		CharacterKnowledge status = context.valueAt(body, CharacterField.STATUS, current)
			.orElse(CharacterKnowledge.NONE);
		return body.toBuilder()
			.romaji(identity.romaji())
			.japaneseName(identity.japaneseName())
			.translations(translationsOf(identity.names()))
			.status(status.status())
			.epithets(context.entries(body.epithets(), "epithets",
					(epithet, window) -> new Epithet(epithet.text(), window)))
			.aliases(context.entries(body.aliases(), "aliases", (alias, window) -> new Alias(alias.name(), window)))
			.species(context.entries(body.species(), "species", SpeciesEntry::speciesContentId,
					(entry, window) -> new SpeciesEntry(entry.speciesContentId(), window)))
			.affiliations(membershipsAt(body.affiliations(), context))
			.devilFruits(context.entries(body.devilFruits(), "devilFruits", FruitUse::devilFruitContentId,
					(use, window) -> new FruitUse(use.devilFruitContentId(), window)))
			.relatives(context.entries(body.relatives(), "relatives", Relative::relativeContentId,
					(relative, window) -> new Relative(relative.relativeContentId(), relative.kind(), window)))
			.gallery(context.entries(body.gallery(), "gallery",
					(image, window) -> new GalleryImage(image.imageId(), image.kind(), window, image.caption())))
			.firstAppearance(context.trimmed(body.firstAppearance()))
			.description(context.paragraphs(ParagraphSection.DESCRIPTION, body.description()))
			.initialKnowledge(CharacterKnowledge.NONE)
			.reveals(List.of())
			.build();
	}

	/**
	 * Each membership with its periods discovered by N, when its affiliation is met
	 * (3.4.7); one with no period discovered yet is not there.
	 */
	private static List<Membership> membershipsAt(List<Membership> memberships, PreviewContext context) {
		List<Membership> visible = new ArrayList<>();
		for (int index = 0; index < memberships.size(); index++) {
			Membership membership = memberships.get(index);
			String field = AFFILIATIONS_FIELD + "[" + index + "]";
			List<MembershipPeriod> periods = context.entries(membership.periods(), field + ".periods",
					(period, window) -> new MembershipPeriod(window, period.role(), period.marineRank()));
			if (!periods.isEmpty() && context.seesTarget(membership.affiliationContentId(), field)) {
				visible.add(new Membership(membership.affiliationContentId(), periods));
			}
		}
		return visible;
	}

	private static String mainPortraitOf(Character body, PreviewContext context) {
		return context.mainPortrait(body.gallery()).map(GalleryImage::imageId).orElse(null);
	}

	/**
	 * The characters online naming this one as a relative, as the reader at N sees them:
	 * the entry discovered, the character holding it met, called as known there.
	 */
	private List<RelativeOf> relativesOfAt(UUID contentId, ReaderPosition position) {
		List<RelativeOf> links = this.references.relativesOf(contentId);
		Map<UUID, Character> holders = this.previews.online(this.characters,
				links.stream().map(RelativeOf::characterId).toList());
		List<StoryPoint> points = Stream
			.concat(links.stream().flatMap(link -> link.window().points()),
					pointsOf(Stream.of(holders.values())).stream())
			.toList();
		PreviewContext context = this.previews.contextFor(position, points, ReaderPreviews.firstAppearances(holders));
		return links.stream()
			.filter(link -> context.stateOf(link.window()) != WindowState.HIDDEN)
			.filter(link -> holders.containsKey(link.characterId())
					&& context.hasMet(holders.get(link.characterId()).firstAppearance()))
			.map(link -> new RelativeOf(link.character().named(nameAt(context, holders.get(link.characterId()))),
					link.kind(), context.trimmed(link.window())))
			.toList();
	}

	private static <R> R renamed(R reference, Preview<Character> preview, NamedBy<R> named) {
		UUID id = idOf(reference);
		RevealedName name = preview.namesAt().get(id);
		return (name == null) ? reference : named.apply(reference, name);
	}

	private static UUID idOf(Object reference) {
		return switch (reference) {
			case DevilFruitReference fruit -> fruit.id();
			case CharacterReference character -> character.id();
			default -> throw new IllegalArgumentException("Not a named reference: " + reference);
		};
	}

	private static CharacterKnowledge currentOf(Character character) {
		return new CharacterKnowledge(character.romaji(), character.japaneseName(), character.status(),
				character.names());
	}

	private static Map<String, CharacterTranslation> translationsOf(Map<String, String> names) {
		Map<String, CharacterTranslation> translations = new TreeMap<>();
		names.forEach((language, name) -> translations.put(language, new CharacterTranslation(name)));
		return translations;
	}

	private static <E> List<UUID> ids(List<E> entries, Function<E, UUID> target) {
		return entries.stream().map(target).distinct().toList();
	}

	private static List<StoryPoint> pointsOf(Stream<? extends Collection<? extends WindowedItem>> items) {
		return items.flatMap(Collection::stream).flatMap(item -> item.points().stream()).toList();
	}

	/** A reference called by another name. */
	@FunctionalInterface
	private interface NamedBy<R> {

		R apply(R reference, RevealedName name);

	}

}
