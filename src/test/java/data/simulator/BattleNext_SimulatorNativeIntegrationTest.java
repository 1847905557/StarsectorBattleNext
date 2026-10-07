package data.simulator;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.ModManagerAPI;
import com.fs.starfarer.api.ModSpecAPI;
import com.fs.starfarer.api.SettingsAPI;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.InputImplementation;
import org.lwjgl.opengl.PixelFormat;

import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.lang.reflect.Proxy;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

class BattleNext_SimulatorNativeIntegrationTest {

	@Test
	void nativeHandlesMouseIsolationAndScriptSandboxUseActualGameDependencies() throws Exception {
		String output = runProbe(Probe.class);
		assertTrue(output.contains("Simulator native handles bound"), output);
		assertTrue(output.contains("Simulator mouse isolation verified"), output);
		assertTrue(output.contains("Simulator script sandbox verified"), output);
	}

	@Test
	void crashReportsUseTheCallerOwnedPersistentDiagnosticsDirectory() {
		Path core = Path.of("core").toAbsolutePath();
		Path diagnostics = Path.of("diagnostics with spaces").toAbsolutePath();
		List<String> command = BattleNext_SimulatorLaunch.javaCommand(core, diagnostics);
		assertEquals(List.of("-XX:ErrorFile=" + diagnostics.resolve("hs_err_pid%p.log")),
				command.stream().filter(option -> option.startsWith("-XX:ErrorFile=")).toList());
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
				String.join(System.getProperty("path.separator"), classpath), "-Djava.library.path=" + core.resolve("native/windows"), probe.getName()).redirectErrorStream(true).start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(0, process.waitFor(), output);
		return output;
	}

	public static class Probe {
		public static void main(String[] arguments) throws Exception {
			new BattleNext_SimulatorNative(Probe.class.getClassLoader());
			System.out.println("Simulator native handles bound");
			MouseProbe.main(arguments);
			SandboxProbe.main(arguments);
		}
	}

	public static class MouseProbe {

		public static void main(String[] arguments) throws Exception {

			Display.setLocation(-20000, -20000);
			Display.create(new PixelFormat());
			var implementation = Mouse.class.getDeclaredField("implementation");
			implementation.setAccessible(true);
			Object original = implementation.get(null);
			AtomicInteger nativeMouseCalls = new AtomicInteger();
			InputImplementation observed = (InputImplementation) Proxy.newProxyInstance(MouseProbe.class.getClassLoader(),
					new Class<?>[]{InputImplementation.class}, (instance, method, values) -> {
				if (Set.of("setCursorPosition", "grabMouse", "setNativeCursor", "pollMouse", "readMouse").contains(method.getName())) {
					nativeMouseCalls.incrementAndGet();
				}
				return method.invoke(original, values);
			});
			implementation.set(null, observed);
			BattleNext_SimulatorMouse isolated = BattleNext_SimulatorMouse.getInstance();
			try {
				if (BattleNext_SimulatorMouse.getInstance() != isolated) throw new AssertionError("Mouse backend must be reused");
				for (int cycle = 0; cycle < 10; cycle++) {
					Mouse.setCursorPosition(400, 300);
					Mouse.setGrabbed(true);
					Mouse.setGrabbed(false);
					Mouse.setNativeCursor(null);
					Mouse.poll();
					if (nativeMouseCalls.get() != 0) throw new AssertionError("Virtual cursor requests reached the native mouse backend");
					if (Mouse.isInsideWindow() || Mouse.getDWheel() != 0 || Mouse.isButtonDown(0)) throw new AssertionError("Virtual mouse must keep physical input isolated");
				}
				isolated.cleanup();
				if (implementation.get(null) != observed) throw new AssertionError("Mouse backend cleanup must restore its original owner");
				isolated.cleanup();
				System.out.println("Simulator mouse isolation verified");
			} finally {
				isolated.cleanup();
				implementation.set(null, original);
				Display.destroy();
			}
		}
	}

	public static class SandboxProbe {

		public static void main(String[] arguments) throws Exception {

			Path root = Files.createTempDirectory("BattleNext-simulator-sandbox-");
			Path mod = root.resolve("game/mods/fixture");
			Files.createDirectories(mod);
			Files.createDirectories(root.resolve("game/starsector-core"));
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
			Map<Field, Object> previousNativeSettings = new LinkedHashMap<>();
			for (var field : Class.forName("com.fs.starfarer.settings.StarfarerSettings").getDeclaredFields()) {
				if (field.getType() != JSONObject.class || !Modifier.isStatic(field.getModifiers())) continue;
				field.setAccessible(true);
				previousNativeSettings.put(field, field.get(null));
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
				for (var entry : previousNativeSettings.entrySet()) {
					entry.getKey().set(null, entry.getValue());
				}
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
			Path runtime = (Path) storageType.getMethod("directory", String.class).invoke(storage, "_runtime/session");
			Class<?> configType = loader.loadClass("data.simulator.BattleNext_SimulationConfig");
			Object config = configType.getMethod("defaults").invoke(null);
			Class<?> requestType = loader.loadClass("data.simulator.BattleNext_SimulationRequest");
			Object request = requestType.getMethod("fromVariants", List.class, List.class, configType)
					.invoke(null, List.of("wolf_CS", "wolf_CS"), List.of("wolf_CS"), config);
			String id = (String) requestType.getMethod("id").invoke(request);
			Class<?> resultType = loader.loadClass("data.simulator.BattleNext_SimulationResult");
			JSONObject json = new JSONObject().put("id", id).put("outcome", "FAILED").put("report", "")
					.put("simulationSeconds", 0f).put("executionNanos", 0L).put("steps", 0L).put("peakHeapBytes", 0L).put("diagnostic", "中文 50%");
			Object result = resultType.getMethod("fromJson", JSONObject.class).invoke(null, json);
			storageType.getMethod("write", requestType, resultType).invoke(storage, request, result);
			List<?> pending = (List<?>) storageType.getMethod("pending").invoke(storage);
			if (!pending.equals(List.of(root.resolve("owned/_pending/" + id + ".json")))) throw new AssertionError("Sandbox journal listing");
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
				Object config = configType.getMethod("defaults").invoke(null);
				Class<?> requestType = loader.loadClass("data.simulator.BattleNext_SimulationRequest");
				Object request = requestType.getMethod("fromVariants", List.class, List.class, configType)
						.invoke(null, List.of("wolf_CS"), List.of("wolf_CS"), config);
				Object task = type.getMethod("submit", requestType).invoke(service, request);
				String id = (String) requestType.getMethod("id").invoke(request);
				Class<?> taskType = task.getClass();
				long deadline = System.nanoTime() + 15000000000L;
				Object result;
				while ((result = taskType.getMethod("getResult").invoke(task)) == null && System.nanoTime() < deadline) {
					Thread.sleep(20L);
				}
				if (result == null) throw new AssertionError("Sandbox startup failure must resolve the task");
				if (!result.getClass().getMethod("outcome").invoke(result).toString().equals("FAILED")) throw new AssertionError("Sandbox startup outcome");
				if (!result.getClass().getMethod("id").invoke(result).equals(id)) throw new AssertionError("Sandbox startup result identity");
				if (!result.getClass().getMethod("diagnostic").invoke(result).toString().contains("cold start")) throw new AssertionError("Sandbox startup failure phase");
				if (!taskType.getMethod("getState").invoke(task).toString().equals("FINISHED")) throw new AssertionError("Sandbox startup terminal state");
				service.close();
				Path diagnostics = root.resolve("game/mods/fixture/tournament/_simulations/_diagnostics");
				try (var paths = Files.list(diagnostics)) {
					List<Path> logs = paths.toList();
					if (logs.isEmpty()) throw new AssertionError("Sandbox launch must redirect worker output");
					for (Path log : logs) {
						if (!Files.readString(log, StandardCharsets.UTF_8).contains("ClassNotFoundException: data.simulator.BattleNext_SimulatorWorker")) throw new AssertionError("Expected isolated worker classpath failure");
					}
				}
				JSONObject journal = new JSONObject(Files.readString(root.resolve("game/mods/fixture/tournament/_simulations/_pending/" + id + ".json"), StandardCharsets.UTF_8));
				if (!journal.getJSONObject("request").toString().equals(requestType.getMethod("toJson").invoke(request).toString())) throw new AssertionError("Sandbox startup journal request");
				if (!journal.getJSONObject("result").toString().equals(result.getClass().getMethod("toJson").invoke(result).toString())) throw new AssertionError("Sandbox startup journal result");
			}
			try (var paths = Files.list(root.resolve("game/mods/fixture/tournament/_simulations/_runtime"))) {
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
