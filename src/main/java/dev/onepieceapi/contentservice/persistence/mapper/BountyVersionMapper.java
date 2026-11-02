package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import dev.onepieceapi.contentservice.domain.bounty.BountyKnowledge;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.persistence.entity.BountyFields;
import dev.onepieceapi.contentservice.persistence.entity.BountyRevealEntity;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import lombok.experimental.UtilityClass;

import java.time.Instant;
import java.util.TreeMap;

/**
 * Between the rows of a bounty version - its workflow in the shared table, what it says
 * in the entity's own, its initial knowledge and reveals - and the domain.
 */
@UtilityClass
public class BountyVersionMapper {

	public Version<Bounty> toDomain(BountyVersionEntity entity) {
		var body = Bounty.builder()
			.posterName(entity.getPosterName())
			.amount(entity.getAmount())
			.condition(entity.getCondition())
			.inactive(entity.isInactive())
			.previousContentId(entity.getPreviousContentId())
			.holderContentId(entity.getHolderContentId())
			.imageId(entity.getImageId())
			.firstAppearance(new StoryPoint(entity.getFirstAppearanceChapterId(), entity.getFirstAppearanceEpisodeId()))
			.description(ParagraphMapper.descriptionOf(entity.getParagraphs()))
			.initialKnowledge(new BountyKnowledge(entity.getInitialInactive()))
			.reveals(entity.getReveals().stream().map(BountyVersionMapper::toDomain).toList())
			.build();
		return ContentVersionMapper.toDomain(entity.getVersion(), body);
	}

	/** Makes the version say what was given, in place of what it said. */
	public void rewrite(BountyVersionEntity entity, Bounty body, Instant now) {
		entity.rewrite(BountyFields.builder()
			.posterName(body.posterName())
			.amount(body.amount())
			.condition(body.condition())
			.inactive(body.inactive())
			.previousContentId(body.previousContentId())
			.holderContentId(body.holderContentId())
			.imageId(body.imageId())
			.firstAppearance(FirstAppearanceMapper.toIds(body.firstAppearance()))
			.paragraphs(ParagraphMapper.toFields(body.paragraphs()))
			.initialInactive(body.initialKnowledge().inactive())
			.reveals(body.reveals().stream().map(BountyVersionMapper::toWritten).toList())
			.build(), now);
	}

	/**
	 * The titles sorted by language code, so the same version always reads the same way.
	 */
	private static Reveal<BountyField, BountyKnowledge> toDomain(BountyRevealEntity reveal) {
		return new Reveal<>(RevealMapper.pointOf(reveal), reveal.getFields(), new BountyKnowledge(reveal.getInactive()),
				new TreeMap<>(reveal.getTitles()));
	}

	private static BountyRevealEntity.Written toWritten(Reveal<BountyField, BountyKnowledge> reveal) {
		return new BountyRevealEntity.Written(FirstAppearanceMapper.toIds(reveal.revealedAt()), reveal.fields(),
				reveal.values().inactive(), reveal.titles());
	}

}
