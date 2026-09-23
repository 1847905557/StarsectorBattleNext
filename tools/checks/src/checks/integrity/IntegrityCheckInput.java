package checks.integrity;

import java.nio.file.Path;

public record IntegrityCheckInput(Path checksSourceRoot, Path fixturesRoot, Path readme) {
	public static IntegrityCheckInput registry(Path checksSourceRoot) {
		return new IntegrityCheckInput(checksSourceRoot, null, null);
	}

	public static IntegrityCheckInput fixtures(Path checksSourceRoot, Path fixturesRoot) {
		return new IntegrityCheckInput(checksSourceRoot, fixturesRoot, null);
	}

	public static IntegrityCheckInput readme(Path checksSourceRoot, Path readme) {
		return new IntegrityCheckInput(checksSourceRoot, null, readme);
	}

	public static IntegrityCheckInput full(Path checksSourceRoot, Path fixturesRoot, Path readme) {
		return new IntegrityCheckInput(checksSourceRoot, fixturesRoot, readme);
	}

	public Path projectRulesDir() {
		return checksSourceRoot.resolve("checks").resolve("project").resolve("rules");
	}
}
