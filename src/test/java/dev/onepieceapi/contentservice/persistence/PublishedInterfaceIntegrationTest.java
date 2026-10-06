package dev.onepieceapi.contentservice.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The interface one-piece-public-api reads ({@code V8},
 * docs/adr/0003-published-interface.md) against a real PostgreSQL (Testcontainers): the
 * slug of a romaji, the {@code published} views showing only what is online, the revision
 * counter moved by triggers, and what the reader role may and may not do. Seeded in SQL:
 * these are rules of the database, whatever service writes the rows.
 * <p>
 * Seeded for every test:
 * <ul>
 * <li>Zoan - v1 superseded as "Dobutsu", v2 online, in Italian and English</li>
 * <li>Logia - v1 retired, once online (slug "logia" in its history)</li>
 * <li>Paramecia - v1 draft, never online</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedInterfaceIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	private UUID zoan;

	private UUID zoanOnline;

	private UUID logiaRetired;

	private UUID parameciaDraft;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
		this.zoan = content();
		version(this.zoan, 1, "SUPERSEDED", "Dobutsu");
		this.zoanOnline = version(this.zoan, 2, "PUBLISHED", "Zoan");
		UUID logia = content();
		this.logiaRetired = version(logia, 1, "RETIRED", "Logia");
		slug(logia, "logia");
		this.parameciaDraft = version(content(), 1, "DRAFT", "Paramecia");
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|',
			value = { "Chōjin-kei | chojin-kei", "'  Zoan!!  ' | zoan", "Ryū_Ryū no Mi | ryu-ryu-no-mi",
					"Gomu--Gomu | gomu-gomu", "Ope Ope 2 | ope-ope-2", "Élan | elan", "'¡¿…!' | ", "' - ' | " })
	void theSlugOfARomajiIsLowercaseWithoutAccentsAndHyphenated(String romaji, String slug) {
		assertThat(this.jdbc.sql("select slug_of(?)").param(romaji).query(String.class).optional())
			.isEqualTo(Optional.ofNullable(slug));
	}

	@Test
	void theDevilFruitTypesShownAreTheVersionsOnlineInEachLanguage() {
		var rows = this.jdbc.sql("select id, slug, romaji, language, name from published.devil_fruit_type")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getString("slug"), rs.getString("romaji"),
					rs.getString("language"), rs.getString("name")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(tuple(this.zoan, "zoan", "Zoan", "it", "Zoan IT"),
				tuple(this.zoan, "zoan", "Zoan", "en", "Zoan EN"));
	}

	@Test
	void theSlugsShownAreThoseOfTheContentsOnline() {
		slug(this.zoan, "zoan");
		slug(this.zoan, "dobutsu");

		var slugs = this.jdbc.sql("select slug from published.content_slug").query(String.class).list();

		assertThat(slugs).containsExactlyInAnyOrder("zoan", "dobutsu");
	}

	@Test
	void theLanguagesShownAreTheCatalog() {
		assertThat(this.jdbc.sql("select code from published.language").query(String.class).list())
			.containsExactlyInAnyOrder("it", "en");
	}

	@Test
	void theRevisionMovesWhenAVersionGoesOnlineOrOffline() {
		long start = revision();

		status(this.logiaRetired, "PUBLISHED");
		assertThat(revision()).isEqualTo(start + 1);
		status(this.zoanOnline, "RETIRED");
		assertThat(revision()).isEqualTo(start + 2);
		version(content(), 1, "PUBLISHED", "Kodai");
		assertThat(revision()).isEqualTo(start + 3);
		this.jdbc.sql("delete from content_version where id = ?").param(this.logiaRetired).update();
		assertThat(revision()).isEqualTo(start + 4);
	}

	@Test
	void theRevisionStaysPutWhileTheWorkHappensOffline() {
		long start = revision();

		this.jdbc.sql("update devil_fruit_type_version set romaji = 'Paramecia-kei' where version_id = ?")
			.param(this.parameciaDraft)
			.update();
		status(this.parameciaDraft, "IN_REVIEW");
		status(this.parameciaDraft, "READY_TO_PUBLISH");
		status(this.logiaRetired, "ARCHIVED");
		version(content(), 1, "DRAFT", "Kodai");

		assertThat(revision()).isEqualTo(start);
	}

	@Test
	void theRevisionMovesWhenTheLanguageCatalogChanges() {
		long start = revision();

		this.jdbc.sql("insert into language (code, name) values ('fr', 'Français')").update();

		assertThat(revision()).isEqualTo(start + 1);
	}

	@Test
	void theReaderReadsThePublishedViews() {
		asReader();

		assertThat(this.jdbc.sql("select count(*) from published.devil_fruit_type").query(Long.class).single())
			.isEqualTo(2);
		assertThat(this.jdbc.sql("select count(*) from published.content_slug").query(Long.class).single()).isZero();
		assertThat(this.jdbc.sql("select count(*) from published.language").query(Long.class).single()).isEqualTo(2);
		assertThat(this.jdbc.sql("select revision from published.revision").query(Long.class).single()).isPositive();
	}

	@Test
	void theReaderCannotReadTheTablesBehindTheViews() {
		asReader();

		assertThatThrownBy(() -> this.jdbc.sql("select count(*) from content_version").query(Long.class).single())
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("permission denied");
	}

	@Test
	void theReaderCannotWriteThroughAView() {
		asReader();

		assertThatThrownBy(
				() -> this.jdbc.sql("insert into published.language (code, name) values ('fr', 'Français')").update())
			.isInstanceOf(DataAccessException.class)
			.rootCause()
			.hasMessageContaining("permission denied");
	}

	/** The rest of the test's transaction runs with the reader's rights only. */
	private void asReader() {
		this.jdbc.sql("set local role public_api_reader").update();
	}

	private long revision() {
		return this.jdbc.sql("select revision from published_revision").query(Long.class).single();
	}

	private void status(UUID versionId, String status) {
		this.jdbc.sql("update content_version set status = ? where id = ?").params(status, versionId).update();
	}

	private void slug(UUID contentId, String slug) {
		this.jdbc
			.sql("insert into content_slug (entity_type, slug, content_id, assigned_at)"
					+ " values ('DEVIL_FRUIT_TYPE', ?, ?, ?)")
			.params(slug, contentId, EARLIER)
			.update();
	}

	private UUID content() {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("insert into content (id, entity_type, created_at) values (?, 'DEVIL_FRUIT_TYPE', ?)")
			.params(id, EARLIER)
			.update();
		return id;
	}

	/** Seeds a version with its romaji and a name in both languages of the catalog. */
	private UUID version(UUID contentId, int number, String status, String romaji) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into content_version (id, content_id, version_number, author_user_id, author_username,
				                             author_email, status, created_at, updated_at)
				values (?, ?, ?, ?, 'chopper', 'chopper@onepiece.local', ?, ?, ?)""")
			.params(id, contentId, number, UUID.randomUUID(), status, EARLIER, EARLIER)
			.update();
		this.jdbc.sql("insert into devil_fruit_type_version (version_id, romaji) values (?, ?)")
			.params(id, romaji)
			.update();
		for (String language : new String[] { "it", "en" }) {
			this.jdbc
				.sql("insert into devil_fruit_type_version_translation (version_id, language_code, name)"
						+ " values (?, ?, ?)")
				.params(id, language, romaji + " " + language.toUpperCase())
				.update();
		}
		return id;
	}

}
