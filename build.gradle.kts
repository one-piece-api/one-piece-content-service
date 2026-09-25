plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("io.spring.javaformat") version "0.0.48"
	checkstyle
}

group = "dev.onepieceapi"
version = "0.0.1-SNAPSHOT"
description = "One Piece API - content editorial workflow backend"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(25)
	}
}

repositories {
	mavenCentral()
	// one-piece-exception (shared error-handling library) - GitHub Packages requires
	// authentication even to resolve a public package; GITHUB_ACTOR/GITHUB_TOKEN are
	// already set in CI, a personal PAT with read:packages covers local dev (see that
	// repo's README). Same setup as one-piece-user-service.
	maven {
		name = "GitHubPackages"
		url = uri("https://maven.pkg.github.com/one-piece-api/one-piece-exception")
		credentials {
			username = System.getenv("GITHUB_ACTOR")
			password = System.getenv("GITHUB_TOKEN")
		}
	}
}

dependencies {
	implementation("dev.onepieceapi:one-piece-exception:0.2.0")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-flyway")
	runtimeOnly("org.flywaydb:flyway-database-postgresql")
	runtimeOnly("org.postgresql:postgresql")
	// OpenAPI spec + Swagger UI, same setup as one-piece-user-service - see
	// docs/adr/0001-openapi-contract-and-bruno-collection.md. Not managed by Spring Boot's
	// BOM; 3.1.x is the line built against Boot 4.1.
	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.1")
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")
	testCompileOnly("org.projectlombok:lombok")
	testAnnotationProcessor("org.projectlombok:lombok")
	testImplementation("org.springframework.boot:spring-boot-starter-actuator-test")
	testImplementation("org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
	testImplementation("org.springframework.boot:spring-boot-resttestclient")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter")
	testImplementation("org.testcontainers:testcontainers-postgresql")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

// Rewrites openapi/openapi.yaml from the current controllers instead of failing on a
// mismatch - the one command to run after an API change (see OpenApiSpecTest).
tasks.register<Test>("updateOpenApiSpec") {
	description = "Regenerates openapi/openapi.yaml from the current controllers."
	group = "documentation"
	testClassesDirs = sourceSets.test.get().output.classesDirs
	classpath = sourceSets.test.get().runtimeClasspath
	filter { includeTestsMatching("*OpenApiSpecTest") }
	systemProperty("openapi.update", "true")
	outputs.upToDateWhen { false }
}

checkstyle {
	toolVersion = "14.0.0"
}
