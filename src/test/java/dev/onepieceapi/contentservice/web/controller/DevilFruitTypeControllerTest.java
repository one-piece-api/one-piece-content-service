package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitType;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentListSummary;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionEvent;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.service.DevilFruitTypeService;
import dev.onepieceapi.contentservice.service.exception.DevilFruitTypeNotFoundException;
import dev.onepieceapi.contentservice.service.exception.TranslationLanguageUnknownException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionConflictException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.exception.VersionIdenticalException;
import dev.onepieceapi.contentservice.service.exception.VersionIncompleteException;
import dev.onepieceapi.contentservice.service.exception.VersionNotFoundException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.exception.web.ConcurrentModificationExceptionHandler;
import dev.onepieceapi.exception.web.FieldViolation;
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
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Imports the real {@link SecurityConfig}: every read takes {@code content:read} and
 * every write {@code content:write}, the caller's own permissions are what the service
 * decides from, and what is not found, not allowed or not valid comes back with its
 * status and error code.
 */
@WebMvcTest(DevilFruitTypeController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
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
		var row = new ContentSummary<>(CONTENT_ID, version(2, VersionStatus.IN_REVIEW), 1, Set.of());
		when(this.service.list(any(), any(), any(), any()))
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
		when(this.service.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		var request = get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW))
			.param("status", "IN_REVIEW")
			.param("q", "zoan")
			.param("author", "nami")
			.param("updatedWithinDays", "7")
			.param("page", "2")
			.param("size", "5")
			.param("sort", "romaji,asc");
		this.mockMvc.perform(request).andExpect(status().isOk());

		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(eq(Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)), any(),
				eq(new ContentFilter(VersionStatus.IN_REVIEW, "zoan", "nami", 7)), pageable.capture());
		assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
		assertThat(pageable.getValue().getPageSize()).isEqualTo(5);
		assertThat(pageable.getValue().getSort().getOrderFor("romaji")).isNotNull();
	}

	@Test
	void thePageSizeHasADefaultAndIsCapped() throws Exception {
		when(this.service.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));
		var pageable = ArgumentCaptor.forClass(Pageable.class);

		this.mockMvc.perform(get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ)));
		this.mockMvc.perform(get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ)).param("size", "5000"));

		verify(this.service, times(2)).list(any(), any(), any(), pageable.capture());
		assertThat(pageable.getAllValues()).extracting(Pageable::getPageSize).containsExactly(20, 100);
	}

	@ParameterizedTest(name = "{0} -> {1}: {2}")
	@CsvSource(delimiter = '|', textBlock = """
			/devil-fruit-types?updatedWithinDays=-1 | updatedWithinDays | must be greater than or equal to 0
			/devil-fruit-types?status=NOPE          | status            | invalid value
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
		when(this.service.list(any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		var request = get("/devil-fruit-types").with(callerWith(Permission.CONTENT_READ))
			.param("sort", "updatedAt,desc")
			.param("sort", "romaji,asc");
		this.mockMvc.perform(request).andExpect(status().isOk());
	}

	@Test
	void theSummaryGivesTheTotalsAndTheStatusesTheCallerMayFilterBy() throws Exception {
		var summary = new ContentListSummary(20, 3, EnumSet.of(VersionStatus.IN_REVIEW, VersionStatus.PUBLISHED));
		when(this.service.summary(eq(Set.of(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)), any()))
			.thenReturn(summary);

		var request = get("/devil-fruit-types/summary")
			.with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW));
		this.mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(20))
			.andExpect(jsonPath("$.mine").value(3))
			.andExpect(jsonPath("$.statuses[0]").value("IN_REVIEW"))
			.andExpect(jsonPath("$.statuses[1]").value("PUBLISHED"))
			.andExpect(jsonPath("$.statuses.length()").value(2));
	}

	@Test
	void theSummaryIsComputedForTheCallerItself() throws Exception {
		when(this.service.summary(any(), any())).thenReturn(new ContentListSummary(0, 0, Set.of()));
		var caller = ArgumentCaptor.forClass(User.class);

		this.mockMvc.perform(get("/devil-fruit-types/summary").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		verify(this.service).summary(any(), caller.capture());
		assertThat(caller.getValue().username()).isEqualTo("crewmate");
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
				List.of(readOnly(version(1, VersionStatus.PUBLISHED)), readOnly(version(2, VersionStatus.IN_REVIEW))));
		when(this.service.get(eq(Set.of(Permission.CONTENT_READ)), any(), eq(CONTENT_ID))).thenReturn(content);

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
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new DevilFruitTypeNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/devil-fruit-types/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_DEVIL_FRUIT_TYPE_NOT_FOUND"));
	}

	@Test
	void aVersionComesWithItsContent() throws Exception {
		var access = new VersionAccess<>(version(2, VersionStatus.IN_REVIEW), EnumSet.of(VersionAction.PULL_BACK));
		when(this.service.getVersion(eq(Set.of(Permission.CONTENT_READ)), any(), eq(CONTENT_ID), eq(2)))
			.thenReturn(access);

		this.mockMvc
			.perform(get("/devil-fruit-types/" + CONTENT_ID + "/versions/2").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").value(2))
			.andExpect(jsonPath("$.allowedActions.length()").value(1))
			.andExpect(jsonPath("$.allowedActions[0]").value("PULL_BACK"))
			.andExpect(jsonPath("$.body.romaji").value("Zoan"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Zoo Zoo"))
			.andExpect(jsonPath("$.body.translations.it.description").value("Trasforma in animale"))
			.andExpect(jsonPath("$.body.translations.en.name").doesNotExist())
			.andExpect(jsonPath("$.body.translations.en.description").value("Turns into an animal"));
	}

	@Test
	void aVersionTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(3)))
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

	@Test
	void creatingAContentAnswersWithItAndPassesOnWhatWasWrittenAsTyped() throws Exception {
		var draft = new VersionAccess<>(version(1, VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		var request = post("/devil-fruit-types").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\" Zoan \",\"translations\":{\"it\":{\"name\":\"Zoo Zoo\"}}}");
		this.mockMvc.perform(request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.versions[0].number").value(1))
			.andExpect(jsonPath("$.versions[0].allowedActions[0]").value("EDIT"));

		var written = ArgumentCaptor.forClass(DevilFruitType.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().romaji()).isEqualTo(" Zoan ");
		assertThat(written.getValue().translations().get("it"))
			.isEqualTo(new DevilFruitTypeTranslation("Zoo Zoo", null));
	}

	@Test
	void editingADraftAnswersWithTheVersionAsItNowIs() throws Exception {
		var draft = new VersionAccess<>(version(1, VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any())).thenReturn(draft);

		this.mockMvc.perform(edit("{\"romaji\":\"Zoan\"}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.number").value(1))
			.andExpect(jsonPath("$.body.romaji").value("Zoan"))
			.andExpect(jsonPath("$.allowedActions[0]").value("EDIT"));
	}

	@Test
	void aDraftMayBeSavedWithNothingInIt() throws Exception {
		var draft = new VersionAccess<>(version(1, VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	@Test
	void writingIsForbiddenWithoutContentWrite() throws Exception {
		var readOnly = callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);
		var create = post("/devil-fruit-types").contentType("application/json").content("{}");

		this.mockMvc.perform(create.with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(edit("{}").with(readOnly)).andExpect(status().isForbidden());
		verifyNoInteractions(this.service);
	}

	@Test
	void writingIsUnauthorizedWithoutAToken() throws Exception {
		var create = post("/devil-fruit-types").contentType("application/json").content("{}");

		this.mockMvc.perform(create).andExpect(status().isUnauthorized());
		this.mockMvc.perform(edit("{}")).andExpect(status().isUnauthorized());
	}

	@Test
	void aDraftTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.edit(any(), any(), any(), eq(1), any()))
			.thenThrow(new VersionNotFoundException(CONTENT_ID, 1));

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_NOT_FOUND"));
	}

	@Test
	void aDraftOfSomeoneElseIsForbidden() throws Exception {
		when(this.service.edit(any(), any(), any(), eq(1), any()))
			.thenThrow(new VersionActionForbiddenException(VersionAction.EDIT));

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_FORBIDDEN"));
	}

	@Test
	void aVersionThatIsNoLongerADraftIsAConflict() throws Exception {
		when(this.service.edit(any(), any(), any(), eq(1), any()))
			.thenThrow(new VersionActionConflictException(VersionAction.EDIT));

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_CONFLICT"));
	}

	@Test
	void aValueAlreadyUsedIsUnprocessableAndNamesItsFields() throws Exception {
		var taken = List.of(new FieldViolation("romaji", "is already used by another content"),
				new FieldViolation("translations[it].name", "is already used by another content"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		var request = post("/devil-fruit-types").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\"Zoan\"}");
		this.mockMvc.perform(request)
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("romaji"))
			.andExpect(jsonPath("$.errors[1].field").value("translations[it].name"));
	}

	@Test
	void aLanguageOutsideTheCatalogIsUnprocessable() throws Exception {
		when(this.service.edit(any(), any(), any(), eq(1), any()))
			.thenThrow(new TranslationLanguageUnknownException(List.of("fr")));

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_TRANSLATION_LANGUAGE_UNKNOWN"))
			.andExpect(jsonPath("$.languages[0]").value("fr"));
	}

	@Test
	void aTextLongerThanItsLimitIsABadRequestNamingItsField() throws Exception {
		var tooLong = "x".repeat(101);
		var body = "{\"romaji\":\"%s\",\"translations\":{\"it\":{\"name\":\"%s\",\"description\":\"%s\"}}}"
			.formatted(tooLong, tooLong, "x".repeat(2001));

		var result = this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains("\"romaji\"", "translations[it].name",
				"translations[it].description");
		verifyNoInteractions(this.service);
	}

	@Test
	void aTextAsLongAsItsLimitIsAccepted() throws Exception {
		var draft = new VersionAccess<>(version(1, VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);
		var body = "{\"romaji\":\"%s\",\"translations\":{\"it\":{\"name\":\"%s\",\"description\":\"%s\"}}}"
			.formatted("x".repeat(100), "x".repeat(100), "x".repeat(2000));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	@Test
	void discardingADraftAnswersWithNoContent() throws Exception {
		this.mockMvc.perform(discard().with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isNoContent());

		verify(this.service).delete(eq(Set.of(Permission.CONTENT_WRITE)), any(), eq(CONTENT_ID), eq(1));
	}

	@Test
	void discardingIsForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken() throws Exception {
		this.mockMvc.perform(discard().with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(discard()).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@ParameterizedTest
	@CsvSource({ "NOT_FOUND, 404, CONTENT_VERSION_NOT_FOUND", "FORBIDDEN, 403, CONTENT_VERSION_ACTION_FORBIDDEN",
			"CONFLICT, 409, CONTENT_VERSION_ACTION_CONFLICT" })
	void aRefusedDiscardAnswersWithItsStatusAndCode(String refusal, int status, String errorCode) throws Exception {
		RuntimeException refused = switch (refusal) {
			case "NOT_FOUND" -> new VersionNotFoundException(CONTENT_ID, 1);
			case "FORBIDDEN" -> new VersionActionForbiddenException(VersionAction.DELETE);
			default -> new VersionActionConflictException(VersionAction.DELETE);
		};
		doThrow(refused).when(this.service).delete(any(), any(), any(), eq(1));

		this.mockMvc.perform(discard().with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	@Test
	void submittingAnswersWithTheVersionNowInReview() throws Exception {
		var submitted = new VersionAccess<>(version(1, VersionStatus.IN_REVIEW), EnumSet.of(VersionAction.PULL_BACK));
		when(this.service.submit(eq(Set.of(Permission.CONTENT_WRITE)), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(submitted);

		this.mockMvc.perform(workflow("submit").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_REVIEW"))
			.andExpect(jsonPath("$.allowedActions[0]").value("PULL_BACK"));
	}

	@Test
	void pullingBackAnswersWithTheVersionNowADraft() throws Exception {
		var draft = new VersionAccess<>(version(1, VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.pullBack(eq(Set.of(Permission.CONTENT_WRITE)), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(draft);

		this.mockMvc.perform(workflow("pull-back").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"));
	}

	@ParameterizedTest
	@CsvSource({ "submit", "pull-back" })
	void submittingAndPullingBackAreForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken(String action)
			throws Exception {
		this.mockMvc.perform(workflow(action).with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(workflow(action)).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@ParameterizedTest
	@CsvSource({ "NOT_FOUND, 404, CONTENT_VERSION_NOT_FOUND", "FORBIDDEN, 403, CONTENT_VERSION_ACTION_FORBIDDEN",
			"CONFLICT, 409, CONTENT_VERSION_ACTION_CONFLICT" })
	void aRefusedPullBackAnswersWithItsStatusAndCode(String refusal, int status, String errorCode) throws Exception {
		RuntimeException refused = switch (refusal) {
			case "NOT_FOUND" -> new VersionNotFoundException(CONTENT_ID, 1);
			case "FORBIDDEN" -> new VersionActionForbiddenException(VersionAction.PULL_BACK);
			default -> new VersionActionConflictException(VersionAction.PULL_BACK);
		};
		when(this.service.pullBack(any(), any(), any(), eq(1))).thenThrow(refused);

		this.mockMvc.perform(workflow("pull-back").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	@Test
	void claimingAnswersWithTheVersionHeldByTheCaller() throws Exception {
		var held = new VersionAccess<>(version(2, VersionStatus.IN_REVIEW), EnumSet.of(VersionAction.RELEASE));
		when(this.service.claim(eq(Set.of(Permission.CONTENT_REVIEW)), any(), eq(CONTENT_ID), eq(1))).thenReturn(held);

		this.mockMvc.perform(workflow("claim").with(callerWith(Permission.CONTENT_REVIEW)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claimant.username").value("zoro"))
			.andExpect(jsonPath("$.allowedActions[0]").value("RELEASE"));
	}

	@Test
	void releasingAnswersWithTheVersionUnclaimed() throws Exception {
		var free = new VersionAccess<>(version(1, VersionStatus.IN_REVIEW), EnumSet.of(VersionAction.CLAIM));
		when(this.service.release(eq(Set.of(Permission.CONTENT_REVIEW)), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(free);

		this.mockMvc.perform(workflow("release").with(callerWith(Permission.CONTENT_REVIEW)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.claimant").doesNotExist())
			.andExpect(jsonPath("$.allowedActions[0]").value("CLAIM"));
	}

	@ParameterizedTest
	@CsvSource({ "claim", "release" })
	void claimingAndReleasingAreForbiddenWithoutContentReviewAndUnauthorizedWithoutAToken(String action)
			throws Exception {
		this.mockMvc.perform(workflow(action).with(callerWith(Permission.CONTENT_READ, Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(workflow(action)).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@ParameterizedTest
	@CsvSource({ "NOT_FOUND, 404, CONTENT_VERSION_NOT_FOUND", "FORBIDDEN, 403, CONTENT_VERSION_ACTION_FORBIDDEN",
			"CONFLICT, 409, CONTENT_VERSION_ACTION_CONFLICT", "CONCURRENT, 409, CONCURRENT_MODIFICATION" })
	void aRefusedClaimAnswersWithItsStatusAndCode(String refusal, int status, String errorCode) throws Exception {
		RuntimeException refused = switch (refusal) {
			case "NOT_FOUND" -> new VersionNotFoundException(CONTENT_ID, 1);
			case "FORBIDDEN" -> new VersionActionForbiddenException(VersionAction.CLAIM);
			case "CONFLICT" -> new VersionActionConflictException(VersionAction.CLAIM);
			default -> new ObjectOptimisticLockingFailureException(ContentVersionEntity.class, CONTENT_ID);
		};
		when(this.service.claim(any(), any(), any(), eq(1))).thenThrow(refused);

		this.mockMvc.perform(workflow("claim").with(callerWith(Permission.CONTENT_REVIEW)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	@ParameterizedTest
	@CsvSource({ "NOT_FOUND, 404, CONTENT_VERSION_NOT_FOUND", "FORBIDDEN, 403, CONTENT_VERSION_ACTION_FORBIDDEN" })
	void aRefusedReleaseAnswersWithItsStatusAndCode(String refusal, int status, String errorCode) throws Exception {
		RuntimeException refused = "NOT_FOUND".equals(refusal) ? new VersionNotFoundException(CONTENT_ID, 1)
				: new VersionActionForbiddenException(VersionAction.RELEASE);
		when(this.service.release(any(), any(), any(), eq(1))).thenThrow(refused);

		this.mockMvc.perform(workflow("release").with(callerWith(Permission.CONTENT_REVIEW)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	@ParameterizedTest
	@CsvSource({ "NOT_FOUND, 404, CONTENT_VERSION_NOT_FOUND", "FORBIDDEN, 403, CONTENT_VERSION_ACTION_FORBIDDEN",
			"CONFLICT, 409, CONTENT_VERSION_ACTION_CONFLICT" })
	void aRefusedSubmissionAnswersWithItsStatusAndCode(String refusal, int status, String errorCode) throws Exception {
		RuntimeException refused = switch (refusal) {
			case "NOT_FOUND" -> new VersionNotFoundException(CONTENT_ID, 1);
			case "FORBIDDEN" -> new VersionActionForbiddenException(VersionAction.SUBMIT);
			default -> new VersionActionConflictException(VersionAction.SUBMIT);
		};
		when(this.service.submit(any(), any(), any(), eq(1))).thenThrow(refused);

		this.mockMvc.perform(workflow("submit").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().is(status))
			.andExpect(jsonPath("$.errorCode").value(errorCode));
	}

	@Test
	void anIncompleteVersionIsUnprocessableAndNamesTheMissingFields() throws Exception {
		var missing = List.of(new FieldViolation("romaji", "is required for review"),
				new FieldViolation("translations[en].name", "is required for review"));
		when(this.service.submit(any(), any(), any(), eq(1))).thenThrow(new VersionIncompleteException(missing));

		this.mockMvc.perform(workflow("submit").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_INCOMPLETE"))
			.andExpect(jsonPath("$.errors[0].field").value("romaji"))
			.andExpect(jsonPath("$.errors[1].field").value("translations[en].name"));
	}

	@Test
	void aVersionIdenticalToAnotherIsUnprocessableAndNamesIt() throws Exception {
		when(this.service.submit(any(), any(), any(), eq(2))).thenThrow(new VersionIdenticalException(1));

		var request = post("/devil-fruit-types/" + CONTENT_ID + "/versions/2/submit");
		this.mockMvc.perform(request.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableEntity())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_IDENTICAL"))
			.andExpect(jsonPath("$.identicalTo").value(1));
	}

	/** A workflow action on version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder workflow(String action) {
		return post("/devil-fruit-types/" + CONTENT_ID + "/versions/1/" + action);
	}

	private static MockHttpServletRequestBuilder discard() {
		return delete("/devil-fruit-types/" + CONTENT_ID + "/versions/1");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/devil-fruit-types/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static List<String> readPaths() {
		var content = "/devil-fruit-types/" + CONTENT_ID;
		return List.of("/devil-fruit-types", "/devil-fruit-types/authors", "/devil-fruit-types/summary", content,
				content + "/versions/1", content + "/versions/1/events");
	}

	/** Version 1 is the first one; any later one is based on it and held by zoro. */
	private static VersionAccess<DevilFruitType> readOnly(Version<DevilFruitType> version) {
		return new VersionAccess<>(version, Set.of());
	}

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
