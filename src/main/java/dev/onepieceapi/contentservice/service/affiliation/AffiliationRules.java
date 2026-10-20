package dev.onepieceapi.contentservice.service.affiliation;

import dev.onepieceapi.contentservice.domain.affiliation.Affiliation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.EpisodeVersionRepository;
import dev.onepieceapi.contentservice.service.content.ContentRules;
import dev.onepieceapi.contentservice.service.content.FirstAppearanceRules;
import dev.onepieceapi.contentservice.service.content.RelationRules;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * What an Affiliation needs to go online - published or restored (implementation plan of
 * the Affiliation, D4): a first appearance in a chapter or an episode, and each one it
 * names online. Not even {@code content:admin} lifts it. Nothing blocks its retirement:
 * no entity points to an affiliation yet.
 * <p>
 * The chapter and the episode are held first, so the retirement of either waits for an
 * affiliation going online with it ({@code ChapterRules}, {@code EpisodeRules}).
 */
@Component
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class AffiliationRules implements ContentRules<Affiliation> {

	private final ContentRepository contentRepository;

	private final ChapterVersionRepository chapterRepository;

	private final EpisodeVersionRepository episodeRepository;

	@Override
	public void lockBefore(VersionAction action, UUID contentId, Version<Affiliation> version) {
		RelationRules.lockTargetBefore(action, AffiliationRelations.FIRST_APPEARANCE_CHAPTER, version.body(),
				this.contentRepository);
		RelationRules.lockTargetBefore(action, AffiliationRelations.FIRST_APPEARANCE_EPISODE, version.body(),
				this.contentRepository);
	}

	@Override
	public Optional<ActionBlock> blockOf(VersionAction action, UUID contentId, Version<Affiliation> version) {
		Affiliation affiliation = version.body();
		return FirstAppearanceRules.missing(action, affiliation)
			.or(() -> RelationRules.targetNotOnline(action, AffiliationRelations.FIRST_APPEARANCE_CHAPTER, affiliation,
					this.chapterRepository))
			.or(() -> RelationRules.targetNotOnline(action, AffiliationRelations.FIRST_APPEARANCE_EPISODE, affiliation,
					this.episodeRepository));
	}

}
