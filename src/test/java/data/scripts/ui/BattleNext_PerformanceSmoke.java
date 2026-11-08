package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.sun.management.ThreadMXBean;
import data.model.fleet.BattleNext_FleetStore;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.simulator.BattleNext_SimulatorLaunch;
import data.simulator.BattleNext_SimulatorNative;
import data.simulator.BattleNext_SimulatorMouse;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;
import org.lwjgl.BufferUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;

import java.awt.Color;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;

public final class BattleNext_PerformanceSmoke {

	private static volatile boolean complete;
	private static Path output;

	private BattleNext_PerformanceSmoke() {
	}

	public static void main(String[] arguments) throws Throwable {
		if (arguments.length == 0) {
			Path core = Path.of("../../starsector-core").toAbsolutePath().normalize();
			Path report = Path.of("build/reports/performance").toAbsolutePath();
			Files.createDirectories(report);
			List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
			command.addAll(List.of("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
					"-Dcom.fs.starfarer.settings.paths.logs=" + report, "-cp", System.getProperty("java.class.path"),
					BattleNext_PerformanceSmoke.class.getName(), report.toString()));
			Process process = BattleNext_SimulatorLaunch.start(command, core);
			String text = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			Files.writeString(report.resolve("native-output.log"), text, StandardCharsets.UTF_8);
			if (process.waitFor() != 0 || !text.contains("PERFORMANCE VERIFIED")) {
				throw new AssertionError("Native performance verification failed: " + report + "\n" + text.substring(Math.max(0, text.length() - 6000)));
			}
			System.out.println(text.lines().filter(line -> line.startsWith("PERF ") || line.startsWith("PERFORMANCE")).reduce("", (first, second) -> first + second + "\n"));
			return;
		}
		output = Path.of(arguments[0]);
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "1600x900");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		BattleNext_SimulatorMouse.getInstance();
		Display.setLocation(-20000, -20000);
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%p %c - %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
		Thread driver = new Thread(() -> {
			try {
				long deadline = System.nanoTime() + 180_000_000_000L;
				CombatEngineAPI attached = null;
				Probe probe = new Probe();
				while (!complete) {
					if (System.nanoTime() > deadline) throw new AssertionError("Native performance startup or execution timed out");
					if (Global.getSettings() != null && Global.getCurrentState() == GameState.TITLE && Global.getCombatEngine() != null
							&& attached != Global.getCombatEngine()) {
						Thread.sleep(5000L);
						Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
						attached = Global.getCombatEngine();
						attached.addPlugin(probe);
					}
					Thread.sleep(20L);
				}
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}, "BattleNext performance driver");
		driver.setDaemon(true);
		driver.start();
		Class.forName("com.fs.starfarer.StarfarerLauncher").getMethod("main", String[].class).invoke(null, (Object) new String[0]);
		if (!complete) throw new AssertionError("Performance verification did not complete");
	}

	private interface Work {
		void run() throws Throwable;
	}

	public static final class Probe extends BaseEveryFrameCombatPlugin {
		private final ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
		private final JSONObject results = new JSONObject();
		private final List<String> failures = new ArrayList<>();
		private BattleNext_UiManager manager;
		private CustomPanelAPI root;
		private int stage;

