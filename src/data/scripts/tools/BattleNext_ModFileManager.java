package data.scripts.tools;

import com.fs.starfarer.api.Global;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class BattleNext_ModFileManager {

	private static final String MOD_ID = "AI_Battles_Next";

	private BattleNext_ModFileManager() {

	}


	public static JSONObject readJson(Class<?> source, String path, String modId) throws IOException, JSONException {
		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).put("mod_id", modId).put("format", "json").build();
		BattleNext_Logger.operationStarted(source, "file.read", input);
		try {
			JSONObject result = Global.getSettings().loadJSON(path, modId);
			BattleNext_Logger.operationSucceeded(source, "file.read",
					BattleNext_Logger.fields().put("path", path).put("mod_id", modId).put("object", result).build());
			return result;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(source, "file.read", input, exception);
			throw exception;
		}
	}

	public static JSONArray readCsv(Class<?> source, String path, String modId) throws IOException, JSONException {
		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).put("mod_id", modId).put("format", "csv").build();
		BattleNext_Logger.operationStarted(source, "file.read", input);
		try {
			JSONArray result = Global.getSettings().loadCSV(path, modId);
			BattleNext_Logger.operationSucceeded(source, "file.read",
					BattleNext_Logger.fields().put("path", path).put("mod_id", modId).put("rows", result).build());
			return result;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(source, "file.read", input, exception);
			throw exception;
		}
	}

	public static String readText(Class<?> source, String path, String modId) throws IOException, JSONException {
		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("path", path).put("mod_id", modId).put("format", "text").build();
		BattleNext_Logger.operationStarted(source, "file.read", input);
		try {
			String result = Global.getSettings().loadText(path, modId);
			BattleNext_Logger.operationSucceeded(source, "file.read", input);
			return result;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(source, "file.read", input, exception);
			throw exception;
		}
	}

	public static JSONArray readMergedCsv(Class<?> source, String key, String path) throws IOException, JSONException {
		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("key", key).put("path", path).build();
		BattleNext_Logger.operationStarted(source, "resource.batch.load", input);
		try {
			JSONArray result = Global.getSettings().getMergedSpreadsheetData(key, path);
			BattleNext_Logger.operationSucceeded(source, "resource.batch.load",
					BattleNext_Logger.fields().put("key", key).put("path", path).put("rows", result).build());
			return result;
		} catch (IOException | JSONException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(source, "resource.batch.load", input, exception);
			throw exception;
		}
	}

	public static void writeText(String relativePath, String text) throws IOException {
		writeText(relativePath, text, true);
	}

	public static void writeText(String relativePath, String text, boolean overwrite) throws IOException {
		if (text == null) {
			BattleNext_Logger.warn(BattleNext_ModFileManager.class, "file.write.validation.rejected",
					BattleNext_Logger.fields().put("path", relativePath).put("text", text).put("overwrite", overwrite).build());
			throw new IOException("Unable to write null text: " + relativePath);
		}
		writeBytes(relativePath, text.getBytes(StandardCharsets.UTF_8), overwrite);
	}

	public static void writeBytes(String relativePath, byte[] data) throws IOException {
		writeBytes(relativePath, data, true);
	}

	public static void writeBytes(String relativePath, byte[] data, boolean overwrite) throws IOException {

		BattleNext_Logger.Fields logFields = BattleNext_Logger.fields().put("path", relativePath).put("mod_id", MOD_ID).put("overwrite", overwrite).build();
		BattleNext_Logger.operationStarted(BattleNext_ModFileManager.class, "file.write", logFields);
		try {
			if (data == null) throw new IOException("Unable to write null data: " + relativePath);
			ReflectiveModFileOperations bridge = createBridge(relativePath);
			Object target = resolveTarget(bridge, relativePath, overwrite);
			Object output = null;
			Throwable failure = null;
			try {
				output = bridge.outputConstructor.invoke(targetPath(target), false);
				bridge.write.invoke(output, data);
				bridge.flush.invoke(output);
			} catch (Throwable exception) {
				failure = exception;
			} finally {
				if (output != null) {
					try {
						bridge.close.invoke(output);
					} catch (Throwable exception) {
						if (failure == null) failure = exception;
						else failure.addSuppressed(exception);
					}
				}
			}
			if (failure != null) throw writeFailure(relativePath, failure);
			BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.write", logFields);
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_ModFileManager.class, "file.write", logFields, exception);
			throw exception;
		}
	}

	public static byte[] readBytes(String relativePath) throws IOException {

		BattleNext_Logger.Fields logFields = BattleNext_Logger.fields().put("path", relativePath).put("mod_id", MOD_ID).build();
		BattleNext_Logger.operationStarted(BattleNext_ModFileManager.class, "file.read", logFields);
		try {
			ReflectiveModFileOperations bridge = createBridge(relativePath);
			ModFileOperationTarget target = resolveExisting(bridge, relativePath);
			if (!(boolean) invoke(bridge.exists, target.file)) {
				throw new IOException("The target does not exist: " + relativePath);
			}
			if ((boolean) invoke(bridge.directoryCheck, target.file)) {
				throw new IOException("The target is a directory: " + relativePath);
			}
			Object input = null;
			Object output = null;
			byte[] result = null;
			Throwable failure = null;
			try {
				input = bridge.inputConstructor.invoke(target.canonicalPath);
				output = bridge.byteArrayOutputConstructor.invoke();
				byte[] buffer = new byte[8192];
				int count;
				while ((count = (int) bridge.read.invoke(input, buffer)) >= 0) {
					if (count > 0) bridge.byteArrayWrite.invoke(output, buffer, 0, count);
				}
				result = (byte[]) bridge.toByteArray.invoke(output);
			} catch (Throwable exception) {
				failure = exception;
			} finally {
				if (input != null) {
					try {
						bridge.closeInput.invoke(input);
					} catch (Throwable exception) {
						if (failure == null) failure = exception;
						else failure.addSuppressed(exception);
					}
				}
			}
			if (failure != null) throw fileFailure(relativePath, failure);
			BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.read", logFields);
			return result;
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_ModFileManager.class, "file.read", logFields, exception);
			throw exception;
		}
	}

	public static List<String> list(String directory, boolean recursive) throws IOException {
		ReflectiveModFileOperations bridge = createBridge(directory);
		return listRoot(bridge, normalizePath(directory), recursive);
	}

	public static List<String> listJson(String directory) throws IOException {
		return list(directory, false).stream().filter(path -> path.endsWith(".json")).toList();
	}

	public static boolean exists(String relativePath) throws IOException {
		ReflectiveModFileOperations bridge = createBridge(relativePath);
		ModFileOperationTarget target = resolveExisting(bridge, relativePath);
		return (boolean) invoke(bridge.exists, target.file);
	}

	public static void delete(String relativePath) throws IOException {

		BattleNext_Logger.Fields logFields = BattleNext_Logger.fields().put("path", relativePath).put("mod_id", MOD_ID).build();
		BattleNext_Logger.operationStarted(BattleNext_ModFileManager.class, "file.delete", logFields);
		try {
			ReflectiveModFileOperations bridge = createBridge(relativePath);
			ModFileOperationTarget target = resolveExisting(bridge, relativePath);
			if (!(boolean) invoke(bridge.exists, target.file)) {
				BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.delete", logFields);
				return;
			}
			if ((boolean) invoke(bridge.directoryCheck, target.file)) {
				throw new IOException("The target is a directory: " + relativePath);
			}
			deleteTarget(bridge, target.file, relativePath);
			BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.delete", logFields);
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_ModFileManager.class, "file.delete", logFields, exception);
			throw exception;
		}
	}

	public static void deleteRecursively(String relativePath) throws IOException {

		BattleNext_Logger.Fields logFields = BattleNext_Logger.fields().put("path", relativePath).put("mod_id", MOD_ID).put("recursive", true).build();
		BattleNext_Logger.operationStarted(BattleNext_ModFileManager.class, "file.delete.bundle", logFields);
		try {
			ReflectiveModFileOperations bridge = createBridge(relativePath);
			ModFileOperationTarget target = resolveExisting(bridge, relativePath);
			if (!(boolean) invoke(bridge.exists, target.file)) {
				BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.delete.bundle", logFields);
				return;
			}
			if (!(boolean) invoke(bridge.directoryCheck, target.file)) {
				throw new IOException("The target is not a directory: " + relativePath);
			}
			deleteTree(bridge, target.file, target.rootCanonical, relativePath);
			BattleNext_Logger.operationSucceeded(BattleNext_ModFileManager.class, "file.delete.bundle", logFields);
		} catch (IOException | RuntimeException exception) {
			BattleNext_Logger.operationFailed(BattleNext_ModFileManager.class, "file.delete.bundle", logFields, exception);
			throw exception;
		}
	}

	private static ReflectiveModFileOperations createBridge(String relativePath) throws IOException {
		try {
			Class<?> fileType = Class.forName("java.io.File", false, null);
			Class<?> inputType = Class.forName("java.io.FileInputStream", false, null);
			Class<?> outputType = Class.forName("java.io.FileOutputStream", false, null);
			Class<?> byteArrayOutputType = Class.forName("java.io.ByteArrayOutputStream", false, null);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			return new ReflectiveModFileOperations(
					lookup.findConstructor(fileType, MethodType.methodType(void.class, String.class)),
					lookup.findVirtual(fileType, "exists", MethodType.methodType(boolean.class)),
					lookup.findVirtual(fileType, "isDirectory", MethodType.methodType(boolean.class)),
					lookup.findVirtual(fileType, "isFile", MethodType.methodType(boolean.class)),
					lookup.findVirtual(fileType, "getName", MethodType.methodType(String.class)),
					lookup.findVirtual(fileType, "getCanonicalPath", MethodType.methodType(String.class)),
					lookup.findVirtual(fileType, "getAbsolutePath", MethodType.methodType(String.class)),
					lookup.findVirtual(fileType, "getParentFile", MethodType.methodType(fileType)),
					lookup.findVirtual(fileType, "mkdirs", MethodType.methodType(boolean.class)),
					lookup.findVirtual(fileType, "listFiles", MethodType.methodType(fileType.arrayType())),
					lookup.findVirtual(fileType, "delete", MethodType.methodType(boolean.class)),
					lookup.findConstructor(inputType, MethodType.methodType(void.class, String.class)),
					lookup.findVirtual(inputType, "read", MethodType.methodType(int.class, byte[].class)),
					lookup.findConstructor(outputType, MethodType.methodType(void.class, String.class, boolean.class)),
					lookup.findVirtual(outputType, "write", MethodType.methodType(void.class, byte[].class)),
					lookup.findVirtual(outputType, "flush", MethodType.methodType(void.class)),
					lookup.findVirtual(outputType, "close", MethodType.methodType(void.class)),
					lookup.findVirtual(inputType, "close", MethodType.methodType(void.class)),
					lookup.findConstructor(byteArrayOutputType, MethodType.methodType(void.class)),
					lookup.findVirtual(byteArrayOutputType, "write",
							MethodType.methodType(void.class, byte[].class, int.class, int.class)),
					lookup.findVirtual(byteArrayOutputType, "toByteArray", MethodType.methodType(byte[].class)));
		} catch (Throwable exception) {
			throw writeFailure(relativePath, exception);
		}
	}

	private static Object resolveTarget(ReflectiveModFileOperations bridge, String relativePath, boolean overwrite) throws IOException {
		String normalizedPath = normalizePath(relativePath);
		try {
			String rootPath = Global.getSettings().getModManager().getModSpec(MOD_ID).getPath();
			Object root = bridge.fileConstructor.invoke(rootPath);
			if (!(boolean) bridge.exists.invoke(root) || !(boolean) bridge.directoryCheck.invoke(root)) {
				throw new IOException("Mod root is missing: " + rootPath);
			}
			Object target = bridge.fileConstructor.invoke(rootPath + "/" + normalizedPath);
			String rootCanonical = (String) bridge.canonicalPath.invoke(root);
			String targetCanonical = (String) bridge.canonicalPath.invoke(target);
			ensureInsideRoot(rootCanonical, targetCanonical, normalizedPath);
			if (samePath(rootCanonical, targetCanonical)) {
				throw new IOException("The mod root is not a file target: " + normalizedPath);
			}
			if ((boolean) bridge.exists.invoke(target)) {
				if ((boolean) bridge.directoryCheck.invoke(target)) {
					throw new IOException("The target is a directory: " + normalizedPath);
				}
				if (!overwrite) throw new IOException("The target already exists: " + normalizedPath);
			}
			Object parent = bridge.parent.invoke(target);
			if (parent == null) throw new IOException("The target has no parent: " + normalizedPath);
			String parentCanonical = (String) bridge.canonicalPath.invoke(parent);
			ensureInsideRoot(rootCanonical, parentCanonical, normalizedPath);
			if (!(boolean) bridge.exists.invoke(parent) && !(boolean) bridge.mkdirs.invoke(parent)) {
				throw new IOException("Unable to create parent directory: " + normalizedPath);
			}
			if (!(boolean) bridge.exists.invoke(parent) || !(boolean) bridge.directoryCheck.invoke(parent)) {
				throw new IOException("The parent is not a directory: " + normalizedPath);
			}
			targetCanonical = (String) bridge.canonicalPath.invoke(target);
			ensureInsideRoot(rootCanonical, targetCanonical, normalizedPath);
			return new ModFileOperationTarget(target, targetCanonical, rootCanonical);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw writeFailure(relativePath, exception);
		}
	}

	private static ModFileOperationTarget resolveExisting(ReflectiveModFileOperations bridge, String relativePath) throws IOException {
		String normalizedPath = normalizePath(relativePath);
		try {
			String rootPath = Global.getSettings().getModManager().getModSpec(MOD_ID).getPath();
			Object root = bridge.fileConstructor.invoke(rootPath);
			if (!(boolean) bridge.exists.invoke(root) || !(boolean) bridge.directoryCheck.invoke(root)) {
				throw new IOException("Mod root is missing: " + rootPath);
			}
			Object target = bridge.fileConstructor.invoke(rootPath + "/" + normalizedPath);
			String rootCanonical = (String) bridge.canonicalPath.invoke(root);
			String targetCanonical = (String) bridge.canonicalPath.invoke(target);
			ensureInsideRoot(rootCanonical, targetCanonical, normalizedPath);
			if (samePath(rootCanonical, targetCanonical)) {
				throw new IOException("The mod root is not a file target: " + normalizedPath);
			}
			return new ModFileOperationTarget(target, targetCanonical, rootCanonical);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw fileFailure(relativePath, exception);
		}
	}

	private static String normalizePath(String relativePath) throws IOException {
		if (relativePath == null || relativePath.isBlank()) {
			throw new IOException("Unable to access empty relative path");
		}
		String normalized = relativePath.replace('\\', '/');
		if (isAbsolute(normalized)) {
			throw new IOException("Absolute paths are not allowed: " + relativePath);
		}
		return normalized;
	}

	private static Object invoke(MethodHandle handle, Object target) throws IOException {
		try {
			return handle.invoke(target);
		} catch (Throwable exception) {
			throw fileFailure("metadata", exception);
		}
	}

	private static void deleteTarget(ReflectiveModFileOperations bridge, Object target, String relativePath) throws IOException {
		try {
			if (!(boolean) bridge.delete.invoke(target) && (boolean) bridge.exists.invoke(target)) {
				throw new IOException("Unable to delete target: " + relativePath);
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw fileFailure(relativePath, exception);
		}
	}

	private static void deleteTree(ReflectiveModFileOperations bridge, Object target, String rootCanonical, String relativePath)
			throws IOException {
		try {
			String canonical = (String) bridge.canonicalPath.invoke(target);
			String absolute = (String) bridge.absolutePath.invoke(target);
			ensureInsideRoot(rootCanonical, canonical, relativePath);
			if (!samePath(canonical, absolute)) {
				throw new IOException("Symbolic links are not allowed in recursive deletion: " + relativePath);
			}
			if ((boolean) bridge.directoryCheck.invoke(target)) {
				Object[] children = (Object[]) bridge.listFiles.invoke(target);
				if (children == null) throw new IOException("Unable to list directory: " + relativePath);
				for (Object child : children) {
					deleteTree(bridge, child, rootCanonical, relativePath);
				}
			}
			deleteTarget(bridge, target, relativePath);
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw fileFailure(relativePath, exception);
		}
	}

	private static List<String> listRoot(ReflectiveModFileOperations bridge, String directory, boolean recursive) throws IOException {
		try {
			String rootPath = Global.getSettings().getModManager().getModSpec(MOD_ID).getPath();
			Object root = bridge.fileConstructor.invoke(rootPath);
			if (!(boolean) bridge.exists.invoke(root) || !(boolean) bridge.directoryCheck.invoke(root)) {
				throw new IOException("Mod root is missing: " + rootPath);
			}
			Object folder = bridge.fileConstructor.invoke(rootPath + "/" + directory);
			if (!(boolean) bridge.exists.invoke(folder)) return List.of();
			if (!(boolean) bridge.directoryCheck.invoke(folder)) {
				throw new IOException("Not a directory: " + rootPath + "/" + directory);
			}
			List<String> result = new ArrayList<>();
			collect(bridge, folder, directory, recursive, result);
			Collections.sort(result);
			return result;
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw fileFailure(directory, exception);
		}
	}

	private static void collect(ReflectiveModFileOperations bridge, Object folder, String relative, boolean recursive, List<String> result)
			throws IOException {
		try {
			Object[] children = (Object[]) bridge.listFiles.invoke(folder);
			if (children == null) throw new IOException("Unable to list directory: " + relative);
			for (Object child : children) {
				String path = relative + "/" + (String) bridge.name.invoke(child);
				if ((boolean) bridge.regularFile.invoke(child)) {
					result.add(path);
				} else if (recursive && (boolean) bridge.directoryCheck.invoke(child)) {
					collect(bridge, child, path, true, result);
				}
			}
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw fileFailure(relative, exception);
		}
	}

	private static String targetPath(Object target) {
		return ((ModFileOperationTarget) target).canonicalPath;
	}

	private static void ensureInsideRoot(String root, String target, String relativePath) throws IOException {
		String comparableRoot = comparablePath(root);
		String comparableTarget = comparablePath(target);
		String prefix = comparableRoot.endsWith("/") ? comparableRoot : comparableRoot + "/";
		if (!comparableTarget.equals(comparableRoot) && !comparableTarget.startsWith(prefix)) {
			throw new IOException("Path escapes the mod root: " + relativePath);
		}
	}

	private static boolean samePath(String first, String second) {
		return comparablePath(first).equals(comparablePath(second));
	}

	private static String comparablePath(String path) {
		if (isWindowsPath(path)) return path.replace('\\', '/').toLowerCase(Locale.ROOT);
		return path;
	}

	private static boolean isWindowsPath(String path) {
		return path.startsWith("\\\\") || (path.length() >= 2 && path.charAt(1) == ':');
	}

	private static boolean isAbsolute(String path) {
		if (path.startsWith("/") || path.startsWith("\\")) return true;
		return path.length() >= 2 && Character.isLetter(path.charAt(0)) && path.charAt(1) == ':';
	}

	private static IOException fileFailure(String relativePath, Throwable exception) {
		Throwable cause = exception;
		if (exception instanceof InvocationTargetException invocation
				&& invocation.getCause() != null) {
			cause = invocation.getCause();
		}
		String message = cause.getMessage();
		return new IOException("Unable to access mod file " + relativePath + ": "
				+ (message == null ? cause.getClass().getName() : message), cause);
	}

	private static IOException writeFailure(String relativePath, Throwable exception) {
		return fileFailure(relativePath, exception);
	}

	private static class ModFileOperationTarget {
		private final Object file;
		private final String canonicalPath;
		private final String rootCanonical;

		private ModFileOperationTarget(Object file, String canonicalPath, String rootCanonical) {
			this.file = file;
			this.canonicalPath = canonicalPath;
			this.rootCanonical = rootCanonical;
		}
	}

	private static class ReflectiveModFileOperations {
		private final MethodHandle fileConstructor;
		private final MethodHandle exists;
		private final MethodHandle directoryCheck;
		private final MethodHandle regularFile;
		private final MethodHandle name;
		private final MethodHandle canonicalPath;
		private final MethodHandle absolutePath;
		private final MethodHandle parent;
		private final MethodHandle mkdirs;
		private final MethodHandle listFiles;
		private final MethodHandle delete;
		private final MethodHandle inputConstructor;
		private final MethodHandle read;
		private final MethodHandle outputConstructor;
		private final MethodHandle write;
		private final MethodHandle flush;
		private final MethodHandle close;
		private final MethodHandle closeInput;
		private final MethodHandle byteArrayOutputConstructor;
		private final MethodHandle byteArrayWrite;
		private final MethodHandle toByteArray;

		private ReflectiveModFileOperations(MethodHandle fileConstructor, MethodHandle exists, MethodHandle directoryCheck,
				MethodHandle regularFile, MethodHandle name, MethodHandle canonicalPath,
				MethodHandle absolutePath, MethodHandle parent, MethodHandle mkdirs,
				MethodHandle listFiles, MethodHandle delete, MethodHandle inputConstructor, MethodHandle read,
				MethodHandle outputConstructor, MethodHandle write, MethodHandle flush, MethodHandle close,
				MethodHandle closeInput, MethodHandle byteArrayOutputConstructor, MethodHandle byteArrayWrite,
				MethodHandle toByteArray) {
			this.fileConstructor = fileConstructor;
			this.exists = exists;
			this.directoryCheck = directoryCheck;
			this.regularFile = regularFile;
			this.name = name;
			this.canonicalPath = canonicalPath;
			this.absolutePath = absolutePath;
			this.parent = parent;
			this.mkdirs = mkdirs;
			this.listFiles = listFiles;
			this.delete = delete;
			this.inputConstructor = inputConstructor;
			this.read = read;
			this.outputConstructor = outputConstructor;
			this.write = write;
			this.flush = flush;
			this.close = close;
			this.closeInput = closeInput;
			this.byteArrayOutputConstructor = byteArrayOutputConstructor;
			this.byteArrayWrite = byteArrayWrite;
			this.toByteArray = toByteArray;
		}
	}
}
