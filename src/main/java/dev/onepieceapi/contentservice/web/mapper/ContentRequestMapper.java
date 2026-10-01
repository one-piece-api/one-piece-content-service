package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.web.dto.request.ContentListRequest;
import lombok.experimental.UtilityClass;

/** From the requests shared by every kind of content to the domain. */
@UtilityClass
public class ContentRequestMapper {

	public ContentFilter toFilter(ContentListRequest request) {
		return new ContentFilter(request.status(), request.q(), request.author(), request.updatedWithinDays());
	}

}
