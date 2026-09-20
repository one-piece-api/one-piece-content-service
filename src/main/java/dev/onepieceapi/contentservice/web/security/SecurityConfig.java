package dev.onepieceapi.contentservice.web.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.resource.OAuth2ResourceServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Every request is authenticated against Keycloak (realm "onepiece") as a JWT-based
 * OAuth2 resource server, except the Kubernetes health probes - same token, same realm
 * {@code one-piece-user-service} already validates, no new Keycloak client (see
 * {@code docs/implementation-plan-content.md} §2 Working Assumptions). Endpoint-level
 * authorization is driven entirely by {@link SecuredEndpoint}.
 */
@Configuration
@RequiredArgsConstructor(onConstructor_ = { @Autowired })
public class SecurityConfig {

	private final ContentPermissionJwtAuthenticationConverter jwtAuthenticationConverter;

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http) {
		http.csrf(AbstractHttpConfigurer::disable)
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			.authorizeHttpRequests(auth -> {
				SecuredEndpoint.configureAll(auth);
				// Backstop only: every real endpoint is enumerated in SecuredEndpoint, so
				// this covers anything not yet added to it - authenticated, not denyAll,
				// to avoid turning a plain 404/500 into a confusing 403.
				auth.anyRequest().authenticated();
			})
			.oauth2ResourceServer(oauth2 -> oauth2.jwt(this::configureJwt));
		return http.build();
	}

	private void configureJwt(OAuth2ResourceServerConfigurer<HttpSecurity>.JwtConfigurer jwt) {
		jwt.jwtAuthenticationConverter(this.jwtAuthenticationConverter);
	}

}
