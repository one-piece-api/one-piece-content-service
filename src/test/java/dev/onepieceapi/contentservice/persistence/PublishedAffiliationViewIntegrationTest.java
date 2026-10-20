package dev.onepieceapi.contentservice.persistence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The Affiliation in the interface one-piece-public-api reads ({@code V23}) against a
 * real PostgreSQL (Testcontainers): only the versions online are shown, one row per
 * language, and the emblem is visible only while an affiliation online uses it. Seeded in
 * SQL, as the table owner, and read through the {@code published} views.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedAffiliationViewIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	private static final String[] BOTH = { "it", "en" };

	private static final String PUBLISHED = "PUBLISHED";

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
	}

	@Test
	void onlyTheVersionsOnlineAreShownOnePerLanguageWithTheRomajiAsKeyAndItsSlug() {
		UUID strawHats = content();
		affiliation(strawHats, 1, "SUPERSEDED", "Mugiwara no Ichimi", BOTH);
		affiliation(strawHats, 2, PUBLISHED, "Mugiwara no Ichimi", BOTH);
		affiliation(content(), 1, "DRAFT", "Kaigun", BOTH);
		affiliation(content(), 1, "RETIRED", "Seifu", BOTH);

		var rows = this.jdbc.sql("select id, slug, romaji, language, name, description from published.affiliation")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getString("slug"), rs.getString("romaji"),
					rs.getString("language"), rs.getString("name"), rs.getString("description")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(
				tuple(strawHats, "mugiwara-no-ichimi", "Mugiwara no Ichimi", "it", "Name IT", "Description IT"),
				tuple(strawHats, "mugiwara-no-ichimi", "Mugiwara no Ichimi", "en", "Name EN", "Description EN"));
	}

	@Test
	void theFirstAppearanceIsTheChapterOnlineInTheSameLanguage() {
		UUID chapter = chapterOnline(1, "it");
		UUID strawHats = content();
		set(affiliation(strawHats, 1, PUBLISHED, "Mugiwara no Ichimi", BOTH), "first_appearance_chapter_id", chapter);

		var rows = this.jdbc
			.sql("select language, first_appearance_chapter_id, first_appearance_chapter_slug,"
					+ " first_appearance_chapter_number, first_appearance_chapter_title from published.affiliation")
			.query((rs, n) -> tuple(rs.getString("language"), rs.getObject("first_appearance_chapter_id"),
					rs.getString("first_appearance_chapter_slug"), rs.getObject("first_appearance_chapter_number"),
					rs.getString("first_appearance_chapter_title")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(tuple("it", chapter, "1", 1, "Title IT"),
				tuple("en", chapter, null, null, null));
	}

	@Test
	void theEmblemOfAnAffiliationOnlineIsAPublishedImageAndADraftOneIsNot() {
		String online = image('d');
		String draft = image('e');
		set(affiliation(content(), 1, PUBLISHED, "Mugiwara no Ichimi", "it"), "image_id", online);
		set(affiliation(content(), 1, "DRAFT", "Kaigun", "it"), "image_id", draft);

		var ids = this.jdbc.sql("select id from published.image").query(String.class).list();

		assertThat(ids).containsExactly(online);
	}

	private UUID content() {
		return content("AFFILIATION");
	}

	private UUID content(String entityType) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("insert into content (id, entity_type, created_at) values (?, ?, ?)")
			.params(id, entityType, EARLIER)
			.update();
		return id;
	}

	private UUID version(UUID contentId, int versionNumber, String status) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into content_version (id, content_id, version_number, author_user_id, author_username,
				                             author_email, status, created_at, updated_at)
				values (?, ?, ?, ?, 'chopper', 'chopper@onepiece.local', ?, ?, ?)""")
			.params(id, contentId, versionNumber, UUID.randomUUID(), status, EARLIER, EARLIER)
			.update();
		return id;
	}

	/**
	 * Seeds an affiliation version with "Name LANGUAGE" and "Description LANGUAGE" in
	 * each given language; returns its version id.
	 */
	private UUID affiliation(UUID contentId, int versionNumber, String status, String romaji, String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into affiliation_version (version_id, romaji) values (?, ?)").params(id, romaji).update();
		for (String language : languages) {
			this.jdbc
				.sql("insert into affiliation_version_translation (version_id, language_code, name, description)"
						+ " values (?, ?, ?, ?)")
				.params(id, language, "Name " + language.toUpperCase(), "Description " + language.toUpperCase())
				.update();
		}
		return id;
	}

	/** Sets one column of an affiliation version; the column is this test's constant. */
	private void set(UUID versionId, String column, Object value) {
		this.jdbc.sql("update affiliation_version set " + column + " = ? where version_id = ?")
			.params(value, versionId)
			.update();
	}

	/** A chapter online, of an arc and a saga online, titled "Title LANGUAGE". */
	private UUID chapterOnline(int number, String... languages) {
		UUID saga = content("SAGA");
		UUID sagaVersion = version(saga, 1, PUBLISHED);
		this.jdbc.sql("insert into saga_version (version_id, number) values (?, 1)").params(sagaVersion).update();
		titles("saga_version", sagaVersion, languages);
		UUID arc = content("ARC");
		UUID arcVersion = version(arc, 1, PUBLISHED);
		this.jdbc.sql("insert into arc_version (version_id, number, saga_content_id) values (?, 1, ?)")
			.params(arcVersion, saga)
			.update();
		titles("arc_version", arcVersion, languages);
		UUID chapter = content("CHAPTER");
		UUID chapterVersion = version(chapter, 1, PUBLISHED);
		this.jdbc.sql("insert into chapter_version (version_id, number, arc_content_id) values (?, ?, ?)")
			.params(chapterVersion, number, arc)
			.update();
		titles("chapter_version", chapterVersion, languages);
		return chapter;
	}

	/** The table name is this test's constant. */
	private void titles(String table, UUID versionId, String... languages) {
		for (String language : languages) {
			this.jdbc.sql("insert into " + table + "_translation (version_id, language_code, title) values (?, ?, ?)")
				.params(versionId, language, "Title " + language.toUpperCase())
				.update();
		}
	}

	/**
	 * Seeds a stored image whose id is the given digit repeated: its bytes do not matter
	 * here.
	 */
	private String image(char digit) {
		String id = String.valueOf(digit).repeat(64);
		this.jdbc
			.sql("insert into image (id, content_type, width, height, size_bytes, bytes, created_at)"
					+ " values (?, 'image/png', 600, 420, 1, ?, ?)")
			.params(id, new byte[] { 1 }, EARLIER)
			.update();
		return id;
	}

}
