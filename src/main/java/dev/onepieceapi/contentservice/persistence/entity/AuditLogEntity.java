package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The JPA row behind one audit record - see {@code db/migration/V1__baseline.sql} (and
 * {@code V2} for the target version and the actor's username) and
 * {@code docs/user-flows/content-editorial-workflow.md} 7.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AuditLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	private String action;

	/** Who acted; the username is missing on the records written before it was stored. */
	@Embedded
	@AttributeOverride(name = "userId", column = @Column(name = "actor_user_id"))
	@AttributeOverride(name = "username", column = @Column(name = "actor_username"))
	@AttributeOverride(name = "email", column = @Column(name = "actor_email"))
	private UserEmbeddable actor;

	/**
	 * Null for an action with no single content as its target - e.g. the language
	 * catalog, which is system configuration rather than a content.
	 */
	private UUID targetContentId;

	/**
	 * Set when the action is about one version of the target content. The id, not the
	 * number: a number is reused once a draft is deleted, an id never is.
	 */
	private UUID targetVersionId;

	private String targetLabel;

	private String detail;

	/**
	 * Whether the actor could act only through {@code content:admin} - see {@code V5}.
	 */
	private boolean override;

	private Instant occurredAt;

}
