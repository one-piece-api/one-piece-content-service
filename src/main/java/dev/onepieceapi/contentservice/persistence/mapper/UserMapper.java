package dev.onepieceapi.contentservice.persistence.mapper;

import dev.onepieceapi.contentservice.domain.security.User;
import dev.onepieceapi.contentservice.persistence.entity.UserEmbeddable;
import lombok.experimental.UtilityClass;

/** Between the {@link User} of the domain and the way a row stores one. */
@UtilityClass
public class UserMapper {

	/** Null stays null: a version nobody holds has no claimant. */
	public User toDomain(UserEmbeddable user) {
		return user == null ? null : new User(user.getUserId(), user.getUsername(), user.getEmail());
	}

	public UserEmbeddable toEmbeddable(User user) {
		return user == null ? null : new UserEmbeddable(user.id(), user.username(), user.email());
	}

}
