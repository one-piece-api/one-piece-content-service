package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldNameConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A membership of one version of a Character (implementation plan of the Character, D10,
 * D14; workflow 3.4.7): a child of {@link CharacterVersionEntity}, saved and removed only
 * through it. Its row has an id of its own, the parent of its periods.
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

	/** In order; the position is the index in the list. */
	@OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
	@JoinColumn(name = "membership_id", nullable = false)
	@OrderColumn(name = "position")
	private List<CharacterMembershipPeriodEntity> periods = new ArrayList<>();

	public CharacterMembershipEntity(MembershipFields written) {
		this.id = UUID.randomUUID();
		rewrite(written);
	}

	/** Replaces what the membership says, each period kept in its row. */
	public void rewrite(MembershipFields written) {
		this.affiliationContentId = written.affiliationContentId();
		PositionalRows.rewrite(this.periods, written.periods(), CharacterMembershipPeriodEntity::new,
				CharacterMembershipPeriodEntity::rewrite);
	}

}
