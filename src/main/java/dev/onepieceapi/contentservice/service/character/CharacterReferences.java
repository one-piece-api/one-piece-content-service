package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.affiliation.AffiliationReference;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.FruitUse;
import dev.onepieceapi.contentservice.domain.character.Membership;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitReference;
import dev.onepieceapi.contentservice.domain.species.SpeciesReference;
import dev.onepieceapi.contentservice.service.affiliation.AffiliationLinks;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitLinks;
import dev.onepieceapi.contentservice.service.species.SpeciesLinks;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

/**
 * What the lists of a Character point to, as it is today (implementation plan of the
 * Character): its species, the affiliations of its memberships and its Devil Fruits, each
 * looked up in one query for the whole list.
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class CharacterReferences {

	private final SpeciesLinks species;

	private final AffiliationLinks affiliations;

	private final DevilFruitLinks devilFruits;

	public Map<UUID, SpeciesReference> speciesOf(Character character) {
		return this.species.referencesOf(character.speciesIds());
	}

	public Map<UUID, AffiliationReference> affiliationsOf(Character character) {
		return this.affiliations
			.referencesOf(character.affiliations().stream().map(Membership::affiliationContentId).toList());
	}

	public Map<UUID, DevilFruitReference> devilFruitsOf(Character character) {
		return this.devilFruits
			.referencesOf(character.devilFruits().stream().map(FruitUse::devilFruitContentId).toList());
	}

}
