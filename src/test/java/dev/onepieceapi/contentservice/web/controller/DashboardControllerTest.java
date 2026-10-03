package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.dashboard.Activity;
import dev.onepieceapi.contentservice.domain.dashboard.ContentTitle;
import dev.onepieceapi.contentservice.domain.dashboard.StatusCount;
import dev.onepieceapi.contentservice.domain.dashboard.StatusRow;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.DashboardService;
import dev.onepieceapi.contentservice.service.StatusPage;
import dev.onepieceapi.contentservice.service.exception.StatusNotFoundException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The dashboard's API: both reads take {@code content:read}, and answer for the caller of
 * the request - the real {@link SecurityConfig} is imported to prove it.
 */
@WebMvcTest(DashboardController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class DashboardControllerTest {

	private static final UUID CONTENT = UUID.fromString("3f2a9c1b-0000-4000-8000-000000000001");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DashboardService service;

	@Test
	void aReaderGetsOneCounterPerStatusWithTheirShareOrNull() throws Exception {
		when(this.service.statusCounts(any(), any())).thenReturn(List.of(new StatusCount(VersionStatus.DRAFT, 3, 1L),
				new StatusCount(VersionStatus.PUBLISHED, 5, null)));

		this.mockMvc.perform(get("/dashboard").with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.statuses[0].status").value("DRAFT"))
			.andExpect(jsonPath("$.statuses[0].count").value(3))
			.andExpect(jsonPath("$.statuses[0].mine").value(1))
			.andExpect(jsonPath("$.statuses[1].status").value("PUBLISHED"))
			.andExpect(jsonPath("$.statuses[1].mine").doesNotExist());
	}

	@Test
	void theCountersAreForTheCallerAndTheirPermissions() throws Exception {
		when(this.service.statusCounts(any(), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/dashboard").with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)))
			.andExpect(status().isOk());

		verify(this.service).statusCounts(argThat(permissions -> permissions.contains(Permission.CONTENT_REVIEW)),
				argThat(user -> TestCallers.USERNAME.equals(user.username())));
	}

	@Test
	void aReaderGetsTheirActivityWithTheContentsTitle() throws Exception {
		var published = Activity.builder()
			.action("VERSION_PUBLISHED")
			.occurredAt(Instant.parse("2026-10-03T10:00:00Z"))
			.entityType(EntityType.DEVIL_FRUIT_TYPE)
			.contentId(CONTENT)
			.versionNumber(2)
			.label("Logia")
			.title(new ContentTitle(Map.of("it", "Rogia"), "Logia"))
			.build();
		var discarded = Activity.builder()
			.action("VERSION_DELETED")
			.occurredAt(Instant.parse("2026-10-03T09:00:00Z"))
			.contentId(UUID.randomUUID())
			.label("Paramecia")
			.build();
		when(this.service.activity(any(), any())).thenReturn(List.of(published, discarded));

		this.mockMvc.perform(get("/dashboard/activity").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].action").value("VERSION_PUBLISHED"))
			.andExpect(jsonPath("$[0].entityType").value("DEVIL_FRUIT_TYPE"))
			.andExpect(jsonPath("$[0].contentId").value(CONTENT.toString()))
			.andExpect(jsonPath("$[0].versionNumber").value(2))
			.andExpect(jsonPath("$[0].title.names.it").value("Rogia"))
			.andExpect(jsonPath("$[0].title.fallback").value("Logia"))
			.andExpect(jsonPath("$[1].label").value("Paramecia"))
			.andExpect(jsonPath("$[1].title").doesNotExist())
			.andExpect(jsonPath("$[1].versionNumber").doesNotExist());
	}

	@Test
	void aStatusPageAnswersItsRowsAndTheCountersOfItsScope() throws Exception {
		var version = Version.<ContentTitle>builder()
			.number(3)
			.status(VersionStatus.IN_REVIEW)
			.author(new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local"))
			.claimant(new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local"))
			.body(new ContentTitle(Map.of("it", "Rogia"), "Logia"))
			.updatedAt(Instant.parse("2026-10-03T10:00:00Z"))
			.build();
		var row = new StatusRow(EntityType.DEVIL_FRUIT_TYPE, CONTENT, version, 2,
				Set.of(VersionAction.APPROVE, VersionAction.RELEASE), Set.of());
		when(this.service.statusPage(any(), any(), eq(VersionStatus.IN_REVIEW), any(), any()))
			.thenReturn(new StatusPage(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1), 2, 1L));

		this.mockMvc
			.perform(get("/dashboard/statuses/IN_REVIEW").param("mine", "true")
				.with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.all").value(2))
			.andExpect(jsonPath("$.mine").value(1))
			.andExpect(jsonPath("$.rows.totalElements").value(1))
			.andExpect(jsonPath("$.rows.content[0].entityType").value("DEVIL_FRUIT_TYPE"))
			.andExpect(jsonPath("$.rows.content[0].versionNumber").value(3))
			.andExpect(jsonPath("$.rows.content[0].title.names.it").value("Rogia"))
			.andExpect(jsonPath("$.rows.content[0].claimant.username").value("zoro"))
			.andExpect(jsonPath("$.rows.content[0].onlineVersionNumber").value(2))
			.andExpect(jsonPath("$.rows.content[0].allowedActions.length()").value(2));
		verify(this.service).statusPage(any(), any(), eq(VersionStatus.IN_REVIEW),
				argThat(filter -> filter.mine() && filter.author() == null), any());
	}

	@Test
	void aStatusPageTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.statusPage(any(), any(), any(), any(), any()))
			.thenThrow(new StatusNotFoundException(VersionStatus.DRAFT));

		this.mockMvc.perform(get("/dashboard/statuses/DRAFT").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_STATUS_NOT_FOUND"));
	}

	@Test
	void aStatusPageIsSortedOnlyByTheLastUpdate() throws Exception {
		this.mockMvc
			.perform(get("/dashboard/statuses/PUBLISHED").param("sort", "romaji,asc")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void theAuthorsOfAStatusAreListed() throws Exception {
		when(this.service.statusAuthors(any(), eq(VersionStatus.ARCHIVED)))
			.thenReturn(List.of(new User(UUID.randomUUID(), "nami", "nami@onepiece.local")));

		this.mockMvc.perform(get("/dashboard/statuses/ARCHIVED/authors").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].username").value("nami"));
	}

	@Test
	void aCallerWithoutContentReadIsForbidden() throws Exception {
		this.mockMvc.perform(get("/dashboard").with(callerWith(Permission.LANGUAGES_MANAGE)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/dashboard/activity").with(callerWith(Permission.LANGUAGES_MANAGE)))
			.andExpect(status().isForbidden());
	}

	@Test
	void anUnauthenticatedCallerIsUnauthorized() throws Exception {
		this.mockMvc.perform(get("/dashboard")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/dashboard/activity")).andExpect(status().isUnauthorized());
	}

}
