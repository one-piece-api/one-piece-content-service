package dev.onepieceapi.contentservice.service.bounty;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import dev.onepieceapi.contentservice.domain.bounty.BountyKnowledge;
import dev.onepieceapi.contentservice.domain.character.Character;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Preview;
import dev.onepieceapi.contentservice.domain.readertime.RevealedName;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.BountyVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.CharacterVersionEntity;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.character.CharacterPreviewer;
import dev.onepieceapi.contentservice.service.content.ContentDefinition;
import dev.onepieceapi.contentservice.service.content.ContentPreviewer;
import dev.onepieceapi.contentservice.service.content.PreviewContext;
import dev.onepieceapi.contentservice.service.content.ReaderPreviews;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * A Bounty as a reader at N sees it (UF-CNT-23): met from its first appearance, whether
 * it is inactive as known there, its holder and the bounty it replaces when the reader
 * meets them, its description as the paragraphs read there. Its chain is the bounties
 * online the reader has met: one replacing it later does not make it replaced yet.
 */
@Component
@Transactional(readOnly = true)
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class BountyPreviewer implements ContentPreviewer<Bounty> {

	private static final String HOLDER_FIELD = "holder";

	private static final String PREVIOUS_FIELD = "previous";

	private static final Set<VersionStatus> ONLINE = Set.of(VersionStatus.PUBLISHED);

	private final ReaderPreviews previews;

	private final BountyLinks links;

	private final CharacterLinks characterLinks;

	private final ContentDefinition<Bounty, BountyVersionEntity> bounties;

	private final ContentDefinition<Character, CharacterVersionEntity> characters;

	@Override
	public Preview<Bounty> previewOf(UUID contentId, Bounty body, ReaderPosition position) {
		Map<UUID, Character> holders = this.previews.online(this.characters, idsOf(body.holderContentId()));
		Map<UUID, Bounty> previous = this.previews.online(this.bounties, idsOf(body.previousContentId()));
		Map<UUID, StoryPoint> targets = new HashMap<>(ReaderPreviews.firstAppearances(holders));
		targets.putAll(ReaderPreviews.firstAppearances(previous));
		List<StoryPoint> points = Stream
			.concat(body.points().stream(), holders.values().stream().flatMap(holder -> holder.points().stream()))
			.toList();
		PreviewContext context = this.previews.contextFor(position, points, targets);
		if (!context.hasMet(body.firstAppearance())) {
			return Preview.notVisible(position);
		}
		boolean inactive = context.valueAt(body, BountyField.INACTIVE, new BountyKnowledge(body.inactive()))
			.map(BountyKnowledge::inactive)
			.orElse(false);
		UUID holderId = body.holderContentId();
		UUID previousId = body.previousContentId();
		Map<UUID, RevealedName> namesAt = new HashMap<>();
		holders.forEach((id, holder) -> namesAt.put(id, CharacterPreviewer.nameAt(context, holder)));
		return context.previewOf(body.toBuilder()
			.inactive(inactive)
			.holderContentId(context.seesTarget(holderId, HOLDER_FIELD) ? holderId : null)
			.previousContentId(context.seesTarget(previousId, PREVIOUS_FIELD) ? previousId : null)
			.firstAppearance(context.trimmed(body.firstAppearance()))
			.description(context.paragraphs(ParagraphSection.DESCRIPTION, body.description()))
			.initialKnowledge(BountyKnowledge.NONE)
			.reveals(List.of())
			.build(), null, namesAt);
	}

	/**
	 * The chain of the bounty previewed as the reader at N sees it: walked through the
	 * bounties online, keeping those met at N; the one replacing it only when met.
	 */
	public BountyChain chainAt(UUID contentId, Preview<Bounty> preview) {
		BountyChain online = this.links.chainOf(contentId, preview.body().previousContentId(), ONLINE);
		Map<UUID, Bounty> members = this.previews.online(this.bounties, online.contentIds());
		PreviewContext context = this.previews.contextFor(preview.position(), List.of(),
				ReaderPreviews.firstAppearances(members));
		List<UUID> met = online.contentIds()
			.stream()
			.filter(id -> id.equals(contentId) || isMet(id, members, context))
			.toList();
		UUID next = isMet(online.next(), members, context) ? online.next() : null;
		return new BountyChain(met, next);
	}

	/** The holder of the bounty previewed, called as the reader at N knows them. */
	public Map<UUID, CharacterReference> holdersAt(Preview<Bounty> preview) {
		Map<UUID, CharacterReference> holders = new HashMap<>();
		this.characterLinks.referencesOf(idsOf(preview.body().holderContentId())).forEach((id, holder) -> {
			RevealedName name = preview.namesAt().get(id);
			holders.put(id, (name == null) ? holder : holder.named(name));
		});
		return holders;
	}

	private static boolean isMet(UUID id, Map<UUID, Bounty> members, PreviewContext context) {
		return id != null && members.containsKey(id) && context.hasMet(members.get(id).firstAppearance());
	}

	private static List<UUID> idsOf(UUID id) {
		return Stream.of(id).filter(Objects::nonNull).toList();
	}

}
