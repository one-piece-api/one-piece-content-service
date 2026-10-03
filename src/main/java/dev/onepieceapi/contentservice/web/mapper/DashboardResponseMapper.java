package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.web.dto.response.ActivityResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentTitleResponse;
import dev.onepieceapi.contentservice.web.dto.response.DashboardResponse;
import dev.onepieceapi.contentservice.web.dto.response.StatusCountResponse;
import lombok.experimental.UtilityClass;

import java.util.List;

/** From the dashboard's domain to its response bodies. */
@UtilityClass
public class DashboardResponseMapper {

	public DashboardResponse toDashboardResponse(List<StatusCount> counts) {
		return new DashboardResponse(counts.stream().map(DashboardResponseMapper::toStatusCountResponse).toList());
	}

	public ActivityResponse toActivityResponse(Activity activity) {
		return ActivityResponse.builder()
			.action(activity.action())
			.occurredAt(activity.occurredAt())
			.entityType(activity.entityType())
			.contentId(activity.contentId())
			.versionNumber(activity.versionNumber())
			.label(activity.label())
			.title(activity.title() == null ? null : toTitleResponse(activity.title()))
			.build();
	}

	private static StatusCountResponse toStatusCountResponse(StatusCount count) {
		return new StatusCountResponse(count.status(), count.contents(), count.mine());
	}

	private static ContentTitleResponse toTitleResponse(ContentTitle title) {
		return new ContentTitleResponse(title.names(), title.fallback());
	}

}
