package checks.project;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import checks.project.model.RuleContext;
import checks.project.model.SourceFile;
import checks.project.model.Violation;
import checks.project.support.JavaSourceFiles;
import checks.project.support.ProjectParser;
import checks.project.support.ProjectTypeSolver;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ProjectRuleRunner {
	private ProjectRuleRunner() {
	}

	public static List<Violation> run(Path sourceRoot, List<ProjectRule> rules) throws IOException {
		return run(ProjectRuleInput.source(sourceRoot, List.of()), rules).violations();
	}

	public static List<Violation> run(Path sourceRoot, List<Path> sourceRoots, List<Path> classpath, List<ProjectRule> rules) throws IOException {
		return run(new ProjectRuleInput(sourceRoot, sourceRoots, classpath), rules).violations();
	}

	public static ProjectRuleResult run(ProjectRuleInput input, List<ProjectRule> rules) throws IOException {
		ProjectParser.configure(ProjectTypeSolver.build(input.sourceRoots(), input.classpath()));
		RuleContext context = new RuleContext();
		indexSourceModels(input.sourceRoot(), context);
		List<Violation> violations = new ArrayList<>();
		for (Path file : JavaSourceFiles.list(input.sourceRoot())) {
			checkFile(file, rules, context, violations);
		}
		violations.sort(Comparator
				.comparing((Violation violation) -> violation.file().toString())
				.thenComparingInt(Violation::line)
				.thenComparing(Violation::rule));
		return new ProjectRuleResult(violations);
	}

	private static void checkFile(Path file, List<ProjectRule> rules, RuleContext context, List<Violation> violations) {
		CompilationUnit unit;
		try {
			unit = StaticJavaParser.parse(file);
		} catch (RuntimeException | IOException ex) {
			violations.add(new Violation(file, 1, "ParseError", ex.getMessage()));
			return;
		}

		SourceFile sourceFile = new SourceFile(file, unit);
		for (ProjectRule rule : rules) {
			violations.addAll(rule.check(sourceFile, context));
		}
	}

	private static void indexSourceModels(Path sourceRoot, RuleContext context) throws IOException {
		if (context.sourceInheritanceIndex().isIndexed()) {
			return;
		}
		for (Path file : JavaSourceFiles.list(sourceRoot)) {
			try {
				context.sourceInheritanceIndex().index(StaticJavaParser.parse(file));
			} catch (RuntimeException ex) {
				// Parse errors are reported during the rule pass for the same file.
			}
		}
		context.sourceInheritanceIndex().markIndexed();
	}
}
