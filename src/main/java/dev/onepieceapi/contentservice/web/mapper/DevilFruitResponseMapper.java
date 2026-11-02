package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitField;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitKnowledge;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.SubcategoryReference;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitKnowledgeResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitRevealResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.SubcategoryReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.TypeReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From Devil Fruits to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The type a fruit points to is
 * given already looked up, as are the chapter and the episode it first appears in - as
 * today, for a whole page at once - so the mapper reads no database.
 */
@UtilityClass
public class DevilFruitResponseMapper {

	/** A version of a Devil Fruit as its caller meets it, with everything it says. */
	public VersionResponse<DevilFruitResponse> toVersionResponse(VersionAccess<DevilFruit> access,
			Map<UUID, TypeReference> types, Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access, fruit -> toResponse(fruit, types, storyItems, images));
	}

	/** A row of the list of Devil Fruits, with how many characters currently use it. */
	public ContentSummaryResponse<DevilFruitNamesResponse> toSummaryResponse(ContentSummary<DevilFruit> summary,
			Map<UUID, TypeReference> types, long userCount) {
		return ContentResponseMapper.toSummaryResponse(summary, fruit -> toNamesResponse(fruit, types, userCount));
	}

	/** Everything the version says. */
	public DevilFruitResponse toResponse(DevilFruit fruit, Map<UUID, TypeReference> types,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		Map<String, DevilFruitTranslationResponse> translations = new TreeMap<>();
		fruit.translations().forEach((language, translation) -> translations.put(language, toResponse(translation)));
		return DevilFruitResponse.builder()
			.romaji(fruit.romaji())
			.type(toTypeResponse(fruit.typeContentId(), types))
			.subcategory(toSubcategoryResponse(fruit, types))
			.multipleUsers(fruit.multipleUsers())
			.translations(translations)
			.image(images.toResponse(fruit.imageId()))
			.firstAppearance(FirstAppearanceMapper.toResponse(fruit.firstAppearance(), storyItems))
			.description(ParagraphResponseMapper.toResponse(fruit.description(), storyItems))
			.advantages(ParagraphResponseMapper.toResponse(fruit.advantages(), storyItems))
			.disadvantages(ParagraphResponseMapper.toResponse(fruit.disadvantages(), storyItems))
			.initialKnowledge(toInitialResponse(fruit.initialKnowledge(), types))
			.reveals(fruit.reveals().stream().map(reveal -> toResponse(reveal, types, storyItems)).toList())
			.build();
	}

	/** Null for nothing known apart from the current values. */
	private static DevilFruitKnowledgeResponse toInitialResponse(DevilFruitKnowledge knowledge,
			Map<UUID, TypeReference> types) {
		if (knowledge.equals(DevilFruitKnowledge.NONE)) {
			return null;
		}
		return new DevilFruitKnowledgeResponse(knowledge.romaji(), toTypeResponse(knowledge.typeContentId(), types),
				toSubcategoryResponse(knowledge.typeContentId(), knowledge.subcategoryId(), types),
				toNameResponses(knowledge.names()));
	}

	private static DevilFruitRevealResponse toResponse(Reveal<DevilFruitField, DevilFruitKnowledge> reveal,
			Map<UUID, TypeReference> types, Map<UUID, NumberedReference> storyItems) {
		DevilFruitKnowledge values = reveal.values();
		return new DevilFruitRevealResponse(FirstAppearanceMapper.toResponse(reveal.revealedAt(), storyItems),
				reveal.titles(), reveal.fields(), values.romaji(), toTypeResponse(values.typeContentId(), types),
				toSubcategoryResponse(values.typeContentId(), values.subcategoryId(), types),
				toNameResponses(values.names()));
	}

	private static Map<String, DevilFruitTranslationResponse> toNameResponses(Map<String, String> names) {
		Map<String, DevilFruitTranslationResponse> byLanguage = new TreeMap<>();
		names.forEach((language, name) -> byLanguage.put(language, new DevilFruitTranslationResponse(name)));
		return byLanguage;
	}

	/**
	 * What a list row shows: the names, for the languages that have one, the type and how
	 * many characters currently use it.
	 */
	public DevilFruitNamesResponse toNamesResponse(DevilFruit fruit, Map<UUID, TypeReference> types, long userCount) {
		return new DevilFruitNamesResponse(fruit.romaji(), fruit.names(), toTypeResponse(fruit.typeContentId(), types),
				toSubcategoryResponse(fruit, types), userCount);
	}

	/**
	 * The subcategory with its names, as its type was last approved; with only its id
	 * when the type no longer has it, which a read must not fail for; null for none.
	 */
	public SubcategoryReferenceResponse toSubcategoryResponse(DevilFruit fruit, Map<UUID, TypeReference> types) {
		return toSubcategoryResponse(fruit.typeContentId(), fruit.subcategoryId(), types);
	}

	/** The same, for a type and a subcategory held apart from the current ones. */
	public SubcategoryReferenceResponse toSubcategoryResponse(UUID typeContentId, UUID subcategoryId,
			Map<UUID, TypeReference> types) {
		if (subcategoryId == null) {
			return null;
		}
		return Optional.ofNullable(types.get(typeContentId))
			.flatMap(type -> type.subcategory(subcategoryId))
			.map(DevilFruitResponseMapper::toResponse)
			.orElseGet(() -> new SubcategoryReferenceResponse(subcategoryId, Map.of()));
	}

	/**
	 * The type with its names; with only its id when it has nothing approved to be called
	 * by, which the link makes impossible but a read must not fail for; null for no type.
	 */
	public TypeReferenceResponse toTypeResponse(UUID typeContentId, Map<UUID, TypeReference> types) {
		if (typeContentId == null) {
			return null;
		}
		TypeReference type = types.get(typeContentId);
		return type == null ? new TypeReferenceResponse(typeContentId, null, Map.of(), List.of()) : toResponse(type);
	}

	public TypeReferenceResponse toResponse(TypeReference type) {
		return new TypeReferenceResponse(type.id(), type.romaji(), type.names(),
				type.subcategories().stream().map(DevilFruitResponseMapper::toResponse).toList());
	}

	private static SubcategoryReferenceResponse toResponse(SubcategoryReference subcategory) {
		return new SubcategoryReferenceResponse(subcategory.id(), subcategory.names());
	}

	private static DevilFruitTranslationResponse toResponse(DevilFruitTranslation translation) {
		return new DevilFruitTranslationResponse(translation.name());
	}

}
