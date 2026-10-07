package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.devilfruit.TypeReference;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
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
import dev.onepieceapi.contentservice.service.DevilFruitService;
import dev.onepieceapi.contentservice.service.DevilFruitTypeLinks;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.web.security.SecurityConfig;
import dev.onepieceapi.exception.web.ApplicationExceptionHandler;
import dev.onepieceapi.exception.web.ConcurrentModificationExceptionHandler;
import dev.onepieceapi.exception.web.FieldViolation;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The Devil Fruit section over HTTP: what the workflow endpoints it shares with every
 * entity are tested by {@link DevilFruitTypeControllerTest}, here what is its own - the
 * type a fruit points to, as it is today, in a version and in a row; the list narrowed to
 * a type; a type refused or an action blocked; and who may write.
 */
@WebMvcTest(DevilFruitController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class DevilFruitControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID TYPE_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final Instant CREATED = Instant.parse("2026-10-07T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final TypeReference PARAMECIA = new TypeReference(TYPE_ID, "Paramecia",
			Map.of("it", "Paramisha", "en", "Paramecia"));

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private DevilFruitService service;

	@MockitoBean
	private DevilFruitTypeLinks links;

	@Test
	void aVersionSaysItsTypeAsItIsTodayAndWhichActionsAreBlocked() throws Exception {
		var blocked = new BlockedAction(VersionAction.PUBLISH, BlockReason.TYPE_NOT_ONLINE,
				Map.of("typeId", TYPE_ID, "typeRomaji", "Paramecia"));
		var access = new VersionAccess<>(version(VersionStatus.READY_TO_PUBLISH, TYPE_ID),
				EnumSet.of(VersionAction.PUBLISH), EnumSet.noneOf(VersionAction.class), List.of(blocked));
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.links.referencesOf(anyCollection())).thenReturn(Map.of(TYPE_ID, PARAMECIA));

		this.mockMvc
			.perform(get("/devil-fruits/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.romaji").value("Gomu Gomu"))
			.andExpect(jsonPath("$.body.type.id").value(TYPE_ID.toString()))
			.andExpect(jsonPath("$.body.type.romaji").value("Paramecia"))
			.andExpect(jsonPath("$.body.type.names.it").value("Paramisha"))
			.andExpect(jsonPath("$.body.translations.it.name").value("Gomu"))
			.andExpect(jsonPath("$.allowedActions[0]").value("PUBLISH"))
			.andExpect(jsonPath("$.blockedActions[0].action").value("PUBLISH"))
			.andExpect(jsonPath("$.blockedActions[0].reason").value("TYPE_NOT_ONLINE"))
			.andExpect(jsonPath("$.blockedActions[0].detail.typeRomaji").value("Paramecia"));
	}

	@Test
	void aVersionWithoutATypeSaysNoneAndAskedNoOne() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, null), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.links.referencesOf(anyCollection())).thenReturn(Map.of());

		this.mockMvc
			.perform(get("/devil-fruits/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.type").doesNotExist())
			.andExpect(jsonPath("$.blockedActions").isEmpty());
		verify(this.links).referencesOf(Set.of());
	}

	@Test
	void theListLooksUpTheTypesOfAWholePageAtOnce() throws Exception {
		var other = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, TYPE_ID), 1, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, other), null, Set.of()),
				new ContentSummary<>(UUID.randomUUID(), version(VersionStatus.DRAFT, TYPE_ID), null, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 3));
		when(this.links.referencesOf(anyCollection())).thenReturn(Map.of(TYPE_ID, PARAMECIA));

		this.mockMvc.perform(get("/devil-fruits").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalElements").value(3))
			.andExpect(jsonPath("$.content[0].body.type.romaji").value("Paramecia"))
			.andExpect(jsonPath("$.content[1].body.type.id").value(other.toString()))
			.andExpect(jsonPath("$.content[1].body.type.romaji").doesNotExist())
			.andExpect(jsonPath("$.content[2].body.type.names.en").value("Paramecia"));

		var asked = ArgumentCaptor.forClass(Collection.class);
		verify(this.links).referencesOf(asked.capture());
		assertThat(asked.getValue()).containsExactlyInAnyOrder(TYPE_ID, other);
	}

	@Test
	void theListIsNarrowedToATypeByItsId() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/devil-fruits").param("type", TYPE_ID.toString())
				.param("q", "gomu")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().related()).containsExactly(Map.entry("type", TYPE_ID));
		assertThat(filter.getValue().query()).isEqualTo("gomu");
	}

	@Test
	void aTypeThatIsNotAnIdIsABadRequest() throws Exception {
		this.mockMvc.perform(get("/devil-fruits").param("type", "paramecia").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void creatingAFruitPassesOnTheTypeAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, TYPE_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		var request = post("/devil-fruits").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\"Gomu Gomu\",\"type\":\"" + TYPE_ID
					+ "\",\"translations\":{\"it\":{\"name\":\"Gomu\"}}}");
		this.mockMvc.perform(request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(DevilFruit.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture());
		assertThat(written.getValue().typeContentId()).isEqualTo(TYPE_ID);
		assertThat(written.getValue().translations().get("it"))
			.isEqualTo(new DevilFruitTranslation("Gomu", null, null, null));
	}

	@Test
	void editingAnswersWithTheVersionAndItsType() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, TYPE_ID), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any())).thenReturn(access);
		when(this.links.referencesOf(anyCollection())).thenReturn(Map.of(TYPE_ID, PARAMECIA));

		var request = put("/devil-fruits/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\"Gomu Gomu\",\"type\":\"" + TYPE_ID + "\"}");
		this.mockMvc.perform(request)
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.type.romaji").value("Paramecia"));
	}

	@Test
	void aTypeThatCannotBeLinkedIsUnprocessableAndNamesTheType() throws Exception {
		var invalid = List.of(new FieldViolation("type", "must be a Devil Fruit Type with an approved version"));
		when(this.service.create(any(), any(), any())).thenThrow(new ValueInvalidException(invalid));

		var request = post("/devil-fruits").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"romaji\":\"Gomu Gomu\",\"type\":\"" + TYPE_ID + "\"}");
		this.mockMvc.perform(request)
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("type"));
	}

	@Test
	void anActionTheTypeBlocksIsAConflictWithItsReasonAndDetail() throws Exception {
		var block = new ActionBlock(BlockReason.TYPE_NOT_ONLINE, Map.of("typeRomaji", "Paramecia"));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/devil-fruits/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("TYPE_NOT_ONLINE"))
			.andExpect(jsonPath("$.detail.typeRomaji").value("Paramecia"));
	}

	@Test
	void readingTakesContentReadAndWritingContentWrite() throws Exception {
		this.mockMvc.perform(get("/devil-fruits")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/devil-fruits").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/devil-fruits").with(callerWith(Permission.CONTENT_READ))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());
	}

	private static Version<DevilFruit> version(VersionStatus status, UUID type) {
		var translations = Map.of("it", new DevilFruitTranslation("Gomu", "Elastico", null, null));
		return Version.<DevilFruit>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new DevilFruit("Gomu Gomu", type, translations))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
