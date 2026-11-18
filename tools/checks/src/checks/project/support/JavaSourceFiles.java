package checks.project.support;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public final class JavaSourceFiles {
	private JavaSourceFiles() {
	}

	public static List<Path> list(Path sourceRoot) throws IOException {
		try (Stream<Path> paths = Files.walk(sourceRoot)) {
			return paths
					.filter(Files::isRegularFile)
					.filter(path -> path.toString().endsWith(".java"))
					.sorted()
					.toList();
		}
	}
}
