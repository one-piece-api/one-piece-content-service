package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.BountyListRequest;
import dev.onepieceapi.contentservice.web.dto.request.BountyRequest;
import lombok.experimental.UtilityClass;
import org.springframework.web.multipart.MultipartFile;

/** From the requests about bounties to the domain. */
@UtilityClass
public class BountyRequestMapper {

	/** What the request says, as typed: tidying it up is left to the domain. */
	public Bounty toDomain(BountyRequest request) {
		return new Bounty(request.posterName(), request.amount(), request.condition(), request.markedInactive(),
				request.previous(), request.holder(), null, FirstAppearanceMapper.toDomain(request.firstAppearance()),
				ParagraphRequestMapper.toDomain(request.description()));
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
