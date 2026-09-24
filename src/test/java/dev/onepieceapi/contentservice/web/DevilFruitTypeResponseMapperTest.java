package dev.onepieceapi.contentservice.web;

import dev.onepieceapi.contentservice.domain.Translation;
import dev.onepieceapi.contentservice.domain.WorkingRevision;
import dev.onepieceapi.contentservice.domain.WorkingRevisionStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
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
		var revision = aRevision(
				Map.of("it", new Translation("Nome italiano", null), "en", new Translation("English name", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, null);

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void prefersEnglishWhenTheAcceptLanguageIsEnglish() {
		var revision = aRevision(
				Map.of("it", new Translation("Nome italiano", null), "en", new Translation("English name", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, "en-US,en;q=0.9");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void prefersItalianWhenTheAcceptLanguageIsItalian() {
		var revision = aRevision(
				Map.of("it", new Translation("Nome italiano", null), "en", new Translation("English name", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, "it-IT,it;q=0.9,en;q=0.8");

		assertThat(summary.displayName()).isEqualTo("Nome italiano");
	}

	@Test
	void fallsBackToItalianWhenItalianIsRequestedButOnlyEnglishExists() {
		var revision = aRevision(Map.of("en", new Translation("English name", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, "it");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	@Test
	void fallsBackToAnyOtherTranslationWhenNeitherEnglishNorItalianExists() {
		var revision = aRevision(Map.of("fr", new Translation("Nom francais", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, "en");

		assertThat(summary.displayName()).isEqualTo("Nom francais");
	}

	@Test
	void treatsAnUnparseableAcceptLanguageAsEnglish() {
		var revision = aRevision(
				Map.of("it", new Translation("Nome italiano", null), "en", new Translation("English name", null)));

		var summary = DevilFruitTypeResponseMapper.toSummary(revision, "not a locale");

		assertThat(summary.displayName()).isEqualTo("English name");
	}

	private static WorkingRevision aRevision(Map<String, Translation> translations) {
		return new WorkingRevision(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "author@onepiece.local",
				"Romaji", WorkingRevisionStatus.DRAFT, translations, null, null, null, Instant.EPOCH);
	}

}
