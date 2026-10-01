package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.persistence.entity.AuditLogEntity;
import dev.onepieceapi.contentservice.persistence.entity.LanguageEntity;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** The small mappers: a user, an audit record, a language. */
class PersistenceMappersTest {

	private static final User ZORO = new User(UUID.randomUUID(), "zoro", "zoro@onepiece.local");

	@Test
	void aUserGoesToItsStoredFormAndBackUnchanged() {
		var stored = UserMapper.toEmbeddable(ZORO);

		assertThat(stored.getUserId()).isEqualTo(ZORO.id());
		assertThat(stored.getUsername()).isEqualTo("zoro");
		assertThat(stored.getEmail()).isEqualTo("zoro@onepiece.local");
		assertThat(UserMapper.toDomain(stored)).isEqualTo(ZORO);
	}

	@Test
	void noUserStaysNoUser() {
		assertThat(UserMapper.toDomain(null)).isNull();
		assertThat(UserMapper.toEmbeddable(null)).isNull();
	}

	@Test
	void anAuditRecordReadsAsAStepOfTheHistoryOfItsVersion() {
		var occurredAt = Instant.parse("2026-10-01T09:30:00Z");
		var record = AuditLogEntity.builder()
			.action("VERSION_REJECTED")
			.actor(UserMapper.toEmbeddable(ZORO))
			.detail("Too short")
			.occurredAt(occurredAt)
			.build();

		var event = AuditLogMapper.toVersionEvent(record);

		assertThat(event.action()).isEqualTo("VERSION_REJECTED");
		assertThat(event.actor()).isEqualTo(ZORO);
		assertThat(event.detail()).isEqualTo("Too short");
		assertThat(event.occurredAt()).isEqualTo(occurredAt);
	}

	@Test
	void anAuditRecordWrittenBeforeUsernamesWereStoredHasAnActorWithoutOne() {
		var actor = new UserEmbeddable(ZORO.id(), null, ZORO.email());
		var record = AuditLogEntity.builder().action("LANGUAGE_CREATED").actor(actor).build();

		assertThat(AuditLogMapper.toVersionEvent(record).actor()).isEqualTo(new User(ZORO.id(), null, ZORO.email()));
	}

	@Test
	void aLanguageGoesToItsRowAndBackUnchanged() {
		var french = new Language("fr", "Français");

		LanguageEntity row = LanguageMapper.toEntity(french);

		assertThat(row.getCode()).isEqualTo("fr");
		assertThat(row.getName()).isEqualTo("Français");
		assertThat(LanguageMapper.toDomain(row)).isEqualTo(french);
	}

}
