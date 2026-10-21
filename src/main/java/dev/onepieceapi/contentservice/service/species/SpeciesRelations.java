package dev.onepieceapi.contentservice.service.species;

import dev.onepieceapi.contentservice.domain.species.Species;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationDescriptor;

/**
 * The relations of a Species (ADR-0008): the chapter and the episode it first appears in.
 * Both keep a chapter or an episode from being retired as {@code species}. Characters
 * will point to it (Character plan).
 */
public final class SpeciesRelations {

	/** The chapter a species first appears in. */
	public static final RelationDescriptor<Species> FIRST_APPEARANCE_CHAPTER = FirstAppearanceRules
		.chapterRelation("species");

	/** The episode a species first appears in. */
	public static final RelationDescriptor<Species> FIRST_APPEARANCE_EPISODE = FirstAppearanceRules
		.episodeRelation("species");

	private SpeciesRelations() {
	}

}
