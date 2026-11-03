package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.dashboard.StatusFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import dev.onepieceapi.contentservice.web.dto.request.PreviewRequest;
import dev.onepieceapi.contentservice.web.dto.request.StatusPageRequest;
import dev.onepieceapi.readertime.ReaderPosition;
import lombok.experimental.UtilityClass;

/** From the requests shared by every kind of content to the domain. */
@UtilityClass
public class ContentRequestMapper {

	public ContentFilter toFilter(ContentListRequest request) {
		return new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
	}

	/** The filters of a dashboard status page; "mine" only when asked for. */
	public StatusFilter toStatusFilter(StatusPageRequest request) {
		return new StatusFilter(request.entity(), request.author(), Boolean.TRUE.equals(request.mine()));
	}

	/** Where the reader of a preview is; the request holds exactly one of the two. */
	public ReaderPosition toPosition(PreviewRequest request) {
		return (request.asOfChapter() != null) ? ReaderPosition.atChapter(request.asOfChapter())
				: ReaderPosition.atEpisode(request.asOfEpisode());
	}

}
