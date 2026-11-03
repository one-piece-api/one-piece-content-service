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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase.Replace;

/**
 * The reader axis in the interface one-piece-public-api reads ({@code V42}) against a
 * real PostgreSQL (Testcontainers): every point as the number of a chapter or episode
 * online, null when that chapter or episode is not online; membership periods,
 * paragraphs, gallery, initial knowledge and reveals of the versions online only. Seeded
 * in SQL, as the table owner, and read through the {@code published} views.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@Testcontainers
class PublishedReaderAxisViewIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6");

	private static final Timestamp EARLIER = Timestamp.from(Instant.parse("2026-10-01T10:00:00Z"));

	private static final String PUBLISHED = "PUBLISHED";

	private static final String DRAFT = "DRAFT";

	private static final String CHARACTER = "CHARACTER";

	@Autowired
	private DataSource dataSource;

	private JdbcClient jdbc;

	@BeforeEach
	void setUp() {
		this.jdbc = JdbcClient.create(this.dataSource);
	}

	@Test
	void chaptersAndEpisodesAreNumberedByTheirVersionOnline() {
		UUID romance = content("CHAPTER");
		numbered("chapter", romance, 1, "SUPERSEDED", 10);
		numbered("chapter", romance, 2, PUBLISHED, 1);
		UUID draftOnly = content("CHAPTER");
		numbered("chapter", draftOnly, 1, DRAFT, 2);
		UUID episode = episodeOnline(1);

		var chapters = this.jdbc.sql("select id, number from published.chapter_number")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getInt("number")))
			.list();
		var episodes = this.jdbc.sql("select id, number from published.episode_number")
			.query((rs, n) -> tuple(rs.getObject("id"), rs.getInt("number")))
			.list();

		assertThat(chapters).containsExactly(tuple(romance, 1));
		assertThat(episodes).containsExactly(tuple(episode, 1));
	}

	@Test
	void aWindowReadsAsNumbersAndAPartNotOnlineIsNull() {
		UUID chapterFive = chapterOnline(5);
		UUID episodeThree = episodeOnline(3);
		UUID chapterDraft = content("CHAPTER");
		numbered("chapter", chapterDraft, 1, DRAFT, 9);
		UUID luffy = content(CHARACTER);
		UUID online = character(luffy, 1, PUBLISHED, "Monkey D. Luffy");
		this.jdbc.sql("""
				insert into character_version_alias (version_id, position, alias, discovered_chapter_id,
				                                     discovered_episode_id, ended_chapter_id)
				values (?, 0, 'Mugiwara', ?, ?, ?)""").params(online, chapterFive, episodeThree, chapterDraft).update();
		UUID draft = character(luffy, 2, DRAFT, "Monkey D. Luffy");
		this.jdbc.sql("insert into character_version_alias (version_id, position, alias) values (?, 0, 'Draft')")
			.params(draft)
			.update();

		var aliases = this.jdbc.sql("""
				select alias, discovered_chapter, discovered_episode, ended_chapter, ended_episode
				from published.character_alias""")
			.query((rs, n) -> tuple(rs.getString("alias"), rs.getObject("discovered_chapter"),
					rs.getObject("discovered_episode"), rs.getObject("ended_chapter"), rs.getObject("ended_episode")))
			.list();

		assertThat(aliases).containsExactly(tuple("Mugiwara", 5, 3, null, null));
	}

	@Test
	void theMembershipPeriodsAreListedWithTheirWindowAndRoles() {
		UUID chapterTwo = chapterOnline(2);
		UUID chapterSeven = chapterOnline(7);
		UUID marines = affiliationOnline("kaigun");
		UUID coby = content(CHARACTER);
		UUID online = character(coby, 1, PUBLISHED, "Coby");
		UUID membership = membership(online, 0, marines);
		period(membership, 0, "CHORE_BOY", chapterTwo, chapterSeven, "it", "Mozzo");
		period(membership, 1, "PETTY_OFFICER", chapterSeven, null, "it", "Sottufficiale");

		var periods = this.jdbc.sql("""
				select membership_position, position, marine_rank, discovered_chapter, ended_chapter
				from published.character_affiliation_period order by position""")
			.query((rs, n) -> tuple(rs.getInt("membership_position"), rs.getInt("position"),
					rs.getString("marine_rank"), rs.getObject("discovered_chapter"), rs.getObject("ended_chapter")))
			.list();
		var roles = this.jdbc.sql("""
				select position, language, role
				from published.character_affiliation_period_role order by position""")
			.query((rs, n) -> tuple(rs.getInt("position"), rs.getString("language"), rs.getString("role")))
			.list();

		assertThat(periods).containsExactly(tuple(0, 0, "CHORE_BOY", 2, 7), tuple(0, 1, "PETTY_OFFICER", 7, null));
		assertThat(roles).containsExactly(tuple(0, "it", "Mozzo"), tuple(1, "it", "Sottufficiale"));
	}

	@Test
	void theParagraphsOfEveryItemOnlineAreListedWithTheirWindow() {
		UUID chapterOne = chapterOnline(1);
		UUID luffy = content(CHARACTER);
		UUID online = character(luffy, 1, PUBLISHED, "Monkey D. Luffy");
		ParagraphRows.paragraph(this.jdbc, "character", online, 0, "DESCRIPTION", Map.of("it", "Un pirata"));
		this.jdbc.sql("update character_version_paragraph set discovered_chapter_id = ? where version_id = ?")
			.params(chapterOne, online)
			.update();
		UUID species = content("SPECIES");
		UUID speciesVersion = version(species, 1, PUBLISHED);
		this.jdbc.sql("insert into species_version (version_id, romaji) values (?, 'ningen')")
			.params(speciesVersion)
			.update();
		ParagraphRows.paragraph(this.jdbc, "species", speciesVersion, 0, "DESCRIPTION", Map.of("en", "Humans"));
		UUID draft = character(luffy, 2, DRAFT, "Monkey D. Luffy");
		ParagraphRows.paragraph(this.jdbc, "character", draft, 0, "DESCRIPTION", Map.of("it", "Bozza"));

		var paragraphs = this.jdbc.sql("""
				select content_id, section, position, language, text, discovered_chapter
				from published.paragraph order by text""")
			.query((rs, n) -> tuple(rs.getObject("content_id"), rs.getString("section"), rs.getInt("position"),
					rs.getString("language"), rs.getString("text"), rs.getObject("discovered_chapter")))
			.list();

		assertThat(paragraphs).containsExactly(tuple(species, "DESCRIPTION", 0, "en", "Humans", null),
				tuple(luffy, "DESCRIPTION", 0, "it", "Un pirata", 1));
	}

	@Test
	void theGalleryIsListedWithItsKindWindowAndCaptions() {
		UUID chapterOne = chapterOnline(1);
		UUID luffy = content(CHARACTER);
		UUID online = character(luffy, 1, PUBLISHED, "Monkey D. Luffy");
		String image = image('a');
		UUID galleryImage = UUID.randomUUID();
		this.jdbc.sql("""
				insert into character_version_image (id, version_id, position, image_id, kind, discovered_chapter_id)
				values (?, ?, 0, ?, 'ASPETTO', ?)""").params(galleryImage, online, image, chapterOne).update();
		this.jdbc
			.sql("insert into character_version_image_translation (gallery_image_id, language_code, caption)"
					+ " values (?, 'it', 'Il ragazzo di gomma')")
			.params(galleryImage)
			.update();

		var gallery = this.jdbc.sql("""
				select character_id, position, image_id, kind, discovered_chapter, ended_chapter
				from published.character_gallery""")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getInt("position"), rs.getString("image_id"),
					rs.getString("kind"), rs.getObject("discovered_chapter"), rs.getObject("ended_chapter")))
			.list();
		var captions = this.jdbc.sql("select position, language, caption from published.character_gallery_caption")
			.query((rs, n) -> tuple(rs.getInt("position"), rs.getString("language"), rs.getString("caption")))
			.list();

		assertThat(gallery).containsExactly(tuple(luffy, 0, image, "ASPETTO", 1, null));
		assertThat(captions).containsExactly(tuple(0, "it", "Il ragazzo di gomma"));
	}

	@Test
	void aCharactersInitialKnowledgeAndRevealsAreListedWithTheFieldsTheyName() {
		UUID chapterNika = chapterOnline(1044);
		UUID joyBoy = content(CHARACTER);
		UUID online = character(joyBoy, 1, PUBLISHED, "Joy Boy");
		this.jdbc
			.sql("insert into character_version_initial_knowledge (version_id, romaji, status)"
					+ " values (?, 'Nika', 'UNKNOWN')")
			.params(online)
			.update();
		this.jdbc
			.sql("insert into character_version_initial_knowledge_translation (version_id, language_code, name)"
					+ " values (?, 'it', 'Nika')")
			.params(online)
			.update();
		UUID reveal = UUID.randomUUID();
		this.jdbc.sql("""
				insert into character_version_reveal (id, version_id, position, revealed_chapter_id)
				values (?, ?, 0, ?)""").params(reveal, online, chapterNika).update();
		for (String field : new String[] { "STATUS", "IDENTITY" }) {
			this.jdbc.sql("insert into character_version_reveal_field (reveal_id, field) values (?, ?)")
				.params(reveal, field)
				.update();
		}
		this.jdbc
			.sql("insert into character_version_reveal_translation (reveal_id, language_code, title)"
					+ " values (?, 'it', 'Il dio del sole')")
			.params(reveal)
			.update();

		var knowledge = this.jdbc
			.sql("select character_id, romaji, slug, status from published.character_initial_knowledge")
			.query((rs, n) -> tuple(rs.getObject("character_id"), rs.getString("romaji"), rs.getString("slug"),
					rs.getString("status")))
			.list();
		var reveals = this.jdbc.sql("""
				select position, fields, romaji, status, revealed_chapter, revealed_episode
				from published.character_reveal""")
			.query((rs, n) -> tuple(rs.getInt("position"), Arrays.asList((Object[]) rs.getArray("fields").getArray()),
					rs.getString("romaji"), rs.getString("status"), rs.getObject("revealed_chapter"),
					rs.getObject("revealed_episode")))
			.list();
		var titles = this.jdbc.sql("select language, title, name from published.character_reveal_translation")
			.query((rs, n) -> tuple(rs.getString("language"), rs.getString("title"), rs.getString("name")))
			.list();

		assertThat(knowledge).containsExactly(tuple(joyBoy, "Nika", "nika", "UNKNOWN"));
		assertThat(reveals).containsExactly(tuple(0, List.of("IDENTITY", "STATUS"), null, null, 1044, null));
		assertThat(titles).containsExactly(tuple("it", "Il dio del sole", null));
	}

	@Test
	void aBountysInitialKnowledgeAndRevealsAreThoseOfItsVersionOnline() {
		UUID marineford = chapterOnline(574);
		UUID poster = content("BOUNTY");
		UUID online = version(poster, 1, PUBLISHED);
		this.jdbc
			.sql("insert into bounty_version (version_id, poster_name, amount, condition)"
					+ " values (?, 'Portgas D. Ace', 550000000, 'DEAD_OR_ALIVE')")
			.params(online)
			.update();
		this.jdbc.sql("insert into bounty_version_initial_knowledge (version_id, inactive) values (?, false)")
			.params(online)
			.update();
		UUID reveal = UUID.randomUUID();
		this.jdbc.sql("""
				insert into bounty_version_reveal (id, version_id, position, revealed_chapter_id)
				values (?, ?, 0, ?)""").params(reveal, online, marineford).update();
		this.jdbc.sql("insert into bounty_version_reveal_field (reveal_id, field) values (?, 'INACTIVE')")
			.params(reveal)
			.update();

		var knowledge = this.jdbc.sql("select bounty_id, inactive from published.bounty_initial_knowledge")
			.query((rs, n) -> tuple(rs.getObject("bounty_id"), rs.getBoolean("inactive")))
			.list();
		var reveals = this.jdbc.sql("select bounty_id, fields, inactive, revealed_chapter from published.bounty_reveal")
			.query((rs, n) -> tuple(rs.getObject("bounty_id"),
					Arrays.asList((Object[]) rs.getArray("fields").getArray()), rs.getObject("inactive"),
					rs.getObject("revealed_chapter")))
			.list();

		assertThat(knowledge).containsExactly(tuple(poster, false));
		assertThat(reveals).containsExactly(tuple(poster, List.of("INACTIVE"), null, 574));
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
	 * A chapter or episode version with a number; the table prefix is this test's
	 * constant.
	 */
	private void numbered(String table, UUID contentId, int versionNumber, String status, int number) {
		this.jdbc.sql("insert into " + table + "_version (version_id, number) values (?, ?)")
			.params(version(contentId, versionNumber, status), number)
			.update();
	}

	private UUID chapterOnline(int number) {
		UUID chapter = content("CHAPTER");
		numbered("chapter", chapter, 1, PUBLISHED, number);
		return chapter;
	}

	private UUID episodeOnline(int number) {
		UUID episode = content("EPISODE");
		numbered("episode", episode, 1, PUBLISHED, number);
		return episode;
	}

	private UUID character(UUID contentId, int versionNumber, String status, String romaji) {
		UUID id = version(contentId, versionNumber, status);
		this.jdbc.sql("insert into character_version (version_id, romaji) values (?, ?)").params(id, romaji).update();
		return id;
	}

	private UUID affiliationOnline(String romaji) {
		UUID id = content("AFFILIATION");
		this.jdbc.sql("insert into affiliation_version (version_id, romaji) values (?, ?)")
			.params(version(id, 1, PUBLISHED), romaji)
			.update();
		return id;
	}

	private UUID membership(UUID versionId, int position, UUID affiliationId) {
		UUID id = UUID.randomUUID();
		this.jdbc
			.sql("insert into character_version_affiliation (id, version_id, position, affiliation_content_id)"
					+ " values (?, ?, ?, ?)")
			.params(id, versionId, position, affiliationId)
			.update();
		return id;
	}

	private void period(UUID membershipId, int position, String rank, UUID discoveredChapterId, UUID endedChapterId,
			String language, String role) {
		UUID id = UUID.randomUUID();
		this.jdbc.sql("""
				insert into character_version_affiliation_period (id, membership_id, position, marine_rank,
				                                                  discovered_chapter_id, ended_chapter_id)
				values (?, ?, ?, ?, ?, ?)""")
			.params(id, membershipId, position, rank, discoveredChapterId, endedChapterId)
			.update();
		this.jdbc
			.sql("insert into character_version_affiliation_period_translation (period_id, language_code, role)"
					+ " values (?, ?, ?)")
			.params(id, language, role)
			.update();
	}

	/**
	 * A stored image whose id is the given digit repeated: its bytes do not matter here.
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
