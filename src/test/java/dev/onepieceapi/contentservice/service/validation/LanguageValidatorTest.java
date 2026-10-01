package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.language.Language;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageCodeException;
import dev.onepieceapi.contentservice.service.exception.InvalidLanguageNameException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LanguageValidatorTest {

	private final LanguageValidator validator = new LanguageValidator();

	@Test
	void aTwoLetterCodeWithANameIsValid() {
		assertThatCode(() -> this.validator.validate(new Language("fr", "Français"))).doesNotThrowAnyException();
		assertThatCode(() -> this.validator.validate(new Language("es", "x".repeat(100)))).doesNotThrowAnyException();
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "f", "fra", "FR", "f1", "12", "f r" })
	void aCodeThatIsNotTwoLowercaseLettersIsRefused(String code) {
		assertThatThrownBy(() -> this.validator.validate(new Language(code, "Français")))
			.isInstanceOf(InvalidLanguageCodeException.class);
	}

	@Test
	void aBlankOrTooLongNameIsRefused() {
		assertThatThrownBy(() -> this.validator.validate(new Language("fr", "")))
			.isInstanceOf(InvalidLanguageNameException.class);
		assertThatThrownBy(() -> this.validator.validate(new Language("fr", "   ")))
			.isInstanceOf(InvalidLanguageNameException.class);
		assertThatThrownBy(() -> this.validator.validate(new Language("fr", "x".repeat(101))))
			.isInstanceOf(InvalidLanguageNameException.class);
	}

	@Test
	void theCodeIsCheckedBeforeTheName() {
		assertThatThrownBy(() -> this.validator.validate(new Language("123", "")))
			.isInstanceOf(InvalidLanguageCodeException.class);
	}

}
