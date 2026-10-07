package data.model.fleet.io;

import data.model.BattleNext_BaseSaver;
import data.model.BattleNext_LoadSource;
import data.model.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_VariantBuilder;
import data.scripts.tools.BattleNext_EntryId;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import org.json.JSONException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class BattleNext_FleetSaver extends BattleNext_BaseSaver<BattleNext_FleetModel> {

	private static final String BASE_FOLDER = "tournament";
	private static final String BASE_ROOT = BASE_FOLDER + "/";
	private static final String DATA_FILE = "fleet_data.json";
	private static final String BACKUP_FOLDER = "backup";
	private static final DateTimeFormatter BACKUP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

	private final FileOperations files;
	private final TimeProvider timeProvider;

	public BattleNext_FleetSaver() {
		this(new ModFileOperations(), () -> LocalDateTime.now(ZoneId.systemDefault()));
	}

	BattleNext_FleetSaver(FileOperations files) {
		this(files, () -> LocalDateTime.now(ZoneId.systemDefault()));
	}

	BattleNext_FleetSaver(FileOperations files, TimeProvider timeProvider) {
		this.files = files;
		this.timeProvider = timeProvider;
	}

	@Override
	public String save(BattleNext_FleetModel fleet) throws IOException, JSONException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("fleet", fleet).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetSaver.class, "fleet.commit", input);
		try {
			if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
				throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
			}

			String directory = BASE_ROOT + fleet.getFolderName();
			String path = directory + "/" + DATA_FILE;
			if (fleet.getSourceType() == BattleNext_LoadSource.LEGACY) {
				String source = fleet.getSourcePath().replace('\\', '/');
				String file = source.substring(source.lastIndexOf('/') + 1);
				String number = file.replaceAll("^player(\\d+)_data\\.csv$", "$1");
				if (fleet.getId().equals("ab_player" + number)
						|| directory.equalsIgnoreCase(source.substring(0, source.lastIndexOf('/')))) {
					throw new IOException("Legacy fleet requires a new ID and target directory: " + source);
				}
			}
			if (files.exists(directory) && !files.exists(path)) {
				List<String> targetFiles = files.list(directory, false);
				for (String target : targetFiles) {
					String file = target.substring(target.lastIndexOf('/') + 1);
					if (!file.matches("player\\d+_data\\.csv")) continue;
					String number = file.replaceAll("^player(\\d+)_data\\.csv$", "$1");
					if (targetFiles.contains(directory + "/player" + number + "_fleet.csv")) {
						throw new IOException("Legacy fleet directory is reserved: " + directory);
					}
				}
			}
			String oldFolder = previousFolder(fleet.getSourcePath());
			String oldDirectory = oldFolder == null ? null : BASE_ROOT + oldFolder;
			if (oldDirectory != null && !oldDirectory.equals(directory) && files.exists(path)) {
				throw new IOException("Fleet target already exists: " + path);
			}
			String fleetJson = fleet.toJson().toString();
			Map<String, String> variants = prepareVariants(fleet, directory);
			Set<String> staleVariants = findStaleVariants(directory, variants.keySet());
			Map<String, byte[]> targetSnapshot = snapshot(directory);
			Map<String, byte[]> oldSnapshot = oldDirectory == null || oldDirectory.equals(directory)
					? Map.of() : snapshot(oldDirectory);

			try {
				if (oldDirectory != null && !oldDirectory.equals(directory)) {
					copyBackupHistory(oldDirectory, directory);
					createBackup(oldDirectory, directory);
				} else {
					createBackup(directory, directory);
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
				BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.commit",
					() -> BattleNext_Logger.fields().put("fleet", fleet).put("path", path).put("variant_paths", variants.keySet()).put("stale_variants", staleVariants).build());
				return path;
			} catch (Exception exception) {
				BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.commit",
					BattleNext_Logger.fields().put("fleet", fleet).put("path", path).put("variant_paths", variants.keySet()).put("stale_variants", staleVariants).build(), exception);
				BattleNext_Logger.operationStarted(BattleNext_FleetSaver.class, "fleet.rollback",
					() -> BattleNext_Logger.fields().put("fleet", fleet).put("target_directory", directory).put("previous_directory", oldDirectory).put("target_files", targetSnapshot.keySet()).put("previous_files", oldSnapshot.keySet()).build());
				try {
					restore(directory, targetSnapshot);
					restore(oldDirectory, oldSnapshot);
					BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.rollback",
					() -> BattleNext_Logger.fields().put("fleet", fleet).put("target_directory", directory).put("previous_directory", oldDirectory).put("target_files", targetSnapshot.keySet()).put("previous_files", oldSnapshot.keySet()).build());
				} catch (Exception rollbackException) {
					BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.rollback",
					BattleNext_Logger.fields().put("fleet", fleet).put("target_directory", directory).put("previous_directory", oldDirectory).build(), rollbackException);
					exception.addSuppressed(rollbackException);
				}
				throwFailure(exception);
				return path;
			}
		} catch (IOException | JSONException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_FleetSaver.class, "fleet.commit.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.commit", input, exception);
			}
			throw exception;
		}
	}

	@Override
	public void delete(BattleNext_FleetModel fleet) throws IOException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("fleet", fleet).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetSaver.class, "fleet.delete", input);
		try {
			if (!BattleNext_EntryId.isValidPathSegment(fleet.getFolderName())) {
				throw new IOException("Invalid fleet folder name: " + fleet.getFolderName());
			}
			files.deleteRecursively(BASE_ROOT + fleet.getFolderName());
			BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.delete",
					() -> BattleNext_Logger.fields().put("fleet", fleet).put("path", BASE_ROOT + fleet.getFolderName()).build());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_FleetSaver.class, "fleet.delete.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.delete", input, exception);
			}
			throw exception;
		}
	}

	private void createBackup(String sourceDirectory, String targetDirectory) throws IOException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetSaver.class, "fleet.backup", input);
		try {
			String sourceData = sourceDirectory + "/" + DATA_FILE;
			if (!files.exists(sourceData)) {
				BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.backup",
						() -> BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory)
							.put("created", false).build());
				return;
			}
			String backupDirectory = nextBackupDirectory(targetDirectory);
			files.writeBytes(backupDirectory + "/" + DATA_FILE, files.readBytes(sourceData));
			for (String path : files.list(sourceDirectory, false)) {
				if (path.endsWith(".variant")) {
					String name = path.substring(sourceDirectory.length() + 1);
					files.writeBytes(backupDirectory + "/" + name, files.readBytes(path));
				}
			}
			BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.backup",
					() -> BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory).build());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_FleetSaver.class, "fleet.backup.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.backup", input, exception);
			}
			throw exception;
		}
	}

	private void copyBackupHistory(String sourceDirectory, String targetDirectory) throws IOException {

		BattleNext_Logger.Fields input = BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory).build();
		BattleNext_Logger.operationStarted(BattleNext_FleetSaver.class, "fleet.backup.history.copy", input);
		try {
			String sourceBackup = sourceDirectory + "/" + BACKUP_FOLDER;
		if (!files.exists(sourceBackup)) {
			BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.backup.history.copy",
					() -> BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory)
						.put("copied", false).build());
			return;
		}
			String targetBackup = targetDirectory + "/" + BACKUP_FOLDER;
			for (String path : files.list(sourceBackup, true)) {
				String relative = path.substring(sourceBackup.length() + 1);
				String targetPath = targetBackup + "/" + relative;
				if (files.exists(targetPath)) {
					throw new IOException("Backup history collision: " + targetPath);
				}
				files.writeBytes(targetPath, files.readBytes(path));
			}
			BattleNext_Logger.operationSucceeded(BattleNext_FleetSaver.class, "fleet.backup.history.copy",
					() -> BattleNext_Logger.fields().put("source_directory", sourceDirectory).put("target_directory", targetDirectory).build());
		} catch (IOException | RuntimeException exception) {
			if (exception instanceof IllegalArgumentException || exception instanceof IllegalStateException) {
				BattleNext_Logger.warn(BattleNext_FleetSaver.class, "fleet.backup.history.copy.rejected", input, exception);
			} else {
				BattleNext_Logger.operationFailed(BattleNext_FleetSaver.class, "fleet.backup.history.copy", input, exception);
			}
			throw exception;
		}
	}

	private String nextBackupDirectory(String directory) throws IOException {
		String base = directory + "/" + BACKUP_FOLDER + "/" + BACKUP_FORMAT.format(timeProvider.now());
		String candidate = base;
		int suffix = 1;
		while (files.exists(candidate)) {
			candidate = base + "-" + suffix++;
		}
		return candidate;
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

	interface TimeProvider {
		LocalDateTime now();
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
