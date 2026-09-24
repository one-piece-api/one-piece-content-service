package dev.onepieceapi.contentservice.persistence.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row in the ADMIN-managed language catalog
 * (docs/user-flows/authentication-and-user-management.md 3.2). Presence in this table is
 * what "active" means - there is no separate soft-delete flag, so removing a language
 * (Step 10, {@code languages:manage}) is a literal {@code DELETE}.
 */
@Entity
@Table(name = "language")
@Getter
@NoArgsConstructor
public class LanguageEntity {

	@Id
	private String code;

	/** Full display name (e.g. "English") - shown in the ADMIN catalog screen. */
	private String name;

	public LanguageEntity(String code, String name) {
		this.code = code;
		this.name = name;
	}

}
