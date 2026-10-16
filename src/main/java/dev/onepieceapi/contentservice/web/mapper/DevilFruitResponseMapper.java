package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitBelief;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.SubcategoryReference;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.request.RevealedField;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitBeliefResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitRevealResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitResponse;
import dev.onepieceapi.contentservice.web.dto.response.DevilFruitTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.SubcategoryReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.RevealedAtResponse;
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
 * given already looked up, as is the chapter it first appears in - as today, for a whole
 * page at once - so the mapper reads no database.
 */
@UtilityClass
public class DevilFruitResponseMapper {

	/** A version of a Devil Fruit as its caller meets it, with everything it says. */
	public VersionResponse<DevilFruitResponse> toVersionResponse(VersionAccess<DevilFruit> access,
			Map<UUID, TypeReference> types, Map<UUID, NumberedReference> chapters, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access, fruit -> toResponse(fruit, types, chapters, images));
	}

	/** A row of the list of Devil Fruits. */
	public ContentSummaryResponse<DevilFruitNamesResponse> toSummaryResponse(ContentSummary<DevilFruit> summary,
			Map<UUID, TypeReference> types) {
		return ContentResponseMapper.toSummaryResponse(summary, fruit -> toNamesResponse(fruit, types));
	}

	/** Everything the version says. */
	public DevilFruitResponse toResponse(DevilFruit fruit, Map<UUID, TypeReference> types,
			Map<UUID, NumberedReference> chapters, ImageResponseMapper images) {
		List<DevilFruitRevealResponse> reveals = fruit.reveals()
			.stream()
			.map(reveal -> toRevealResponse(reveal, types, chapters))
			.toList();
		return new DevilFruitResponse(fruit.romaji(), toTypeResponse(fruit.typeContentId(), types),
				toSubcategoryResponse(fruit, types), toResponse(fruit.translations()),
				images.toResponse(fruit.imageId()), FirstAppearanceMapper.toResponse(fruit.firstAppearance(), chapters),
				toBeliefResponse(fruit.initialKnowledge(), types), reveals);
	}

	/** None when it holds nothing; its subcategory named as its own type has it. */
	private static DevilFruitBeliefResponse toBeliefResponse(DevilFruitBelief belief, Map<UUID, TypeReference> types) {
		if (belief.isEmpty()) {
			return null;
		}
		return new DevilFruitBeliefResponse(belief.romaji(), toTypeResponse(belief.typeContentId(), types),
				toSubcategoryResponse(belief.typeContentId(), belief.subcategoryId(), types),
				toResponse(belief.translations()));
	}

	/** Its fields in a fixed order; its subcategory named as its own type has it. */
	private static DevilFruitRevealResponse toRevealResponse(DevilFruitReveal reveal, Map<UUID, TypeReference> types,
			Map<UUID, NumberedReference> chapters) {
		UUID chapter = reveal.revealedAtChapterId();
		var revealedAt = new RevealedAtResponse(
				chapter == null ? null : StoryResponseMapper.toReferenceResponse(chapter, chapters));
		DevilFruitBelief values = reveal.values();
		List<RevealedField> fields = reveal.fields().stream().sorted().map(RevealedField::of).toList();
		return new DevilFruitRevealResponse(revealedAt, reveal.titles(), fields, values.romaji(),
				toTypeResponse(values.typeContentId(), types),
				toSubcategoryResponse(values.typeContentId(), values.subcategoryId(), types),
				toResponse(values.translations()));
	}

	private static Map<String, DevilFruitTranslationResponse> toResponse(
			Map<String, DevilFruitTranslation> translations) {
		Map<String, DevilFruitTranslationResponse> responses = new TreeMap<>();
		translations.forEach((language, translation) -> responses.put(language, toResponse(translation)));
		return responses;
	}

	/**
	 * What a list row shows: the names, for the languages that have one, and the type.
	 */
	public DevilFruitNamesResponse toNamesResponse(DevilFruit fruit, Map<UUID, TypeReference> types) {
		return new DevilFruitNamesResponse(fruit.romaji(), fruit.names(), toTypeResponse(fruit.typeContentId(), types),
				toSubcategoryResponse(fruit, types));
	}

	/**
	 * The subcategory with its names, as its type was last approved; with only its id
	 * when the type no longer has it, which a read must not fail for; null for none.
	 */
	public SubcategoryReferenceResponse toSubcategoryResponse(DevilFruit fruit, Map<UUID, TypeReference> types) {
		return toSubcategoryResponse(fruit.typeContentId(), fruit.subcategoryId(), types);
	}

	private static SubcategoryReferenceResponse toSubcategoryResponse(UUID typeContentId, UUID subcategoryId,
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
		return new DevilFruitTranslationResponse(translation.name(), translation.description(),
				translation.advantages(), translation.disadvantages());
	}

}
