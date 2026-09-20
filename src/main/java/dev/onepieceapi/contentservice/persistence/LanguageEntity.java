package dev.onepieceapi.contentservice.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row in the ADMIN-managed language catalog
 * (docs/user-flows/authentication-and-user-management.md 3.2) - read-only until Step 10
 * adds CRUD.
 */
@Entity
@Table(name = "language")
@Getter
@NoArgsConstructor
public class LanguageEntity {

	@Id
	private String code;

	public LanguageEntity(String code) {
		this.code = code;
	}

}
