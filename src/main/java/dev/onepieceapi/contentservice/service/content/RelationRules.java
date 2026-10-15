package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.experimental.UtilityClass;
import org.springframework.data.domain.PageRequest;
import org.springframework.util.StringUtils;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The rules every relation follows (implementation plan of the Devil Fruit, D2; of the
 * story structure, S5), written once for any {@link RelationDescriptor}, for each content
 * it points to:
 * <ul>
 * <li>on every save, the related content is <em>linkable</em> - one of its versions
 * passed review, which it never stops being; an id naming nothing and a content with
 * nothing approved are told alike, so nobody learns which ids exist;</li>
 * <li>an item goes online - published or restored - only while the related content is
 * online;</li>
 * <li>the related content cannot be retired while online items point to it.</li>
 * </ul>
 * Not even {@code content:admin} lifts them: it relaxes who may act, not the data. Going
 * online holds the related contents shared first, retiring one holds it exclusively: the
 * two never run at the same time, so neither reads what the other is about to change. The
 * {@link ContentRules} of each entity call them, and add their own on top.
 */
@UtilityClass
public class RelationRules {

	/** The actions that put a version online. */
	public static final Set<VersionAction> GOING_ONLINE = EnumSet.of(VersionAction.PUBLISH, VersionAction.RESTORE);

	private static final String ID_SUFFIX = "Id";

	private static final String ID = "id";

	private static final String COUNT = "count";

	private static final String NAMES = "names";

	/** Refuses a save pointing to a content that is not linkable; none named is fine. */
	public <T> void requireLinkable(RelationDescriptor<T> relation, T body, VersionBodyRepository<?> targets) {
		notLinkable(relation, body, "", targets).ifPresent(violation -> {
			throw new ValueInvalidException(List.of(violation));
		});
	}

	/**
	 * What a save pointing to a content that is not linkable breaks - the first one, for
	 * a relation to many - also for a relation held by a part of the body, e.g. one
	 * subcategory of a type, so every part at fault can be reported; empty when every one
	 * is linkable, or none is named.
	 * @param fieldPrefix where the part is in the request, e.g. {@code subcategories[2].}
	 */
	public <T> Optional<FieldViolation> notLinkable(RelationDescriptor<T> relation, T part, String fieldPrefix,
			VersionBodyRepository<?> targets) {
		return relation.targetsOf(part)
			.stream()
			.filter(target -> !targets.existsWithStatus(target, VersionStatus.approved()))
			.findFirst()
			.map(target -> new FieldViolation(fieldPrefix + relation.fieldOf(target), relation.notLinkable()));
	}

	/**
	 * Before an item goes online: holds the related contents shared, in the order of
	 * their ids, so two items holding the same ones never wait for each other.
	 */
	public <T> void lockTargetBefore(VersionAction action, RelationDescriptor<T> relation, T body,
			ContentRepository contents) {
		if (GOING_ONLINE.contains(action)) {
			relation.targetsOf(body).stream().sorted().forEach(contents::lockShared);
		}
	}

	/**
	 * Refuses an item going online while a related content is not - the first one, for a
	 * relation to many - naming that content as it was last approved.
	 */
	public <T> Optional<ActionBlock> targetNotOnline(VersionAction action, RelationDescriptor<T> relation, T body,
			VersionBodyRepository<?> targets) {
		return targetNotOnline(action, relation, body, Map.of(), targets);
	}

	/**
	 * The same, for a relation held by a part of the body - e.g. one subcategory of a
	 * type - the detail first naming that part.
	 * @param part what names the part in the detail, e.g. {@code {subcategoryId}}
	 */
	public <T> Optional<ActionBlock> targetNotOnline(VersionAction action, RelationDescriptor<T> relation, T body,
			Map<String, Object> part, VersionBodyRepository<?> targets) {
		if (!GOING_ONLINE.contains(action)) {
			return Optional.empty();
		}
		return relation.targetsOf(body)
			.stream()
			.filter(target -> !targets.existsWithStatus(target, Set.of(VersionStatus.PUBLISHED)))
			.findFirst()
			.map(target -> notOnline(relation, target, part, targets));
	}

	private static ActionBlock notOnline(RelationDescriptor<?> relation, UUID target, Map<String, Object> part,
			VersionBodyRepository<?> targets) {
		Map<String, Object> detail = new LinkedHashMap<>(part);
		detail.put(relation.name() + ID_SUFFIX, target);
		targets.findMostRecent(List.of(target), VersionStatus.approved())
			.stream()
			.findFirst()
			.flatMap(VersionBodyEntity::naturalKey)
			.filter(key -> !key.isMissing())
			.ifPresent(key -> detail.put(relation.name() + StringUtils.capitalize(key.field()), key.value()));
		return new ActionBlock(relation.targetNotOnline(), detail);
	}

	/** Before the related content is retired: holds it exclusively. */
	public void lockLinkedBefore(VersionAction action, UUID contentId, ContentRepository contents) {
		if (action == VersionAction.RETIRE) {
			contents.lockExclusive(contentId);
		}
	}

	/**
	 * Refuses retiring a content while online items point to it: how many, and the first
	 * {@code previewSize} of them by their id and natural key.
	 */
	public Optional<ActionBlock> onlineItemsLinked(VersionAction action, RelationDescriptor<?> relation, UUID contentId,
			LinkedItems items, int previewSize) {
		if (action != VersionAction.RETIRE) {
			return Optional.empty();
		}
		long count = items.countOnline().applyAsLong(contentId);
		if (count == 0) {
			return Optional.empty();
		}
		List<Map<String, Object>> named = items.findOnline()
			.apply(contentId, PageRequest.ofSize(previewSize))
			.stream()
			.map(RelationRules::named)
			.toList();
		return Optional
			.of(new ActionBlock(relation.onlineItemsLinked(), Map.of(COUNT, count, relation.itemsName(), named)));
	}

	/**
	 * An item by its id and natural key, e.g. {@code {id, romaji}}; by its names, e.g.
	 * the titles of an event, for an entity without a natural key: {@code {id, names}}.
	 */
	public Map<String, Object> named(VersionBodyEntity item) {
		Map<String, Object> named = new LinkedHashMap<>();
		named.put(ID, item.getContentId());
		item.naturalKey()
			.ifPresentOrElse(key -> named.put(key.field(), key.value()), () -> named.put(NAMES, item.names()));
		return named;
	}

}
