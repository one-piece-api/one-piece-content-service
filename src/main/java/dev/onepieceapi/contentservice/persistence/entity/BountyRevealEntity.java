package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.bounty.BountyField;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A reveal of one version of a Bounty (workflow 3.7.1): see {@link RevealEntity}. */
@Entity
@Table(name = "bounty_version_reveal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BountyRevealEntity extends RevealEntity {

	@ElementCollection
	@CollectionTable(name = "bounty_version_reveal_field", joinColumns = @JoinColumn(name = "reveal_id"))
	@Column(name = "field")
	@Enumerated(EnumType.STRING)
	private Set<BountyField> fields = EnumSet.noneOf(BountyField.class);

	/** The value learned; null when not part of it. */
	private Boolean inactive;

	/** The title per language code; a bounty learns nothing translated. */
	@ElementCollection
	@CollectionTable(name = "bounty_version_reveal_translation", joinColumns = @JoinColumn(name = "reveal_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "title")
	private Map<String, String> titles = new HashMap<>();

	public BountyRevealEntity(Written written) {
		super(UUID.randomUUID());
		rewrite(written);
	}

	/** Replaces what the reveal says. */
	public void rewrite(Written written) {
		rewritePoint(written.point());
		this.fields.clear();
		this.fields.addAll(written.fields());
		this.inactive = written.inactive();
		this.titles.clear();
		this.titles.putAll(written.titles());
	}

	/** What a reveal row says, as {@link #rewrite} writes it. */
	public record Written(FirstAppearanceIds point, Set<BountyField> fields, Boolean inactive,
			Map<String, String> titles) {
	}

}
