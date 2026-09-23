package checks.integrity.support;

import checks.integrity.model.FixtureMetadata;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Set;

public final class FixtureMetadataReader {
	private FixtureMetadataReader() {
	}

	public static FixtureMetadata read(Path file) throws IOException {
		Set<String> expectedRules = new LinkedHashSet<>();
		Set<String> covers = new LinkedHashSet<>();
		for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
			String trimmed = line.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			if (trimmed.startsWith("#")) {
				String prefix = "# covers:";
				if (trimmed.startsWith(prefix)) {
					for (String item : trimmed.substring(prefix.length()).split("[,\\s]+")) {
						if (!item.isBlank()) {
							covers.add(item.trim());
						}
					}
				}
				continue;
			}
			expectedRules.add(trimmed);
		}
		return new FixtureMetadata(expectedRules, covers);
	}
}
