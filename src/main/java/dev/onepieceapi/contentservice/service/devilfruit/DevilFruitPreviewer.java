package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSubcategory;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.RevealedName;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitTypeVersionEntity;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A Devil Fruit as a reader at N sees it (UF-CNT-23): met from its first appearance, its
 * name, romaji and type with subcategory as known there (workflow 3.7.1: "Gomu Gomu no
 * Mi", a Paramecia, before chapter 1044), its long texts as the paragraphs read there.
 * The type at N is read on its version online and hidden until the reader meets it, its
 * subcategory likewise.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitPreviewer implements ContentPreviewer<DevilFruit> {

	private static final String TYPE_FIELD = "type";

	private static final String SUBCATEGORY_FIELD = "subcategory";

	private final ReaderPreviews previews;

	private final ContentDefinition<DevilFruitType, DevilFruitTypeVersionEntity> types;

	@Override
	public Preview<DevilFruit> previewOf(UUID contentId, DevilFruit body, ReaderPosition position) {
		List<UUID> typeIds = Stream
			.concat(Stream.of(body.initialKnowledge().typeContentId(), body.typeContentId()),
					body.reveals().stream().map(reveal -> reveal.values().typeContentId()))
			.filter(Objects::nonNull)
			.distinct()
			.toList();
		PreviewContext context = this.previews.contextFor(position, body.points(),
				targetsOf(this.previews.online(this.types, typeIds)));
		if (!context.hasMet(body.firstAppearance())) {
			return Preview.notVisible(position);
		}
		DevilFruitKnowledge current = currentOf(body);
		Optional<DevilFruitKnowledge> type = context.valueAt(body, DevilFruitField.TYPE, current);
		UUID typeId = type.map(DevilFruitKnowledge::typeContentId).orElse(null);
		UUID subcategoryId = type.map(DevilFruitKnowledge::subcategoryId).orElse(null);
		boolean typeSeen = context.seesTarget(typeId, TYPE_FIELD);
		boolean subcategorySeen = typeSeen && context.seesTarget(subcategoryId, SUBCATEGORY_FIELD);
		RevealedName name = nameAt(context, body);
		return context.previewOf(body.toBuilder()
			.romaji(name.romaji())
			.translations(translationsOf(name))
			.typeContentId(typeSeen ? typeId : null)
			.subcategoryId(subcategorySeen ? subcategoryId : null)
			.firstAppearance(context.trimmed(body.firstAppearance()))
			.description(context.paragraphs(ParagraphSection.DESCRIPTION, body.description()))
			.advantages(context.paragraphs(ParagraphSection.ADVANTAGES, body.advantages()))
			.disadvantages(context.paragraphs(ParagraphSection.DISADVANTAGES, body.disadvantages()))
			.initialKnowledge(DevilFruitKnowledge.NONE)
			.reveals(List.of())
			.build());
	}

	/** What the reader at N calls the fruit: its romaji and its names known there. */
	public static RevealedName nameAt(PreviewContext context, DevilFruit fruit) {
		DevilFruitKnowledge current = currentOf(fruit);
		String romaji = context.valueAt(fruit, DevilFruitField.ROMAJI, current)
			.map(DevilFruitKnowledge::romaji)
			.orElse(null);
		Map<String, String> names = context.valueAt(fruit, DevilFruitField.NAME, current)
			.map(DevilFruitKnowledge::names)
			.orElse(Map.of());
		return new RevealedName(romaji, names);
	}

	/** What the main fields say, as the values a reveal holds. */
	private static DevilFruitKnowledge currentOf(DevilFruit fruit) {
		return new DevilFruitKnowledge(fruit.romaji(), fruit.typeContentId(), fruit.subcategoryId(), fruit.names());
	}

	/**
	 * The types online with their first appearance, and each of their subcategories with
	 * its own: a subcategory is met apart from its type (workflow 3.7), with its type
	 * when it has none - it is never required.
	 */
	private static Map<UUID, StoryPoint> targetsOf(Map<UUID, DevilFruitType> onlineTypes) {
		Map<UUID, StoryPoint> targets = new HashMap<>(ReaderPreviews.firstAppearances(onlineTypes));
		onlineTypes.values()
			.forEach(type -> type.subcategories()
				.forEach(subcategory -> targets.put(subcategory.id(), metAt(subcategory, type))));
		return targets;
	}

	private static StoryPoint metAt(DevilFruitTypeSubcategory subcategory, DevilFruitType type) {
		return subcategory.lacksFirstAppearance() ? type.firstAppearance() : subcategory.firstAppearance();
	}

	private static Map<String, DevilFruitTranslation> translationsOf(RevealedName name) {
		Map<String, DevilFruitTranslation> translations = new TreeMap<>();
		name.names().forEach((language, text) -> translations.put(language, new DevilFruitTranslation(text)));
		return translations;
	}

}
