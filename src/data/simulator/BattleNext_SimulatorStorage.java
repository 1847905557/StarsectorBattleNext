package data.simulator;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

public class BattleNext_SimulatorStorage {

	private final Path root;
	private final MethodHandle createDirectories;
	private final MethodHandle list;

	public BattleNext_SimulatorStorage(Path root) throws IOException {
		this.root = root.toAbsolutePath().normalize();
		try {
			Class<?> files = Class.forName("java.nio.file.Files", false, null);
			Class<?> attributes = Class.forName("java.nio.file.attribute.FileAttribute", false, null);
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			createDirectories = lookup.findStatic(files, "createDirectories", MethodType.methodType(Path.class, Path.class, attributes.arrayType()))
					.asCollector(attributes.arrayType(), 0);
			list = lookup.findStatic(files, "list", MethodType.methodType(Stream.class, Path.class));
		} catch (ReflectiveOperationException exception) {
			throw new IOException("Unable to bind simulator directory operations", exception);
		}
	}

	public Path directory(String sessionId) throws IOException {
		Path target = root.resolve(sessionId);
		invoke(createDirectories, target);
		return target;
	}

	public List<Path> listFiles(Path directory) throws IOException {
		try (Stream<Path> paths = (Stream<Path>) invoke(list, directory)) {
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
			throw new IOException("Unable to perform simulator directory operation: " + handle.type(), exception);
		}
	}
}
