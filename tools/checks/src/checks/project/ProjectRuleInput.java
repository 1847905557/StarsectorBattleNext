package checks.project;

import java.nio.file.Path;
import java.util.List;

public record ProjectRuleInput(Path sourceRoot, List<Path> sourceRoots, List<Path> classpath) {
	public static ProjectRuleInput source(Path sourceRoot, List<Path> classpath) {
		return new ProjectRuleInput(sourceRoot, List.of(sourceRoot), classpath);
	}
}
