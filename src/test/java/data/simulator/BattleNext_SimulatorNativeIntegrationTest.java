package data.simulator;

import org.junit.jupiter.api.Test;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.net.URLClassLoader;
import java.lang.reflect.Proxy;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Comparator;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class BattleNext_SimulatorNativeIntegrationTest {

	@Test
	void allNativeHandlesBindAgainstActualGameJars() throws Exception {
		String output = runProbe(Probe.class);
		assertTrue(output.contains("Simulator native handles bound"), output);
	}

	@Test
	void crashReportsUseTheCallerOwnedPersistentDiagnosticsDirectory() {
		Path core = Path.of("core").toAbsolutePath();
		Path diagnostics = Path.of("diagnostics with spaces").toAbsolutePath();
		List<String> command = BattleNext_SimulatorLaunch.javaCommand(core, diagnostics);
		assertEquals(List.of("-XX:ErrorFile=" + diagnostics.resolve("hs_err_pid%p.log")),
				command.stream().filter(option -> option.startsWith("-XX:ErrorFile=")).toList());
	}

	@Test
	void simulatorFileTransportLaunchAndNativeOperationsRunThroughTheActualScriptSandbox() throws Exception {
		String output = runProbe(SandboxProbe.class);
		assertTrue(output.contains("Simulator script sandbox verified"), output);
	}

	private String runProbe(Class<?> probe) throws Exception {
		Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
		List<String> classpath = new ArrayList<>();
		classpath.add(Path.of(BattleNext_SimulatorNative.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
		classpath.add(Path.of(Probe.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString());
		try (var paths = Files.list(core)) {
			classpath.addAll(paths.filter(path -> path.toString().endsWith(".jar")).map(Path::toString).toList());
		}
		Process process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin/java.exe").toString(), "-Xverify:none", "-cp",
				String.join(System.getProperty("path.separator"), classpath), probe.getName()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), output);
		return output;
	}

	public static class Probe {
		public static void main(String[] arguments) throws Exception {
			new BattleNext_SimulatorNative(Probe.class.getClassLoader());
			System.out.println("Simulator native handles bound");
		}
	}

	public static class SandboxProbe {

		public static void main(String[] arguments) throws Exception {

			Path root = Files.createTempDirectory("BattleNext-simulator-sandbox-");
			Path mod = Path.of("").toAbsolutePath();
			SettingsAPI previous = Global.getSettings();
			String saves = System.getProperty("com.fs.starfarer.settings.paths.saves");
			System.setProperty("com.fs.starfarer.settings.paths.saves", root.resolve("saves").toString());
			ModSpecAPI spec = (ModSpecAPI) Proxy.newProxyInstance(SandboxProbe.class.getClassLoader(), new Class<?>[]{ModSpecAPI.class},
					(instance, method, values) -> method.getName().equals("getPath") ? mod.toString() : null);
			ModManagerAPI manager = (ModManagerAPI) Proxy.newProxyInstance(SandboxProbe.class.getClassLoader(), new Class<?>[]{ModManagerAPI.class},
					(instance, method, values) -> switch (method.getName()) {
						case "getModSpec" -> spec;
						case "getEnabledModsCopy" -> List.of();
						default -> null;
					});
			Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SandboxProbe.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
					(instance, method, values) -> switch (method.getName()) {
						case "getModManager" -> manager;
						case "getBoolean" -> false;
						default -> null;
					}));
			JSONObject nativeSettings = new JSONObject().put("devMode", false);
			for (var field : Class.forName("com.fs.starfarer.settings.StarfarerSettings").getDeclaredFields()) {
				if (field.getType() != JSONObject.class || !Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true);
				field.set(null, nativeSettings);
			}
			ClassLoader restricted = (ClassLoader) Class.forName("com.fs.starfarer.loading.scripts.B")
					.getConstructor(ClassLoader.class).newInstance(SandboxProbe.class.getClassLoader());
			try (SandboxLoader loader = new SandboxLoader(BattleNext_SimulatorNative.class.getProtectionDomain().getCodeSource().getLocation(), restricted)) {
				boolean enforced = false;
				try {
					restricted.loadClass("java.nio.file.Files");
				} catch (SecurityException exception) {
					enforced = true;
				}
				if (!enforced) throw new AssertionError("Actual game script restrictions must be active");
				verifyStorage(loader, root);
				verifyProtocol(loader);
				Class<?> nativeType = loader.loadClass("data.simulator.BattleNext_SimulatorNative");
				nativeType.getConstructor(ClassLoader.class).newInstance(loader);
				verifyService(loader, root);
				System.out.println("Simulator script sandbox verified");
			} finally {
				Global.setSettings(previous);
				if (saves == null) {
					System.clearProperty("com.fs.starfarer.settings.paths.saves");
				} else {
					System.setProperty("com.fs.starfarer.settings.paths.saves", saves);
				}
				try (var paths = Files.walk(root)) {
					for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
						Files.delete(path);
					}
				}
			}
		}

		private static void verifyStorage(ClassLoader loader, Path root) throws Exception {

			Class<?> storageType = loader.loadClass("data.simulator.BattleNext_SimulatorStorage");
			Object storage = storageType.getConstructor(Path.class).newInstance(root.resolve("owned"));
			Path source = root.resolve("source/common/nested/settings.json");
			Files.createDirectories(source.getParent());
			byte[] original = "中文 50%".getBytes(StandardCharsets.UTF_8);
			Files.write(source, original);
			Path runtime = (Path) storageType.getMethod("directory", String.class).invoke(storage, "runtime/session");
			storageType.getMethod("snapshotCommon", Path.class, Path.class).invoke(storage, root.resolve("source"), runtime.resolve("saves"));
			if (!Arrays.equals(original, Files.readAllBytes(runtime.resolve("saves/common/nested/settings.json")))) {
				throw new AssertionError("Sandbox settings snapshot must preserve bytes");
			}
			Class<?> configType = loader.loadClass("data.simulator.BattleNext_SimulationConfig");
			Object config = configType.getMethod("lab").invoke(null);
			Class<?> requestType = loader.loadClass("data.simulator.BattleNext_SimulationRequest");
			String id = UUID.randomUUID().toString();
			Object request = requestType.getConstructor(String.class, List.class, List.class, configType)
					.newInstance(id, List.of("wolf_CS", "wolf_CS"), List.of("wolf_CS"), config);
			Class<?> resultType = loader.loadClass("data.simulator.BattleNext_SimulationResult");
			JSONObject json = new JSONObject().put("id", id).put("outcome", "FAILED").put("report", "")
					.put("simulationSeconds", 0f).put("executionNanos", 0L).put("steps", 0L).put("peakHeapBytes", 0L).put("diagnostic", "中文 50%");
			Object result = resultType.getMethod("fromJson", JSONObject.class).invoke(null, json);
			storageType.getMethod("write", requestType, resultType).invoke(storage, request, result);
			List<?> pending = (List<?>) storageType.getMethod("pending").invoke(storage);
			if (!pending.equals(List.of(root.resolve("owned/pending/" + id + ".json")))) throw new AssertionError("Sandbox journal listing");
			Object stored = storageType.getMethod("read", Path.class).invoke(storage, pending.get(0));
			if (!result.equals(stored.getClass().getMethod("result").invoke(stored))) throw new AssertionError("Sandbox journal round trip");
			storageType.getMethod("acknowledge", String.class).invoke(storage, id);
			if (!((List<?>) storageType.getMethod("pending").invoke(storage)).isEmpty()) throw new AssertionError("Sandbox journal acknowledgment");
			storageType.getMethod("cleanup", Path.class).invoke(storage, runtime);
			if (Files.exists(runtime)) throw new AssertionError("Sandbox owned runtime cleanup");
		}

		private static void verifyProtocol(ClassLoader loader) throws Exception {
			Class<?> protocol = loader.loadClass("data.simulator.BattleNext_SimulatorProtocol");
			try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
					Socket client = new Socket(InetAddress.getLoopbackAddress(), server.getLocalPort());
					Socket socket = server.accept();
					AutoCloseable sender = (AutoCloseable) protocol.getConstructor(Socket.class, String.class).newInstance(client, "session");
					AutoCloseable receiver = (AutoCloseable) protocol.getConstructor(Socket.class, String.class).newInstance(socket, "session")) {
				protocol.getMethod("send", String.class, JSONObject.class).invoke(sender, "REQUEST", new JSONObject().put("value", "中文 50%"));
				JSONObject message = (JSONObject) protocol.getMethod("receive").invoke(receiver);
				if (!message.getJSONObject("payload").getString("value").equals("中文 50%")) throw new AssertionError("Sandbox protocol round trip");
			}
		}

		private static void verifyService(ClassLoader loader, Path root) throws Exception {

			Class<?> type = loader.loadClass("data.simulator.BattleNext_SimulatorService");
			try (AutoCloseable service = (AutoCloseable) type.getMethod("getInstance").invoke(null)) {
				Class<?> configType = loader.loadClass("data.simulator.BattleNext_SimulationConfig");
				Object config = configType.getMethod("lab").invoke(null);
				Object task = type.getMethod("submit", List.class, List.class, configType).invoke(service, List.of("wolf_CS"), List.of("wolf_CS"), config);
				Class<?> taskType = task.getClass();
				long deadline = System.nanoTime() + 15000000000L;
				Object result;
				while ((result = taskType.getMethod("getResult").invoke(task)) == null && System.nanoTime() < deadline) {
					Thread.sleep(20L);
				}
				if (result == null) throw new AssertionError("Sandbox startup failure must resolve the task");
				if (!result.getClass().getMethod("outcome").invoke(result).toString().equals("FAILED")) throw new AssertionError("Sandbox startup outcome");
				Path diagnostics = root.resolve("saves/common/AI_Battles_Next/simulator/diagnostics");
				try (var paths = Files.list(diagnostics)) {
					List<Path> logs = paths.toList();
					if (logs.isEmpty()) throw new AssertionError("Sandbox launch must redirect worker output");
					for (Path log : logs) {
						if (!Files.readString(log, StandardCharsets.UTF_8).contains("BattleNext_SimulatorWorker")) throw new AssertionError("Expected isolated worker classpath failure");
					}
				}
			}
			try (var paths = Files.list(root.resolve("saves/common/AI_Battles_Next/simulator/runtime"))) {
				if (paths.findAny().isPresent()) throw new AssertionError("Sandbox service must release owned runtime");
			}
		}
	}

	private static class SandboxLoader extends URLClassLoader {

		private SandboxLoader(URL source, ClassLoader parent) {
			super(new URL[]{source}, parent);
		}

		@Override
		protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			synchronized (getClassLoadingLock(name)) {
				if (!name.startsWith("data.simulator.")) return super.loadClass(name, resolve);
				Class<?> type = findLoadedClass(name);
				if (type == null) {
					type = findClass(name);
				}
				if (resolve) {
					resolveClass(type);
				}
				return type;
			}
		}
	}
}
