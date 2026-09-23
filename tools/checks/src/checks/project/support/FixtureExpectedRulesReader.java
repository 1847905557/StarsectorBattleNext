package checks.project.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

public final class FixtureExpectedRulesReader {
	private FixtureExpectedRulesReader() {
	}

	public static Set<String> read(Path file) throws IOException {
		return Files.readAllLines(file, StandardCharsets.UTF_8).stream()
				.map(String::trim)
				.filter(line -> !line.isEmpty())
				.filter(line -> !line.startsWith("#"))
				.collect(Collectors.toCollection(HashSet::new));
	}
}
