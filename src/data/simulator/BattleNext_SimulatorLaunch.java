package data.simulator;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_SimulatorLaunch {

	private BattleNext_SimulatorLaunch() {
	}

	public static List<String> javaCommand(Path core) {
		List<String> command = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(),
				"-Xverify:none", "-Xmx4g", "-Xms512m", "-XX:+UseG1GC", "-XX:+UseStringDeduplication",
				"-XX:-ShowMessageBoxOnError", "-Djava.library.path=" + core.resolve("native/windows"),
				"-Djava.util.Arrays.useLegacyMergeSort=true", "-Dfile.encoding=UTF-8", "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8",
				"-Dlog4j.defaultInitOverride=true"));
		for (String target : List.of("java.base/sun.nio.ch", "java.base/java.nio", "java.base/java.util",
				"java.base/java.util.concurrent", "java.base/java.util.concurrent.locks", "java.base/jdk.internal.ref",
				"java.base/java.lang.reflect", "java.base/java.lang.ref", "java.base/java.text",
				"java.desktop/java.awt.font", "java.desktop/java.awt", "java.desktop/java.awt.geom")) {
			command.add("--add-opens=" + target + "=ALL-UNNAMED");
		}
		return command;
	}

	public static Process start(List<String> command, Path directory) throws IOException {
		try {
			Class<?> file = Class.forName("java.io.File", false, null);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			var constructor = lookup.findConstructor(file, MethodType.methodType(void.class, String.class));
			Object workingDirectory = constructor.invoke(directory.toString());
			ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
			lookup.findVirtual(ProcessBuilder.class, "directory", MethodType.methodType(ProcessBuilder.class, file)).invoke(builder, workingDirectory);
			return builder.start();
		} catch (IOException exception) {
			throw exception;
		} catch (Throwable exception) {
			throw new IOException("Unable to start simulator worker", exception);
		}
	}
}
