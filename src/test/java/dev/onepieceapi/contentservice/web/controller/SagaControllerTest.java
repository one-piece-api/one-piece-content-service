package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.story.NumberedReference;
import dev.onepieceapi.contentservice.domain.story.Saga;
import dev.onepieceapi.contentservice.domain.story.StoryTranslation;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.exception.SagaNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.SagaLinks;
import dev.onepieceapi.contentservice.service.story.SagaService;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static dev.onepieceapi.contentservice.web.controller.TestCallers.callerWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 * The saga section over HTTP: what the workflow endpoints it shares with every entity are
 * tested by {@link DevilFruitTypeControllerTest}, here what is its own - the number and
 * the titles in a version and in a row, the sagas an arc may be linked to, the limits of
 * what a draft is saved with, and who may write.
 */
@WebMvcTest(SagaController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class SagaControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("7b0e6c1a-3f52-4d0c-9a52-1f2f3d4e5a6b");

	private static final Instant CREATED = Instant.parse("2026-10-13T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private SagaService service;

	@MockitoBean
	private SagaLinks links;

	@Test
	void theListReturnsOnePageOfRowsWithTheNumberAndTheTitles() throws Exception {
		var row = new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED), 1, Set.of());
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 1));

		this.mockMvc.perform(get("/sagas").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(1))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].body.number").value(1))
			.andExpect(jsonPath("$.content[0].body.names.it").value("East Blue"))
			.andExpect(jsonPath("$.content[0].body.names.en").doesNotExist())
			.andExpect(jsonPath("$.content[0].body.translations").doesNotExist())
			.andExpect(jsonPath("$.content[0].onlineVersionNumber").value(1));
	}

	@Test
	void theListCanBeSortedByNumber() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/sagas").with(callerWith(Permission.CONTENT_READ)).param("sort", "number,desc"))
			.andExpect(status().isOk());

		var pageable = ArgumentCaptor.forClass(Pageable.class);
		verify(this.service).list(any(), any(), any(), pageable.capture(), any());
		assertThat(pageable.getValue().getSort().getOrderFor("number")).isNotNull();
		assertThat(pageable.getValue().getSort().getOrderFor("number").isDescending()).isTrue();
	}

	@Test
	void aVersionSaysItsNumberAndItsTitlesAndDescriptions() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);

		this.mockMvc.perform(get("/sagas/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.number").value(1))
			.andExpect(jsonPath("$.body.translations.it.title").value("East Blue"))
			.andExpect(jsonPath("$.body.translations.it.description").value("Il mare"))
			.andExpect(jsonPath("$.body.translations.en.title").doesNotExist())
			.andExpect(jsonPath("$.body.translations.en.description").value("The sea"))
			.andExpect(jsonPath("$.allowedActions[0]").value("EDIT"));
	}

	@Test
	void aSagaTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new SagaNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/sagas/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_SAGA_NOT_FOUND"));
	}

	@Test
	void theListRefusesASortByAFieldItDoesNotHave() throws Exception {
		this.mockMvc.perform(get("/sagas").param("sort", "authorEmail").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("pageable"))
			.andExpect(jsonPath("$.errors[0].message")
				.value("can only be sorted by [author, name, number, romaji, status, updatedAt]"));
		verifyNoInteractions(this.service);
	}

	@Test
	void creatingASagaPassesOnWhatWasWrittenAsTypedAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		this.mockMvc
			.perform(post("/sagas").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":0,\"translations\":{\"it\":{\"title\":\" East Blue \"}}}"))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.versions[0].number").value(1));

		var written = ArgumentCaptor.forClass(Saga.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().number()).isZero();
		assertThat(written.getValue().translations().get("it")).isEqualTo(new StoryTranslation(" East Blue ", null));
	}

	@Test
	void editingADraftAnswersWithTheVersionAsItNowIs() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any())).thenReturn(draft);

		this.mockMvc.perform(edit("{\"number\":1}").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.number").value(1))
			.andExpect(jsonPath("$.body.translations.it.title").value("East Blue"));
	}

	@Test
	void aDraftMayBeSavedWithNothingInIt() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);

		this.mockMvc.perform(edit("{}").with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
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
	void aNumberOrATitleAlreadyUsedIsUnprocessableAndNamesItsFields() throws Exception {
		var taken = List.of(new FieldViolation("number", "is already used by another content"),
				new FieldViolation("translations[it].title", "is already used by another content"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc
			.perform(post("/sagas").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"number\":1}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("number"))
			.andExpect(jsonPath("$.errors[1].field").value("translations[it].title"));
	}

	@Test
	void retiringASagaWithArcsOnlineIsAConflictNamingTheArcs() throws Exception {
		UUID arc = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");
		var block = new ActionBlock(BlockReason.ONLINE_ITEMS_LINKED,
				Map.of("count", 7L, "arcs", List.of(Map.of("id", arc, "number", 3))));
		when(this.service.retire(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.RETIRE, block));

		this.mockMvc
			.perform(post("/sagas/" + CONTENT_ID + "/versions/1/retire").with(callerWith(Permission.CONTENT_RETIRE)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("ONLINE_ITEMS_LINKED"))
			.andExpect(jsonPath("$.detail.count").value(7))
			.andExpect(jsonPath("$.detail.arcs[0].id").value(arc.toString()))
			.andExpect(jsonPath("$.detail.arcs[0].number").value(3));
	}

	@ParameterizedTest
	@CsvSource({ "{\"number\":-1}, number",
			"{\"translations\":{\"it\":{\"title\":\"TOO_LONG_TITLE\"}}}, translations[it].title",
			"{\"translations\":{\"it\":{\"description\":\"TOO_LONG_DESCRIPTION\"}}}, translations[it].description" })
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
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), any(), eq(1), any())).thenReturn(draft);
		var body = "{\"number\":0,\"translations\":{\"it\":{\"title\":\"%s\",\"description\":\"%s\"}}}"
			.formatted("x".repeat(200), "x".repeat(2000));

		this.mockMvc.perform(edit(body).with(callerWith(Permission.CONTENT_WRITE))).andExpect(status().isOk());
	}

	@Test
	void theSagasAnArcMayBeLinkedToAreListedForWhoeverWritesArcs() throws Exception {
		var eastBlue = new NumberedReference(CONTENT_ID, 1, Map.of("it", "East Blue"));
		when(this.links.linkable(any(), anyInt(), anyInt()))
			.thenReturn(new PageImpl<>(List.of(eastBlue), PageRequest.of(1, 5), 6));

		this.mockMvc
			.perform(get("/sagas/linkable").param("q", "blue")
				.param("page", "1")
				.param("size", "5")
				.with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(6))
			.andExpect(jsonPath("$.content[0].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.content[0].number").value(1))
			.andExpect(jsonPath("$.content[0].names.it").value("East Blue"));
		verify(this.links).linkable("blue", 1, 5);
	}

	@Test
	void theLinkableSagasAreFirstPageTwentyByDefault() throws Exception {
		when(this.links.linkable(any(), anyInt(), anyInt())).thenReturn(new PageImpl<>(List.of()));

		this.mockMvc.perform(get("/sagas/linkable").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk());

		verify(this.links).linkable(null, 0, 20);
	}

	@Test
	void theLinkableSagasAreNotForWhoOnlyReadsAndAreNotAContentId() throws Exception {
		this.mockMvc.perform(get("/sagas/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
		this.mockMvc.perform(get("/sagas/linkable")).andExpect(status().isUnauthorized());
		verifyNoInteractions(this.links);
	}

	@ParameterizedTest
	@CsvSource({ "size,51", "size,0", "page,-1" })
	void aPageOfLinkableSagasBeyondTheLimitsIsABadRequest(String parameter, String value) throws Exception {
		this.mockMvc.perform(get("/sagas/linkable").param(parameter, value).with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));
	}

	/** A creation, not signed in yet. */
	private static MockHttpServletRequestBuilder create() {
		return post("/sagas").contentType("application/json").content("{}");
	}

	/** A save of version 1 of the content, not signed in yet. */
	private static MockHttpServletRequestBuilder edit(String body) {
		return put("/sagas/" + CONTENT_ID + "/versions/1").contentType("application/json").content(body);
	}

	private static Version<Saga> version(VersionStatus status) {
		var translations = Map.of("it", new StoryTranslation("East Blue", "Il mare"), "en",
				new StoryTranslation(null, "The sea"));
		return Version.<Saga>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new Saga(1, translations))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
