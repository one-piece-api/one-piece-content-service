package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ActionBlock;
import dev.onepieceapi.contentservice.domain.workflow.BlockReason;
import dev.onepieceapi.contentservice.domain.workflow.BlockedAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionAccess;
import dev.onepieceapi.contentservice.domain.workflow.VersionAction;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.mapper.ContentVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.ContentServiceTest.Note;
import dev.onepieceapi.contentservice.service.ContentServiceTest.NoteDefinition;
import dev.onepieceapi.contentservice.service.ContentServiceTest.NoteVersionEntity;
import dev.onepieceapi.contentservice.service.exception.VersionActionBlockedException;
import dev.onepieceapi.contentservice.service.exception.VersionActionForbiddenException;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What an entity's own rules do to the generic workflow (implementation plan of the Devil
 * Fruit, D2, D3), on the same "Note" as {@link ContentServiceTest}: asked after the
 * transition rules, after taking the lock they want, and listed as blocked actions; an
 * entity with none is not affected. The rules of the Devil Fruit themselves are covered
 * where they live.
 */
class ContentRulesTest {

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Set<Permission> PUBLISHER = Set.of(Permission.CONTENT_READ, Permission.CONTENT_PUBLISH);

	private static final ActionBlock BLOCK = new ActionBlock(BlockReason.TYPE_NOT_ONLINE, Map.of("typeId", "t-1"));

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	private final User sanji = new User(UUID.randomUUID(), "sanji", "sanji@onepiece.local");

	@SuppressWarnings("unchecked")
	private final VersionBodyRepository<NoteVersionEntity> repository = mock(VersionBodyRepository.class);

	@SuppressWarnings("unchecked")
	private final ContentValidator<Note, NoteVersionEntity> validator = mock(ContentValidator.class);

	private final ContentVersionRepository contentVersionRepository = mock(ContentVersionRepository.class);

	@SuppressWarnings("unchecked")
	private final ContentRules<Note> rules = mock(ContentRules.class);

	private final UUID contentId = UUID.randomUUID();

	private ContentService<Note, NoteVersionEntity> ruled;

	private ContentService<Note, NoteVersionEntity> unruled;

	@BeforeEach
	void setUp() {
		var clock = Clock.fixed(NOW, ZoneOffset.UTC);
		var ruledDefinition = new NoteDefinition(this.repository, this.validator) {
			@Override
			public ContentRules<Note> rules() {
				return ContentRulesTest.this.rules;
			}
		};
		this.ruled = new ContentService<>(ruledDefinition, this.contentVersionRepository, mock(ContentRepository.class),
				mock(AuditLogService.class), clock);
		this.unruled = new ContentService<>(new NoteDefinition(this.repository, this.validator),
				this.contentVersionRepository, mock(ContentRepository.class), mock(AuditLogService.class), clock);
		var ready = readyToPublish();
		when(this.repository.findVisible(eq(this.contentId), eq(1), anyCollection())).thenReturn(Optional.of(ready));
		when(this.contentVersionRepository.hasOpenVersion(this.contentId)).thenReturn(true);
	}

	@Test
	void anActionTheRulesRefuseAnswersWithTheirReasonAndWritesNothing() {
		when(this.rules.blockOf(eq(VersionAction.PUBLISH), eq(this.contentId), any())).thenReturn(Optional.of(BLOCK));

		assertThatThrownBy(() -> this.ruled.publish(PUBLISHER, this.sanji, this.contentId, 1))
			.isInstanceOfSatisfying(VersionActionBlockedException.class, blocked -> {
				assertThat(blocked.getMessage()).contains("PUBLISH", "TYPE_NOT_ONLINE");
				assertThat(blocked.getDetails()).containsEntry("reason", BlockReason.TYPE_NOT_ONLINE)
					.containsEntry("detail", Map.of("typeId", "t-1"));
			});
		verify(this.repository, never()).flush();
	}

	@Test
	void theLockIsTakenBeforeTheRuleIsRead() {
		when(this.rules.blockOf(eq(VersionAction.PUBLISH), eq(this.contentId), any())).thenReturn(Optional.of(BLOCK));

		assertThatThrownBy(() -> this.ruled.publish(PUBLISHER, this.sanji, this.contentId, 1))
			.isInstanceOf(VersionActionBlockedException.class);

		InOrder order = inOrder(this.rules);
		order.verify(this.rules).lockBefore(eq(VersionAction.PUBLISH), eq(this.contentId), any());
		order.verify(this.rules).blockOf(eq(VersionAction.PUBLISH), eq(this.contentId), any());
	}

	@Test
	void anActionTheCallerMayNotDoNeverReachesTheRules() {
		assertThatThrownBy(() -> this.ruled.publish(Set.of(Permission.CONTENT_READ), this.sanji, this.contentId, 1))
			.isInstanceOf(VersionActionForbiddenException.class);

		verify(this.rules, never()).lockBefore(any(), any(), any());
		verify(this.rules, never()).blockOf(eq(VersionAction.PUBLISH), any(), any());
	}

	@Test
	void theBlockedActionsAreAllowedOnesTheRulesRefuse() {
		when(this.rules.blockOf(eq(VersionAction.PUBLISH), eq(this.contentId), any())).thenReturn(Optional.of(BLOCK));

		VersionAccess<Note> access = this.ruled.getVersion(PUBLISHER, this.sanji, this.contentId, 1);

		assertThat(access.allowedActions()).contains(VersionAction.PUBLISH, VersionAction.ARCHIVE);
		assertThat(access.blockedActions())
			.containsExactly(new BlockedAction(VersionAction.PUBLISH, BlockReason.TYPE_NOT_ONLINE, BLOCK.detail()));
	}

	@Test
	void anEntityWithNoRulesBlocksNothing() {
		VersionAccess<Note> access = this.unruled.getVersion(PUBLISHER, this.sanji, this.contentId, 1);

		assertThat(access.allowedActions()).contains(VersionAction.PUBLISH);
		assertThat(access.blockedActions()).isEmpty();
		verifyNoInteractions(this.rules);
	}

	private NoteVersionEntity readyToPublish() {
		var entity = new NoteVersionEntity(
				ContentVersionMapper.toDraft(this.contentId, 1, null, this.nami, NOW.minusSeconds(60)));
		entity.write(new Note("Memo", Map.of("it", "Nota")), NOW);
		entity.moveTo(VersionStatus.READY_TO_PUBLISH, NOW);
		return entity;
	}

}
