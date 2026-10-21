package dev.onepieceapi.contentservice.web.controller;

import dev.onepieceapi.contentservice.domain.bounty.Bounty;
import dev.onepieceapi.contentservice.domain.bounty.BountyChain;
import dev.onepieceapi.contentservice.domain.bounty.BountyCondition;
import dev.onepieceapi.contentservice.domain.bounty.BountyReference;
import dev.onepieceapi.contentservice.domain.bounty.BountyTranslation;
import dev.onepieceapi.contentservice.domain.character.CharacterReference;
import dev.onepieceapi.contentservice.domain.bounty.BountyValidity;
import dev.onepieceapi.contentservice.domain.image.ImageChange;
import dev.onepieceapi.contentservice.domain.readertime.StoryPoint;
import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.Content;
import dev.onepieceapi.contentservice.domain.workflow.ContentFilter;
import dev.onepieceapi.contentservice.domain.workflow.ContentSummary;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.service.bounty.BountyLinks;
import dev.onepieceapi.contentservice.service.bounty.BountyService;
import dev.onepieceapi.contentservice.service.character.CharacterLinks;
import dev.onepieceapi.contentservice.service.exception.BountyNotFoundException;
import dev.onepieceapi.contentservice.service.exception.ValueAlreadyUsedException;
import dev.onepieceapi.contentservice.service.exception.ValueInvalidException;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.story.FirstAppearanceLinks;
import dev.onepieceapi.contentservice.web.mapper.ImageResponseMapper;
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
 * The Bounty section over HTTP: what the workflow endpoints it shares with every entity
 * are tested by {@link DevilFruitTypeControllerTest}, here what is its own - the validity
 * and the chain a caller reads, the list narrowed and sorted by what only a bounty has,
 * the links refused or blocked, and who may write.
 */
@WebMvcTest(BountyController.class)
@Import({ SecurityConfig.class, ApplicationExceptionHandler.class, ConcurrentModificationExceptionHandler.class })
class BountyControllerTest {

	private static final UUID CONTENT_ID = UUID.fromString("9c1d2e3f-4a5b-4c6d-8e7f-0a1b2c3d4e5f");

	private static final UUID PREVIOUS_ID = UUID.fromString("0f6c3e5a-4b7d-4c1e-9a58-3d2b1c0e7f11");

	private static final UUID NEXT_ID = UUID.fromString("5a1d9c2e-77b4-4d3a-8e0f-2c6b1a9d4e22");

	private static final UUID HOLDER_ID = UUID.fromString("7b3e6d1c-2f48-4a90-b5c7-9d0e1f2a3b44");

	private static final Instant CREATED = Instant.parse("2026-10-07T08:00:00Z");

	private static final User NAMI = new User(UUID.fromString("11111111-1111-1111-1111-111111111111"), "nami",
			"nami@onepiece.local");

	private static final BountyReference FIRST = new BountyReference(PREVIOUS_ID, "Monkey D. Luffy 1500000000",
			"Monkey D. Luffy", 1_500_000_000L);

	private static final BountyReference THIS = new BountyReference(CONTENT_ID, "Monkey D. Luffy 3000000000",
			"Monkey D. Luffy", 3_000_000_000L);

	private static final BountyReference LAST = new BountyReference(NEXT_ID, "Monkey D. Luffy 4000000000",
			"Monkey D. Luffy", 4_000_000_000L);

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private BountyService service;

	@MockitoBean
	private BountyLinks links;

	@MockitoBean
	private CharacterLinks characters;

	@MockitoBean
	private FirstAppearanceLinks firstAppearances;

	@MockitoBean
	private ImageResponseMapper images;

