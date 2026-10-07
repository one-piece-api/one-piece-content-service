package dev.onepieceapi.contentservice.persistence.entity;

import dev.onepieceapi.contentservice.domain.workflow.VersionStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;

import java.time.Instant;
import java.util.UUID;

/**
 * What one version of a content says, in the table of its entity - the part every entity
 * shares: the romaji (docs/user-flows/content-editorial-workflow.md 3.1). It shares its
 * id with the {@link ContentVersionEntity} carrying the workflow of that version, and is
 * saved and removed together with it. Each entity extends it with its own fields, e.g.
 * {@link DevilFruitTypeVersionEntity}, and keeps its localized fields in a map named
 * {@value #TRANSLATIONS} whose values have a {@code name}: the generic list queries
 * search and sort by it.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@FieldNameConstants
public abstract class VersionBodyEntity {

	/** The name every entity gives its map of translations, by language code. */
	public static final String TRANSLATIONS = "translations";

	@Id
	private UUID versionId;

	@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
	@MapsId
	@JoinColumn(name = "version_id")
	private ContentVersionEntity version;

	@Setter
	private String romaji;

	protected VersionBodyEntity(@NonNull ContentVersionEntity version) {
		this.version = version;
	}

	/** The content this version belongs to. */
	public UUID getContentId() {
		return this.version.getContentId();
	}

	/** Takes the version to another status, and notes when. */
	public void moveTo(VersionStatus newStatus, Instant now) {
		this.version.moveTo(newStatus, now);
	}

	/** Sends the version to review, leaving any earlier rejection behind. */
	public void submit(Instant now) {
		this.version.submit(now);
	}

	/** The claimant passes the review. */
	public void approve(Instant now) {
		this.version.approve(now);
	}

	/** The claimant fails the review, saying why. */
	public void reject(String reason, Instant now) {
		this.version.reject(reason, now);
	}

	/** A reviewer takes the version. */
	public void claimBy(UserEmbeddable reviewer) {
		this.version.claimBy(reviewer);
	}

	/** Nobody holds the version any more. */
	public void release() {
		this.version.release();
	}

	/** Notes that what the version says changed now. */
	protected void touch(Instant now) {
		this.version.setUpdatedAt(now);
	}

}
