package dev.onepieceapi.contentservice.domain.bounty;

import java.util.UUID;

/**
 * A bounty as another bounty points to it (the one it replaces, the one replacing it, the
 * chain it belongs to): as it is today, never what it said when the pointing version was
 * written.
 *
 * @param id the content id
 * @param key the key text, e.g. {@code Monkey D. Luffy 3000000000}; null when it has
 * nothing approved or visible to be called by
 * @param posterName null as the key
 * @param amount null as the key
 */
public record BountyReference(UUID id, String key, String posterName, Long amount) {

	/** A bounty known only by its id: nothing visible to be called by. */
	public static BountyReference unnamed(UUID id) {
		return new BountyReference(id, null, null, null);
	}

}
