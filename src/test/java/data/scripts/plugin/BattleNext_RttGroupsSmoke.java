package data.scripts.plugin;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.*;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.missions.BattleNext_lab.MissionDefinition;
import data.scripts.ai.rtt.BattleNext_RttShipAI;
import data.scripts.battlesnext.util.BattleNext_FontData;
import data.scripts.tools.BattleNext_CombatInput;
import data.simulator.BattleNext_SimulatorLaunch;
import data.simulator.BattleNext_SimulatorNative;
import org.apache.log4j.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.util.vector.Vector2f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.jar.JarFile;

public class BattleNext_RttGroupsSmoke {

	private static final String SUCCESS = "RTT lab groups verified: groups=7, bindings=primary/secondary, additive=true, orders=move, rings=2, labels=7/1,3,5, zooms=2";
	private static final float DEPLOYMENT_SETTLE_SECONDS = 4f;
	private static volatile boolean verified;
	private static Path output;

	public static void main(String[] arguments) throws Throwable {

		if (arguments.length == 0) {
			Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
			Path report = Path.of("build/reports/rtt-groups").toAbsolutePath();
			Files.createDirectories(report);
			List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
			command.addAll(List.of("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
					"-Dcom.fs.starfarer.settings.paths.logs=" + report, "-cp", System.getProperty("java.class.path"),
					BattleNext_RttGroupsSmoke.class.getName(), report.toString()));
			Process process = BattleNext_SimulatorLaunch.start(command, core);
			String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			Files.writeString(report.resolve("native-output.log"), result, StandardCharsets.UTF_8);
			if (process.waitFor() != 0 || !result.contains(SUCCESS)) {
				List<String> lines = result.lines().toList();
				throw new AssertionError("Native RTT verification failed: " + report + "\n"
						+ String.join("\n", lines.subList(Math.max(0, lines.size() - 45), lines.size())));
			}
			System.out.println(SUCCESS + "; reports=" + report);
			return;
		}
		output = Path.of(arguments[0]);
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "800x600");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%p %c - %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
		Display.setLocation(-20000, -20000);
		Thread driver = new Thread(BattleNext_RttGroupsSmoke::drive, "RTT lab verification");
		driver.setDaemon(true);
		driver.start();
		Class<?> launcher = Class.forName("com.fs.starfarer.StarfarerLauncher");
		MethodHandles.publicLookup().findStatic(launcher, "main", MethodType.methodType(void.class, String[].class))
				.invoke((Object) new String[0]);
		if (!verified) throw new AssertionError("Native RTT verification did not finish");
	}

	private static void drive() {

		try {
			long deadline = System.nanoTime() + 180000000000L;
			while (Global.getSettings() == null || Global.getCurrentState() != GameState.TITLE || Global.getCombatEngine() == null) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native title startup timed out");
				Thread.sleep(20L);
			}
			Thread.sleep(1000L);
			Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
			var access = new BattleNext_SimulatorNative(BattleNext_RttGroupsSmoke.class.getClassLoader());
			access.configureGraphics();
			MissionDefinitionAPI mission = access.createMission();
			Verification verifier = new Verification();
			MissionDefinitionAPI observed = (MissionDefinitionAPI) Proxy.newProxyInstance(BattleNext_RttGroupsSmoke.class.getClassLoader(),
					new Class<?>[]{MissionDefinitionAPI.class}, (instance, method, args) -> {
						if (method.getName().equals("addPlugin") && args[0] instanceof BattleNext_RttPlugin rtt) verifier.rtt = rtt;
						try {
							return method.invoke(mission, args);
						} catch (InvocationTargetException exception) {
							throw exception.getCause();
						}
					});
			new MissionDefinition().defineMission(observed);
			mission.addPlugin(verifier);
			access.submitMission(mission);
			while (!verified) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native RTT lab verification timed out");
				Thread.sleep(20L);
			}
			while (Global.getCurrentState() != GameState.TITLE) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native lab title return timed out");
				Thread.sleep(20L);
			}
			access.exit();
		} catch (Throwable exception) {
			exception.printStackTrace();
			System.exit(1);
		}
	}

	public static class Verification extends BaseEveryFrameCombatPlugin {

		private BattleNext_RttPlugin rtt;
		private CombatEngineAPI engine;
		private boolean ready;
		private boolean finished;
		private List<ShipAPI> ships;
		private Class<?> eventType;
		private int captureStage;

		@Override
		public void init(CombatEngineAPI engine) {
			this.engine = engine;
			try {
				new BattleNext_SimulatorNative(engine.getClass().getClassLoader()).prepareDeployment(engine, false);
			} catch (Throwable exception) {
				throw new IllegalStateException("Unable to prepare native RTT deployment", exception);
			}
		}

		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			if (ready || finished) return;
			try {
				if (BattleNext_CombatInput.isUiCovered(engine)) return;
				if (engine.getTotalElapsedTime(false) < DEPLOYMENT_SETTLE_SECONDS) return;
				ships = engine.getShips().stream().filter(ship -> ship.getOwner() == 0 && !ship.isShuttlePod() && !ship.isFighter()).limit(4).toList();
				if (ships.size() != 4) throw new AssertionError("Lab must deploy its four player Wolves");
				for (int index = 0; index < ships.size(); index++) {
					ShipAPI ship = ships.get(index);
					ship.setShipAI(null);
					ship.setHoldFire(true);
					ship.getLocation().set(-300f + index * 200f, 0f);
					ship.getVelocity().set(0f, 0f);
				}
				if (BattleNext_CombatInput.isPlayerPlaying(engine)) throw new AssertionError("Lab RTT requires the player shuttle");
				engine.setPaused(true);
				engine.getViewport().set(-800f, -600f, 1600f, 1200f);
				engine.getViewport().setCenter(new Vector2f());
				engine.getViewport().setExternalControl(true);
				try (NativeBindings bindings = new NativeBindings()) {
					eventType = bindings.eventType;
					bindings.hold(-1);
					int[] secondary = {Keyboard.KEY_NUMPAD1, Keyboard.KEY_NUMPAD2, Keyboard.KEY_NUMPAD3,
							Keyboard.KEY_NUMPAD4, Keyboard.KEY_NUMPAD5, Keyboard.KEY_NUMPAD6, Keyboard.KEY_NUMPAD7};
					for (int number = 1; number <= 7; number++) {
						bindings.bind("SHIP_SELECT_GROUP_" + number, Keyboard.KEY_F1 + number - 1, false, false, false,
								secondary[number - 1], false, false, false);
						bindings.bind("SHIP_TOGGLE_GROUP_" + number, Keyboard.KEY_F1 + number - 1, true, false, false,
								secondary[number - 1], false, true, false);
					}
					bindings.bind("SHIP_STRAFE_KEY", Keyboard.KEY_B, false, false, false, Keyboard.KEY_C, false, false, false);
					for (int number = 1; number <= 7; number++) {
						click(ships.get((number - 1) % 2));
						key(Keyboard.KEY_F1 + number - 1, true, false);
						click(ships.get(2));
						key(secondary[number - 1], false, false);
						expect(List.of(ships.get((number - 1) % 2)));
						click(ships.get(2));
						key(secondary[number - 1], false, true);
						click(ships.get(3));
						key(Keyboard.KEY_F1 + number - 1, false, false);
						expect(List.of(ships.get(2)));
					}
					click(ships.get(0));
					key(Keyboard.KEY_F1, true, false);
					key(Keyboard.KEY_F3, true, false);
					key(Keyboard.KEY_F5, true, false);
					click(ships.get(1));
					key(Keyboard.KEY_F7, true, false);
					bindings.hold(Keyboard.KEY_B);
					key(Keyboard.KEY_F1, false, false);
					expect(List.of(ships.get(1), ships.get(0)));
					bindings.hold(Keyboard.KEY_C);
					key(Keyboard.KEY_F1, false, false);
					expect(List.of(ships.get(1), ships.get(0)));
				}
				InputEventAPI move = event(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_DOWN,
						(int) engine.getViewport().convertWorldXtoScreenX(0f), (int) engine.getViewport().convertWorldYtoScreenY(300f), 1);
				rtt.processInputPreCoreControls(0f, List.of(move));
				if (!move.isConsumed()) throw new AssertionError("Recalled native ships must accept movement orders");
				for (ShipAPI ship : List.of(ships.get(1), ships.get(0))) {
					if (!(ship.getCustomData().get(BattleNext_RttShipAI.CONTROL_KEY) instanceof BattleNext_RttShipAI ai)
							|| ai.getCommandType() != BattleNext_RttShipAI.CommandType.MOVE || ship.getShipAI().getAIFlags() != ai.getAIFlags()) {
						throw new AssertionError("Native recalled ships must receive their RTT movement AI");
					}
				}
				ready = true;
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}

		private void click(ShipAPI ship) throws ReflectiveOperationException {
			int x = (int) engine.getViewport().convertWorldXtoScreenX(ship.getLocation().x);
			int y = (int) engine.getViewport().convertWorldYtoScreenY(ship.getLocation().y);
			InputEventAPI down = event(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_DOWN, x, y, 0);
			InputEventAPI up = event(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_UP, x, y, 0);
			rtt.processInputPreCoreControls(0f, List.of(down, up));
			if (!down.isConsumed() || !up.isConsumed()) throw new AssertionError("RTT must consume native selection clicks: active="
					+ BattleNext_RttPlugin.acceptsMouseInput(engine) + ", combatOver=" + engine.isCombatOver()
					+ ", down=" + down.isConsumed() + ", up=" + up.isConsumed());
		}

		private void key(int code, boolean ctrl, boolean alt) throws ReflectiveOperationException {
			InputEventAPI event = event(InputEventClass.KEYBOARD_EVENT, InputEventType.KEY_DOWN, 0, 0, code);
			eventType.getMethod("setCtrlDown", boolean.class).invoke(event, ctrl);
			eventType.getMethod("setAltDown", boolean.class).invoke(event, alt);
			rtt.processInputPreCoreControls(0f, List.of(event));
			if (!event.isConsumed()) throw new AssertionError("RTT must consume the rebound native group key: " + code);
		}

		private InputEventAPI event(InputEventClass kind, InputEventType type, int x, int y, int code) throws ReflectiveOperationException {
			return (InputEventAPI) eventType.getConstructor(InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class)
					.newInstance(kind, type, x, y, code, '\0');
		}

		private void expect(List<ShipAPI> expected) throws ReflectiveOperationException {
			Field selected = BattleNext_RttPlugin.class.getDeclaredField("selected");
			selected.setAccessible(true);
			if (!new ArrayList<>((Set<?>) selected.get(rtt)).equals(expected)) throw new AssertionError("Native recalled ships differ from the saved selection");
		}

		@Override
		public void renderInUICoords(ViewportAPI viewport) {
			if (!ready || finished) return;
			try {
				rtt.renderInUICoords(viewport);
				Field textField = BattleNext_RttPlugin.class.getDeclaredField("groupText");
				textField.setAccessible(true);
				var text = (BattleNext_FontData.TextObject) textField.get(rtt);
				if (!text.getTextDataList().get(0).text.equals("1,3,5")) throw new AssertionError("Native group labels must list every membership in ascending order");
				BufferedImage image = capture(output.resolve(captureStage == 0 ? "selected-groups.png" : "selected-groups-zoomed-out.png"));
				for (ShipAPI ship : List.of(ships.get(0), ships.get(1))) {
					float diagonal = (ship.getCollisionRadius() * 1.2f + 30f) / (float) Math.sqrt(2f);
					int left = (int) viewport.convertWorldXtoScreenX(ship.getLocation().x + diagonal) + 4;
					int top = image.getHeight() - (int) viewport.convertWorldYtoScreenY(ship.getLocation().y - diagonal) + 4;
					int pixels = 0;
					for (int y = top; y < top + 22; y++) {
						for (int x = left; x < left + (ship == ships.get(0) ? 38 : 9); x++) {
							int rgb = image.getRGB(x, y);
							if ((rgb >> 16 & 255) < 100 && (rgb >> 8 & 255) > 100 && (rgb & 255) > 100) pixels++;
						}
					}
					if (pixels == 0) throw new AssertionError("Native group number must draw cyan glyphs below and right of the selection circle");
					System.out.println("RTT label pixels: zoom=" + captureStage + ", ship=" + ship.getId() + ", count=" + pixels);
				}
				if (captureStage++ == 0) {
					viewport.set(-1600f, -1200f, 3200f, 2400f);
					viewport.setCenter(new Vector2f());
					return;
				}
				finished = true;
				System.out.println(SUCCESS);
				verified = true;
				new BattleNext_SimulatorNative(engine.getClass().getClassLoader()).finish(engine);
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}
	}

	private static class NativeBindings implements AutoCloseable {

		private final Class<?> eventType;
		private final Class<?> actionType;
		private final Map<Object, Object> mappings;
		private final Map<Object, Object> index;
		private final Map<Object, Object> originalIndex = new HashMap<>();
		private final Map<Object, Object[]> originals = new HashMap<>();
		private final ByteBuffer keyboard;
		private final byte[] originalKeys;

		private NativeBindings() throws Exception {

			List<Class<?>> types = new ArrayList<>();
			try (JarFile jar = new JarFile(Path.of("starfarer_obf.jar").toFile())) {
				for (var entry : Collections.list(jar.entries())) {
					String name = entry.getName();
					if (!name.endsWith(".class") || !(name.startsWith("com/fs/starfarer/title/") || name.startsWith("com/fs/starfarer/util/"))) continue;
					types.add(Class.forName(name.substring(0, name.length() - 6).replace('/', '.'), false, BattleNext_RttGroupsSmoke.class.getClassLoader()));
				}
			}
			eventType = types.stream().filter(type -> InputEventAPI.class.isAssignableFrom(type) && !type.isInterface()).findFirst().orElseThrow();
			actionType = types.stream().filter(type -> type.isEnum() && Arrays.stream(type.getEnumConstants())
					.anyMatch(value -> ((Enum<?>) value).name().equals("SHIP_SELECT_GROUP_1"))).findFirst().orElseThrow();
			Class<?> config = types.stream().filter(type -> Arrays.stream(type.getDeclaredMethods()).anyMatch(method ->
					Modifier.isStatic(method.getModifiers()) && Arrays.equals(method.getParameterTypes(),
							new Class<?>[]{actionType, int.class, boolean.class, boolean.class, boolean.class}))).findFirst().orElseThrow();
			List<Map<Object, Object>> maps = new ArrayList<>();
			for (Field field : config.getDeclaredFields()) {
				if (!Modifier.isStatic(field.getModifiers()) || !Map.class.isAssignableFrom(field.getType())) continue;
				field.setAccessible(true);
				maps.add((Map<Object, Object>) field.get(null));
			}
			mappings = maps.stream().filter(map -> map.containsKey(action("SHIP_SELECT_GROUP_1"))).findFirst().orElseThrow();
			index = maps.stream().filter(map -> map.keySet().stream().anyMatch(Integer.class::isInstance)).findFirst().orElseThrow();
			for (var entry : index.entrySet()) {
				Map<Object, Object> categories = new HashMap<>();
				((Map<?, ?>) entry.getValue()).forEach((category, values) -> categories.put(category, new ArrayList<>((List<?>) values)));
				originalIndex.put(entry.getKey(), categories);
			}
			index.clear();
			Field buffer = Keyboard.class.getDeclaredField("keyDownBuffer");
			buffer.setAccessible(true);
			keyboard = (ByteBuffer) buffer.get(null);
			originalKeys = new byte[keyboard.capacity()];
			for (int i = 0; i < originalKeys.length; i++) {
				originalKeys[i] = keyboard.get(i);
			}
		}

		private Object action(String name) {
			return Arrays.stream(actionType.getEnumConstants()).filter(value -> ((Enum<?>) value).name().equals(name)).findFirst().orElseThrow();
		}

		private void bind(String name, int primary, boolean ctrl, boolean alt, boolean shift,
				int secondary, boolean secondaryCtrl, boolean secondaryAlt, boolean secondaryShift) throws Exception {

			Object action = action(name);
			Object pair = mappings.get(action);
			Field one = pair.getClass().getField("one");
			Field two = pair.getClass().getField("two");
			Object first = one.get(pair);
			Object second = two.get(pair);
			originals.put(action, new Object[]{first, second});
			for (Object categories : index.values()) {
				for (Object values : ((Map<?, ?>) categories).values()) {
					((List<?>) values).removeIf(value -> value == first || value == second);
				}
			}
			Constructor<?> constructor = first.getClass().getConstructor(actionType, int.class, boolean.class, boolean.class, boolean.class);
			Object newFirst = constructor.newInstance(action, primary, ctrl, alt, shift);
			Object newSecond = constructor.newInstance(action, secondary, secondaryCtrl, secondaryAlt, secondaryShift);
			one.set(pair, newFirst);
			two.set(pair, newSecond);
			Field categories = Arrays.stream(actionType.getDeclaredFields()).filter(field -> EnumSet.class.isAssignableFrom(field.getType())).findFirst().orElseThrow();
			categories.setAccessible(true);
			for (var entry : Map.of(primary, newFirst, secondary, newSecond).entrySet()) {
				Map<Object, Object> byCategory = (Map<Object, Object>) index.computeIfAbsent(entry.getKey(), key -> new HashMap<>());
				for (Object category : (Set<?>) categories.get(action)) {
					((List<Object>) byCategory.computeIfAbsent(category, key -> new ArrayList<>())).add(entry.getValue());
				}
			}
		}

		private void hold(int key) {
			for (int i = 0; i < keyboard.capacity(); i++) {
				keyboard.put(i, (byte) (i == key ? 1 : 0));
			}
		}

		@Override
		public void close() throws ReflectiveOperationException {
			for (var entry : originals.entrySet()) {
				Object pair = mappings.get(entry.getKey());
				pair.getClass().getField("one").set(pair, entry.getValue()[0]);
				pair.getClass().getField("two").set(pair, entry.getValue()[1]);
			}
			index.clear();
			index.putAll(originalIndex);
			for (int i = 0; i < originalKeys.length; i++) {
				keyboard.put(i, originalKeys[i]);
			}
		}
	}

	private static BufferedImage capture(Path path) throws Exception {

		int width = Display.getWidth();
		int height = Display.getHeight();
		ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
		GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
		BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int index = (y * width + x) * 4;
				image.setRGB(x, height - y - 1, 0xff000000 | (pixels.get(index) & 0xff) << 16
						| (pixels.get(index + 1) & 0xff) << 8 | pixels.get(index + 2) & 0xff);
			}
		}
		ImageIO.write(image, "png", path.toFile());
		return image;
	}
}
