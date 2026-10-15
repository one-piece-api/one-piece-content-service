package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.story.Chapter;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ArcVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VolumeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.LinkedItems;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRelations;
import dev.onepieceapi.contentservice.service.devilfruittype.DevilFruitTypeRelations;
import dev.onepieceapi.contentservice.service.event.EventRelations;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What a chapter needs of its arc and of its volume, and owes what first appears in it:
 * the rules of every relation ({@link RelationRules}) for
 * {@link StoryRelations#CHAPTER_ARC} and {@link StoryRelations#CHAPTER_VOLUME} as the
 * pointing side - a chapter goes online only while its arc is, and its volume when it has
 * one, holding both shared first - and for the first appearances as the target
 * (implementation plan of the timeline, T7) - a chapter cannot be retired while fruits,
 * or types (by themselves or through a subcategory), are online first appearing in it,
 * nor while events are online occurring, ending or revealed in it (implementation plan of
 * the timeline, T5), nor while fruits are online with a knowledge phase superseded in it
 * (T9), holding it exclusively first. Nothing of its own on top.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class ChapterRules implements ContentRules<Chapter> {

	private final ContentRepository contentRepository;

	private final ArcVersionRepository arcRepository;

	private final VolumeVersionRepository volumeRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final DevilFruitTypeVersionRepository typeRepository;

	private final EventVersionRepository eventRepository;

	private final RulesProperties properties;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Chapter> version) {
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_ARC, version.body(), this.contentRepository);
		RelationRules.lockTargetBefore(action, StoryRelations.CHAPTER_VOLUME, version.body(), this.contentRepository);
		RelationRules.lockLinkedBefore(action, contentId, this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Chapter> version) {
		return RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_ARC, version.body(), this.arcRepository)
			.or(() -> RelationRules.targetNotOnline(action, StoryRelations.CHAPTER_VOLUME, version.body(),
					this.volumeRepository))
			.or(() -> fruitsLinked(action, contentId))
			.or(() -> fruitPhasesLinked(action, contentId))
			.or(() -> typesLinked(action, contentId))
			.or(() -> eventsLinked(action, contentId));
	}

	private Optional<ActionBlock> fruitsLinked(VersionAction action, UUID contentId) {
		var fruits = new LinkedItems(this.fruitRepository::countOnlineFirstAppearingIn,
				this.fruitRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitRelations.FIRST_APPEARANCE, contentId, fruits,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> typesLinked(VersionAction action, UUID contentId) {
		var types = new LinkedItems(this.typeRepository::countOnlineFirstAppearingIn,
				this.typeRepository::findOnlineFirstAppearingIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitTypeRelations.FIRST_APPEARANCE, contentId, types,
				this.properties.blockingPreviewSize());
	}

	private Optional<ActionBlock> fruitPhasesLinked(VersionAction action, UUID contentId) {
		var fruits = new LinkedItems(this.fruitRepository::countOnlineWithPhaseIn,
				this.fruitRepository::findOnlineWithPhaseIn);
		return RelationRules.onlineItemsLinked(action, DevilFruitRelations.PHASE_CHAPTER, contentId, fruits,
				this.properties.blockingPreviewSize());
	}

	/** One block for the events, whether they occur, end or are revealed in it. */
	private Optional<ActionBlock> eventsLinked(VersionAction action, UUID contentId) {
		var events = new LinkedItems(this.eventRepository::countOnlineInChapter,
				this.eventRepository::findOnlineInChapter);
		return RelationRules.onlineItemsLinked(action, EventRelations.OCCURS_AT_CHAPTER, contentId, events,
				this.properties.blockingPreviewSize());
	}

}
