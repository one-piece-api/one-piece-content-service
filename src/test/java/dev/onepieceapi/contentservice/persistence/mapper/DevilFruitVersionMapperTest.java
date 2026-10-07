package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruit;
import dev.onepieceapi.contentservice.domain.devilfruit.DevilFruitTranslation;
import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import dev.onepieceapi.contentservice.persistence.entity.DevilFruitVersionEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

class DevilFruitVersionMapperTest {

	private static final UUID CONTENT_ID = UUID.randomUUID();

	private static final UUID TYPE_ID = UUID.randomUUID();

	private static final Instant CREATED = Instant.parse("2026-09-30T08:00:00Z");

	private static final Instant UPDATED = Instant.parse("2026-10-01T09:30:00Z");

	private static final User CHOPPER = new User(UUID.randomUUID(), "chopper", "chopper@onepiece.local");

	@Test
	void aVersionTakesItsWorkflowFromTheSharedRowAndWhatItSaysFromItsOwn() {
		var entity = draft();
		var body = new DevilFruit("Gomu Gomu", TYPE_ID,
				Map.of("it", new DevilFruitTranslation("Gom Gom", "Elastico", "Pro", "Contro")));

		DevilFruitVersionMapper.rewrite(entity, body, UPDATED);
		var version = DevilFruitVersionMapper.toDomain(entity);

		assertThat(version.number()).isEqualTo(1);
		assertThat(version.status()).isEqualTo(VersionStatus.DRAFT);
		assertThat(version.author()).isEqualTo(CHOPPER);
		assertThat(version.updatedAt()).isEqualTo(UPDATED);
		assertThat(version.body()).isEqualTo(body);
	}

	@Test
	void rewritingReplacesWhatTheVersionSaidIncludingItsType() {
		var entity = draft();
		DevilFruitVersionMapper.rewrite(entity,
				new DevilFruit("Gomu", TYPE_ID, Map.of("it", new DevilFruitTranslation("Gom", null, null, null), "en",
						new DevilFruitTranslation("Rubber", null, null, null))),
				CREATED);

		DevilFruitVersionMapper.rewrite(entity,
				new DevilFruit("Mera", null, Map.of("en", new DevilFruitTranslation("Fire", null, null, null))),
				UPDATED);

		assertThat(entity.getRomaji()).isEqualTo("Mera");
		assertThat(entity.getTypeContentId()).isNull();
		assertThat(entity.getTranslations()).containsOnlyKeys("en");
		assertThat(DevilFruitVersionMapper.toDomain(entity).body().translations())
			.containsExactly(entry("en", new DevilFruitTranslation("Fire", null, null, null)));
	}

	private static DevilFruitVersionEntity draft() {
		var workflow = ContentVersionMapper.toDraft(CONTENT_ID, 1, null, CHOPPER, CREATED);
		return new DevilFruitVersionEntity(workflow);
	}

}
