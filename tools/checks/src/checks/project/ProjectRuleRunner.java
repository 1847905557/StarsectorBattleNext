package checks.project;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import checks.project.analysis.LifecycleControllerAnalysis;
import checks.project.analysis.SourceInheritanceIndex;
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
	public record SourceFile(Path path, CompilationUnit unit) {

	}

	public record RuleContext(LifecycleControllerAnalysis lifecycleControllerAnalysis, SourceInheritanceIndex sourceInheritanceIndex) {
		public RuleContext() {
			this(new LifecycleControllerAnalysis(), new SourceInheritanceIndex());
		}
	}

	public record Result(List<Violation> violations) {

	}

	private ProjectRuleRunner() {

	}

	public static List<Violation> run(Path sourceRoot, List<ProjectRule> rules) throws IOException {
		return run(ProjectRuleInput.source(sourceRoot, List.of()), rules).violations();
	}

	public static List<Violation> run(Path sourceRoot, List<Path> sourceRoots, List<Path> classpath, List<ProjectRule> rules) throws IOException {
		return run(new ProjectRuleInput(sourceRoot, sourceRoots, classpath), rules).violations();
	}

	public static Result run(ProjectRuleInput input, List<ProjectRule> rules) throws IOException {
		ProjectParser.configure(ProjectTypeSolver.build(input.sourceRoots(), input.classpath()));
		RuleContext context = new RuleContext();
		for (Path file : JavaSourceFiles.list(input.sourceRoot())) {
			try {
				context.sourceInheritanceIndex().index(StaticJavaParser.parse(file));
			} catch (RuntimeException ex) {
				// Parse errors are reported during the rule pass for the same file.
			}
		}

		List<Violation> violations = new ArrayList<>();
		for (Path file : JavaSourceFiles.list(input.sourceRoot())) {
			CompilationUnit unit;
			try {
				unit = StaticJavaParser.parse(file);
			} catch (RuntimeException | IOException ex) {
				violations.add(new Violation(file, 1, "ParseError", ex.getMessage()));
				continue;
			}

			SourceFile sourceFile = new SourceFile(file, unit);
			for (ProjectRule rule : rules) violations.addAll(rule.check(sourceFile, context));
		}
		violations.sort(Comparator
				.comparing((Violation violation) -> violation.file().toString())
				.thenComparingInt(Violation::line)
				.thenComparing(Violation::rule));
		return new Result(violations);
	}
}
