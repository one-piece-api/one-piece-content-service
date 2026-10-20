package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.repository.BountyVersionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Follows the chain of bounties - each one's previous - for the two rules that must not
 * let it close on itself (implementation plan of the Bounty, B2): on save, through the
 * latest approved versions, and on going online, through the online ones.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class BountyChains {

	/** The most links followed: no real chain comes near, a corrupt one stops here. */
	static final int MAX_LENGTH = 10_000;

	private final BountyVersionRepository repository;

	/**
	 * Whether following the previous of each bounty, from {@code start} on, reaches
	 * {@code target} - {@code start} itself included. Each bounty is read by its most
	 * recent version among the statuses; a chain already closed on itself elsewhere ends
	 * the walk instead of looping.
	 */
	public boolean reaches(UUID start, UUID target, Collection<VersionStatus> statuses) {
		Set<UUID> seen = new HashSet<>();
		UUID current = start;
		while (current != null && seen.size() < MAX_LENGTH && seen.add(current)) {
			if (current.equals(target)) {
				return true;
			}
			current = this.repository.findPreviousOfMostRecent(current, statuses).orElse(null);
		}
		return false;
	}

}
