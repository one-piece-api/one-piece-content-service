package dev.onepieceapi.contentservice.service.content;

import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.NaturalKey;
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
 * story structure, S5), written once for any {@link RelationDescriptor}:
 * <ul>
 * <li>on every save, the related content is <em>linkable</em> - one of its versions
 * passed review, which it never stops being; an id naming nothing and a content with
 * nothing approved are told alike, so nobody learns which ids exist;</li>
 * <li>an item goes online - published or restored - only while the related content is
 * online;</li>
 * <li>the related content cannot be retired while online items point to it.</li>
 * </ul>
 * Not even {@code content:admin} lifts them: it relaxes who may act, not the data. Going
 * online holds the related content shared first, retiring it holds it exclusively: the
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

	/** Refuses a save pointing to a content that is not linkable; none named is fine. */
	public <T> void requireLinkable(RelationDescriptor<T> relation, T body, VersionBodyRepository<?> targets) {
		UUID target = relation.targetOf(body);
		if (target != null && !targets.existsWithStatus(target, VersionStatus.approved())) {
			throw new ValueInvalidException(List.of(new FieldViolation(relation.name(), relation.notLinkable())));
		}
	}

	/** Before an item goes online: holds the related content shared. */
	public <T> void lockTargetBefore(VersionAction action, RelationDescriptor<T> relation, T body,
			ContentRepository contents) {
		UUID target = relation.targetOf(body);
		if (GOING_ONLINE.contains(action) && target != null) {
			contents.lockShared(target);
		}
	}

	/**
	 * Refuses an item going online while its related content is not, naming that content
	 * as it was last approved.
	 */
	public <T> Optional<ActionBlock> targetNotOnline(VersionAction action, RelationDescriptor<T> relation, T body,
			VersionBodyRepository<?> targets) {
		UUID target = relation.targetOf(body);
		if (!GOING_ONLINE.contains(action) || target == null
				|| targets.existsWithStatus(target, Set.of(VersionStatus.PUBLISHED))) {
			return Optional.empty();
		}
		Map<String, Object> detail = new LinkedHashMap<>();
		detail.put(relation.name() + ID_SUFFIX, target);
		targets.findMostRecent(List.of(target), VersionStatus.approved())
			.stream()
			.findFirst()
			.map(VersionBodyEntity::naturalKey)
			.filter(key -> !key.isMissing())
			.ifPresent(key -> detail.put(relation.name() + StringUtils.capitalize(key.field()), key.value()));
		return Optional.of(new ActionBlock(relation.targetNotOnline(), detail));
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

	/** An item by its id and natural key, e.g. {@code {id, romaji}}. */
	public Map<String, Object> named(VersionBodyEntity item) {
		NaturalKey key = item.naturalKey();
		Map<String, Object> named = new LinkedHashMap<>();
		named.put(ID, item.getContentId());
		named.put(key.field(), key.value());
		return named;
	}

}
