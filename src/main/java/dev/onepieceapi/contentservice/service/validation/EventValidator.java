package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.config.TimelineProperties;
import dev.onepieceapi.contentservice.domain.event.Event;
import dev.onepieceapi.contentservice.domain.event.EventTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.ChapterTime;
import dev.onepieceapi.contentservice.domain.event.EventTime.WorldDateTime;
import dev.onepieceapi.contentservice.domain.event.EventTimeOrder;
import dev.onepieceapi.contentservice.domain.event.WorldDate;
import dev.onepieceapi.contentservice.persistence.entity.EventVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.EventVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.LanguageRepository;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import dev.onepieceapi.contentservice.service.event.ChapterNumbers;
import dev.onepieceapi.contentservice.service.event.EventRelations;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * The rules an event must meet (docs/user-flows/content-editorial-workflow.md 3.1.8;
 * implementation plan of the timeline, T3-T5): those of every content - see
 * {@link ContentValidator}; an event has no natural key and its title may repeat - and,
 * on every save and again at submission:
 * <ul>
 * <li>chapters and Devil Fruits it may be linked to, as every relation
 * ({@link RelationRules});</li>
 * <li>a world date before the story: it ends in the year the story starts at the
 * latest;</li>
 * <li>times in order ({@link EventTimeOrder}, with the chapters' numbers as last
 * approved): it does not end before it occurs - so an event occurring in a chapter cannot
 * end at a world date - and is not revealed before it occurs.</li>
 * </ul>
 * Which shapes a time may take - a chapter or a world date, never both - and the order of
 * the years of one world date are the request's to check.
 */
@Component
public class EventValidator extends ContentValidator<Event, EventVersionEntity> {

	private static final String WORLD_DATE_FIELD = "%s.worldDate.%s";

	private static final String YEAR_FROM = "yearFrom";

	private static final String YEAR_TO = "yearTo";

	private static final String AFTER_STORY_START = "must not be after %d, the year the story starts";

	private static final String BEFORE_OCCURS_AT = "must not come before occursAt";

	private final ChapterVersionRepository chapterRepository;

	private final DevilFruitVersionRepository fruitRepository;

	private final ChapterNumbers chapterNumbers;

	private final TimelineProperties timeline;

	@Autowired
	public EventValidator(EventVersionRepository versionRepository, LanguageRepository languageRepository,
			ChapterVersionRepository chapterRepository, DevilFruitVersionRepository fruitRepository,
			ChapterNumbers chapterNumbers, TimelineProperties timeline) {
		super(versionRepository, languageRepository, EventVersionMapper::toDomain);
		this.chapterRepository = chapterRepository;
		this.fruitRepository = fruitRepository;
		this.chapterNumbers = chapterNumbers;
		this.timeline = timeline;
	}

	@Override
	protected void requireValidFields(Event body) {
		List<FieldViolation> violations = new ArrayList<>();
		afterStoryStart(Event.OCCURS_AT_FIELD, body.occursAt()).ifPresent(violations::add);
		afterStoryStart(Event.ENDS_AT_FIELD, body.endsAt()).ifPresent(violations::add);
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

	/** Linkable first: an order is only read between chapters that may be chosen. */
	@Override
	protected void requireValidRelations(Event body) {
		EventRelations.CHAPTERS
			.forEach(relation -> RelationRules.requireLinkable(relation, body, this.chapterRepository));
		RelationRules.requireLinkable(EventRelations.DEVIL_FRUITS, body, this.fruitRepository);
		requireInOrder(body);
	}

	/** Named on the year that is too late: the last one of a span. */
	private Optional<FieldViolation> afterStoryStart(String field, EventTime time) {
		if (!(time instanceof WorldDateTime(WorldDate date)) || date.latestYear() <= this.timeline.storyStartYear()) {
			return Optional.empty();
		}
		String year = date.yearTo() == null ? YEAR_FROM : YEAR_TO;
		return Optional.of(new FieldViolation(WORLD_DATE_FIELD.formatted(field, year),
				AFTER_STORY_START.formatted(this.timeline.storyStartYear())));
	}

	private void requireInOrder(Event body) {
		Map<UUID, Integer> numbers = this.chapterNumbers
			.approved(Stream.of(body.occursAtChapterId(), body.endsAtChapterId(), body.revealedAtChapterId()).toList());
		var order = new EventTimeOrder(numbers::get);
		List<FieldViolation> violations = new ArrayList<>();
		if (order.certainlyBefore(body.endsAt(), body.occursAt())) {
			violations.add(new FieldViolation(Event.ENDS_AT_FIELD, BEFORE_OCCURS_AT));
		}
		if (body.revealedAtChapterId() != null
				&& order.certainlyBefore(new ChapterTime(body.revealedAtChapterId()), body.occursAt())) {
			violations.add(new FieldViolation(Event.REVEALED_AT_FIELD, BEFORE_OCCURS_AT));
		}
		if (!violations.isEmpty()) {
			throw new ValueInvalidException(violations);
		}
	}

}
