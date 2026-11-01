package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.readertime.StoryWindow;
import dev.onepieceapi.contentservice.domain.readertime.Windowed;
import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;

/**
 * One stay of a character in an affiliation (workflow 3.4.7): its window on the reader
 * axis, what the character is in it - "Capitano" / "Captain" - and, in the Navy, its
 * rank. Koby has one period per rank.
 *
 * @param role per language code, for the languages it is written in; none for no role
 * @param marineRank null for none; allowed only in a Navy affiliation
 */
public record MembershipPeriod(StoryWindow window, Map<String, String> role,
		MarineRank marineRank) implements Windowed {

	public MembershipPeriod {
		window = StoryWindow.orNone(window);
		role = role == null ? Map.of() : Map.copyOf(role);
	}

	/**
	 * Every text of the role without the space around it; a language left blank is not
	 * written.
	 */
	MembershipPeriod normalized() {
		return new MembershipPeriod(this.window, Text.written(this.role), this.marineRank);
	}

	/** What the role says in this language; null when nothing is written in it. */
	public String roleIn(String language) {
		return this.role.get(language);
	}

	/** Whether the role is written in at least one language. */
	public boolean hasRole() {
		return !this.role.isEmpty();
	}

}
