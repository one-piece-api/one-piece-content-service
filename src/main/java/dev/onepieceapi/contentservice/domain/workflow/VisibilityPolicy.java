package dev.onepieceapi.contentservice.domain.workflow;

import dev.onepieceapi.contentservice.domain.security.Permission;
import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_READ;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_REVIEW;
import static dev.onepieceapi.contentservice.domain.security.Permission.CONTENT_WRITE;

/**
 * Which statuses a caller sees, given their permissions - the table of
 * docs/user-flows/content-editorial-workflow.md 4.3, as data. Everything a caller reads
 * of an item (lists, detail, history, filters, counters) is restricted to the versions in
 * these statuses, so an item with no visible version does not exist for them.
 */
@UtilityClass
public class VisibilityPolicy {

	/** A status is visible to a caller holding any of its permissions. */
	private static final Map<VersionStatus, Set<Permission>> VISIBLE_TO = new EnumMap<>(VersionStatus.class);

	static {
		VISIBLE_TO.put(VersionStatus.DRAFT, Set.of(CONTENT_WRITE));
		VISIBLE_TO.put(VersionStatus.REJECTED, Set.of(CONTENT_WRITE));
		VISIBLE_TO.put(VersionStatus.IN_REVIEW, Set.of(CONTENT_WRITE, CONTENT_REVIEW));
		VISIBLE_TO.put(VersionStatus.READY_TO_PUBLISH, Set.of(CONTENT_READ));
		VISIBLE_TO.put(VersionStatus.PUBLISHED, Set.of(CONTENT_READ));
		VISIBLE_TO.put(VersionStatus.ARCHIVED, Set.of(CONTENT_READ));
		VISIBLE_TO.put(VersionStatus.RETIRED, Set.of(CONTENT_READ));
		VISIBLE_TO.put(VersionStatus.SUPERSEDED, Set.of(CONTENT_READ));
	}

	public Set<VersionStatus> visibleStatuses(Set<Permission> permissions) {
		return Arrays.stream(VersionStatus.values())
			.filter(status -> !Collections.disjoint(VISIBLE_TO.get(status), permissions))
			.collect(Collectors.toCollection(() -> EnumSet.noneOf(VersionStatus.class)));
	}

}
