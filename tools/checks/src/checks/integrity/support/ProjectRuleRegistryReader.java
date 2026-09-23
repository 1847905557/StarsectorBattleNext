package checks.integrity.support;

import checks.integrity.model.RuleRegistry;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.ObjectCreationExpr;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public final class ProjectRuleRegistryReader {
	private ProjectRuleRegistryReader() {
	}

	public static RuleRegistry read(Path rulesDir) throws IOException {
		Set<String> ruleClasses = listRuleClasses(rulesDir);
		return new RuleRegistry(ruleClasses, readRuleNames(ruleClasses));
	}

	public static Set<String> listRuleClasses(Path rulesDir) throws IOException {
		try (Stream<Path> paths = Files.list(rulesDir)) {
			return paths
					.filter(Files::isRegularFile)
					.map(path -> path.getFileName().toString())
					.filter(name -> name.endsWith("Rule.java"))
					.filter(name -> !name.equals("ProjectRules.java"))
					.map(name -> name.substring(0, name.length() - ".java".length()))
					.collect(LinkedHashSet::new, Set::add, Set::addAll);
		}
	}

	public static Set<String> readRegisteredRuleClasses(Path projectRulesFile) throws IOException {
		CompilationUnit unit = StaticJavaParser.parse(projectRulesFile);
		return unit.findAll(ObjectCreationExpr.class).stream()
				.map(creation -> creation.getType().getNameAsString())
				.collect(LinkedHashSet::new, Set::add, Set::addAll);
	}

	private static Map<String, String> readRuleNames(Set<String> ruleClasses) {
		Map<String, String> ruleNames = new LinkedHashMap<>();
		for (String ruleClass : ruleClasses) {
			try {
				Class<?> type = Class.forName("checks.project.rules." + ruleClass);
				Field nameField = type.getField("NAME");
				ruleNames.put(ruleClass, (String) nameField.get(null));
			} catch (ReflectiveOperationException | ClassCastException ex) {
				ruleNames.put(ruleClass, ruleClass);
			}
		}
		return ruleNames;
	}
}
