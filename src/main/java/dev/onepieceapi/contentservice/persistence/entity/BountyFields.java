package dev.onepieceapi.contentservice.persistence.entity;

import java.util.List;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import lombok.Builder;

import java.util.UUID;

/**
 * Everything a bounty version says, as its row is written - what
 * {@link BountyVersionEntity#rewrite} replaces it with.
 */
@Builder
public record BountyFields(String posterName, Long amount, BountyCondition condition, boolean inactive,
		UUID previousContentId, UUID holderContentId, String imageId, FirstAppearanceIds firstAppearance,
		List<ParagraphFields> paragraphs, Boolean initialInactive, List<BountyRevealEntity.Written> reveals) {

}
