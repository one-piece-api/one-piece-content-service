package dev.onepieceapi.contentservice.domain.character;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

/** The kinds of relative and what each reads as from the other side (D13). */
class RelativeKindTest {

	@ParameterizedTest
	@CsvSource({ "PARENT,CHILD", "CHILD,PARENT", "GRANDPARENT,GRANDCHILD", "GRANDCHILD,GRANDPARENT",
			"UNCLE_AUNT,NEPHEW_NIECE", "NEPHEW_NIECE,UNCLE_AUNT", "ADOPTIVE_PARENT,ADOPTIVE_CHILD",
			"ADOPTIVE_CHILD,ADOPTIVE_PARENT", "SIBLING,SIBLING", "COUSIN,COUSIN", "SPOUSE,SPOUSE",
			"SWORN_SIBLING,SWORN_SIBLING" })
	void eachKindReadsFromTheOtherSideAsItsInverse(RelativeKind kind, RelativeKind inverse) {
		assertThat(kind.inverse()).isEqualTo(inverse);
	}

	@ParameterizedTest
	@EnumSource(RelativeKind.class)
	void theInverseOfTheInverseIsTheKindItself(RelativeKind kind) {
		assertThat(kind.inverse().inverse()).isEqualTo(kind);
	}

	@Test
	void thereAreTwelveKinds() {
		assertThat(RelativeKind.values()).hasSize(12);
	}

}