		@Override
		public void renderInUICoords(ViewportAPI viewport) {
			try {
				if (stage == 0) {
					manager = new BattleNext_UiManager(() -> { });
					root = Global.getSettings().createCustom(Global.getSettings().getScreenWidth() * 0.8f,
							Global.getSettings().getScreenHeight() * 0.8f, new BaseCustomUIPanelPlugin());
					root.getPosition().setLocation(Global.getSettings().getScreenWidth() * 0.1f, Global.getSettings().getScreenHeight() * 0.1f);
					manager.attach(root);
					measure("first_visible", 1, 50d, manager::open);
					BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
					boolean loading = manager.isLoading() && field(manager, "attachedView") == null;
					boolean started = field(session, "preparation") instanceof CompletableFuture<?>;
					if (!loading || !started) throw new AssertionError("Opening must display loading and start preparation immediately");
					results.put("loading_shell_visible", loading).put("preparation_started_on_open", started);
					capture("management-loading.png");
					stage++;
				} else if (stage == 1) {
					manager.advance(1f / 60f);
					if (manager.isLoading()) return;
					BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
					if (!session.errors().isEmpty()) throw new AssertionError(session.errors());
					results.put("enabled_mods", Global.getSettings().getModManager().getEnabledModsCopy().stream().map(mod -> mod.getId()).toList());
					results.put("fleets", session.data().fleets().getAll().size());
					results.put("members", session.data().fleets().getAll().stream().mapToInt(fleet -> fleet.getMembers().size()).sum());
					results.put("variants", session.data().variants().options().size());
					results.put("reports", session.data().reports().getAll().size());
					JSONArray fleetDirectory = new JSONArray();
					for (var fleet : session.data().fleets().getAll()) {
						fleetDirectory.put(new JSONObject().put("id", fleet.getId()).put("name", fleet.getName())
								.put("path", fleet.getSourcePath()).put("members", fleet.getMembers().size()));
					}
					results.put("fleet_directory", fleetDirectory);
					BattleNext_ModFileManager.ReadSession.deactivate();
					BattleNext_FleetStore direct = new BattleNext_FleetStore();
					direct.reload();
					var expected = new LinkedHashMap<String, String>();
					var actual = new LinkedHashMap<String, String>();
					for (var fleet : direct.getAll()) expected.put(fleet.getId(), fleet.toJson().toString());
					for (var fleet : session.data().fleets().getAll()) actual.put(fleet.getId(), fleet.toJson().toString());
					Files.writeString(output.resolve("fleet-catalog-comparison.json"), new JSONObject().put("direct", new JSONObject(expected))
							.put("management", new JSONObject(actual)).toString(2), StandardCharsets.UTF_8);
					if (!expected.equals(actual)) throw new AssertionError("Management fleet catalog differs from direct file loading");
					results.put("direct_fleet_catalog_equal", true);
					session.activate();
					stage++;
				} else if (stage == 2) {
					Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
					for (Object tab : tabs) {
						Object other = tabs[tab == tabs[6] ? 0 : 6];
						manager.buttonPressed(tab);
						measure("tab_" + tab, 20, 50d, () -> {
							manager.buttonPressed(other);
							manager.buttonPressed(tab);
						});
					}
					stage++;
				} else if (stage == 3) {
					BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
					BattleNext_VariantSourceProvider source = new BattleNext_VariantSourceProvider(session.data().variants());
					BattleNext_OptionPicker picker = new BattleNext_OptionPicker(new BattleNext_UiInputRouter(), root, source,
							BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, List::of, ids -> { });
					measure("picker_open", 20, 50d, () -> {
						picker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(200f, 500f, 200f, 30f));
						picker.close();
					});
					picker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(200f, 500f, 200f, 30f));
					capture("picker-hulls.png");
					CustomPanelAPI popup = (CustomPanelAPI) field(picker, "popup");
					popup.getPlugin().buttonPressed("browse:pinned");
					capture("picker-pinned.png");
					picker.close();
					stage++;
				} else if (stage == 4) {
					TooltipMakerAPI host = root.createUIElement(400f, 30f, false);
					BattleNext_TextField text = new BattleNext_TextField(new BattleNext_UiInputRouter(), root, 400f, 30f);
					text.attach(host, root);
					text.setMaxChars(-1);
					text.setText("a".repeat(8000));
					Method locate = BattleNext_TextField.class.getDeclaredMethod("cursorForMouse", float.class);
					locate.setAccessible(true);
					measure("text_8000_click", 20, 4d, () -> locate.invoke(text, text.getPosition().getX() + 100f));
					text.close();
					stage++;
				} else if (stage == 5) {
					measure("rectangles_1000", 20, 8d, () -> {
						BattleNext_GeometryRenderer.beginBatch();
						try {
							for (int index = 0; index < 1000; index++) BattleNext_GeometryRenderer.fillRect(0f, 0f, 10f, 10f, Color.WHITE, 1f);
						} finally {
							BattleNext_GeometryRenderer.endBatch();
						}
						GL11.glFinish();
					});
					stage++;
				} else if (stage == 6) {
					Files.writeString(output.resolve("measurements.json"), results.toString(2), StandardCharsets.UTF_8);
					if (!failures.isEmpty()) throw new AssertionError(failures);
					System.out.println("PERFORMANCE VERIFIED");
					complete = true;
					stage++;
					new BattleNext_SimulatorNative(getClass().getClassLoader()).exit();
				}
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}

		private void measure(String name, int count, double limit, Work work) throws Throwable {
			JSONArray samples = new JSONArray();
			double[] times = new double[count];
			for (int index = 0; index < count; index++) {
				long thread = Thread.currentThread().getId();
				long before = allocations.getThreadAllocatedBytes(thread);
				long start = System.nanoTime();
				work.run();
				times[index] = (System.nanoTime() - start) / 1_000_000d;
				samples.put(new JSONObject().put("milliseconds", times[index]).put("allocated_bytes", allocations.getThreadAllocatedBytes(thread) - before));
			}
			Arrays.sort(times);
			double p95 = times[(int) Math.ceil(count * 0.95d) - 1];
			results.put(name, new JSONObject().put("p95_ms", p95).put("limit_ms", limit).put("samples", samples));
			System.out.println("PERF " + name + " p95_ms=" + p95 + " limit_ms=" + limit);
			if (p95 > limit) failures.add(name + "=" + p95);
		}

		private Object field(Object target, String name) throws ReflectiveOperationException {
			Field field = target.getClass().getDeclaredField(name);
			field.setAccessible(true);
			return field.get(target);
		}

		private void capture(String name) throws Exception {
			GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
			GL11.glMatrixMode(GL11.GL_PROJECTION);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
			GL11.glOrtho(0d, Global.getSettings().getScreenWidth(), 0d, Global.getSettings().getScreenHeight(), -1d, 1d);
			GL11.glMatrixMode(GL11.GL_MODELVIEW);
			GL11.glPushMatrix();
			GL11.glLoadIdentity();
			try {
				root.render(1f);
				GL11.glFinish();
				int width = Display.getWidth();
				int height = Display.getHeight();
				ByteBuffer pixels = BufferUtils.createByteBuffer(width * height * 4);
				GL11.glReadPixels(0, 0, width, height, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, pixels);
				BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
				for (int y = 0; y < height; y++) {
					for (int x = 0; x < width; x++) {
						int offset = (y * width + x) * 4;
						image.setRGB(x, height - y - 1, 0xff000000 | (pixels.get(offset) & 255) << 16
								| (pixels.get(offset + 1) & 255) << 8 | pixels.get(offset + 2) & 255);
					}
				}
				ImageIO.write(image, "png", output.resolve(name).toFile());
			} finally {
				GL11.glPopMatrix();
				GL11.glMatrixMode(GL11.GL_PROJECTION);
				GL11.glPopMatrix();
				GL11.glPopAttrib();
			}
		}
	}
}
