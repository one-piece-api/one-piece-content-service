package dev.onepieceapi.contentservice.domain.devilfruittype;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DevilFruitTypeSortFieldTest {

	@Test
	void aSortFieldIsFoundByTheNameUsedInARequest() {
		assertThat(DevilFruitTypeSortField.of("updatedAt")).contains(DevilFruitTypeSortField.UPDATED_AT);
		assertThat(DevilFruitTypeSortField.of("romaji")).contains(DevilFruitTypeSortField.ROMAJI);
		assertThat(DevilFruitTypeSortField.of("name")).contains(DevilFruitTypeSortField.NAME);
		assertThat(DevilFruitTypeSortField.of("author")).contains(DevilFruitTypeSortField.AUTHOR);
		assertThat(DevilFruitTypeSortField.of("status")).contains(DevilFruitTypeSortField.STATUS);
		assertThat(DevilFruitTypeSortField.of("UPDATED_AT")).isEmpty();
	}

}
