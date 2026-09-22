package dev.onepieceapi.contentservice.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * An immutable snapshot of one item's fields at the moment of a successful Publish
 * (UF-CNT-07) - append-only, never edited or deleted (4.1's "Published version"). One row
 * per publish/rollback event once Step 7 adds rollback; for now (Step 5) only Publish
 * creates these. {@code sequenceNumber} is 1-based per item, assigned at creation time as
 * "how many versions this item already has, plus one" - simple and correct for a
 * single-PUBLISHER-at-a-time action, no dedicated DB sequence needed.
 */
@Entity
@Table(name = "content_version")
@Getter
@NoArgsConstructor
public class ContentVersionEntity {

	@Id
	private UUID id;

	@Column(name = "item_id", nullable = false)
	private UUID itemId;

	@Column(name = "sequence_number", nullable = false)
	private int sequenceNumber;

	private String romaji;

	@Column(name = "publisher_id", nullable = false)
	private UUID publisherId;

	@Column(name = "publisher_email")
	private String publisherEmail;

	@Column(name = "published_at", nullable = false)
	private Instant publishedAt;

	public ContentVersionEntity(UUID id, UUID itemId, int sequenceNumber, String romaji, UUID publisherId,
			String publisherEmail, Instant publishedAt) {
		this.id = id;
		this.itemId = itemId;
		this.sequenceNumber = sequenceNumber;
		this.romaji = romaji;
		this.publisherId = publisherId;
		this.publisherEmail = publisherEmail;
		this.publishedAt = publishedAt;
	}

}
