package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.MarineRank;

import java.util.Map;

/**
 * What a period row says, as {@link CharacterMembershipPeriodEntity#rewrite} writes it.
 *
 * @param window null for none chosen
 * @param role per language code
 */
public record MembershipPeriodFields(StoryWindowEmbeddable window, Map<String, String> role, MarineRank marineRank) {

}
