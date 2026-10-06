package data.simulator;

import data.scripts.tools.BattleNext_Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.CopyOption;
import java.nio.file.LinkOption;
import java.nio.file.OpenOption;
import java.nio.file.StandardCopyOption;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class BattleNext_SimulatorStorage {

	private static final List<String> GRAPHICS_OPTIONS = List.of("enableLights", "enableBloom", "enableNormal", "autoGenNormals",
			"loadMaterial", "loadSurface", "enableDistortion", "enablePostProcess", "preloadAllMaps");
	private final Path root;
	private final FileAccess files;

	public BattleNext_SimulatorStorage(Path root) throws IOException {
		this.root = root.toAbsolutePath().normalize();
		files = new FileAccess();
	}

	public Path directory(String name) throws IOException {
		Path target = root.resolve(name);
		invoke(files.createDirectories, target);
		return target;
	}

	public List<Path> listFiles(Path directory, boolean recursive) throws IOException {
		List<Path> result = new ArrayList<>();
		for (Path path : listPaths(directory, recursive)) {
			if (!(boolean) invoke(files.isRegularFile, path)) continue;
			result.add(path);
		}
		return result;
	}

	public void snapshotCommon(Path sourceSaves, Path workerSaves) throws IOException {

		Path source = sourceSaves.resolve("common");
		if (!(boolean) invoke(files.isDirectory, source)) return;
		for (Path path : listFiles(source, true)) {
			if (path.toAbsolutePath().normalize().startsWith(root) || path.startsWith(source.resolve("AI_Battles_Next/simulator"))) continue;
			Path target = workerSaves.resolve("common").resolve(source.relativize(path));
			invoke(files.createDirectories, target.getParent());
			BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("source", path.toString()).put("target", target.toString()).build();
			BattleNext_Logger.operationStarted(getClass(), "simulator.settings.snapshot", fields);
			try {
				byte[] bytes = (byte[]) invoke(files.readAllBytes, path);
				invoke(files.write, target, bytes);
				BattleNext_Logger.operationSucceeded(getClass(), "simulator.settings.snapshot",
						BattleNext_Logger.fields().put("source", path.toString()).put("target", target.toString()).put("bytes", bytes).build());
			} catch (IOException exception) {
				BattleNext_Logger.operationFailed(getClass(), "simulator.settings.snapshot", fields, exception);
				throw exception;
			}
		}
	}

	public void configureGraphics(Path workerSaves) throws IOException, JSONException {
		configureOptions(workerSaves.resolve("common/LunaSettings/shaderLib.json.data"), GRAPHICS_OPTIONS, Map.of());
		configureOptions(workerSaves.resolve("common/BUtil_Configs.json.data"),
				List.of("BUtil_EnableShader", "BUtil_EnableDistortion", "BUtil_EnableDebug", "BUtil_EnableCL"),
				Map.of("BUtil_InstanceClamp", 8192, "BUtil_CurveNode", 32, "BUtil_CurveInterpolation", 32, "BUtil_Language", "_en_us"));
		configureOptions(workerSaves.resolve("common/shaderpacks/BUtil_NotSelectedShaderPacks.json.data"),
				List.of("BUtil_EnableBloom", "BUtil_EnableAA", "BUtil_DepthAA"), Map.of());
	}

	private void configureOptions(Path target, List<String> options, Map<String, Object> defaults) throws IOException, JSONException {

		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("path", target.toString()).put("options", options).build();
		BattleNext_Logger.operationStarted(getClass(), "simulator.graphics.configure", fields);
		try {
			JSONObject settings;
			if ((boolean) invoke(files.isRegularFile, target)) {
				BattleNext_Logger.operationStarted(getClass(), "simulator.graphics.read", fields);
				settings = new JSONObject(new String((byte[]) invoke(files.readAllBytes, target), StandardCharsets.UTF_8));
				BattleNext_Logger.operationSucceeded(getClass(), "simulator.graphics.read",
						BattleNext_Logger.fields().put("path", target.toString()).put("settings", settings).build());
			} else {
				settings = new JSONObject();
				BattleNext_Logger.info(getClass(), "simulator.graphics.defaults",
						BattleNext_Logger.fields().put("path", target.toString()).put("source", "LunaLib missing-field defaults").build());
			}
			for (var entry : defaults.entrySet()) {
				if (settings.has(entry.getKey())) continue;
				settings.put(entry.getKey(), entry.getValue());
			}
			for (String option : options) {
				settings.put(option, false);
			}
			invoke(files.createDirectories, target.getParent());
			BattleNext_Logger.operationStarted(getClass(), "simulator.graphics.write",
					BattleNext_Logger.fields().put("path", target.toString()).put("settings", settings).build());
			invoke(files.write, target, settings.toString().getBytes(StandardCharsets.UTF_8));
			BattleNext_Logger.operationSucceeded(getClass(), "simulator.graphics.configure",
					BattleNext_Logger.fields().put("path", target.toString()).put("settings", settings).build());
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(getClass(), "simulator.graphics.configure", fields, exception);
			throw exception;
		}
	}

	public void write(BattleNext_SimulationRequest request, BattleNext_SimulationResult result) throws IOException, JSONException {
		Path target = directory("pending").resolve(result.id() + ".json");
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("path", target.toString()).put("result", result.toJson()).build();
		BattleNext_Logger.operationStarted(getClass(), "simulator.result.write", fields);
		try {
			Path staging = target.resolveSibling(target.getFileName() + ".pending");
			byte[] bytes = new JSONObject().put("request", request.toJson()).put("result", result.toJson()).toString().getBytes(StandardCharsets.UTF_8);
			invoke(files.write, staging, bytes);
			invoke(files.move, staging, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE});
			BattleNext_Logger.operationSucceeded(getClass(), "simulator.result.write", fields);
		} catch (IOException exception) {
			BattleNext_Logger.operationFailed(getClass(), "simulator.result.write", fields, exception);
			throw exception;
		}
	}

	public List<Path> pending() throws IOException {
		return listFiles(directory("pending"), false).stream().filter(path -> path.getFileName().toString().endsWith(".json")).toList();
	}

	public void writeSnapshot(BattleNext_TournamentSnapshot snapshot) throws IOException, JSONException {
		Path target = directory("tournaments").resolve(snapshot.getId() + ".json");
		Path staging = target.resolveSibling(target.getFileName() + ".pending");
		invoke(files.write, staging, snapshot.toJson().toString().getBytes(StandardCharsets.UTF_8));
		invoke(files.move, staging, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE});
		BattleNext_Logger.info(getClass(), "simulator.tournament.checkpoint", BattleNext_Logger.fields().put("id", snapshot.getId()).put("version", snapshot.getVersion()).build());
	}

	public List<BattleNext_TournamentSnapshot> tournamentSnapshots() throws IOException, JSONException {
		List<BattleNext_TournamentSnapshot> snapshots = new ArrayList<>();
		for (Path path : listFiles(directory("tournaments"), false)) {
			if (!path.toString().endsWith(".json")) continue;
			BattleNext_TournamentSnapshot snapshot = new BattleNext_TournamentSnapshot();
			snapshot.fromJson(new JSONObject(new String((byte[]) invoke(files.readAllBytes, path), StandardCharsets.UTF_8)));
			snapshots.add(snapshot);
		}
		return snapshots;
	}

	public void deleteSnapshot(String id) throws IOException {
		invoke(files.deleteIfExists, root.resolve("tournaments").resolve(id + ".json"));
	}

	public record Stored(BattleNext_SimulationRequest request, BattleNext_SimulationResult result) { }

	public Stored read(Path path) throws IOException, JSONException {
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("path", path.toString()).build();
		BattleNext_Logger.operationStarted(getClass(), "simulator.result.read", fields);
		try {
			JSONObject json = new JSONObject(new String((byte[]) invoke(files.readAllBytes, path), StandardCharsets.UTF_8));
			BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromJson(json.getJSONObject("request"));
			BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(json.getJSONObject("result"));
			if (!request.id().equals(result.id()) || !path.getFileName().toString().equals(result.id() + ".json")) throw new IOException("Simulator journal identity mismatch: " + path);
			BattleNext_Logger.operationSucceeded(getClass(), "simulator.result.read", BattleNext_Logger.fields()
					.put("path", path.toString()).put("result", result.toJson()).build());
			return new Stored(request, result);
		} catch (IOException | JSONException exception) {
			BattleNext_Logger.operationFailed(getClass(), "simulator.result.read", fields, exception);
			throw exception;
		}
	}

	public void acknowledge(String id) throws IOException {
		Path target = root.resolve("pending").resolve(id + ".json");
		invoke(files.deleteIfExists, target);
		BattleNext_Logger.info(getClass(), "simulator.result.acknowledged", BattleNext_Logger.fields().put("path", target.toString()).build());
	}

	public void cleanup(Path directory) throws IOException {
		Path target = directory.toAbsolutePath().normalize();
		if (!target.startsWith(root.resolve("runtime")) || target.equals(root)) throw new IOException("Simulator cleanup target is outside the owned runtime: " + target);
		for (Path path : listPaths(target, true).stream().sorted(Comparator.reverseOrder()).toList()) {
			invoke(files.delete, path);
		}
		BattleNext_Logger.info(getClass(), "simulator.runtime.cleaned", BattleNext_Logger.fields().put("path", target.toString()).build());
	}

	private List<Path> listPaths(Path directory, boolean recursive) throws IOException {
		try (Stream<Path> paths = (Stream<Path>) invoke(recursive ? files.walk : files.list, directory)) {
			return paths.sorted().toList();
		}
	}

	private Object invoke(MethodHandle handle, Object... arguments) throws IOException {
		try {
			return handle.invokeWithArguments(arguments);
		} catch (IOException exception) {
			throw exception;
		} catch (RuntimeException | Error exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IOException("Unable to perform simulator file operation: " + handle.type(), exception);
		}
	}

	private static class FileAccess {
		private final MethodHandle createDirectories;
		private final MethodHandle isDirectory;
		private final MethodHandle isRegularFile;
		private final MethodHandle list;
		private final MethodHandle walk;
		private final MethodHandle readAllBytes;
		private final MethodHandle write;
		private final MethodHandle move;
		private final MethodHandle delete;
		private final MethodHandle deleteIfExists;

		private FileAccess() throws IOException {

			try {
				Class<?> files = Class.forName("java.nio.file.Files", false, null);
				Class<?> attributes = Class.forName("java.nio.file.attribute.FileAttribute", false, null);
				Class<?> visits = Class.forName("java.nio.file.FileVisitOption", false, null);
				MethodHandles.Lookup lookup = MethodHandles.publicLookup();
				createDirectories = lookup.findStatic(files, "createDirectories", MethodType.methodType(Path.class, Path.class, attributes.arrayType()))
						.asCollector(attributes.arrayType(), 0);
				isDirectory = lookup.findStatic(files, "isDirectory", MethodType.methodType(boolean.class, Path.class, LinkOption[].class)).asCollector(LinkOption[].class, 0);
				isRegularFile = lookup.findStatic(files, "isRegularFile", MethodType.methodType(boolean.class, Path.class, LinkOption[].class)).asCollector(LinkOption[].class, 0);
				list = lookup.findStatic(files, "list", MethodType.methodType(Stream.class, Path.class));
				walk = lookup.findStatic(files, "walk", MethodType.methodType(Stream.class, Path.class, visits.arrayType())).asCollector(visits.arrayType(), 0);
				readAllBytes = lookup.findStatic(files, "readAllBytes", MethodType.methodType(byte[].class, Path.class));
				write = lookup.findStatic(files, "write", MethodType.methodType(Path.class, Path.class, byte[].class, OpenOption[].class)).asCollector(OpenOption[].class, 0);
				move = lookup.findStatic(files, "move", MethodType.methodType(Path.class, Path.class, Path.class, CopyOption[].class)).asFixedArity();
				delete = lookup.findStatic(files, "delete", MethodType.methodType(void.class, Path.class));
				deleteIfExists = lookup.findStatic(files, "deleteIfExists", MethodType.methodType(boolean.class, Path.class));
			} catch (ReflectiveOperationException exception) {
				throw new IOException("Unable to bind simulator file operations", exception);
			}
		}
	}
}
