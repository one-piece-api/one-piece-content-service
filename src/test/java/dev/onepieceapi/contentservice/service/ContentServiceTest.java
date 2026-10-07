package dev.onepieceapi.contentservice.service;

import dev.onepieceapi.contentservice.domain.security.Permission;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.ContentBody;
import dev.onepieceapi.contentservice.domain.workflow.EntityType;
import dev.onepieceapi.contentservice.domain.workflow.Version;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.ContentEntity;
import dev.onepieceapi.contentservice.persistence.entity.ContentVersionEntity;
import dev.onepieceapi.contentservice.persistence.entity.VersionBodyEntity;
import dev.onepieceapi.contentservice.persistence.mapper.ContentVersionMapper;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.VersionBodyRepository;
import dev.onepieceapi.contentservice.service.exception.ContentErrorCode;
import dev.onepieceapi.contentservice.service.validation.ContentValidator;
import dev.onepieceapi.exception.NotFoundException;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The generic workflow run with an entity that exists only here, a "Note": whatever the
 * service needs of an entity it takes from the entity's definition, so nothing of the
 * Devil Fruit Type is left in it. The workflow rules themselves are covered, on a real
 * database, by the Devil Fruit Type integration tests.
 */
class ContentServiceTest {

	private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");

	private static final Set<Permission> EDITOR = Set.of(Permission.CONTENT_READ, Permission.CONTENT_WRITE);

	private final User nami = new User(UUID.randomUUID(), "nami", "nami@onepiece.local");

	@SuppressWarnings("unchecked")
	private final VersionBodyRepository<NoteVersionEntity> repository = mock(VersionBodyRepository.class);

	@SuppressWarnings("unchecked")
	private final ContentValidator<Note, NoteVersionEntity> validator = mock(ContentValidator.class);

	private final ContentVersionRepository contentVersionRepository = mock(ContentVersionRepository.class);

	private final ContentRepository contentRepository = mock(ContentRepository.class);

	private final AuditLogService auditLogService = mock(AuditLogService.class);

	private ContentService<Note, NoteVersionEntity> service;

	@BeforeEach
	void setUp() {
		var definition = new NoteDefinition(this.repository, this.validator);
		this.service = new ContentService<>(definition, this.contentVersionRepository, this.contentRepository,
				this.auditLogService, Clock.fixed(NOW, ZoneOffset.UTC));
		when(this.repository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
	}

	@Test
	void aNewContentIsOfTheEntityOfItsDefinitionAndSaysWhatTheDefinitionWrote() {
		var content = this.service.create(EDITOR, this.nami, new Note("  Memo ", Map.of("it", "Nota")));

		var savedContent = ArgumentCaptor.forClass(ContentEntity.class);
		verify(this.contentRepository).save(savedContent.capture());
		assertThat(savedContent.getValue().getEntityType()).isEqualTo(EntityType.DEVIL_FRUIT_TYPE);
		var savedVersion = ArgumentCaptor.forClass(NoteVersionEntity.class);
		verify(this.repository).save(savedVersion.capture());
		assertThat(savedVersion.getValue().getRomaji()).isEqualTo("Memo");
		verify(this.validator).validateDraft(content.id(), new Note("Memo", Map.of("it", "Nota")));
		assertThat(content.versions()).singleElement().satisfies(access -> {
			assertThat(access.version().status()).isEqualTo(VersionStatus.DRAFT);
			assertThat(access.version().author()).isEqualTo(this.nami);
			assertThat(access.version().body().romaji()).isEqualTo("Memo");
		});
	}

	@Test
	void theAuditRecordNamesTheVersionByItsRomaji() {
		this.service.create(EDITOR, this.nami, new Note("Memo", Map.of()));

		var record = ArgumentCaptor.forClass(VersionAuditRecord.class);
		verify(this.auditLogService).recordOnVersion(record.capture());
		assertThat(record.getValue().action()).isEqualTo("VERSION_CREATED");
		assertThat(record.getValue().label()).isEqualTo("Memo");
	}

	@Test
	void aContentTheCallerCannotSeeIsReportedAsTheDefinitionSays() {
		UUID contentId = UUID.randomUUID();
		when(this.repository.findVisible(eq(contentId), anyCollection())).thenReturn(List.of());

		assertThatThrownBy(() -> this.service.get(EDITOR, this.nami, contentId))
			.isInstanceOf(NoteNotFoundException.class);
	}

	@Test
	void aSubmissionIsCheckedByTheValidatorOfTheDefinition() {
		UUID contentId = UUID.randomUUID();
		var draft = draft(contentId, new Note("Memo", Map.of("it", "Nota")));
		when(this.repository.findVisible(eq(contentId), eq(1), anyCollection())).thenReturn(Optional.of(draft));
		when(this.contentVersionRepository.hasOpenVersion(contentId)).thenReturn(true);

		var submitted = this.service.submit(EDITOR, this.nami, contentId, 1);

		verify(this.validator).validateSubmission(contentId, 1, new Note("Memo", Map.of("it", "Nota")));
		assertThat(submitted.version().status()).isEqualTo(VersionStatus.IN_REVIEW);
	}

	private NoteVersionEntity draft(UUID contentId, Note body) {
		var entity = new NoteVersionEntity(ContentVersionMapper.toDraft(contentId, 1, null, this.nami, NOW));
		entity.write(body, NOW);
		return entity;
	}

	/** What a version of a note says: a romaji and a name per language. */
	record Note(String romaji, Map<String, String> names) implements ContentBody<Note> {

		@Override
		public Set<String> languages() {
			return this.names.keySet();
		}

		@Override
		public Note normalized() {
			return new Note(this.romaji == null ? null : this.romaji.strip(), this.names);
		}

		@Override
		public List<String> missingFields(Collection<String> languages) {
			return this.romaji == null ? List.of("romaji") : List.of();
		}

	}

	/** The version table of notes - never persisted here. */
	static class NoteVersionEntity extends VersionBodyEntity {

		private Map<String, String> names = Map.of();

		NoteVersionEntity(ContentVersionEntity version) {
			super(version);
		}

		void write(Note body, Instant now) {
			setRomaji(body.romaji());
			this.names = body.names();
			touch(now);
		}

		Note read() {
			return new Note(getRomaji(), this.names);
		}

	}

	static class NoteNotFoundException extends NotFoundException {

		NoteNotFoundException(UUID contentId) {
			super(ContentErrorCode.DEVIL_FRUIT_TYPE_NOT_FOUND, "Note " + contentId + " not found");
		}

	}

	@Getter
	@Accessors(fluent = true)
	@RequiredArgsConstructor
	static class NoteDefinition implements ContentDefinition<Note, NoteVersionEntity> {

		private final VersionBodyRepository<NoteVersionEntity> repository;

		private final ContentValidator<Note, NoteVersionEntity> validator;

		@Override
		public EntityType entityType() {
			return EntityType.DEVIL_FRUIT_TYPE;
		}

		@Override
		public Version<Note> toDomain(NoteVersionEntity entity) {
			return ContentVersionMapper.toDomain(entity.getVersion(), entity.read());
		}

		@Override
		public NoteVersionEntity newVersion(ContentVersionEntity workflow) {
			return new NoteVersionEntity(workflow);
		}

		@Override
		public void rewrite(NoteVersionEntity entity, Note body, Instant now) {
			entity.write(body, now);
		}

		@Override
		public NotFoundException notFound(UUID contentId) {
			return new NoteNotFoundException(contentId);
		}

	}

}
