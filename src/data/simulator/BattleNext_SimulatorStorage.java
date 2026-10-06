package data.simulator;

import data.scripts.tools.BattleNext_Logger;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

public class BattleNext_SimulatorStorage {

	private final Path root;
	private final FileAccess files;
	private final Map<Path, String> loadErrors = new LinkedHashMap<>();

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

	public void write(BattleNext_SimulationRequest request, BattleNext_SimulationResult result) throws IOException, JSONException {
		Path target = directory("_pending").resolve(result.id() + ".json");
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
		return catalogFiles("_pending");
	}

	private List<Path> catalogFiles(String name) {
		try {
			return listFiles(directory(name), false).stream().filter(path -> path.getFileName().toString().endsWith(".json")).toList();
		} catch (IOException exception) {
			loadErrors.put(root.resolve(name), exception.toString());
			BattleNext_Logger.warn(getClass(), "simulator.source.unavailable", BattleNext_Logger.fields().put("path", root.resolve(name).toString()).build(), exception);
			return List.of();
		}
	}

	public void writeTask(BattleNext_SimulationTask task) throws IOException, JSONException {
		synchronized (task) {
			Path target = root.resolve(task.getRequest().id() + ".json");
			Path staging = target.resolveSibling(target.getFileName() + ".pending");
			invoke(files.createDirectories, root);
			invoke(files.write, staging, task.toJson().toString().getBytes(StandardCharsets.UTF_8));
			invoke(files.move, staging, target, new CopyOption[]{StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE});
		}
	}

	public List<JSONObject> readTasks() throws IOException, JSONException {
		loadErrors.clear();
		List<JSONObject> tasks = new ArrayList<>();
		Map<Path, JSONObject> candidates = new LinkedHashMap<>();
		Map<String, Integer> counts = new HashMap<>();
		for (Path path : catalogFiles("")) {
			String raw = "";
			try {
				raw = new String((byte[]) invoke(files.readAllBytes, path), StandardCharsets.UTF_8);
				JSONObject json = new JSONObject(raw);
				String id = json.getJSONObject("request").getString("id");
				counts.merge(id, 1, Integer::sum);
				if (!path.getFileName().toString().equals(id + ".json")) throw new IOException("Simulator task file identity mismatch: " + path);
				candidates.put(path, json);
			} catch (IOException | JSONException | IllegalArgumentException exception) {
				loadErrors.put(path, raw + ": " + exception);
				BattleNext_Logger.warn(getClass(), "simulator.task.file.rejected", BattleNext_Logger.fields().put("path", path.toString()).put("raw", raw).build(), exception);
			}
		}
		for (JSONObject json : candidates.values()) {
			String id = json.getJSONObject("request").getString("id");
			if (counts.get(id) > 1) {
				rejectTask(json, new IllegalArgumentException("Duplicate simulator task ID: " + id));
				continue;
			}
			tasks.add(json);
		}
		return tasks;
	}

	public void rejectTask(JSONObject json, Exception exception) throws JSONException {
		String id = json.getJSONObject("request").getString("id");
		Path path = root.resolve(id + ".json");
		loadErrors.put(path, json + ": " + exception);
		BattleNext_Logger.warn(getClass(), "simulator.task.item.rejected", BattleNext_Logger.fields().put("path", path.toString()).put("raw", json).build(), exception);
	}

	public Map<Path, String> getLoadErrors() {
		return Map.copyOf(loadErrors);
	}

	public record Stored(BattleNext_SimulationRequest request, BattleNext_SimulationResult result) { }

	public Stored read(Path path) throws IOException, JSONException {
		BattleNext_Logger.Fields fields = BattleNext_Logger.fields().put("path", path.toString()).build();
		BattleNext_Logger.operationStarted(getClass(), "simulator.result.read", fields);
		String raw = "";
		try {
			raw = new String((byte[]) invoke(files.readAllBytes, path), StandardCharsets.UTF_8);
			JSONObject json = new JSONObject(raw);
			BattleNext_SimulationRequest request = BattleNext_SimulationRequest.fromJson(json.getJSONObject("request"));
			BattleNext_SimulationResult result = BattleNext_SimulationResult.fromJson(json.getJSONObject("result"));
			if (!request.id().equals(result.id()) || !path.getFileName().toString().equals(result.id() + ".json")) throw new IOException("Simulator journal identity mismatch: " + path);
			BattleNext_Logger.operationSucceeded(getClass(), "simulator.result.read", BattleNext_Logger.fields()
					.put("path", path.toString()).put("result", result.toJson()).build());
			return new Stored(request, result);
		} catch (IOException | JSONException | IllegalArgumentException exception) {
			loadErrors.put(path, raw + ": " + exception);
			BattleNext_Logger.operationFailed(getClass(), "simulator.result.read", fields, exception);
			throw exception;
		}
	}

	public void acknowledge(String id) throws IOException {
		Path target = root.resolve("_pending").resolve(id + ".json");
		invoke(files.deleteIfExists, target);
		BattleNext_Logger.info(getClass(), "simulator.result.acknowledged", BattleNext_Logger.fields().put("path", target.toString()).build());
	}

	public void deleteTask(String id) throws IOException {
		invoke(files.deleteIfExists, root.resolve("_pending").resolve(id + ".json"));
		invoke(files.deleteIfExists, root.resolve(id + ".json.pending"));
		invoke(files.deleteIfExists, root.resolve(id + ".json"));
	}

	public void cleanup(Path directory) throws IOException {
		Path target = directory.toAbsolutePath().normalize();
		if (!target.startsWith(root.resolve("_runtime")) || target.equals(root)) throw new IOException("Simulator cleanup target is outside the owned runtime: " + target);
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
