package dev.onepieceapi.contentservice.domain.workflow;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NaturalKeyTest {

	@Test
	void aKeyMadeOfOneFieldIsNamedByIt() {
		var key = new NaturalKey("romaji", "Gomu Gomu no Mi");

		assertThat(key.fields()).containsExactly("romaji");
	}

	@Test
	void noFieldsMeansTheFieldItself() {
		assertThat(new NaturalKey("number", 1044, null).fields()).containsExactly("number");
		assertThat(new NaturalKey("number", 1044, List.of()).fields()).containsExactly("number");
	}

	@Test
	void aCompositeKeyKeepsItsFieldsInOrder() {
		var key = new NaturalKey("key", "Monkey D. Luffy 3000000000", List.of("posterName", "amount"));

		assertThat(key.field()).isEqualTo("key");
		assertThat(key.fields()).containsExactly("posterName", "amount");
		assertThat(key.text()).isEqualTo("Monkey D. Luffy 3000000000");
	}

	@Test
	void theFieldsAreACopyThatCannotBeChanged() {
		var given = new ArrayList<>(List.of("posterName", "amount"));
		var key = new NaturalKey("key", "x", given);

		given.add("other");

		assertThat(key.fields()).containsExactly("posterName", "amount");
		assertThatThrownBy(() -> key.fields().add("other")).isInstanceOf(UnsupportedOperationException.class);
	}

}
