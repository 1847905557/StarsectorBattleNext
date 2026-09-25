package data.scripts.tools;

import com.fs.starfarer.api.Global;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Lists names under this mod's data roots; file contents remain owned by SettingsAPI. */
public final class BattleNext_FileCatalog {

	private static final String MOD_ID = "AI_Battles_Next";
	private static final String SAVES_PROPERTY = "com.fs.starfarer.settings.paths.saves";

	private BattleNext_FileCatalog() {

	}

	public static List<String> resources(String directory, boolean recursive) {
		String root = Global.getSettings().getModManager().getModSpec(MOD_ID).getPath();
		return list(root, directory, recursive, false);
	}

	public static List<String> common(String directory, boolean recursive) {
		String saves = System.getProperty(SAVES_PROPERTY);
		if (saves == null || saves.isBlank()) {
			throw new IllegalStateException("Unable to locate common data: missing " + SAVES_PROPERTY);
		}
		return list(saves + "/common", directory, recursive, true);
	}

	private static List<String> list(String root, String directory, boolean recursive, boolean optional) {
		String path = root + "/" + directory;
		try {
			Class<?> fileType = Class.forName("java.io.File", false, null);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			MethodHandle construct = lookup.findConstructor(fileType, MethodType.methodType(void.class, String.class));
			MethodHandle exists = lookup.findVirtual(fileType, "exists", MethodType.methodType(boolean.class));
			MethodHandle directoryCheck = lookup.findVirtual(fileType, "isDirectory", MethodType.methodType(boolean.class));
			MethodHandle regular = lookup.findVirtual(fileType, "isFile", MethodType.methodType(boolean.class));
			MethodHandle name = lookup.findVirtual(fileType, "getName", MethodType.methodType(String.class));
			MethodHandle children = lookup.findVirtual(fileType, "listFiles", MethodType.methodType(fileType.arrayType()));
			Object folder = construct.invoke(path);
			if (!(boolean) exists.invoke(folder)) {
				if (optional) return List.of();
				throw new IllegalStateException("Resource directory is missing: " + path);
			}
			if (!(boolean) directoryCheck.invoke(folder)) throw new IllegalStateException("Not a directory: " + path);
			List<String> result = new ArrayList<>();
			collect(folder, directory, recursive, result, directoryCheck, regular, name, children);
			Collections.sort(result);
			return result;
		} catch (IllegalStateException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IllegalStateException("Unable to list directory " + path + ": " + exception, exception);
		}
	}

	private static void collect(Object folder, String relative, boolean recursive, List<String> result,
			MethodHandle directoryCheck, MethodHandle regular, MethodHandle name, MethodHandle childrenHandle) throws Throwable {
		Object[] children = (Object[]) childrenHandle.invoke(folder);
		if (children == null) throw new IllegalStateException("Unable to list directory: " + relative);
		for (Object child : children) {
			String childName = (String) name.invoke(child);
			String path = relative + "/" + childName;
			if ((boolean) regular.invoke(child)) result.add(path);
			else if (recursive && (boolean) directoryCheck.invoke(child)) {
				collect(child, path, true, result, directoryCheck, regular, name, childrenHandle);
			}
		}
	}
}
