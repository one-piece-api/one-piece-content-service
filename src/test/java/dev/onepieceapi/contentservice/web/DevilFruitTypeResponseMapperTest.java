package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.persistence.entity.TranslationEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionEntity;
import dev.onepieceapi.contentservice.persistence.entity.WorkingRevisionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The list-row {@code displayName} (Enciclopedia/Le mie bozze/In Revisione) must follow
 * the caller's UI language - English by default, Italian only when the caller's
 * `Accept-Language` says so - never a language hardcoded on the server, regardless of
 * what other translations exist.
 */
class DevilFruitTypeResponseMapperTest {

	@Test
	void prefersEnglishWhenNoAcceptLanguageIsSent() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "it", "Nome italiano"),
				translation(revision, "en", "English name"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, null);

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void prefersEnglishWhenTheAcceptLanguageIsEnglish() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "it", "Nome italiano"),
				translation(revision, "en", "English name"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, "en-US,en;q=0.9");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void prefersItalianWhenTheAcceptLanguageIsItalian() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "it", "Nome italiano"),
				translation(revision, "en", "English name"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, "it-IT,it;q=0.9,en;q=0.8");

		assertThat(summary.displayName()).isEqualTo("Nome italiano");
	}

	@Test
	void fallsBackToItalianWhenItalianIsRequestedButOnlyEnglishExists() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "en", "English name"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, "it");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void fallsBackToAnyOtherTranslationWhenNeitherEnglishNorItalianExists() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "fr", "Nom francais"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, "en");

		assertThat(summary.displayName()).isEqualTo("Nom francais");
	}

	@Test
	void treatsAnUnparseableAcceptLanguageAsEnglish() {
		var revision = aRevision();
		var translations = List.of(translation(revision, "it", "Nome italiano"),
				translation(revision, "en", "English name"));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, translations, "not a locale");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	private static WorkingRevisionEntity aRevision() {
		return new WorkingRevisionEntity(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				"author@onepiece.local", WorkingRevisionStatus.DRAFT, Instant.EPOCH);
	}

	private static TranslationEntity translation(WorkingRevisionEntity revision, String languageCode, String name) {
		return new TranslationEntity(revision.getId(), languageCode, name, null);
	}

}
