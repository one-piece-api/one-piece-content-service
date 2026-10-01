package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig}: every read takes {@code content:read}, the
 * caller's own permissions are what the service decides visibility from, and what is not
 * found or not valid comes back with its status and error code.
 */
@WebMvcTest(DevilFruitTypeController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class })
class DevilFruitTypeControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("7b0e6c1a-3f52-4d0c-9a52-1f2f3d4e5a6b");

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final User ZORO = new User(UUID.fromString("22222222-2222-2222-2222-222222222222"), "zoro",
			"zoro@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevilFruitTypeService service;

	@Test
	void theListReturnsOnePageOfRows() throws Exception {
		var row = new ContentSummary<>(CONTENT_ID, version(2, VersionStatus.IN_REVIEW), 1);
		when(this.service.list(any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 41));

		this.mockMvc.perform(get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.page").value(0))
			.andExpect(jsonPath("$.size").value(20))
			.andExpect(jsonPath("$.totalElements").value(41))
			.andExpect(jsonPath("$.totalPages").value(3))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].versionNumber").value(2))
			.andExpect(jsonPath("$.content[0].status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.content[0].body.romaji").value("Zoan"))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Zoo Zoo"))
			.andExpect(jsonPath("$.content[0].body.names.en").doesNotExist())
			.andExpect(jsonPath("$.content[0].author.username").value("nami"))
			.andExpect(jsonPath("$.content[0].author.email").value("nami@onepiece.local"))
			.andExpect(jsonPath("$.content[0].updatedAt").value("2026-10-01T09:30:00Z"))
			.andExpect(jsonPath("$.content[0].onlineVersionNumber").value(1));
	}

	@Test
	void theListPassesTheCallersPermissionsFiltersAndPageToTheService() throws Exception {
		when(this.service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		var request = get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW))
			.param("status", "IN_REVIEW")
			.param("q", "zoan")
			.param("author", NAMI.id().toString())
			.param("updatedWithinDays", "7")
			.param("page", "2")
			.param("size", "5")
			.param("sort", "romaji,asc");
		this.mockMvc.perform(request).andExpect(status().isOk());

		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(eq(Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)),
				eq(new ContentFilter(VersionStatus.IN_REVIEW, "zoan", NAMI.id(), 7)), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().getOrderFor("romaji")).isNotNull();
	}

	@Test
	void thePageSizeHasADefaultAndIsCapped() throws Exception {
		when(this.service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
		var pageable = ArgumentCaptor.forClass(Pageable.class);

		this.mockMvc.perform(get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ)));
		this.mockMvc.perform(get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ)).param("size", "5000"));

		verify(this.service, times(2)).list(any(), any(), pageable.capture());
		assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(20, 100);
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource(delimiter = '|', textBlock = """
			/devil-fruit-types?updatedWithinDays=-1 | updatedWithinDays | must be greater than or equal to 0
			/devil-fruit-types?status=NOPE          | status            | invalid value
			/devil-fruit-types?author=not-a-uuid    | author            | invalid value
			/devil-fruit-types?sort=authorEmail     | pageable          | can only be sorted by [romaji, updatedAt]
			/devil-fruit-types/not-a-uuid           | id                | invalid value
			/devil-fruit-types/not-a-uuid/versions/1| id                | invalid value
			/devil-fruit-types/7b0e6c1a-3f52-4d0c-9a52-1f2f3d4e5a6b/versions/two | number | invalid value
			""")
	void aRequestOfTheWrongShapeIsRefusedNamingTheField(String uri, String field, String message) throws Exception {
		this.mockMvc.perform(get(uri).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andExpect(jsonPath("$.errors[0].field").value(field))
			.andExpect(jsonPath("$.errors[0].message").value(message));
		verifyNoInteractions(this.service);
	}

	@Test
	void theListCanBeSortedByEitherSortableFieldInAnyDirection() throws Exception {
		when(this.service.list(any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		var request = get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ))
			.param("sort", "updatedAt,desc")
			.param("sort", "romaji,asc");
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void theAuthorsAreListedForTheFilter() throws Exception {
		when(this.service.authors(Set.of(Permission.CONTENT_READ))).thenReturn(List.of(NAMI, ZORO));

		this.mockMvc.perform(get("/devil-fruit-types/authors").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].id").value(NAMI.id().toString()))
			.andExpect(jsonPath("$[1].email").value("zoro@onepiece.local"));
	}

	@Test
	void aContentComesWithItsVersionChain() throws Exception {
		var content = new Content<>(CONTENT_ID,
				List.of(version(1, VersionStatus.PUBLISHED), version(2, VersionStatus.IN_REVIEW)));
		when(this.service.get(Set.of(Permission.CONTENT_READ), CONTENT_ID)).thenReturn(content);

		this.mockMvc.perform(get("/devil-fruit-types/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.onlineVersionNumber").value(1))
			.andExpect(jsonPath("$.versions[0].number").value(1))
			.andExpect(jsonPath("$.versions[0].everPublished").value(true))
			.andExpect(jsonPath("$.versions[0].basedOn").doesNotExist())
			.andExpect(jsonPath("$.versions[1].status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.versions[1].everPublished").value(false))
			.andExpect(jsonPath("$.versions[1].basedOn").value(1))
			.andExpect(jsonPath("$.versions[1].claimant.email").value("zoro@onepiece.local"))
			.andExpect(jsonPath("$.versions[1].createdAt").value("2026-09-30T08:00:00Z"))
			.andExpect(jsonPath("$.versions[1].body").doesNotExist());
	}

	@Test
	void aContentTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), eq(CONTENT_ID))).thenThrow(new DevilFruitTypeNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/devil-fruit-types/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_DEVIL_FRUIT_TYPE_NOT_FOUND"));
	}

	@Test
	void aVersionComesWithItsContent() throws Exception {
		when(this.service.getVersion(Set.of(Permission.CONTENT_READ), CONTENT_ID, 2))
			.thenReturn(version(2, VersionStatus.IN_REVIEW));

		this.mockMvc
			.perform(get("/devil-fruit-types/" + CONTENT_ID + "/versions/2").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").value(2))
			.andExpect(jsonPath("$.body.romaji").value("Zoan"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Zoo Zoo"))
			.andExpect(jsonPath("$.body.translations.it.description").value("Trasforma in animale"))
			.andExpect(jsonPath("$.body.translations.en.name").doesNotExist())
			.andExpect(jsonPath("$.body.translations.en.description").value("Turns into an animal"));
	}

	@Test
	void aVersionTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.getVersion(any(), eq(CONTENT_ID), eq(3)))
			.thenThrow(new VersionNotFoundException(CONTENT_ID, 3));

		this.mockMvc
			.perform(get("/devil-fruit-types/" + CONTENT_ID + "/versions/3").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_NOT_FOUND"));
	}

	@Test
	void theEventsOfAVersionAreItsTimeline() throws Exception {
		var rejected = new VersionEvent("VERSION_REJECTED", ZORO, "Too short", UPDATED);
		when(this.service.events(Set.of(Permission.CONTENT_READ), CONTENT_ID, 2)).thenReturn(List.of(rejected));

		var request = get("/devil-fruit-types/" + CONTENT_ID + "/versions/2/events")
			.with(callerWith(Permission.CONTENT_READ));
		this.mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$[0].action").value("VERSION_REJECTED"))
			.andExpect(jsonPath("$[0].actor.email").value("zoro@onepiece.local"))
			.andExpect(jsonPath("$[0].detail").value("Too short"))
			.andExpect(jsonPath("$[0].occurredAt").value("2026-10-01T09:30:00Z"));
	}

	@Test
	void everyReadIsForbiddenWithoutContentRead() throws Exception {
		for (var path : readPaths()) {
			// content:write alone grants no read: each permission is checked on its own.
			this.mockMvc.perform(get(path).with(callerWith(Permission.CONTENT_WRITE)))
				.andExpect(status().isForbidden());
		}
	}

	@Test
	void everyReadIsUnauthorizedWithoutAToken() throws Exception {
		for (var path : readPaths()) {
			this.mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
		}
	}

	private static List<String> readPaths() {
		var content = "/devil-fruit-types/" + CONTENT_ID;
		return List.of("/devil-fruit-types", "/devil-fruit-types/authors", content, content + "/versions/1",
				content + "/versions/1/events");
	}

	/** Version 1 is the first one; any later one is based on it and held by zoro. */
	private static Version<DevilFruitType> version(int number, VersionStatus status) {
		var translations = Map.of("it", new DevilFruitTypeTranslation("Zoo Zoo", "Trasforma in animale"), "en",
				new DevilFruitTypeTranslation(null, "Turns into an animal"));
		return Version.<DevilFruitType>builder()
			.number(number)
			.basedOn(number == 1 ? null : 1)
			.status(status)
			.author(NAMI)
			.claimant(number == 1 ? null : ZORO)
			.body(new DevilFruitType("Zoan", translations))
			.createdAt(CREATED)
			.updatedAt(UPDATED)
			.build();
	}

}
