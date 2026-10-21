package dev.onepieceapi.contentservice.service.character;

import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.SpeciesVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What a Character needs to go online - published or restored (implementation plan of the
 * Character, D8, D9): a first appearance in a chapter or an episode, each one it names
 * online, and every species of its list online. Not even {@code content:admin} lifts it.
 * Nothing blocks its retirement yet: no entity points to a character (bounties will, C6).
 * <p>
 * The chapter, the episode and every species are held shared first, so the retirement of
 * any of them waits for a character going online with it ({@code ChapterRules},
 * {@code EpisodeRules}, {@code SpeciesRules}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class CharacterRules implements ContentRules<Character> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	private final SpeciesVersionRepository speciesRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Character> version) {
		Character character = version.body();
		RelationRules.lockTargetBefore(action, CharacterRelations.FIRST_APPEARANCE_CHAPTER, character,
				this.contentRepository);
		RelationRules.lockTargetBefore(action, CharacterRelations.FIRST_APPEARANCE_EPISODE, character,
				this.contentRepository);
		RelationRules.lockTargetBefore(action, CharacterRelations.SPECIES, character.speciesIds(),
				this.contentRepository);
	}

	/** First what the character itself lacks, then what it points to, in its order. */
	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Character> version) {
		Character character = version.body();
		return FirstAppearanceRules.missing(action, character)
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.FIRST_APPEARANCE_CHAPTER, character,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.FIRST_APPEARANCE_EPISODE, character,
					this.episodeRepository))
			.or(() -> RelationRules.targetNotOnline(action, CharacterRelations.SPECIES, character.speciesIds(),
					CharacterRelations.SPECIES_FIELD, this.speciesRepository));
	}

}
