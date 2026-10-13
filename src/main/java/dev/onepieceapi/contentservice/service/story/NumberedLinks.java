package dev.onepieceapi.contentservice.service.story;

import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSortField;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A story entity keyed by a number as the target of a relation (implementation plan of
 * the story structure, S4, S8): the contents others point to, as they are today, and
 * those that may be chosen. Each answers in batch, one query for a whole page. One per
 * target entity, extending this one, e.g. {@link SagaLinks}.
 *
 * @param <T> what a version of the target entity says
 * @param <E> the target entity's version table
 */
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class NumberedLinks<T extends ContentBody<T>, E extends VersionBodyEntity> {

	/** The language a sort by name would read; the targets are sorted by number. */
	private static final String NO_LANGUAGE = "en";

	private final VersionBodyRepository<E> repository;

	private final Function<E, Version<T>> toDomain;

	private final Clock clock;

	/**
	 * The contents as they were last approved, by content id; one with no approved
	 * version is not in the map. Visible to whoever reads contents: a content that can be
	 * linked to is never hidden from them (DF-D2).
	 */
	public Map<UUID, NumberedReference> referencesOf(Collection<UUID> contentIds) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.findMostRecent(contentIds, VersionStatus.approved())
			.stream()
			.map(this::referenceOf)
			.collect(Collectors.toMap(NumberedReference::id, Function.identity()));
	}

	/**
	 * The contents that may be linked to - those with a version that passed review - one
	 * page at a time, by number, narrowed by what their number or a title contains.
	 */
	public Page<NumberedReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(ContentSortField.NUMBER.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(this::referenceOf);
	}

	private NumberedReference referenceOf(E version) {
		T body = this.toDomain.apply(version).body();
		return new NumberedReference(version.getContentId(), (Integer) body.naturalKey().value(), body.names());
	}

}
