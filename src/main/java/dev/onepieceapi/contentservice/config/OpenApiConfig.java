package dev.onepieceapi.contentservice.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.OAuthFlow;
import io.swagger.v3.oas.models.security.OAuthFlows;
import io.swagger.v3.oas.models.security.Scopes;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

/**
 * Document-level OpenAPI metadata: every operation requires a Keycloak access token
 * (authorization code + PKCE), and the server is the context path alone, so the committed
 * spec stays host-independent - same setup as {@code one-piece-user-service}. See
 * {@code docs/adr/0001-openapi-contract-and-bruno-collection.md}.
 */
@Configuration
public class OpenApiConfig {

	static final String SECURITY_SCHEME = "keycloak";

	private static final String CONTROLLER_SUFFIX = "Controller";

	@Bean
	OpenAPI openApi(@Value("${spring.application.name}") String applicationName,
			@Value("${server.servlet.context-path}") String contextPath,
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuerUri) {
		var authorizationCode = new OAuthFlow().authorizationUrl(issuerUri + "/protocol/openid-connect/auth")
			.tokenUrl(issuerUri + "/protocol/openid-connect/token")
			.refreshUrl(issuerUri + "/protocol/openid-connect/token")
			.scopes(new Scopes().addString("openid", "OpenID Connect sign-in"));
		var keycloak = new SecurityScheme().type(SecurityScheme.Type.OAUTH2)
			.flows(new OAuthFlows().authorizationCode(authorizationCode));
		return new OpenAPI().info(new Info().title(applicationName).version("v1"))
			.addServersItem(new Server().url(contextPath))
			.components(new Components().addSecuritySchemes(SECURITY_SCHEME, keycloak))
			.addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
	}

	/**
	 * Scopes each operationId by its controller ({@code LanguageController#list} becomes
	 * {@code languageList}): this service's handler methods are short verbs meant to be
	 * read within their controller ({@code list}, {@code get}, {@code create}), which
	 * would otherwise collide in the spec's flat operationId namespace and get unstable
	 * {@code _1}/{@code _2} suffixes.
	 */
	@Bean
	OperationCustomizer controllerScopedOperationIds() {
		return (operation, handlerMethod) -> {
			var controller = handlerMethod.getBeanType().getSimpleName().replace(CONTROLLER_SUFFIX, "");
			var method = StringUtils.capitalize(handlerMethod.getMethod().getName());
			return operation.operationId(StringUtils.uncapitalize(controller) + method);
		};
	}

}
