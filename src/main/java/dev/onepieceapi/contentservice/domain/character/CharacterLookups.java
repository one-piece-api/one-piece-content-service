package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationReference;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a character's lists point to, and who points to it, as it is today: looked up once
 * for the version being answered, so the mapper reads no database.
 *
 * @param species its species, by content id
 * @param affiliations the affiliations of its memberships, by content id
 * @param devilFruits its Devil Fruits, by content id
 * @param relatives its relatives, by content id
 * @param relativesOf the characters online that name it as their relative, with the
 * inverse kind (implementation plan of the Character, D13)
 */
public record CharacterLookups(Map<UUID, SpeciesReference> species, Map<UUID, AffiliationReference> affiliations,
		Map<UUID, DevilFruitReference> devilFruits, Map<UUID, CharacterReference> relatives,
		List<RelativeOf> relativesOf) {

}
