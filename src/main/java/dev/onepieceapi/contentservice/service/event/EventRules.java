package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * What an event needs to go online - published or restored (implementation plan of the
 * timeline, T5): somewhere the reader learns of it ({@link BlockReason#REVEAL_MISSING});
 * the rules of every relation ({@link RelationRules}) for its chapters and its Devil
 * Fruits - online, each one, holding them shared first; and no fruit taking part in it
 * first appearing after it is revealed ({@link EventReveals}). First what the event
 * itself lacks, then what it points to. Nothing points to an event: it is retired freely.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class EventRules implements ContentRules<Event> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final EventReveals reveals;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Event> version) {
		EventRelations.CHAPTERS.forEach(
				relation -> RelationRules.lockTargetBefore(action, relation, version.body(), this.contentRepository));
		RelationRules.lockTargetBefore(action, EventRelations.DEVIL_FRUITS, version.body(), this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Event> version) {
		if (!RelationRules.GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		Event event = version.body();
		return revealMissing(event).or(() -> chapterNotOnline(action, event))
			.or(() -> RelationRules.targetNotOnline(action, EventRelations.DEVIL_FRUITS, event, this.fruitRepository))
			.or(() -> this.reveals.participantsAfterReveal(event));
	}

	private static Optional<ActionBlock> revealMissing(Event event) {
		return event.effectiveRevealedAtChapterId().isPresent() ? Optional.empty()
				: Optional.of(new ActionBlock(BlockReason.REVEAL_MISSING, Map.of()));
	}

	private Optional<ActionBlock> chapterNotOnline(VersionAction action, Event event) {
		return EventRelations.CHAPTERS.stream()
			.map(relation -> RelationRules.targetNotOnline(action, relation, event, this.chapterRepository))
			.flatMap(Optional::stream)
			.findFirst();
	}

}
