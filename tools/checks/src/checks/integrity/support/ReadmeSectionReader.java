package checks.integrity.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class ReadmeSectionReader {
	private ReadmeSectionReader() {
	}

	public static String readSection(Path readme, String heading) throws IOException {
		List<String> lines = Files.readAllLines(readme, StandardCharsets.UTF_8);
		String headingPrefix = headingPrefix(heading);
		StringBuilder section = new StringBuilder();
		boolean insideSection = false;
		for (String line : lines) {
			if (line.startsWith(headingPrefix)) {
				if (insideSection) {
					break;
				}
				insideSection = line.equals(heading);
				continue;
			}
			if (insideSection) {
				section.append(line).append('\n');
			}
		}
		return section.toString();
	}

	private static String headingPrefix(String heading) {
		int index = 0;
		while (index < heading.length() && heading.charAt(index) == '#') {
			index++;
		}
		return "#".repeat(index) + " ";
	}
}
