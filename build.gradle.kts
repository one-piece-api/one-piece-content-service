plugins {
	java
	id("org.springframework.boot") version "4.1.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("io.spring.javaformat") version "0.0.48"
	checkstyle
	`maven-publish`
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
		// gpr.user/gpr.token in ~/.gradle/gradle.properties for local dev (a PAT classic with
		// read:packages only), GITHUB_ACTOR/GITHUB_TOKEN in CI - a global GITHUB_TOKEN env var
		// would also take over the gh CLI's own login.
		credentials {
			username = providers.gradleProperty("gpr.user").orElse(providers.environmentVariable("GITHUB_ACTOR")).orNull
			password = providers.gradleProperty("gpr.token").orElse(providers.environmentVariable("GITHUB_TOKEN")).orNull
		}
	}
}

dependencies {
	implementation("dev.onepieceapi:one-piece-exception:0.6.0")
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

// The Flyway migrations alone, published for one-piece-public-api's tests: they build the
// same schema, "published" views included (docs/adr/0003-published-interface.md). The
// version is the number of the latest migration, so "migrations 8" means "schema up to V8".
val migrationsDir = layout.projectDirectory.dir("src/main/resources/db/migration")
val migrationsVersion = migrationsDir.asFile
	.list()
	.orEmpty()
	.mapNotNull { Regex("""^V(\d+)__.+\.sql$""").find(it)?.groupValues?.get(1)?.toInt() }
	.max()
	.toString()

val migrationsJar by tasks.registering(Jar::class) {
	description = "Packages the Flyway migrations alone, for one-piece-public-api's tests."
	group = "build"
	archiveBaseName = "one-piece-content-service-migrations"
	archiveVersion = migrationsVersion
	// Not build/libs: the Dockerfile copies the one jar it finds there.
	destinationDirectory = layout.buildDirectory.dir("migrations")
	from(migrationsDir) { into("db/migration") }
}

// The only publication is SQL files with no dependencies: no Spring Boot BOM in its POM.
dependencyManagement {
	generatedPomCustomization {
		enabled(false)
	}
}

// Read by CI to skip the publication when this version is already on GitHub Packages.
tasks.register("printMigrationsVersion") {
	description = "Prints the version of the migrations artifact."
	group = "help"
	val version = migrationsVersion
	doLast { println(version) }
}

publishing {
	publications {
		create<MavenPublication>("migrations") {
			artifactId = "one-piece-content-service-migrations"
			version = migrationsVersion
			artifact(migrationsJar)
			pom {
				name = "one-piece-content-service-migrations"
				description = "Flyway migrations of one-piece-content-service's database"
				url = "https://github.com/one-piece-api/one-piece-content-service"
			}
		}
	}
	repositories {
		maven {
			name = "GitHubPackages"
			url = uri("https://maven.pkg.github.com/one-piece-api/one-piece-content-service")
			credentials {
				username = System.getenv("GITHUB_ACTOR")
				password = System.getenv("GITHUB_TOKEN")
			}
		}
	}
}
