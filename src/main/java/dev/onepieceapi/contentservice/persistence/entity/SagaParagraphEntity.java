package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** A paragraph of one version of a saga (workflow 3.7.3): see {@link ParagraphEntity}. */
@Entity
@Table(name = "saga_version_paragraph")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SagaParagraphEntity extends ParagraphEntity {

	@ElementCollection
	@CollectionTable(name = "saga_version_paragraph_translation", joinColumns = @JoinColumn(name = "paragraph_id"))
	@MapKeyColumn(name = "language_code")
	@Column(name = "text")
	@Getter
	private Map<String, String> text = new HashMap<>();

	public SagaParagraphEntity(ParagraphFields written) {
		super(UUID.randomUUID());
		rewrite(written);
	}

}
