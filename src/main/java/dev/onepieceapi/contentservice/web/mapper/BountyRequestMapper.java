package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import dev.onepieceapi.contentservice.domain.bounty.BountyKnowledge;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.Reveal;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.BountyKnowledgeRequest;
import dev.onepieceapi.contentservice.web.dto.request.BountyListRequest;
import dev.onepieceapi.contentservice.web.dto.request.BountyRequest;
import dev.onepieceapi.contentservice.web.dto.request.BountyRevealRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

/** From the requests about bounties to the domain. */
@UtilityClass
public class BountyRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Bounty toDomain(BountyRequest request) {
		return Bounty.builder()
			.posterName(request.posterName())
			.amount(request.amount())
			.condition(request.condition())
			.inactive(request.markedInactive())
			.previousContentId(request.previous())
			.holderContentId(request.holder())
			.firstAppearance(FirstAppearanceMapper.toDomain(request.firstAppearance()))
			.description(ParagraphRequestMapper.toDomain(request.description()))
			.initialKnowledge(Optional.ofNullable(request.initialKnowledge())
				.map(BountyRequestMapper::toDomain)
				.orElse(BountyKnowledge.NONE))
			.reveals(RevealRequestMapper.each(request.reveals(), BountyRequestMapper::toDomain))
			.build();
	}

	private static BountyKnowledge toDomain(BountyKnowledgeRequest knowledge) {
		return new BountyKnowledge(knowledge.inactive());
	}

	private static Reveal<BountyField, BountyKnowledge> toDomain(BountyRevealRequest reveal) {
		return RevealRequestMapper.toDomain(reveal, new BountyKnowledge(reveal.inactive()));
	}

	/**
	 * What the save does to the draft's poster: replaced by an upload, removed when
	 * asked, kept otherwise; both at once is refused.
	 * @param upload the file sent with the request; null or empty for none
	 */
	public ImageChange toImageChange(BountyRequest request, MultipartFile upload) {
		return ImageRequestMapper.toImageChange(request.removesImage(), upload);
	}

	/**
	 * The common filters, then the condition, the holder and the validity when the list
	 * is narrowed.
	 */
	public ContentFilter toFilter(BountyListRequest request) {
		var filter = new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
		if (request.holder() != null) {
			filter = filter.withAttribute(Bounty.HOLDER_ATTRIBUTE, request.holder());
		}
		if (request.condition() != null) {
			filter = filter.withAttribute(Bounty.CONDITION_ATTRIBUTE, request.condition());
		}
		return request.validity() == null ? filter
				: filter.withAttribute(Bounty.VALIDITY_ATTRIBUTE, request.validity());
	}

}
