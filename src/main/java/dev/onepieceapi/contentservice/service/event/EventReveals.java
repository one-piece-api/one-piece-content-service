package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.mapper.EventVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An online event is never revealed before a Devil Fruit taking part in it first appears
 * (docs/user-flows/content-editorial-workflow.md 3.4.3; implementation plan of the
 * timeline, T5): the reader would learn of the fruit through the event. Checked from both
 * sides as either goes online - published or restored - with the chapters' numbers as
 * online: an event against its fruits, a fruit against its events. Each block says how
 * many are at fault and names the first few.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class EventReveals {

	private static final String COUNT = "count";

	private static final String FRUITS = "fruits";

	private static final String EVENTS = "events";

	private final DevilFruitVersionRepository fruitRepository;

	private final EventVersionRepository eventRepository;

	private final ChapterNumbers chapterNumbers;

	private final RulesProperties properties;

	/**
	 * The online fruits taking part in the event that first appear after it is revealed;
	 * empty when the event has no reveal to compare - another rule refuses that.
	 */
	public Optional<ActionBlock> participantsAfterReveal(Event event) {
		Optional<UUID> revealedAt = event.effectiveRevealedAtChapterId();
		if (revealedAt.isEmpty() || event.devilFruitContentIds().isEmpty()) {
			return Optional.empty();
		}
		List<DevilFruitVersionEntity> fruits = this.fruitRepository.findMostRecent(event.devilFruitContentIds(),
				Set.of(VersionStatus.PUBLISHED));
		Set<UUID> chapters = new HashSet<>();
		chapters.add(revealedAt.get());
		fruits.forEach(fruit -> chapters.add(fruit.getFirstAppearanceChapterId()));
		Map<UUID, Integer> numbers = this.chapterNumbers.online(chapters);
		Integer revealNumber = numbers.get(revealedAt.get());
		List<DevilFruitVersionEntity> late = fruits.stream()
			.filter(fruit -> before(revealNumber, numbers.get(fruit.getFirstAppearanceChapterId())))
			.toList();
		return blockNaming(BlockReason.REVEALED_BEFORE_PARTICIPANT, FRUITS, late);
	}

	/**
	 * The online events the fruit takes part in that are revealed before the chapter it
	 * would now first appear in; empty when it names none - another rule refuses that.
	 */
	public Optional<ActionBlock> eventsRevealedBefore(UUID fruitContentId, UUID firstAppearanceChapterId) {
		if (firstAppearanceChapterId == null) {
			return Optional.empty();
		}
		Map<EventVersionEntity, UUID> reveals = new LinkedHashMap<>();
		this.eventRepository.findOnlineWithDevilFruit(fruitContentId, Pageable.unpaged())
			.forEach(entity -> EventVersionMapper.toDomain(entity)
				.body()
				.effectiveRevealedAtChapterId()
				.ifPresent(chapter -> reveals.put(entity, chapter)));
		if (reveals.isEmpty()) {
			return Optional.empty();
		}
		Set<UUID> chapters = new HashSet<>(reveals.values());
		chapters.add(firstAppearanceChapterId);
		Map<UUID, Integer> numbers = this.chapterNumbers.online(chapters);
		Integer firstAppearanceNumber = numbers.get(firstAppearanceChapterId);
		List<EventVersionEntity> early = reveals.entrySet()
			.stream()
			.filter(reveal -> before(numbers.get(reveal.getValue()), firstAppearanceNumber))
			.map(Map.Entry::getKey)
			.toList();
		return blockNaming(BlockReason.EVENTS_REVEALED_BEFORE, EVENTS, early);
	}

	/**
	 * Whether the reveal comes before the first appearance; false when either is unknown.
	 */
	private static boolean before(Integer revealNumber, Integer firstAppearanceNumber) {
		return revealNumber != null && firstAppearanceNumber != null && revealNumber < firstAppearanceNumber;
	}

	private Optional<ActionBlock> blockNaming(BlockReason reason, String itemsName,
			List<? extends VersionBodyEntity> items) {
		if (items.isEmpty()) {
			return Optional.empty();
		}
		List<Map<String, Object>> named = items.stream()
			.limit(this.properties.blockingPreviewSize())
			.map(RelationRules::named)
			.toList();
		return Optional.of(new ActionBlock(reason, Map.of(COUNT, (long) items.size(), itemsName, named)));
	}

}
