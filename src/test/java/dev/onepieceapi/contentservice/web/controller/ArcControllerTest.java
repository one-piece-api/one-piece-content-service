package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.Arc;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.exception.ArcNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.ArcLinks;
import dev.onepieceapi.contentservice.service.story.ArcService;
import dev.onepieceapi.contentservice.service.story.SagaLinks;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The arc section over HTTP: what the workflow endpoints it shares with every entity are
 * tested by {@link DevilFruitTypeControllerTest}, here what is its own - the saga an arc
 * points to, as it is today, in a version and in a row; the list narrowed to a saga; a
 * saga refused or an action blocked; the limits of what a draft is saved with; and who
 * may write.
 */
@WebMvcTest(ArcController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class ArcControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID SAGA_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final Instant CREATED = Instant.parse("2026-10-13T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final NumberedReference EAST_BLUE = new NumberedReference(SAGA_ID, 1,
			Map.of("it", "East Blue", "en", "East Blue Saga"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private ArcService service;

	@MockitoBean
	private SagaLinks sagas;

	@MockitoBean
	private ArcLinks arcs;

	@Test
	void aVersionSaysItsSagaAsItIsTodayAndWhichActionsAreBlocked() throws Exception {
		var blocked = new BlockedAction(VersionAction.PUBLISH, BlockReason.RELATED_NOT_ONLINE,
				Map.of("sagaId", SAGA_ID, "sagaNumber", 1));
		var access = new VersionAccess<>(version(VersionStatus.READY_TO_PUBLISH, SAGA_ID),
				EnumSet.of(VersionAction.PUBLISH), EnumSet.noneOf(VersionAction.class), List.of(blocked));
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.sagas.referencesFor(anyCollection())).thenReturn(Map.of(SAGA_ID, EAST_BLUE));

		this.mockMvc.perform(get("/arcs/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.number").value(3))
			.andExpect(jsonPath("$.body.saga.id").value(SAGA_ID.toString()))
			.andExpect(jsonPath("$.body.saga.number").value(1))
			.andExpect(jsonPath("$.body.saga.names.it").value("East Blue"))
			.andExpect(jsonPath("$.body.translations.it.title").value("Orange Town"))
			.andExpect(jsonPath("$.body.translations.it.description").value("Il villaggio"))
			.andExpect(jsonPath("$.allowedActions[0]").value("PUBLISH"))
			.andExpect(jsonPath("$.blockedActions[0].reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.blockedActions[0].detail.sagaId").value(SAGA_ID.toString()))
			.andExpect(jsonPath("$.blockedActions[0].detail.sagaNumber").value(1));
	}

	@Test
	void aVersionWithoutASagaSaysNoneAndAskedNoOne() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.sagas.referencesFor(anyCollection())).thenReturn(Map.of());

		this.mockMvc.perform(get("/arcs/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.saga").doesNotExist());
		verify(this.sagas).referencesFor(List.of(access.body()));
	}

	@Test
	void aSagaWithNothingApprovedIsAnsweredByItsIdAlone() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, SAGA_ID), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.sagas.referencesFor(anyCollection())).thenReturn(Map.of());

		this.mockMvc.perform(get("/arcs/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.saga.id").value(SAGA_ID.toString()))
			.andExpect(jsonPath("$.body.saga.number").doesNotExist());
	}

	@Test
	void theListLooksUpTheSagasOfAWholePageAtOnce() throws Exception {
		var other = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, SAGA_ID), 1, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, other), null, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, SAGA_ID), null, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 3));
		when(this.sagas.referencesFor(anyCollection())).thenReturn(Map.of(SAGA_ID, EAST_BLUE));

		this.mockMvc.perform(get("/arcs").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].body.number").value(3))
			.andExpect(jsonPath("$.content[0].body.names.it").value("Orange Town"))
			.andExpect(jsonPath("$.content[0].body.saga.number").value(1))
			.andExpect(jsonPath("$.content[1].body.saga.id").value(other.toString()))
			.andExpect(jsonPath("$.content[1].body.saga.number").doesNotExist())
			.andExpect(jsonPath("$.content[2].body.saga.names.en").value("East Blue Saga"));

		var asked = ArgumentCaptor.forClass(Collection.class);
		verify(this.sagas).referencesFor(asked.capture());
		assertThat(((Collection<Arc>) asked.getValue()).stream().map(Arc::sagaContentId)).containsExactly(SAGA_ID,
				other, SAGA_ID);
	}

	@Test
	void theListIsNarrowedToASagaByItsIdAndCanBeSortedByNumber() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/arcs").param("saga", SAGA_ID.toString())
				.param("q", "orange")
				.param("sort", "number,asc")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(any(), any(), filter.capture(), pageable.capture(), any());
		assertThat(filter.getValue().related()).containsExactly(Map.entry("saga", SAGA_ID));
		assertThat(filter.getValue().query()).isEqualTo("orange");
		assertThat(pageable.getValue().getSort().getOrderFor("number")).isNotNull();
	}

	@Test
	void aSagaThatIsNotAnIdIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/arcs").param("saga", "east-blue").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
		verifyNoInteractions(this.service);
	}

	@Test
	void anArcTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new ArcNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/arcs/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_ARC_NOT_FOUND"));
	}

	@Test
	void creatingAnArcPassesOnTheSagaAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, SAGA_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/arcs").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":3,\"saga\":\"" + SAGA_ID
						+ "\",\"translations\":{\"it\":{\"title\":\"Orange Town\"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Arc.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().number()).isEqualTo(3);
		assertThat(written.getValue().sagaContentId()).isEqualTo(SAGA_ID);
		assertThat(written.getValue().translations().get("it")).isEqualTo(new StoryTranslation("Orange Town", null));
	}

	@Test
	void editingAnswersWithTheVersionAndItsSaga() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, SAGA_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any())).thenReturn(access);
		when(this.sagas.referencesFor(anyCollection())).thenReturn(Map.of(SAGA_ID, EAST_BLUE));

		this.mockMvc
			.perform(edit("{\"number\":3,\"saga\":\"" + SAGA_ID + "\"}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.saga.number").value(1));
	}

	@Test
	void writingIsForbiddenWithoutContentWriteAndUnauthorizedWithoutAToken() throws Exception {
		var readOnly = callerWith(Permission.CONTENT_READ, Permission.CONTENT_REVIEW);

		this.mockMvc.perform(create().with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(edit("{}").with(readOnly)).andExpect(status().isForbidden());
		this.mockMvc.perform(create()).andExpect(status().isUnauthorized());
		this.mockMvc.perform(edit("{}")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.service);
	}

	@Test
	void aSagaThatCannotBeLinkedIsUnprocessableAndNamesTheSaga() throws Exception {
		var invalid = List.of(new FieldViolation("saga", "must be a Saga with an approved version"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc
			.perform(post("/arcs").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"saga\":\"" + SAGA_ID + "\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("saga"));
	}

	@Test
	void aNumberOrATitleAlreadyUsedIsUnprocessableAndNamesItsFields() throws Exception {
		var taken = List.of(new FieldViolation("number", "is already used by another content"),
				new FieldViolation("translations[en].title", "is already used by another content"));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc.perform(edit("{\"number\":3}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("number"))
			.andExpect(jsonPath("$.errors[1].field").value("translations[en].title"));
	}

	@Test
	void publishingWhileTheSagaIsNotOnlineIsAConflictNamingTheSaga() throws Exception {
		var block = new ActionBlock(BlockReason.RELATED_NOT_ONLINE, Map.of("sagaId", SAGA_ID, "sagaNumber", 1));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/arcs/" + CONTENT_ID + "/versions/1/publish").with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("RELATED_NOT_ONLINE"))
			.andExpect(jsonPath("$.detail.sagaId").value(SAGA_ID.toString()))
			.andExpect(jsonPath("$.detail.sagaNumber").value(1));
	}

	@ParameterizedTest
	@CsvSource({ "{\"number\":-1}, number",
			"{\"translations\":{\"en\":{\"title\":\"TOO_LONG_TITLE\"}}}, translations[en].title",
			"{\"translations\":{\"en\":{\"description\":\"TOO_LONG_DESCRIPTION\"}}}, translations[en].description" })
	void aValueBeyondItsLimitIsABadRequestNamingItsField(String template, String field) throws Exception {
		var body = template.replace("TOO_LONG_TITLE", "x".repeat(201))
			.replace("TOO_LONG_DESCRIPTION", "x".repeat(2001));

		var result = this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"))
			.andReturn();

		assertThat(result.getResponse().getContentAsString()).contains(field);
		verifyNoInteractions(this.service);
	}

	@Test
	void aTextAsLongAsItsLimitIsAccepted() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, SAGA_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);
		var body = "{\"number\":0,\"translations\":{\"en\":{\"title\":\"%s\",\"description\":\"%s\"}}}"
			.formatted("x".repeat(200), "x".repeat(2000));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	@Test
	void theArcsAChapterMayBeLinkedToAreListedForWhoeverWritesChapters() throws Exception {
		var romanceDawn = new NumberedReference(CONTENT_ID, 1, Map.of("it", "Romance Dawn"));
		when(this.arcs.linkable(any(), anyInt(), anyInt()))
			.thenReturn(new PageImpl<>(List.of(romanceDawn), PageRequest.of(1, 5), 6));

		this.mockMvc
			.perform(get("/arcs/linkable").param("q", "dawn")
				.param("page", "1")
				.param("size", "5")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].number").value(1))
			.andExpect(jsonPath("$.content[0].names.it").value("Romance Dawn"));
		verify(this.arcs).linkable("dawn", 1, 5);
	}

	@Test
	void theLinkableArcsAreFirstPageTwentyByDefaultAndNotForWhoOnlyReads() throws Exception {
		when(this.arcs.linkable(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/arcs/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());
		verify(this.arcs).linkable(null, 0, 20);
		this.mockMvc.perform(get("/arcs/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/arcs/linkable")).andExpect(status().isUnauthorized());
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create() {
		return post("/arcs").contentType("application/json").content("{}");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/arcs/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Version<Arc> version(VersionStatus status, UUID saga) {
		var translations = Map.of("it", new StoryTranslation("Orange Town", "Il villaggio"));
		return Version.<Arc>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new Arc(3, saga, translations))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
