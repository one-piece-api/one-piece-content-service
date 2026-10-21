package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountyReference;
import dev.onepieceapi.contentservice.domain.bounty.BountySortField;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.domain.workflow.VisibilityPolicy;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.mapper.BountyVersionMapper;
import dev.onepieceapi.contentservice.persistence.projection.HolderBountyCount;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * What the bounties say of each other (implementation plan of the Bounty, D3): the bounty
 * a bounty replaces and the one replacing it, as they are today; the whole chain; whether
 * a bounty is replaced, which is what its validity is made of; and the bounties one may
 * be linked to. All of it as the caller sees it - through the versions their permissions
 * show - and each answering in batch, one query for a whole page.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class BountyLinks {

	/** The language a sort by name would read; a bounty has no name to read. */
	private static final String NO_LANGUAGE = "en";

	private final BountyVersionRepository repository;

	private final Clock clock;

	/**
	 * The bounties as they were last seen - by the caller, or approved, whichever is more
	 * recent - by content id. One with nothing of either is called by its id alone.
	 */
	public Map<UUID, BountyReference> referencesOf(Collection<UUID> contentIds, Set<VersionStatus> visible) {
		if (contentIds.isEmpty()) {
			return Map.of();
		}
		Set<UUID> ids = new HashSet<>(contentIds);
		Map<UUID, BountyReference> references = this.repository.findMostRecent(ids, nameable(visible))
			.stream()
			.map(BountyLinks::referenceOf)
			.collect(Collectors.toMap(BountyReference::id, Function.identity()));
		ids.forEach(id -> references.computeIfAbsent(id, BountyReference::unnamed));
		return references;
	}

	/**
	 * How many bounties each of the given characters holds for this caller: the bounties
	 * their list shows naming it, which is also what the list of bounties narrowed by
	 * that holder counts. A character with none is not in the map.
	 */
	public Map<UUID, Long> bountyCounts(Set<Permission> permissions, Collection<UUID> holderContentIds) {
		Set<VersionStatus> visible = VisibilityPolicy.visibleStatuses(permissions);
		if (visible.isEmpty() || holderContentIds.isEmpty()) {
			return Map.of();
		}
		return this.repository.countByHolders(holderContentIds, visible)
			.stream()
			.collect(Collectors.toMap(HolderBountyCount::holderContentId, HolderBountyCount::count));
	}

	/**
	 * Which of these bounties another one replaces, for a caller seeing these statuses.
	 */
	public Set<UUID> replacedAmong(Collection<UUID> contentIds, Set<VersionStatus> visible) {
		if (contentIds.isEmpty() || visible.isEmpty()) {
			return Set.of();
		}
		return new HashSet<>(this.repository.findReplaced(contentIds, visible));
	}

	/**
	 * The chain a bounty belongs to: the one replacing it, itself, and the ones it
	 * replaces in turn, newest first. The walk starts from the previous the version shown
	 * says, then follows what the caller sees of each; a bounty met twice ends it.
	 */
	public BountyChain chainOf(UUID contentId, UUID previousContentId, Set<VersionStatus> visible) {
		Set<UUID> seen = new HashSet<>(Set.of(contentId));
		List<UUID> newerFirst = new ArrayList<>();
		UUID next = nextOf(contentId, visible).orElse(null);
		for (UUID newer = next; newer != null && seen.add(newer); newer = nextOf(newer, visible).orElse(null)) {
			newerFirst.add(0, newer);
		}
		newerFirst.add(contentId);
		UUID older = previousContentId;
		while (older != null && seen.add(older)) {
			newerFirst.add(older);
			older = this.repository.findPreviousOfMostRecent(older, visible).orElse(null);
		}
		return new BountyChain(List.copyOf(newerFirst), next);
	}

	/**
	 * The bounties a bounty may be linked to - those with a version that passed review -
	 * one page at a time, by key, narrowed by what their key contains.
	 */
	public Page<BountyReference> linkable(String text, int page, int size) {
		var filter = new ContentFilter(null, text, null, null);
		var pageable = PageRequest.of(page, size, Sort.by(BountySortField.NAME.field()));
		return this.repository.search(VersionStatus.approved(), filter, this.clock, pageable, NO_LANGUAGE)
			.map(BountyLinks::referenceOf);
	}

	private Optional<UUID> nextOf(UUID contentId, Set<VersionStatus> visible) {
		return this.repository.findReplacing(contentId, visible).stream().filter(Objects::nonNull).findFirst();
	}

	/**
	 * What names a bounty: the versions the caller sees, and those that passed review.
	 */
	private static Set<VersionStatus> nameable(Set<VersionStatus> visible) {
		Set<VersionStatus> statuses = EnumSet.copyOf(VersionStatus.approved());
		statuses.addAll(visible);
		return statuses;
	}

	private static BountyReference referenceOf(BountyVersionEntity version) {
		Bounty bounty = BountyVersionMapper.toDomain(version).body();
		return new BountyReference(version.getContentId(), bounty.naturalKeyText(), bounty.posterName(),
				bounty.amount());
	}

}
