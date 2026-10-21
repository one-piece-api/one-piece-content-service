package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountyReference;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.web.dto.response.BountyNamesResponse;
import dev.onepieceapi.contentservice.web.dto.response.BountyReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.BountyResponse;
import dev.onepieceapi.contentservice.web.dto.response.BountyTranslationResponse;
import dev.onepieceapi.contentservice.web.dto.response.CharacterReferenceResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentSummaryResponse;
import dev.onepieceapi.contentservice.web.dto.response.VersionResponse;
import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/**
 * From bounties to their response bodies: what a version says is mapped here, the
 * workflow around it by {@link ContentResponseMapper}. The bounties it points to and the
 * holder and the chapter and the episode it first appears in are given already looked up,
 * as are the bounties a page of the list replaces - as today, for a whole page at once -
 * so the mapper reads no database.
 */
@UtilityClass
public class BountyResponseMapper {

	/** A version of a bounty as its caller meets it, with everything it says. */
	public VersionResponse<BountyResponse> toVersionResponse(VersionAccess<Bounty> access, BountyChain chain,
			Map<UUID, BountyReference> bounties, Map<UUID, CharacterReference> holders,
			Map<UUID, NumberedReference> storyItems, ImageResponseMapper images) {
		return ContentResponseMapper.toVersionResponse(access,
				bounty -> toResponse(bounty, chain, bounties, holders, storyItems, images));
	}

	/** A row of the list of bounties. */
	public ContentSummaryResponse<BountyNamesResponse> toSummaryResponse(ContentSummary<Bounty> summary,
			Set<UUID> replaced) {
		return ContentResponseMapper.toSummaryResponse(summary,
				bounty -> toNamesResponse(bounty, replaced.contains(summary.contentId())));
	}

	/** Everything the version says. */
	public BountyResponse toResponse(Bounty bounty, BountyChain chain, Map<UUID, BountyReference> bounties,
			Map<UUID, CharacterReference> holders, Map<UUID, NumberedReference> storyItems,
			ImageResponseMapper images) {
		Map<String, BountyTranslationResponse> translations = new TreeMap<>();
		bounty.translations().forEach((language, translation) -> translations.put(language, toResponse(translation)));
		List<BountyReferenceResponse> members = chain.contentIds()
			.stream()
			.map(id -> toResponse(id, bounties))
			.toList();
		return new BountyResponse(bounty.posterName(), bounty.amount(), bounty.condition(), bounty.inactive(),
				BountyValidity.of(bounty.inactive(), chain.next() != null),
				toResponse(bounty.previousContentId(), bounties), toHolderResponse(bounty.holderContentId(), holders),
				toResponse(chain.next(), bounties), members, translations, images.toResponse(bounty.imageId()),
				FirstAppearanceMapper.toResponse(bounty.firstAppearance(), storyItems));
	}

	/** What a list row shows: the key, the condition and whether the bounty stands. */
	public BountyNamesResponse toNamesResponse(Bounty bounty, boolean replaced) {
		return new BountyNamesResponse(bounty.naturalKeyText(), bounty.posterName(), bounty.amount(),
				bounty.condition(), BountyValidity.of(bounty.inactive(), replaced));
	}

	/**
	 * The bounty with its key; with only its id when nothing is known to call it by; null
	 * for none.
	 */
	public BountyReferenceResponse toResponse(UUID contentId, Map<UUID, BountyReference> bounties) {
		if (contentId == null) {
			return null;
		}
		return toResponse(bounties.getOrDefault(contentId, BountyReference.unnamed(contentId)));
	}

	/**
	 * The holder with its names; with only its id when it has nothing approved to be
	 * called by, which the link makes impossible but a read must not fail for; null for
	 * none.
	 */
	private static CharacterReferenceResponse toHolderResponse(UUID holderContentId,
			Map<UUID, CharacterReference> holders) {
		if (holderContentId == null) {
			return null;
		}
		CharacterReference holder = holders.get(holderContentId);
		return holder == null ? new CharacterReferenceResponse(holderContentId, null, Map.of())
				: CharacterResponseMapper.toResponse(holder);
	}

	public BountyReferenceResponse toResponse(BountyReference bounty) {
		return new BountyReferenceResponse(bounty.id(), bounty.key(), bounty.posterName(), bounty.amount());
	}

	private static BountyTranslationResponse toResponse(BountyTranslation translation) {
		return new BountyTranslationResponse(translation.description());
	}

}
