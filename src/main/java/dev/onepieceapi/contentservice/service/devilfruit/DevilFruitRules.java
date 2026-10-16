package dev.onepieceapi.contentservice.service.devilfruit;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitClassification;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReveal;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.DevilFruitTypeVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RevealRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.event.EventRelations;
import dev.onepieceapi.contentservice.service.event.ChapterNumbers;
import dev.onepieceapi.contentservice.service.event.EventReveals;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * What a Devil Fruit needs of its type (implementation plan of the Devil Fruit, D2): the
 * rules of every relation ({@link RelationRules}) for its type - a fruit goes online only
 * while the type is - and, on top, when it names a subcategory, only while the type's
 * online version has it (implementation plan of the subcategories, S6). The other actions
 * ask nothing of the type: a fruit can be reviewed or set aside whatever became of it.
 * Going online also needs a first appearance, in a chapter online - the same relation
 * rules for {@link DevilFruitRelations#FIRST_APPEARANCE} (implementation plan of the
 * timeline, T7): first what the fruit itself lacks, then what it points to.
 * <p>
 * Going online holds the type's content shared first ({@link RelationRules}), and putting
 * one of the type's versions online holds it exclusively ({@code DevilFruitTypeRules}):
 * the two never run at the same time, so neither can read a type that the other is about
 * to change.
 * <p>
 * What events owe a fruit (implementation plan of the timeline, T5): it cannot be retired
 * while online events have it taking part ({@link EventRelations#DEVIL_FRUITS}), and a
 * version goes online only if no online event it takes part in is revealed before its
 * first appearance ({@link EventReveals}) - the rule an event follows, seen from the
 * fruit. Both hold the fruit's content exclusively first, so they wait for any event
 * going online with it, which holds it shared.
 * <p>
 * Its initial knowledge and reveals (T9, T13; ADR-0010) go online under the same rules:
 * the type of the initial knowledge online ({@link DevilFruitRelations#INITIAL_TYPE}),
 * the chapter and the type of each reveal online
 * ({@link DevilFruitRelations#REVEAL_CHAPTER}, {@link DevilFruitRelations#REVEAL_TYPE}),
 * a subcategory in the online version of the type it goes with, and the reveals in the
 * order of the story as the chapters online number it ({@link RevealRules}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class DevilFruitRules implements ContentRules<DevilFruit> {

	private static final String REVEAL = "reveal";

	private static final String INITIAL_KNOWLEDGE = "initialKnowledge";

	private final ContentRepository contentRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EventVersionRepository eventRepository;

	private final EventReveals eventReveals;

	private final ChapterNumbers chapterNumbers;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		DevilFruit fruit = version.body();
		RelationRules.lockTargetBefore(action, DevilFruitRelations.TYPE, fruit, this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.FIRST_APPEARANCE, fruit, this.contentRepository);
		RelationRules.lockTargetBefore(action, DevilFruitRelations.INITIAL_TYPE, fruit, this.contentRepository);
		fruit.reveals().forEach(reveal -> {
			RelationRules.lockTargetBefore(action, DevilFruitRelations.REVEAL_CHAPTER, reveal, this.contentRepository);
			RelationRules.lockTargetBefore(action, DevilFruitRelations.REVEAL_TYPE, reveal, this.contentRepository);
		});
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
		if (RelationRules.GOING_ONLINE.contains(action)) {
			this.contentRepository.lockExclusive(contentId);
		}
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<DevilFruit> version) {
		DevilFruit fruit = version.body();
		return FirstAppearanceRules.missing(action, fruit)
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.TYPE, fruit, this.typeRepository))
			.or(() -> subcategoryNotOnline(action, fruit))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.FIRST_APPEARANCE, fruit,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.INITIAL_TYPE, fruit,
					this.typeRepository))
			.or(() -> revealTargetNotOnline(action, fruit))
			.or(() -> revealsOutOfOrder(action, fruit))
			.or(() -> eventsRevealedBefore(action, contentId, fruit))
			.or(() -> RelationRules.onlineItemsLinked(action, EventRelations.DEVIL_FRUITS, contentId, linkedEvents(),
					this.properties.blockingPreviewSize()));
	}

	/** Checked once the chapter of the first appearance is known to be online. */
	private Optional<ActionBlock> eventsRevealedBefore(VersionAction action, UUID contentId, DevilFruit fruit) {
		return RelationRules.GOING_ONLINE.contains(action)
				? this.eventReveals.eventsRevealedBefore(contentId, fruit.firstAppearanceChapterId())
				: Optional.empty();
	}

	/** The queries of the events for the fruits taking part in them. */
	private LinkedItems linkedEvents() {
		return new LinkedItems(this.eventRepository::countOnlineWithDevilFruit,
				this.eventRepository::findOnlineWithDevilFruit);
	}

	/** The chapter, then the type, of the first reveal naming one not online. */
	private Optional<ActionBlock> revealTargetNotOnline(VersionAction action, DevilFruit fruit) {
		List<DevilFruitReveal> reveals = fruit.reveals();
		return IntStream.range(0, reveals.size())
			.mapToObj(index -> RelationRules
				.targetNotOnline(action, DevilFruitRelations.REVEAL_CHAPTER, reveals.get(index), Map.of(REVEAL, index),
						this.chapterRepository)
				.or(() -> RelationRules.targetNotOnline(action, DevilFruitRelations.REVEAL_TYPE, reveals.get(index),
						Map.of(REVEAL, index), this.typeRepository)))
			.flatMap(Optional::stream)
			.findFirst();
	}

	/** Checked once every chapter is known to be online. */
	private Optional<ActionBlock> revealsOutOfOrder(VersionAction action, DevilFruit fruit) {
		if (!RelationRules.GOING_ONLINE.contains(action) || fruit.reveals().isEmpty()) {
			return Optional.empty();
		}
		return RevealRules.outOfOrderOnline(action, fruit, this.chapterNumbers.online(fruit.chapterIds()));
	}

	/**
	 * Checked once the type is known to be online: the subcategory of the fruit, then
	 * that of its initial knowledge and of each reveal, with the type it goes with.
	 */
	private Optional<ActionBlock> subcategoryNotOnline(VersionAction action, DevilFruit fruit) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		return fruit.classifications()
			.stream()
			.filter(classification -> classification.typeContentId() != null && classification.subcategoryId() != null)
			.filter(classification -> typeAsOf(classification.typeContentId(), Set.of(VersionStatus.PUBLISHED))
				.flatMap(type -> type.subcategory(classification.subcategoryId()))
				.isEmpty())
			.findFirst()
			.map(this::subcategoryNotOnline);
	}

	/**
	 * Named as the most recent approved version of the type that has it says, when one
	 * does - the version online may well have left it out - and by the belief saying it.
	 */
	private ActionBlock subcategoryNotOnline(DevilFruitClassification classification) {
		Map<String, Object> detail = new LinkedHashMap<>();
		if (classification.reveal() != null) {
			detail.put(REVEAL, classification.reveal());
		}
		if (classification.initialKnowledge()) {
			detail.put(INITIAL_KNOWLEDGE, true);
		}
		detail.put("typeId", classification.typeContentId());
		detail.put("subcategoryId", classification.subcategoryId());
		this.typeRepository.findLastApprovedSubcategory(classification.typeContentId(), classification.subcategoryId())
			.ifPresent(subcategory -> detail.put("subcategoryNames", subcategory.names()));
		return new ActionBlock(BlockReason.SUBCATEGORY_NOT_ONLINE, detail);
	}

	/** The type as its most recent version in these statuses says it. */
	private Optional<DevilFruitType> typeAsOf(UUID typeContentId, Collection<VersionStatus> statuses) {
		return this.typeRepository.findMostRecent(List.of(typeContentId), statuses)
			.stream()
			.findFirst()
			.map(DevilFruitTypeVersionMapper::toDomain)
			.map(Version::body);
	}

}
