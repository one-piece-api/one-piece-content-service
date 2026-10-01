package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * The JPA row behind one audit record - see {@code db/migration/V1__baseline.sql} and
 * {@code docs/user-flows/content-editorial-workflow.md} 7.
 */
@Entity
@Table(name = "audit_log")
@Getter
@NoArgsConstructor
public class AuditLogEntity {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 64)
	private String action;

	@Column(nullable = false)
	private UUID actorUserId;

	@Column(nullable = false)
	private String actorEmail;

	/**
	 * Null for an action with no single content item as its target - e.g. the language
	 * catalog, which is system configuration rather than a content item.
	 */
	private UUID targetItemId;

	private String targetLabel;

	private String detail;

	@Column(nullable = false)
	private Instant occurredAt;

	public AuditLogEntity(String action, UUID actorUserId, String actorEmail, UUID targetItemId, String targetLabel,
			String detail, Instant occurredAt) {
		this.action = action;
		this.actorUserId = actorUserId;
		this.actorEmail = actorEmail;
		this.targetItemId = targetItemId;
		this.targetLabel = targetLabel;
		this.detail = detail;
		this.occurredAt = occurredAt;
	}

}
