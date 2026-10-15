package dev.onepieceapi.contentservice.service.event;

import dev.onepieceapi.contentservice.config.RulesProperties;
import dev.onepieceapi.contentservice.persistence.repository.ChapterVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.ContentRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitTypeVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.DevilFruitVersionRepository;
import dev.onepieceapi.contentservice.persistence.repository.EventVersionRepository;
import dev.onepieceapi.contentservice.service.devilfruit.DevilFruitRules;

/**
 * The rules that read the events, wired by hand as the repository tests build their
 * services: the fruit's own, and the reveal rule they share with the event's.
 */
public final class EventRulesFixtures {

	public static final RulesProperties PROPERTIES = new RulesProperties(5);

	private EventRulesFixtures() {
	}

	public static EventReveals reveals(DevilFruitVersionRepository fruits, EventVersionRepository events,
			ChapterVersionRepository chapters) {
		return new EventReveals(fruits, events, new ChapterNumbers(chapters), PROPERTIES);
	}

	public static DevilFruitRules devilFruitRules(ContentRepository contents, DevilFruitTypeVersionRepository types,
			ChapterVersionRepository chapters, DevilFruitVersionRepository fruits, EventVersionRepository events) {
		return new DevilFruitRules(contents, types, chapters, events, reveals(fruits, events, chapters), PROPERTIES);
	}

}
