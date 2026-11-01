package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.character.MarineRank;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
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
 * A period of a membership (workflow 3.4.7): a child of
 * {@link CharacterMembershipEntity}, saved and removed only through it. Its row has an id
 * of its own, the parent of the translations of its role.
 */
@Entity
@Table(name = "character_version_affiliation_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
public class CharacterMembershipPeriodEntity {

	@Id
	private UUID id;

	/** Null when none of its points is chosen. */
	@Embedded
	private StoryWindowEmbeddable window;

	@Enumerated(EnumType.STRING)
	private MarineRank marineRank;

	/** The role per language code; a language nothing was saved for is absent. */
	@ElementCollection
	@CollectionTable(name = "character_version_affiliation_period_translation",
			joinColumns = @JoinColumn(name = "period_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "role")
	private Map<String, String> role = new HashMap<>();

	public CharacterMembershipPeriodEntity(MembershipPeriodFields written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the period says. */
	public void rewrite(MembershipPeriodFields written) {
		this.window = written.window();
		this.marineRank = written.marineRank();
		this.role.clear();
		this.role.putAll(written.role());
	}

}
