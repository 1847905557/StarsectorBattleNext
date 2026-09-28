package data.model.fleet.io;

import data.model.BattleNext_BaseSaver;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BattleNext_FleetSaver extends BattleNext_BaseSaver<BattleNext_FleetModel> {

	private static final String BASE_FOLDER = "tournament";
	private static final String BASE_ROOT = BASE_FOLDER + "/";
	private static final String DATA_FILE = "fleet_data.json";

	private final FileOperations files;

	public BattleNext_FleetSaver() {
		this(new ModFileOperations());
	}

	BattleNext_FleetSaver(FileOperations files) {
		this.files = files;
	}

	@Override
	public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {
		if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
			throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
		}

		String directory = BASE_ROOT + fleet.getFolderName();
		String path = directory + "/" + DATA_FILE;
		String oldFolder = previousFolder(fleet.getSourcePath());
		String oldDirectory = oldFolder == null ? null : BASE_ROOT + oldFolder;
		String fleetJson = fleet.toJson().toString();
		String previousBackup = readPreviousBackup(path);
		Map<String, String> variants = prepareVariants(fleet, directory);
		Set<String> staleVariants = findStaleVariants(directory, variants.keySet());
		Map<String, byte[]> targetSnapshot = snapshot(directory);
		Map<String, byte[]> oldSnapshot = oldDirectory == null || oldDirectory.equals(directory)
				? Map.of() : snapshot(oldDirectory);

		try {
			if (previousBackup != null) {
				files.writeBytes(directory + "/" + DATA_FILE + ".bak",
						previousBackup.getBytes(StandardCharsets.UTF_8));
			}
			files.writeBytes(path, fleetJson.getBytes(StandardCharsets.UTF_8));
			for (Map.Entry<String, String> variant : variants.entrySet()) {
				files.writeBytes(variant.getKey(), variant.getValue().getBytes(StandardCharsets.UTF_8));
			}
			for (String staleVariant : staleVariants) {
				files.delete(staleVariant);
			}
			if (oldDirectory != null && !oldDirectory.equals(directory)) {
				files.deleteRecursively(oldDirectory);
			}
			return path;
		} catch (Exception exception) {
			try {
				restore(directory, targetSnapshot);
				restore(oldDirectory, oldSnapshot);
			} catch (Exception rollbackException) {
				exception.addSuppressed(rollbackException);
			}
			throwFailure(exception);
			return path;
		}
	}

	@Override
	public void delete(BattleNext_FleetModel fleet) throws IOException {
		if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
			throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
		}
		files.deleteRecursively(BASE_ROOT + fleet.getFolderName());
	}

	private String readPreviousBackup(String path) throws IOException, JSONException {
		if (!files.exists(path)) return null;
		return new JSONObject(new String(files.readBytes(path), StandardCharsets.UTF_8)).toString();
	}

	private Map<String, String> prepareVariants(BattleNext_FleetModel fleet, String directory)
			throws JSONException, IOException {
		Map<String, String> variants = new LinkedHashMap<>();
		for (BattleNext_VariantBuilder variant : fleet.getVariants()) {
			if (!BattleNext_EntryId.isValidPathSegment(variant.getId())) {
				throw new IOException("Invalid fleet variant ID: " + variant.getId());
			}
			String path = directory + "/" + variant.getId() + ".variant";
			if (variants.put(path, variant.toJson().toString(2)) != null) {
				throw new IOException("Duplicate fleet variant ID: " + variant.getId());
			}
		}
		return variants;
	}

	private Set<String> findStaleVariants(String directory, Set<String> currentVariants) throws IOException {
		Set<String> stale = new LinkedHashSet<>();
		if (!files.exists(directory)) return stale;
		for (String path : files.list(directory, false)) {
			if (path.endsWith(".variant") && !currentVariants.contains(path)) stale.add(path);
		}
		return stale;
	}

	private Map<String, byte[]> snapshot(String directory) throws IOException {
		if (directory == null || !files.exists(directory)) return Map.of();
		Map<String, byte[]> snapshot = new LinkedHashMap<>();
		for (String path : files.list(directory, true)) {
			snapshot.put(path, files.readBytes(path));
		}
		return snapshot;
	}

	private void restore(String directory, Map<String, byte[]> snapshot) throws IOException {
		if (directory == null) return;
		if (files.exists(directory)) files.deleteRecursively(directory);
		for (Map.Entry<String, byte[]> entry : snapshot.entrySet()) {
			files.writeBytes(entry.getKey(), entry.getValue());
		}
	}

	private void throwFailure(Exception exception) throws IOException, JSONException {
		if (exception instanceof IOException ioException) throw ioException;
		if (exception instanceof JSONException jsonException) throw jsonException;
		if (exception instanceof RuntimeException runtimeException) throw runtimeException;
		throw new IOException("Unable to save fleet", exception);
	}

	private String previousFolder(String sourcePath) {
		if (sourcePath == null) return null;
		String normalized = sourcePath.replace('\\', '/');
		if (!normalized.startsWith(BASE_ROOT) || !normalized.endsWith("/" + DATA_FILE)) return null;
		String folder = normalized.substring(BASE_ROOT.length(), normalized.length() - ("/" + DATA_FILE).length());
		return folder.indexOf('/') < 0 ? folder : null;
	}

	interface FileOperations {
		boolean exists(String path) throws IOException;

		List<String> list(String directory, boolean recursive) throws IOException;

		byte[] readBytes(String path) throws IOException;

		void writeBytes(String path, byte[] data) throws IOException;

		void delete(String path) throws IOException;

		void deleteRecursively(String path) throws IOException;
	}

	private static class ModFileOperations implements FileOperations {

		@Override
		public boolean exists(String path) throws IOException {
			return BattleNext_ModFileManager.exists(path);
		}

		@Override
		public List<String> list(String directory, boolean recursive) throws IOException {
			return BattleNext_ModFileManager.list(directory, recursive);
		}

		@Override
		public byte[] readBytes(String path) throws IOException {
			return BattleNext_ModFileManager.readBytes(path);
		}

		@Override
		public void writeBytes(String path, byte[] data) throws IOException {
			BattleNext_ModFileManager.writeBytes(path, data);
		}

		@Override
		public void delete(String path) throws IOException {
			BattleNext_ModFileManager.delete(path);
		}

		@Override
		public void deleteRecursively(String path) throws IOException {
			BattleNext_ModFileManager.deleteRecursively(path);
		}
	}
}
