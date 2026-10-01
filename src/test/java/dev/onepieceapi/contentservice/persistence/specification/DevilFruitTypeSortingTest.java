package dev.onepieceapi.contentservice.persistence.specification;

import dev.onepieceapi.contentservice.domain.devilfruittype.DevilFruitTypeSortField;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class DevilFruitTypeSortingTest {

	@Test
	void withoutASortTheListIsByLastUpdateNewestFirstThenByContent() {
		var resolved = DevilFruitTypeSorting.resolve(PageRequest.of(3, 10));

		assertThat(resolved.getPageNumber()).isEqualTo(3);
		assertThat(resolved.getPageSize()).isEqualTo(10);
		assertThat(resolved.getSort()).extracting(Sort.Order::getProperty, Sort.Order::getDirection)
			.containsExactly(tuple("version.updatedAt", Sort.Direction.DESC),
					tuple("version.itemId", Sort.Direction.ASC));
	}

	@Test
	void theFieldsAskedForBecomeEntityPathsKeepingOrderAndDirection() {
		var requested = Sort.by(Sort.Order.asc("romaji"), Sort.Order.desc("updatedAt"));

		var resolved = DevilFruitTypeSorting.resolve(PageRequest.of(0, 20, requested));

		assertThat(resolved.getSort()).extracting(Sort.Order::getProperty, Sort.Order::getDirection)
			.containsExactly(tuple("romaji", Sort.Direction.ASC), tuple("version.updatedAt", Sort.Direction.DESC),
					tuple("version.itemId", Sort.Direction.ASC));
	}

	@Test
	void everySortableFieldHasAPath() {
		Arrays.stream(DevilFruitTypeSortField.values()).forEach(field -> {
			var resolved = DevilFruitTypeSorting.resolve(PageRequest.of(0, 20, Sort.by(field.field())));
			assertThat(resolved.getSort().toList().get(0).getProperty()).as("%s", field).isNotNull();
		});
	}

	@Test
	void aFieldThatSlippedPastValidationIsABugNotAQuery() {
		var unknown = PageRequest.of(0, 20, Sort.by("authorEmail"));

		assertThatThrownBy(() -> DevilFruitTypeSorting.resolve(unknown)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("authorEmail");
	}

	@Test
	void aSortFieldIsFoundByTheNameUsedInARequest() {
		assertThat(DevilFruitTypeSortField.of("updatedAt")).contains(DevilFruitTypeSortField.UPDATED_AT);
		assertThat(DevilFruitTypeSortField.of("romaji")).contains(DevilFruitTypeSortField.ROMAJI);
		assertThat(DevilFruitTypeSortField.of("UPDATED_AT")).isEmpty();
	}

}
