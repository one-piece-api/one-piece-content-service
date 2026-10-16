package dev.onepieceapi.contentservice.service.devilfruittype;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitClassification;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * What a Devil Fruit Type owes its fruits (implementation plan of the Devil Fruit, D2):
 * the rules of every relation ({@link RelationRules}) as the target of the fruit's type -
 * a type cannot be retired while fruits are online with it - and, on top, a fruit online
 * always has its subcategory online, so a version of the type cannot go online -
 * published or restored - leaving out a subcategory online fruits name (implementation
 * plan of the subcategories, S7). A type archived with no fruit online is not affected.
 * Going online also needs a first appearance in a chapter online, and a chapter online
 * for every subcategory that names one (implementation plan of the timeline, T7): first
 * what the type itself lacks, then what it points to, then what its fruits need of it.
 * Not even {@code content:admin} lifts it.
 * <p>
 * The reveals of the fruits (implementation plan of the timeline, T9; ADR-0010) count
 * alike: a type cannot be retired while a reveal of an online fruit believes it, and a
 * version cannot leave out a subcategory such a reveal names under it - the type in force
 * at the reveal.
 * <p>
 * Each of these holds the type's content exclusively first, so it waits for any fruit
 * going online with it ({@code DevilFruitRules}) and what it then reads includes that
 * fruit.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitTypeRules implements ContentRules<DevilFruitType> {

	private static final String SUBCATEGORY_ID = "subcategoryId";

	private final ContentRepository contentRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final ChapterVersionRepository chapterRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		if (RelationRules.GOING_ONLINE.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
		DevilFruitType type = version.body();
		RelationRules.lockTargetBefore(action, DevilFruitTypeRelations.FIRST_APPEARANCE, type, this.contentRepository);
		type.subcategories()
			.forEach(subcategory -> RelationRules.lockTargetBefore(action,
					DevilFruitTypeRelations.SUBCATEGORY_FIRST_APPEARANCE, subcategory, this.contentRepository));
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruitType> version) {
		DevilFruitType type = version.body();
		return switch (action) {
			case RETIRE -> RelationRules
				.onlineItemsLinked(action, DevilFruitRelations.TYPE, contentId, linkedFruits(),
						this.properties.blockingPreviewSize())
				.or(() -> RelationRules.onlineItemsLinked(action, DevilFruitRelations.REVEAL_TYPE, contentId,
						new LinkedItems(this.fruitRepository::countOnlineWithRevealType,
								this.fruitRepository::findOnlineWithRevealType),
						this.properties.blockingPreviewSize()));
			case PUBLISH,
					RESTORE ->
				FirstAppearanceRules.missing(action, type)
					.or(() -> RelationRules.targetNotOnline(action, DevilFruitTypeRelations.FIRST_APPEARANCE, type,
							this.chapterRepository))
					.or(() -> subcategoryChapterNotOnline(action, type))
					.or(() -> subcategoriesInUse(contentId, type))
					.or(() -> subcategoriesInUseByReveals(contentId, type));
			default -> Optional.empty();
		};
	}

	/**
	 * The first subcategory whose first appearance is in a chapter not online, if any.
	 */
	private Optional<ActionBlock> subcategoryChapterNotOnline(VersionAction action, DevilFruitType type) {
		return type.subcategories()
			.stream()
			.map(subcategory -> RelationRules.targetNotOnline(action,
					DevilFruitTypeRelations.SUBCATEGORY_FIRST_APPEARANCE, subcategory,
					Map.of(SUBCATEGORY_ID, subcategory.id()), this.chapterRepository))
			.flatMap(Optional::stream)
			.findFirst();
	}

	/** The queries of the fruits for the type they point to. */
	private LinkedItems linkedFruits() {
		return new LinkedItems(this.fruitRepository::countOnlineLinkedTo, this.fruitRepository::findOnlineLinkedTo);
	}

	/** The online fruits whose subcategory the version leaves out, each with it. */
	private Optional<ActionBlock> subcategoriesInUse(UUID contentId, DevilFruitType type) {
		Page<DevilFruitVersionEntity> page = this.fruitRepository.findOnlineOutsideSubcategories(contentId,
				type.subcategoryIds(), PageRequest.ofSize(this.properties.blockingPreviewSize()));
		if (page.getTotalElements() == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> fruits = page.getContent()
			.stream()
			.map(DevilFruitTypeRules::withSubcategory)
			.toList();
		return Optional.of(new ActionBlock(BlockReason.SUBCATEGORY_IN_USE,
				Map.of("count", page.getTotalElements(), "fruits", fruits)));
	}

	/**
	 * The online fruits with a reveal naming, under this type, a subcategory the version
	 * leaves out - each with the first such reveal and its subcategory.
	 */
	private Optional<ActionBlock> subcategoriesInUseByReveals(UUID contentId, DevilFruitType type) {
		Set<UUID> kept = type.subcategoryIds();
		List<Map<String, Object>> fruits = new ArrayList<>();
		for (DevilFruitVersionEntity entity : this.fruitRepository.findOnlineWithRevealSubcategory()) {
			DevilFruitVersionMapper.toDomain(entity)
				.body()
				.classifications()
				.stream()
				.filter(classification -> classification.reveal() != null
						&& contentId.equals(classification.typeContentId()) && classification.subcategoryId() != null
						&& !kept.contains(classification.subcategoryId()))
				.findFirst()
				.ifPresent(classification -> fruits.add(withRevealSubcategory(entity, classification)));
		}
		if (fruits.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new ActionBlock(BlockReason.SUBCATEGORY_IN_USE, Map.of("count", (long) fruits.size(),
				"fruits", fruits.subList(0, Math.min(fruits.size(), this.properties.blockingPreviewSize())))));
	}

	/** A fruit by its id and romaji, then the reveal and the subcategory it names. */
	private static Map<String, Object> withRevealSubcategory(DevilFruitVersionEntity fruit,
			DevilFruitClassification classification) {
		Map<String, Object> named = RelationRules.named(fruit);
		named.put("reveal", classification.reveal());
		named.put(SUBCATEGORY_ID, classification.subcategoryId());
		return named;
	}

	/** A fruit by its id and romaji, then the subcategory it names. */
	private static Map<String, Object> withSubcategory(DevilFruitVersionEntity fruit) {
		Map<String, Object> named = RelationRules.named(fruit);
		named.put(SUBCATEGORY_ID, fruit.getSubcategoryId());
		return named;
	}

}
