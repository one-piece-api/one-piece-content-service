package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.service.DashboardService;
import dev.onepieceapi.contentservice.web.ApiPaths;
import dev.onepieceapi.contentservice.web.dto.response.ActivityResponse;
import dev.onepieceapi.contentservice.web.dto.response.DashboardResponse;
import dev.onepieceapi.contentservice.web.mapper.DashboardResponseMapper;
import dev.onepieceapi.contentservice.web.security.AuthenticatedCaller;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The dashboard (UF-CNT-19), across entity types: a counter per status the caller sees,
 * and the caller's own latest actions. Both take {@code content:read} (see
 * {@code SecuredEndpoint}); what they count and list is limited to what the caller may
 * see.
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

}
