package data.scripts.tools;

import com.fs.starfarer.api.Global;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_JsonCatalog {

	private static final String MOD_ID = "AI_Battles_Next";

	private BattleNext_JsonCatalog() {
	}

	public static List<String> resourceFiles(String directory) {
		Path folder = Path.of(Global.getSettings().getModManager().getModSpec(MOD_ID).getPath()).resolve(directory);
		if (!Files.isDirectory(folder)) return List.of();
		List<String> result = new ArrayList<>();
		try (var files = Files.list(folder)) {
			files.filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".json"))
					.sorted().forEach(path -> result.add(directory + "/" + path.getFileName()));
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to list mod data: " + directory, exception);
		}
		return result;
	}

	public static List<String> commonFiles(String directory) {
		Path folder = savesPath().resolve("common").resolve(directory);
		if (!Files.isDirectory(folder)) return List.of();
		List<String> result = new ArrayList<>();
		try (var files = Files.list(folder)) {
			files.filter(Files::isRegularFile).sorted().forEach(path -> {
				String fileName = path.getFileName().toString();
				if (fileName.endsWith(".json.data")) result.add(directory + "/" + fileName.substring(0, fileName.length() - 5));
				else if (fileName.endsWith(".json")) result.add(directory + "/" + fileName);
			});
		} catch (IOException exception) {
			throw new IllegalStateException("Unable to list common data: " + directory, exception);
		}
		return result.stream().distinct().toList();
	}

	private static Path savesPath() {
		try {
			Class<?> type = Class.forName("com.fs.starfarer.settings.StarfarerSettings");
			Method method = type.getMethod("getSavesPath");
			return Path.of(String.valueOf(method.invoke(null)));
		} catch (Exception exception) {
			throw new IllegalStateException("Unable to locate common data", exception);
		}
	}
}
