package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.MarineRank;
import dev.onepieceapi.contentservice.domain.character.Membership;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A membership of one version of a Character (implementation plan of the Character, D10,
 * D14): a child of {@link CharacterVersionEntity}, saved and removed only through it. Its
 * row has an id of its own, the parent of the translations of its role.
 */
@Entity
@Table(name = "character_version_affiliation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
public class CharacterMembershipEntity {

	@Id
	private UUID id;

	/** The content of the affiliation. */
	private UUID affiliationContentId;

	@Enumerated(EnumType.STRING)
	private MarineRank marineRank;

	private boolean former;

	/** The role per language code; a language nothing was saved for is absent. */
	@ElementCollection
	@CollectionTable(name = "character_version_affiliation_translation",
			joinColumns = @JoinColumn(name = "membership_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "role")
	private Map<String, String> role = new HashMap<>();

	public CharacterMembershipEntity(Membership written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the membership says. */
	public void rewrite(Membership written) {
		this.affiliationContentId = written.affiliationContentId();
		this.marineRank = written.marineRank();
		this.former = written.former();
		this.role.clear();
		this.role.putAll(written.role());
	}

}
