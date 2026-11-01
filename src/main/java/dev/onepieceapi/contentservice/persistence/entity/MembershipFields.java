package dev.onepieceapi.contentservice.persistence.entity;

import java.util.List;
import java.util.UUID;

/**
 * What a membership row says, as {@link CharacterMembershipEntity#rewrite} writes it.
 *
 * @param periods in order
 */
public record MembershipFields(UUID affiliationContentId, List<MembershipPeriodFields> periods) {

}
