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
 * The Character in the interface one-piece-public-api reads ({@code V27}, {@code V33})
 * against a real PostgreSQL (Testcontainers): only the versions online are shown, one row
 * per language, with their epithets, aliases, species and memberships in the order of the
 * version, and the portrait visible only while a character online uses it. Seeded in SQL,
 * as the table owner, and read through the {@code published} views.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedCharacterViewIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	private static final String[] BOTH = { "it", "en" };

	private static final String PUBLISHED = "PUBLISHED";

	private static final String CHARACTER = "CHARACTER";

	private static final String SPECIES = "SPECIES";

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
	}

	@Test
	void onlyTheVersionsOnlineAreShownOnePerLanguageWithTheirValues() {
		UUID luffy = content(CHARACTER);
		character(luffy, 1, "SUPERSEDED", "Monkey D. Luffy", BOTH);
		UUID online = character(luffy, 2, PUBLISHED, "Monkey D. Luffy", BOTH);
		this.jdbc.sql("""
				update character_version set japanese_name = 'Monkii Dii Rufi', status = 'ALIVE', gender = 'MALE',
				                             birthday_month = 5, birthday_day = 5
				where version_id = ?""").params(online).update();
		character(content(CHARACTER), 1, "DRAFT", "Nami", BOTH);
		character(content(CHARACTER), 1, "RETIRED", "Usopp", BOTH);

		var rows = this.jdbc.sql("""
				select id, slug, romaji, japanese_name, language, name, description, status, gender, birthday_month,
				       birthday_day
				from published.character""")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getString("slug"), rs.getString("romaji"),
					rs.getString("japanese_name"), rs.getString("language"), rs.getString("name"),
					rs.getString("description"), rs.getString("status"), rs.getString("gender"),
					rs.getObject("birthday_month"), rs.getObject("birthday_day")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(
				tuple(luffy, "monkey-d-luffy", "Monkey D. Luffy", "Monkii Dii Rufi", "it", "Name IT", "Description IT",
						"ALIVE", "MALE", 5, 5),
				tuple(luffy, "monkey-d-luffy", "Monkey D. Luffy", "Monkii Dii Rufi", "en", "Name EN", "Description EN",
						"ALIVE", "MALE", 5, 5));
	}

	@Test
	void theFirstAppearanceIsTheChapterOnlineInTheSameLanguage() {
		UUID chapter = chapterOnline(1, "it");
		UUID luffy = content(CHARACTER);
		UUID version = character(luffy, 1, PUBLISHED, "Monkey D. Luffy", BOTH);
		this.jdbc.sql("update character_version set first_appearance_chapter_id = ? where version_id = ?")
			.params(chapter, version)
			.update();

		var rows = this.jdbc
			.sql("select language, first_appearance_chapter_id, first_appearance_chapter_slug,"
					+ " first_appearance_chapter_number, first_appearance_chapter_title from published.character")
			.query((rs, n) -> tuple(rs.getString("language"), rs.getObject("first_appearance_chapter_id"),
					rs.getString("first_appearance_chapter_slug"), rs.getObject("first_appearance_chapter_number"),
					rs.getString("first_appearance_chapter_title")))
			.list();

		assertThat(rows).containsExactlyInAnyOrder(tuple("it", chapter, "1", 1, "Title IT"),
				tuple("en", chapter, null, null, null));
	}

	@Test
	void theEpithetsOfTheVersionOnlineAreOneRowPerLanguageInTheirOrder() {
		UUID nami = content(CHARACTER);
		UUID superseded = character(nami, 1, "SUPERSEDED", "Nami", BOTH);
		epithet(superseded, 0, "it", "Vecchio");
		UUID online = character(nami, 2, PUBLISHED, "Nami", BOTH);
		epithet(online, 1, "it", "Gatta ladra", "en", "Cat Burglar");
		epithet(online, 0, "it", "Navigatrice", "en", "Navigator");
		epithet(online, 2, "it", "Solo italiano");

		var rows = this.jdbc
			.sql("select character_id, language, position, text from published.character_epithet"
					+ " order by language, position")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getString("language"), rs.getInt("position"),
					rs.getString("text")))
			.list();

		assertThat(rows).containsExactly(tuple(nami, "en", 0, "Navigator"), tuple(nami, "en", 1, "Cat Burglar"),
				tuple(nami, "it", 0, "Navigatrice"), tuple(nami, "it", 1, "Gatta ladra"),
				tuple(nami, "it", 2, "Solo italiano"));
	}

	@Test
	void theAliasesOfTheVersionOnlineAreShownInTheirOrder() {
		UUID usopp = content(CHARACTER);
		UUID online = character(usopp, 1, PUBLISHED, "Usopp", "it");
		alias(online, 1, "Sogeking");
		alias(online, 0, "Usopp il Bugiardo");
		alias(character(usopp, 2, "DRAFT", "Usopp", "it"), 0, "God Usopp");

		var rows = this.jdbc
			.sql("select character_id, position, alias from published.character_alias order by position")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getInt("position"), rs.getString("alias")))
			.list();

		assertThat(rows).containsExactly(tuple(usopp, 0, "Usopp il Bugiardo"), tuple(usopp, 1, "Sogeking"));
	}

	@Test
	void theSpeciesOfTheVersionOnlineAreShownInTheirOrderAndOnlyThoseOnline() {
		UUID humans = speciesOnline("Ningen");
		UUID giants = speciesOnline("Kyojin");
		UUID retired = content(SPECIES);
		this.jdbc.sql("insert into species_version (version_id, romaji) values (?, 'Mink')")
			.params(version(retired, 1, "RETIRED"))
			.update();
		UUID luffy = content(CHARACTER);
		UUID online = character(luffy, 1, PUBLISHED, "Monkey D. Luffy", "it");
		species(online, 0, giants);
		species(online, 1, retired);
		species(online, 2, humans);

		var rows = this.jdbc
			.sql("select character_id, position, species_id from published.character_species order by position")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getInt("position"), rs.getObject("species_id")))
			.list();

		assertThat(rows).containsExactly(tuple(luffy, 0, giants), tuple(luffy, 2, humans));
	}

	@Test
	void theMembershipsOfTheVersionOnlineAreShownInTheirOrderAndOnlyInAffiliationsOnlineWithTheirRoles() {
		UUID mugiwara = affiliationOnline("Mugiwara no Ichimi");
		UUID kaigun = affiliationOnline("Kaigun");
		UUID retired = content("AFFILIATION");
		this.jdbc.sql("insert into affiliation_version (version_id, romaji) values (?, 'Baroque Works')")
			.params(version(retired, 1, "RETIRED"))
			.update();
		UUID koby = content(CHARACTER);
		UUID online = character(koby, 1, PUBLISHED, "Koby", "it");
		membership(online, 2, mugiwara, null, true);
		membership(online, 0, kaigun, "CAPTAIN", false, "it", "Capitano", "en", "Captain");
		membership(online, 1, retired, null, false, "it", "Ex");
		membership(character(koby, 2, "DRAFT", "Koby", "it"), 0, kaigun, "ENSIGN", false);

		var rows = this.jdbc.sql(
				"select character_id, position, affiliation_id, marine_rank, former from published.character_affiliation"
						+ " order by position")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getInt("position"), rs.getObject("affiliation_id"),
					rs.getString("marine_rank"), rs.getBoolean("former")))
			.list();
		var roles = this.jdbc
			.sql("select character_id, position, language, role from published.character_affiliation_role"
					+ " order by position, language")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getInt("position"), rs.getString("language"),
					rs.getString("role")))
			.list();

		assertThat(rows).containsExactly(tuple(koby, 0, kaigun, "CAPTAIN", false),
				tuple(koby, 2, mugiwara, null, true));
		assertThat(roles).containsExactly(tuple(koby, 0, "en", "Captain"), tuple(koby, 0, "it", "Capitano"));
	}

	@Test
	void thePortraitOfACharacterOnlineIsAPublishedImageAndADraftOneIsNot() {
		String online = image('d');
		String draft = image('e');
		portrait(character(content(CHARACTER), 1, PUBLISHED, "Monkey D. Luffy", "it"), online);
		portrait(character(content(CHARACTER), 1, "DRAFT", "Nami", "it"), draft);

		var ids = this.jdbc.sql("select id from published.image").query(String.class).list();

		assertThat(ids).containsExactly(online);
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
	 * Seeds a character version with "Name LANGUAGE" and "Description LANGUAGE" in each
	 * given language; returns its version id.
	 */
	private UUID character(UUID contentId, int versionNumber, String status, String romaji, String... languages) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into character_version (version_id, romaji) values (?, ?)").params(id, romaji).update();
		for (String language : languages) {
			this.jdbc
				.sql("insert into character_version_translation (version_id, language_code, name, description)"
						+ " values (?, ?, ?, ?)")
				.params(id, language, "Name " + language.toUpperCase(), "Description " + language.toUpperCase())
				.update();
		}
		return id;
	}

	/** Seeds an epithet at a position, with its texts as language and text pairs. */
	private void epithet(UUID versionId, int position, String... languageAndText) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("insert into character_version_epithet (id, version_id, position) values (?, ?, ?)")
			.params(id, versionId, position)
			.update();
		for (int i = 0; i < languageAndText.length; i += 2) {
			this.jdbc
				.sql("insert into character_version_epithet_translation (epithet_id, language_code, text)"
						+ " values (?, ?, ?)")
				.params(id, languageAndText[i], languageAndText[i + 1])
				.update();
		}
	}

	private void alias(UUID versionId, int position, String alias) {
		this.jdbc.sql("insert into character_version_alias (version_id, position, alias) values (?, ?, ?)")
			.params(versionId, position, alias)
			.update();
	}

	private void species(UUID versionId, int position, UUID speciesId) {
		this.jdbc
			.sql("insert into character_version_species (version_id, position, species_content_id) values (?, ?, ?)")
			.params(versionId, position, speciesId)
			.update();
	}

	/** Seeds a membership at a position, with its role as language and text pairs. */
	private void membership(UUID versionId, int position, UUID affiliationId, String rank, boolean former,
			String... languageAndRole) {
		UUID id = UUID.randomUUID();
		this.jdbc
			.sql("""
					insert into character_version_affiliation (id, version_id, position, affiliation_content_id, marine_rank,
					                                           former)
					values (?, ?, ?, ?, ?, ?)""")
			.params(id, versionId, position, affiliationId, rank, former)
			.update();
		for (int i = 0; i < languageAndRole.length; i += 2) {
			this.jdbc
				.sql("insert into character_version_affiliation_translation (membership_id, language_code, role)"
						+ " values (?, ?, ?)")
				.params(id, languageAndRole[i], languageAndRole[i + 1])
				.update();
		}
	}

	private UUID affiliationOnline(String romaji) {
		UUID id = content("AFFILIATION");
		this.jdbc.sql("insert into affiliation_version (version_id, romaji) values (?, ?)")
			.params(version(id, 1, PUBLISHED), romaji)
			.update();
		return id;
	}

	private UUID speciesOnline(String romaji) {
		UUID id = content(SPECIES);
		this.jdbc.sql("insert into species_version (version_id, romaji) values (?, ?)")
			.params(version(id, 1, PUBLISHED), romaji)
			.update();
		return id;
	}

	private void portrait(UUID versionId, String imageId) {
		this.jdbc.sql("update character_version set image_id = ? where version_id = ?")
			.params(imageId, versionId)
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