	@Test
	void aVersionSaysItsValidityItsPreviousItsNextAndTheWholeChainNewestFirst() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.PUBLISHED, false, PREVIOUS_ID), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.links.chainOf(eq(CONTENT_ID), eq(PREVIOUS_ID), any()))
			.thenReturn(new BountyChain(List.of(NEXT_ID, CONTENT_ID, PREVIOUS_ID), NEXT_ID));
		when(this.links.referencesOf(anyCollection(), any()))
			.thenReturn(Map.of(PREVIOUS_ID, FIRST, CONTENT_ID, THIS, NEXT_ID, LAST));

		this.mockMvc.perform(get("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.posterName").value("Monkey D. Luffy"))
			.andExpect(jsonPath("$.body.amount").value(3_000_000_000L))
			.andExpect(jsonPath("$.body.condition").value("DEAD_OR_ALIVE"))
			.andExpect(jsonPath("$.body.validity").value("SUPERSEDED"))
			.andExpect(jsonPath("$.body.previous.key").value("Monkey D. Luffy 1500000000"))
			.andExpect(jsonPath("$.body.previous.amount").value(1_500_000_000L))
			.andExpect(jsonPath("$.body.next.id").value(NEXT_ID.toString()))
			.andExpect(jsonPath("$.body.chain[0].id").value(NEXT_ID.toString()))
			.andExpect(jsonPath("$.body.chain[1].id").value(CONTENT_ID.toString()))
			.andExpect(jsonPath("$.body.chain[2].id").value(PREVIOUS_ID.toString()))
			.andExpect(jsonPath("$.body.translations.it.description").value("Taglia"));
	}

	@Test
	void aVersionSaysItsHolderAsItIsToday() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.PUBLISHED, false, null, HOLDER_ID), Set.of());
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1))).thenReturn(access);
		when(this.links.chainOf(eq(CONTENT_ID), any(), any())).thenReturn(new BountyChain(List.of(CONTENT_ID), null));
		when(this.characters.referencesOf(List.of(HOLDER_ID)))
			.thenReturn(Map.of(HOLDER_ID, new CharacterReference(HOLDER_ID, "Nami", Map.of("en", "Nami"))));

		this.mockMvc.perform(get("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.holder.id").value(HOLDER_ID.toString()))
			.andExpect(jsonPath("$.body.holder.romaji").value("Nami"))
			.andExpect(jsonPath("$.body.holder.names.en").value("Nami"));
	}

	@Test
	void aBountyNobodyReplacesIsActiveAndOneMarkedInactiveIsInactiveWhateverElse() throws Exception {
		when(this.links.chainOf(eq(CONTENT_ID), any(), any())).thenReturn(new BountyChain(List.of(CONTENT_ID), null));
		when(this.links.referencesOf(anyCollection(), any())).thenReturn(Map.of(CONTENT_ID, THIS));
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(VersionStatus.DRAFT, false, null), Set.of()))
			.thenReturn(new VersionAccess<>(version(VersionStatus.DRAFT, true, null), Set.of()));

		this.mockMvc.perform(get("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(jsonPath("$.body.validity").value("ACTIVE"))
			.andExpect(jsonPath("$.body.previous").doesNotExist())
			.andExpect(jsonPath("$.body.next").doesNotExist());
		this.mockMvc.perform(get("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(jsonPath("$.body.validity").value("INACTIVE"));
	}

	@Test
	void theChainIsReadThroughTheStatusesTheCallerSees() throws Exception {
		when(this.service.getVersion(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenReturn(new VersionAccess<>(version(VersionStatus.PUBLISHED, false, null), Set.of()));
		when(this.links.chainOf(any(), any(), any())).thenReturn(new BountyChain(List.of(CONTENT_ID), null));

		this.mockMvc.perform(get("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		verify(this.links).chainOf(CONTENT_ID, null, VersionStatus.approved());
	}

	@Test
	void theListSaysTheValidityOfEachRowLookingItUpOnceForThePage() throws Exception {
		var other = UUID.randomUUID();
		var rows = List.of(new ContentSummary<>(CONTENT_ID, version(VersionStatus.PUBLISHED, false, null), 1, Set.of()),
				new ContentSummary<>(other, version(VersionStatus.PUBLISHED, false, null), 1, Set.of()),
				new ContentSummary<>(NEXT_ID, version(VersionStatus.PUBLISHED, true, null), 1, Set.of()));
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(rows, PageRequest.of(0, 20), 3));
		when(this.links.replacedAmong(anyCollection(), any())).thenReturn(Set.of(CONTENT_ID, NEXT_ID));

		this.mockMvc.perform(get("/bounties").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].body.key").value("Monkey D. Luffy 3000000000"))
			.andExpect(jsonPath("$.content[0].body.validity").value("SUPERSEDED"))
			.andExpect(jsonPath("$.content[1].body.validity").value("ACTIVE"))
			.andExpect(jsonPath("$.content[2].body.validity").value("INACTIVE"));
		verify(this.links).replacedAmong(List.of(CONTENT_ID, other, NEXT_ID), VersionStatus.approved());
	}

	@Test
	void theListIsNarrowedByValidityAndConditionAndSortableByAmount() throws Exception {
		when(this.service.list(any(), any(), any(), any(), any()))
			.thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

		this.mockMvc
			.perform(get("/bounties").param("validity", "ACTIVE")
				.param("condition", "ONLY_ALIVE")
				.param("q", "luffy")
				.param("sort", "amount,desc")
				.with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isOk());

		var filter = ArgumentCaptor.forClass(ContentFilter.class);
		verify(this.service).list(any(), any(), filter.capture(), any(), any());
		assertThat(filter.getValue().attribute(Bounty.VALIDITY_ATTRIBUTE)).isEqualTo(BountyValidity.ACTIVE);
		assertThat(filter.getValue().attribute(Bounty.CONDITION_ATTRIBUTE)).isEqualTo(BountyCondition.ONLY_ALIVE);
		assertThat(filter.getValue().query()).isEqualTo("luffy");
	}

	@Test
	void aSortOnlyOtherEntitiesHaveIsABadRequestAndSoIsAValidityThatIsNone() throws Exception {
		this.mockMvc.perform(get("/bounties").param("sort", "romaji").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
		this.mockMvc.perform(get("/bounties").param("validity", "GONE").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isBadRequest());
	}

	@Test
	void creatingABountyPassesOnEverythingItSaysAndAnswersWithTheContent() throws Exception {
		var draft = new VersionAccess<>(version(VersionStatus.DRAFT, false, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.create(any(), any(), any(), any())).thenReturn(new Content<>(CONTENT_ID, List.of(draft)));

		var request = post("/bounties").with(callerWith(Permission.CONTENT_WRITE))
			.contentType("application/json")
			.content("{\"posterName\":\"Monkey D. Luffy\",\"amount\":3000000000,\"condition\":\"DEAD_OR_ALIVE\","
					+ "\"inactive\":true,\"previous\":\"" + PREVIOUS_ID + "\",\"holder\":\"" + HOLDER_ID
					+ "\",\"translations\":{\"it\":{\"description\":\"Taglia\"}}}");
		this.mockMvc.perform(request)
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.id").value(CONTENT_ID.toString()));

		var written = ArgumentCaptor.forClass(Bounty.class);
		verify(this.service).create(eq(Set.of(Permission.CONTENT_WRITE)), any(), written.capture(),
				eq(ImageChange.KEEP));
		assertThat(written.getValue().posterName()).isEqualTo("Monkey D. Luffy");
		assertThat(written.getValue().amount()).isEqualTo(3_000_000_000L);
		assertThat(written.getValue().inactive()).isTrue();
		assertThat(written.getValue().previousContentId()).isEqualTo(PREVIOUS_ID);
		assertThat(written.getValue().holderContentId()).isEqualTo(HOLDER_ID);
		assertThat(written.getValue().translations().get("it")).isEqualTo(new BountyTranslation("Taglia"));
	}

	@Test
	void aNegativeAmountOrALongPosterNameOrDescriptionIsABadRequest() throws Exception {
		for (String body : List.of("{\"amount\":-1}", "{\"posterName\":\"" + "x".repeat(101) + "\"}",
				"{\"translations\":{\"it\":{\"description\":\"" + "x".repeat(2001) + "\"}}}")) {
			this.mockMvc
				.perform(post("/bounties").with(callerWith(Permission.CONTENT_WRITE))
					.contentType("application/json")
					.content(body))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void editingAnswersWithTheVersionAndItsChain() throws Exception {
		var access = new VersionAccess<>(version(VersionStatus.DRAFT, false, null), EnumSet.of(VersionAction.EDIT));
		when(this.service.edit(any(), any(), eq(CONTENT_ID), eq(1), any(), eq(ImageChange.KEEP))).thenReturn(access);
		when(this.links.chainOf(eq(CONTENT_ID), any(), any())).thenReturn(new BountyChain(List.of(CONTENT_ID), null));
		when(this.links.referencesOf(anyCollection(), any())).thenReturn(Map.of(CONTENT_ID, THIS));

		this.mockMvc
			.perform(put("/bounties/" + CONTENT_ID + "/versions/1").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"posterName\":\"Monkey D. Luffy\",\"amount\":3000000000}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.body.chain[0].key").value("Monkey D. Luffy 3000000000"));
	}

	@Test
	void theBountiesThatCanBeLinkedAreListedByTheirKey() throws Exception {
		when(this.links.linkable(any(), eq(0), eq(20))).thenReturn(new PageImpl<>(List.of(FIRST)));

		this.mockMvc.perform(get("/bounties/linkable").param("q", "luffy").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].key").value("Monkey D. Luffy 1500000000"));
		this.mockMvc.perform(get("/bounties/linkable").with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isForbidden());
	}

	@Test
	void aBountyTheCallerDoesNotSeeIsNotFound() throws Exception {
		when(this.service.get(any(), any(), eq(CONTENT_ID))).thenThrow(new BountyNotFoundException(CONTENT_ID));

		this.mockMvc.perform(get("/bounties/" + CONTENT_ID).with(callerWith(Permission.CONTENT_READ)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_BOUNTY_NOT_FOUND"));
	}

	@Test
	void aPreviousThatCannotBeLinkedIsUnprocessableAndNamesIt() throws Exception {
		var invalid = List.of(new FieldViolation("previous", "must be another bounty"));
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ValueInvalidException(invalid));

		this.mockMvc
			.perform(post("/bounties").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"previous\":\"" + PREVIOUS_ID + "\"}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_INVALID"))
			.andExpect(jsonPath("$.errors[0].field").value("previous"));
	}

	@Test
	void aKeyAnotherBountyHasIsUnprocessableAndNamesBothFields() throws Exception {
		var taken = List.of(new FieldViolation("posterName", "is already used by another content"),
				new FieldViolation("amount", "is already used by another content"));
		when(this.service.create(any(), any(), any(), any())).thenThrow(new ValueAlreadyUsedException(taken));

		this.mockMvc
			.perform(post("/bounties").with(callerWith(Permission.CONTENT_WRITE))
				.contentType("application/json")
				.content("{\"posterName\":\"Monkey D. Luffy\",\"amount\":1}"))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VALUE_ALREADY_USED"))
			.andExpect(jsonPath("$.errors[0].field").value("posterName"))
			.andExpect(jsonPath("$.errors[1].field").value("amount"));
	}

	@Test
	void aReplacementAnotherBountyAlreadyHasIsAConflictWithItsReasonAndDetail() throws Exception {
		var block = new ActionBlock(BlockReason.PREVIOUS_ALREADY_REPLACED,
				Map.of("previousId", PREVIOUS_ID, "replacedBy", Map.of("id", NEXT_ID, "key", LAST.key())));
		when(this.service.publish(any(), any(), eq(CONTENT_ID), eq(1)))
			.thenThrow(new VersionActionBlockedException(VersionAction.PUBLISH, block));

		this.mockMvc
			.perform(post("/bounties/" + CONTENT_ID + "/versions/1/publish")
				.with(callerWith(Permission.CONTENT_PUBLISH)))
			.andExpect(status().isConflict())
			.andExpect(jsonPath("$.errorCode").value("CONTENT_VERSION_ACTION_BLOCKED"))
			.andExpect(jsonPath("$.reason").value("PREVIOUS_ALREADY_REPLACED"))
			.andExpect(jsonPath("$.detail.replacedBy.key").value("Monkey D. Luffy 4000000000"));
	}

	@Test
	void readingTakesContentReadAndWritingContentWrite() throws Exception {
		this.mockMvc.perform(get("/bounties")).andExpect(status().isUnauthorized());
		this.mockMvc.perform(get("/bounties").with(callerWith(Permission.CONTENT_WRITE)))
			.andExpect(status().isForbidden());
		this.mockMvc
			.perform(post("/bounties").with(callerWith(Permission.CONTENT_READ))
				.contentType("application/json")
				.content("{}"))
			.andExpect(status().isForbidden());
	}

	private static Version<Bounty> version(VersionStatus status, boolean inactive, UUID previous) {
		return version(status, inactive, previous, null);
	}

	private static Version<Bounty> version(VersionStatus status, boolean inactive, UUID previous, UUID holder) {
		var translations = Map.of("it", new BountyTranslation("Taglia"));
		return Version.<Bounty>builder()
			.number(1)
			.status(status)
			.author(NAMI)
			.body(new Bounty("Monkey D. Luffy", 3_000_000_000L, BountyCondition.DEAD_OR_ALIVE, inactive, previous,
					holder, translations, null, StoryPoint.NONE))
			.createdAt(CREATED)
			.updatedAt(CREATED)
			.build();
	}

}
