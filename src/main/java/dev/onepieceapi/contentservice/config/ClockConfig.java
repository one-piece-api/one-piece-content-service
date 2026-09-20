package dev.onepieceapi.contentservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * A single injectable {@link Clock} bean so services never call {@code Instant.now()}
 * directly - tests substitute a fixed clock instead of depending on wall-clock time. Same
 * pattern as {@code one-piece-user-service}'s {@code ClockConfig}.
 */
@Configuration
public class ClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
