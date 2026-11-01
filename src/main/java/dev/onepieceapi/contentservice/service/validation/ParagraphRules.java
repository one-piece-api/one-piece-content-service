package dev.onepieceapi.contentservice.service.validation;

import dev.onepieceapi.contentservice.domain.readertime.Paragraph;
import dev.onepieceapi.contentservice.domain.readertime.ParagraphSection;
import dev.onepieceapi.contentservice.domain.readertime.Paragraphed;
import dev.onepieceapi.contentservice.service.content.ReaderAxisRules;
import dev.onepieceapi.exception.web.FieldViolation;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The rules on the paragraphs of an item, the same for every entity
 * (docs/user-flows/content-editorial-workflow.md 3.7.3, 3.7.4), checked on every save and
 * again at submission: no paragraph written in no language, and every point linkable and
 * in order on the reader axis.
 */
@Component
@RequiredArgsConstructor
public class ParagraphRules {

	private static final String BLANK = "must not be blank";

	private final ReaderAxisRules readerAxisRules;

	/** Each blank paragraph, then each point at fault. */
	public List<FieldViolation> violationsOnSave(Paragraphed item) {
		List<FieldViolation> violations = new ArrayList<>(blankViolations(item));
		violations.addAll(this.readerAxisRules.violationsOnSave(item));
		return violations;
	}

	/**
	 * Each paragraph written in no language, e.g. {@code description[1]}: for an item
	 * whose points are checked with its other windows.
	 */
	public static List<FieldViolation> blankViolations(Paragraphed item) {
		List<FieldViolation> violations = new ArrayList<>();
		item.paragraphs().forEach((section, paragraphs) -> addBlank(section, paragraphs, violations));
		return violations;
	}

	private static void addBlank(ParagraphSection section, List<Paragraph> paragraphs,
			List<FieldViolation> violations) {
		for (int index = 0; index < paragraphs.size(); index++) {
			if (paragraphs.get(index).isEmpty()) {
				violations.add(new FieldViolation(section.entryField(index), BLANK));
			}
		}
	}

}
