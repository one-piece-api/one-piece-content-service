package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.dashboard.DashboardSortField;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.dashboard.DashboardService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.request.StatusPageRequest;
import dev.onepieceapi.contentservice.web.dto.response.ActivityResponse;
import dev.onepieceapi.contentservice.web.dto.response.DashboardResponse;
import dev.onepieceapi.contentservice.web.dto.response.StatusPageResponse;
import dev.onepieceapi.contentservice.web.dto.response.UserResponse;
import dev.onepieceapi.contentservice.web.mapper.ContentRequestMapper;
import dev.onepieceapi.contentservice.web.mapper.ContentResponseMapper;
import dev.onepieceapi.contentservice.web.mapper.DashboardResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import dev.onepieceapi.contentservice.web.validation.SortableBy;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The dashboard (UF-CNT-19), across entity types: a counter per status the caller sees,
 * the caller's own latest actions, and each status page by page. All take
 * {@code content:read} (see {@code SecuredEndpoint}); what they count and list is limited
 * to what the caller may see - a status page they do not see answers {@code 404}.
 */
@RestController
@Tag(name = "Dashboard")
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
class DashboardController {

	private final DashboardService service;

	@GetMapping(ApiPaths.DASHBOARD)
	DashboardResponse statusCounts(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return DashboardResponseMapper
			.toDashboardResponse(this.service.statusCounts(caller.permissions(), caller.user()));
	}

	@GetMapping(ApiPaths.DASHBOARD_ACTIVITY)
	List<ActivityResponse> activity(@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.activity(caller.permissions(), caller.user())
			.stream()
			.map(DashboardResponseMapper::toActivityResponse)
			.toList();
	}

	@GetMapping(ApiPaths.DASHBOARD_STATUS)
	StatusPageResponse statusPage(@PathVariable VersionStatus status, @ParameterObject @Valid StatusPageRequest request,
			@ParameterObject @SortableBy(DashboardSortField.class) Pageable pageable,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		var page = this.service.statusPage(caller.permissions(), caller.user(), status,
				ContentRequestMapper.toStatusFilter(request), pageable);
		return DashboardResponseMapper.toStatusPageResponse(page);
	}

	/**
	 * Powers a status page's author filter, which cannot be derived from one loaded page.
	 */
	@GetMapping(ApiPaths.DASHBOARD_STATUS_AUTHORS)
	List<UserResponse> statusAuthors(@PathVariable VersionStatus status,
			@AuthenticationPrincipal AuthenticatedCaller caller) {
		return this.service.statusAuthors(caller.permissions(), status)
			.stream()
			.map(ContentResponseMapper::toUserResponse)
			.toList();
	}

}
