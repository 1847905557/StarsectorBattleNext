package data.scripts.plugin;

import com.fs.starfarer.api.combat.CombatUIAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.expr.SimpleName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_AllShipsStatusPluginObfIntegrationTest {

	@Test
	void bindsActualObfuscatedSignaturesInAnIndependentJvm() throws Exception {

		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		List<String> classpath = new ArrayList<>();
		classpath.add(Path.of(BattleNext_AllShipsStatusPlugin.class.getProtectionDomain()
				.getCodeSource().getLocation().toURI()).toString());
		classpath.add(Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
		try (Stream<Path> paths = Files.list(core)) {
			classpath.addAll(paths.filter(path -> path.toString().endsWith(".jar")).sorted()
					.map(Path::toString).toList());
		}
		String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
		Process process = new ProcessBuilder(java, "-Xverify:none", "-cp",
				String.join(File.pathSeparator, classpath), Probe.class.getName()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		assertEquals(0, exitCode, output);
		assertTrue(output.contains("Verified five native status handles and cleanup"), output);
	}

	@Test
	void runtimeDefinitionRemainsIndependentOfRegistrationAndOtherCallers() throws Exception {

		String name = "BattleNext_AllShipsStatusPlugin";
		Path definition = Path.of("src/data/scripts/plugin", name + ".java");
		JavaParser parser = new JavaParser(new ParserConfiguration()
				.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17).setCharacterEncoding(StandardCharsets.UTF_8));
		List<String> references = new ArrayList<>();
		try (Stream<Path> paths = Files.walk(Path.of("src"))) {
			for (Path path : paths.filter(file -> file.toString().endsWith(".java"))
					.filter(file -> !file.startsWith(Path.of("src/test")) && !file.equals(definition)).sorted().toList()) {
				var result = parser.parse(path);
				assertTrue(result.isSuccessful(), path + ": " + result.getProblems());
				var unit = result.getResult().orElseThrow();
				if (unit.findAll(SimpleName.class).stream().anyMatch(identifier -> identifier.asString().equals(name))
						|| unit.getImports().stream().anyMatch(entry -> entry.getNameAsString().endsWith("." + name))) {
					references.add(path.toString());
				}
			}
		}
		Set<String> extensions = Set.of("json", "csv", "txt", "properties", "xml");
		List<Path> configs = new ArrayList<>(List.of(Path.of("mod_info.json")));
		try (Stream<Path> paths = Files.walk(Path.of("data"))) {
			configs.addAll(paths.filter(Files::isRegularFile).filter(path -> extensions.contains(
					path.toString().substring(path.toString().lastIndexOf('.') + 1))).sorted().toList());
		}
		for (Path path : configs) {
			if (Files.readString(path, StandardCharsets.UTF_8).contains(name)) {
				references.add(path.toString());
			}
		}
		assertEquals(List.of(), references);
	}

	public static class Probe {

		public static void main(String[] arguments) throws Exception {

			ClassLoader loader = Probe.class.getClassLoader();
			Class<?> state = Class.forName(BattleNext_AllShipsStatusPlugin.STATE_CLASS, false, loader);
			Class<?> renderer = Class.forName(BattleNext_AllShipsStatusPlugin.RENDERER_CLASS, false, loader);
			Class<?> reticle = Class.forName(BattleNext_AllShipsStatusPlugin.RETICLE_CLASS, false, loader);
			Class<?> ship = Class.forName(BattleNext_AllShipsStatusPlugin.SHIP_CLASS, false, loader);
			if (!CombatUIAPI.class.isAssignableFrom(state)) throw new AssertionError("CombatState must implement CombatUIAPI");
			if (!ShipAPI.class.isAssignableFrom(ship)) throw new AssertionError("Ship must implement ShipAPI");
			if (state.getMethod("getTargetReticleRenderer").getReturnType() != renderer) throw new AssertionError("Renderer return type");
			if (renderer.getMethod("super", ship, float.class).getReturnType() != void.class) throw new AssertionError("Refresh return type");
			if (renderer.getMethod("\u00D200000").getReturnType() != List.class) throw new AssertionError("Reticle list return type");
			if (reticle.getMethod("int").getReturnType() != ship) throw new AssertionError("Reticle ship return type");
			Field secondary = reticle.getDeclaredField("\u00D800000");
			if (secondary.getType() != boolean.class || !Modifier.isPrivate(secondary.getModifiers())) {
				throw new AssertionError("Secondary flag signature");
			}

			BattleNext_AllShipsStatusPlugin.VanillaAccess access = new BattleNext_AllShipsStatusPlugin.VanillaAccess();
			access.bind(state);
			Map<String, MethodType> signatures = Map.of(
					"rendererGetter", MethodType.methodType(renderer, state),
					"reticlesGetter", MethodType.methodType(List.class, renderer),
					"secondaryGetter", MethodType.methodType(boolean.class, reticle),
					"shipGetter", MethodType.methodType(ship, reticle),
					"refresh", MethodType.methodType(void.class, renderer, ship, float.class));
			for (var signature : signatures.entrySet()) {
				Field field = BattleNext_AllShipsStatusPlugin.VanillaAccess.class.getDeclaredField(signature.getKey());
				field.setAccessible(true);
				MethodHandle handle = (MethodHandle) field.get(access);
				if (!handle.type().equals(signature.getValue())) throw new AssertionError(signature.getKey() + ": " + handle.type());
			}
			access.clear();
			for (String name : signatures.keySet()) {
				Field field = BattleNext_AllShipsStatusPlugin.VanillaAccess.class.getDeclaredField(name);
				field.setAccessible(true);
				if (field.get(access) != null) throw new AssertionError("Unreleased handle: " + name);
			}
			System.out.println("Verified five native status handles and cleanup");
		}
	}
}
