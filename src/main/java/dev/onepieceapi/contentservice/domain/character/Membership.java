package dev.onepieceapi.contentservice.domain.character;

import dev.onepieceapi.contentservice.domain.workflow.Text;

import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * A character's place in an affiliation, as of today (implementation plan of the
 * Character, D10): the affiliation, what the character is in it - "Capitano" / "Captain"
 * - and, in the Navy, its rank. Part of the character's version, in the order it is
 * shown.
 *
 * @param affiliationContentId the content id of the affiliation
 * @param role per language code, for the languages it is written in; none for no role
 * @param marineRank null for none; allowed only in a Navy affiliation
 * @param former whether the character no longer belongs to it
 */
public record Membership(UUID affiliationContentId, Map<String, String> role, MarineRank marineRank, boolean former) {

	public Membership {
		role = role == null ? Map.of() : Map.copyOf(role);
	}

	/**
	 * Every text of the role without the space around it; a language left blank is not
	 * written.
	 */
	Membership normalized() {
		Map<String, String> written = new TreeMap<>();
		this.role.forEach((language, value) -> {
			String normalized = Text.stripToNull(value);
			if (normalized != null) {
				written.put(language, normalized);
			}
		});
		return new Membership(this.affiliationContentId, written, this.marineRank, this.former);
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
