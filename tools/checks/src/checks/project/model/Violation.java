package checks.project.model;

import java.nio.file.Path;

public record Violation(Path file, int line, String rule, String message) {
	@Override
	public String toString() {
		return file + ":" + line + ":\t" + rule + ":\t" + message;
	}
}
