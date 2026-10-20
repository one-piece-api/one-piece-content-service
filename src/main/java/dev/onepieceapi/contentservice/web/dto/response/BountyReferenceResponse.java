package dev.onepieceapi.contentservice.web.dto.response;

import java.util.UUID;

/**
 * A bounty as another one points to it: as of today, never what it said when the pointing
 * version was written.
 *
 * @param id the content id
 * @param key the key text, e.g. {@code Monkey D. Luffy 3000000000}; null when it has
 * nothing to be called by
 * @param posterName null as the key
 * @param amount null as the key
 */
public record BountyReferenceResponse(UUID id, String key, String posterName, Long amount) {

}
