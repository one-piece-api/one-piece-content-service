package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * A stored image (implementation plan of the Devil Fruit, D4): never changed once
 * written, since its id is the hash of its bytes.
 */
@Entity
@Table(name = "image")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImageEntity {

	/** The SHA-256 of {@code bytes}, lowercase hex. */
	@Id
	private String id;

	private String contentType;

	private int width;

	private int height;

	private int sizeBytes;

	private byte[] bytes;

	private Instant createdAt;

}
