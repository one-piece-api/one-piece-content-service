package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations of an Affiliation (ADR-0008): the chapter and the episode it first
 * appears in. Both keep a chapter or an episode from being retired as
 * {@code affiliations}. The characters' own relation to an affiliation is
 * {@code CharacterRelations#AFFILIATION}.
 */
public final class AffiliationRelations {

	/** The chapter an affiliation first appears in. */
	public static final RelationDescriptor<Affiliation> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation("affiliations");

	/** The episode an affiliation first appears in. */
	public static final RelationDescriptor<Affiliation> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation("affiliations");

	private AffiliationRelations() {
	}

}
