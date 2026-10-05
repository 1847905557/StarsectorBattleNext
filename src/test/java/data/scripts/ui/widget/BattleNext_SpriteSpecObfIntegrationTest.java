package data.scripts.ui.widget;

import com.fs.starfarer.api.combat.ShipHullSpecAPI;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Constructor;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SpriteSpecObfIntegrationTest {

	@Test
	void readsActualLoadedHullSpriteMetadataThroughTheProductionAdapter() throws Exception {
		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		List<String> classpath = new ArrayList<>();
		classpath.add(Path.of(BattleNext_SpriteSpec.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
		classpath.add(Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
		try (var paths = Files.list(core)) {
			classpath.addAll(paths.filter(path -> path.toString().endsWith(".jar")).sorted().map(Path::toString).toList());
		}
		String java = Path.of(System.getProperty("java.home"), "bin", "java.exe").toString();
		Process process = new ProcessBuilder(java, "-Xverify:none", "-cp", String.join(File.pathSeparator, classpath),
				Probe.class.getName()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), output);
		assertTrue(output.contains("Verified native hull sprite dimensions, centers and immutable snapshots"), output);
	}

	public static class Probe {

		public static void main(String[] arguments) throws Exception {
			Class<?> hullType = Class.forName("com.fs.starfarer.loading.specs.g", false, Probe.class.getClassLoader());
			Class<?> spriteType = hullType.getMethod("getSpriteSpec").getReturnType();
			ShipHullSpecAPI hull = (ShipHullSpecAPI) hullType.getConstructor(String.class).newInstance("probe_hull");
			Constructor<?> constructor = Arrays.stream(spriteType.getConstructors())
					.filter(candidate -> candidate.getParameterCount() == 7).findFirst().orElseThrow();
			float[][] examples = {{140f, 280f, 70f, 115f}, {83f, 129f, 19f, 77f}, {18f, 26f, 4f, 8f}};
			for (float[] values : examples) {
				Object spec = constructor.newInstance("graphics/probe.png", values[0], values[1], values[2], values[3], 220f, null);
				hullType.getMethod("setSpriteSpec", spriteType).invoke(hull, spec);
				BattleNext_SpriteSpec geometry = BattleNext_SpriteSpec.read(hull);
				BattleNext_SpriteSpec expected = new BattleNext_SpriteSpec(values[0], values[1], values[2], values[3]);
				if (!expected.equals(geometry)) throw new AssertionError("Native sprite geometry: " + geometry);
				Object changed = constructor.newInstance("graphics/probe.png", 190f, 310f, 63f, 98f, 220f, null);
				hullType.getMethod("setSpriteSpec", spriteType).invoke(hull, changed);
				if (!expected.equals(geometry)) throw new AssertionError("Captured geometry changed with the native spec");
				if (!new BattleNext_SpriteSpec(190f, 310f, 63f, 98f).equals(BattleNext_SpriteSpec.read(hull))) {
					throw new AssertionError("Reloaded native sprite geometry");
				}
			}
			System.out.println("Verified native hull sprite dimensions, centers and immutable snapshots");
		}
	}
}
