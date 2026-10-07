package dev.onepieceapi.contentservice.domain.workflow;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The combinable filters of an entity section (UF-CNT-18). Every field is optional - null
 * means "do not filter on this" - and the given ones are ANDed together. All of them
 * apply to the version representing the content in the list, i.e. to what the row shows.
 *
 * @param status only contents having a visible version in this status, represented by it
 * @param query text to look for in what the version says; where exactly is up to each
 * kind of content
 * @param author the username of the author of the version
 * @param updatedWithinDays updated today (0) or in the last N days
 * @param related the contents the version must point to, by relation (e.g. the type of a
 * fruit); empty for an entity pointing to none
 */
public record ContentFilter(VersionStatus status, String query, String author, Integer updatedWithinDays,
		Map<String, UUID> related) {

	public ContentFilter {
		related = related == null ? Map.of() : Map.copyOf(related);
	}

	/** Without any relation to narrow by. */
	public ContentFilter(VersionStatus status, String query, String author, Integer updatedWithinDays) {
		this(status, query, author, updatedWithinDays, Map.of());
	}

	/**
	 * The same filter, narrowed to the contents pointing to this one through the named
	 * relation. The relation is named as the entity's attribute holding it, less its
	 * {@code ContentId} suffix: {@code type} for {@code typeContentId}.
	 */
	public ContentFilter relatedTo(String relation, UUID contentId) {
		Map<String, UUID> narrowed = new HashMap<>(this.related);
		narrowed.put(relation, contentId);
		return new ContentFilter(this.status, this.query, this.author, this.updatedWithinDays, narrowed);
	}

	/** No filter at all: every content the caller sees. */
	public static ContentFilter none() {
		return new ContentFilter(null, null, null, null);
	}

	/** Only the contents shown by a version of this author. */
	public static ContentFilter authoredBy(String username) {
		return new ContentFilter(null, null, username, null);
	}

	/**
	 * The statuses a list may show: the ones the caller sees, narrowed to the one being
	 * filtered on. Empty when that one is not among them - a caller cannot filter by a
	 * status they do not see.
	 */
	public Set<VersionStatus> statusesAmong(Set<VersionStatus> visible) {
		if (this.status == null) {
			return visible;
		}
		return visible.contains(this.status) ? EnumSet.of(this.status) : EnumSet.noneOf(VersionStatus.class);
	}

	/** The text to look for, trimmed; empty when there is none worth searching. */
	public Optional<String> text() {
		return Optional.ofNullable(this.query).map(String::trim).filter(Predicate.not(String::isEmpty));
	}

	/**
	 * The instant "updated within N days" starts from: midnight, N days before today. In
	 * UTC, the zone every timestamp of this service is taken in.
	 */
	public Optional<Instant> updatedSince(Clock clock) {
		return Optional.ofNullable(this.updatedWithinDays).map(days -> {
			LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
			return today.minusDays(days).atStartOfDay(ZoneOffset.UTC).toInstant();
		});
	}

}
