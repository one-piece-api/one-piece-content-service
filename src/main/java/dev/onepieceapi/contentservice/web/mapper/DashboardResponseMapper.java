package dev.onepieceapi.contentservice.web.mapper;

import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.domain.dashboard.StatusRow;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.service.content.StatusPage;
import dev.onepieceapi.contentservice.web.dto.response.ActivityResponse;
import dev.onepieceapi.contentservice.web.dto.response.ContentTitleResponse;
import dev.onepieceapi.contentservice.web.dto.response.DashboardResponse;
import dev.onepieceapi.contentservice.web.dto.response.PageResponse;
import dev.onepieceapi.contentservice.web.dto.response.StatusCountResponse;
import dev.onepieceapi.contentservice.web.dto.response.StatusPageResponse;
import dev.onepieceapi.contentservice.web.dto.response.StatusRowResponse;
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

	public StatusPageResponse toStatusPageResponse(StatusPage page) {
		var rows = PageResponse.from(page.rows().map(DashboardResponseMapper::toStatusRowResponse));
		return new StatusPageResponse(rows, page.all(), page.mine());
	}

	private static StatusRowResponse toStatusRowResponse(StatusRow row) {
		Version<ContentTitle> version = row.version();
		return StatusRowResponse.builder()
			.entityType(row.entityType())
			.contentId(row.contentId())
			.versionNumber(version.number())
			.status(version.status())
			.title(version.body() == null ? null : toTitleResponse(version.body()))
			.author(ContentResponseMapper.toUserResponse(version.author()))
			.claimant(ContentResponseMapper.toUserResponse(version.claimant()))
			.updatedAt(version.updatedAt())
			.onlineVersionNumber(row.onlineVersionNumber())
			.allowedActions(List.copyOf(row.allowedActions()))
			.overrideActions(List.copyOf(row.overrideActions()))
			.build();
	}

	private static StatusCountResponse toStatusCountResponse(StatusCount count) {
		return new StatusCountResponse(count.status(), count.contents(), count.mine());
	}

	private static ContentTitleResponse toTitleResponse(ContentTitle title) {
		return new ContentTitleResponse(title.names(), title.fallback());
	}

}
