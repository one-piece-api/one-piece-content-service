package dev.onepieceapi.contentservice.domain.workflow;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContentSortFieldTest {

	@Test
	void aSortFieldIsFoundByTheNameUsedInARequest() {
		assertThat(ContentSortField.of("updatedAt")).contains(ContentSortField.UPDATED_AT);
		assertThat(ContentSortField.of("romaji")).contains(ContentSortField.ROMAJI);
		assertThat(ContentSortField.of("name")).contains(ContentSortField.NAME);
		assertThat(ContentSortField.of("author")).contains(ContentSortField.AUTHOR);
		assertThat(ContentSortField.of("status")).contains(ContentSortField.STATUS);
		assertThat(ContentSortField.of("UPDATED_AT")).isEmpty();
	}

}
