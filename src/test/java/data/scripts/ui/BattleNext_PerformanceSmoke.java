package data.scripts.ui;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.UIComponentAPI;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.sun.management.ThreadMXBean;
import data.model.fleet.BattleNext_FleetStore;
import data.model.fleet.BattleNext_FleetModel;
import data.model.quickbattle.BattleNext_QuickBattleModel;
import java.util.Map;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_TextField;
import data.scripts.ui.widget.BattleNext_TableWidget;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay;
import data.scripts.ui.widget.BattleNext_QuickBattleDisplay.Column;
import data.scripts.ui.widget.BattleNext_PanelBasedWidget.VisibleRows;
import data.scripts.ui.editor.BattleNext_FleetEditor;
import data.scripts.ui.page.BattleNext_FleetLibraryPage;
import data.scripts.ui.widget.provider.BattleNext_VariantSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_FleetMemberProvider;
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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;

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
				long deadlineNanos = System.nanoTime() + 180_000_000_000L;
				CombatEngineAPI attached = null;
				Probe probe = new Probe();
				while (!complete) {
					if (System.nanoTime() > deadlineNanos) throw new AssertionError("Native performance startup or execution timed out");
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
		private BattleNext_FleetEditor fleetEditor;
		private ScrollPanelAPI fleetScroller;
		private int fleetFrame;
		private final JSONArray fleetFrames = new JSONArray();
		private BattleNext_OptionPicker scrollPicker;
		private BattleNext_QuickBattleDisplay scrollTable;
		private CustomPanelAPI tablePanel;
		private int scrollFrame;
		private JSONArray scrollFrames = new JSONArray();

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
					if (!session.errorsCopy().isEmpty()) throw new AssertionError(session.errorsCopy());
					results.put("enabled_mods", Global.getSettings().getModManager().getEnabledModsCopy().stream().map(mod -> mod.getId()).toList());
					List<BattleNext_FleetModel> fleets = session.data().fleets().getAllCopy();
					results.put("fleets", fleets.size());
					int members = 0;
					for (BattleNext_FleetModel fleet : fleets) members += fleet.getMembersReadonly().size();
					results.put("members", members);
					results.put("variants", session.data().variants().options().size());
					results.put("reports", session.data().reports().getAllCopy().size());

					JSONArray fleetDirectory = new JSONArray();
					for (BattleNext_FleetModel fleet : fleets) {
						fleetDirectory.put(new JSONObject().put("id", fleet.getId()).put("name", fleet.getName())
								.put("path", fleet.getSourcePath()).put("members", fleet.getMembersReadonly().size()));
					}
					results.put("fleet_directory", fleetDirectory);

					BattleNext_ModFileManager.ReadSession.deactivate();
					BattleNext_FleetStore direct = new BattleNext_FleetStore();
					direct.reload();
					Map<String, String> expected = new LinkedHashMap<>();
					Map<String, String> actual = new LinkedHashMap<>();
					for (BattleNext_FleetModel fleet : direct.getAllCopy()) expected.put(fleet.getId(), fleet.toJson().toString());
					for (BattleNext_FleetModel fleet : fleets) actual.put(fleet.getId(), fleet.toJson().toString());
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
					if (fleetEditor == null) {
						Object[] tabs = Class.forName("data.scripts.ui.BattleNext_UiManager$BattleManagementPage").getEnumConstants();
						manager.buttonPressed(Arrays.stream(tabs).filter(tab -> tab.toString().equals("FLEETS")).findFirst().orElseThrow());
						BattleNext_FleetLibraryPage page = (BattleNext_FleetLibraryPage) field(manager, "fleetPage");
						BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
						BattleNext_FleetModel fleet = session.data().fleets().getAllCopy().stream()
								.max(Comparator.comparingInt(entry -> entry.getMembersReadonly().size())).orElseThrow();
						page.buttonPressed("fleet:" + fleet.getId());
						fleetEditor = (BattleNext_FleetEditor) field(page, "editor");
						fleetScroller = ((TooltipMakerAPI) field(fleetEditor, "memberViewport")).getExternalScroller();
						results.put("scroll_fleet_id", fleet.getId());
					}
					PositionAPI mouseArea = fleetScroller.getPosition();
					BattleNext_SimulatorMouse.getInstance().setCursorPosition((int) mouseArea.getCenterX(), (int) mouseArea.getCenterY());
					int wheelDelta = fleetFrame / 40 % 2 == 0 ? -120 : 120;
					float before = fleetScroller.getYOffset();
					InputEventAPI wheel = wheelEvent(wheelDelta, mouseArea);
					root.processInput(new ArrayList<>(List.of(wheel)));
					root.advance(1f / 60f);
					manager.advance(1f / 60f);
					capture("fleet-wheel-" + fleetFrame + ".png");
					verifyVisibleRows((VisibleRows) field(fleetEditor, "visibleMembers"), fleetScroller, 0f);
					verifyFleetScroll(wheel, wheelDelta, before);
					fleetFrame++;
					if (fleetFrame == 120) {
						if (fleetScroller.getYOffset() == 0f) {
							throw new AssertionError("Wheel input did not scroll the fleet");
						}
						results.put("fleet_scroll_frames", fleetFrames);
						System.out.println("FLEET WHEEL VERIFIED frames=" + fleetFrame);
						stage++;
					}
				} else if (stage == 5) {
					if (scrollPicker == null) {
						fleetEditor.buttonPressed("add_member");
						scrollPicker = (BattleNext_OptionPicker) field(fleetEditor, "memberPicker");
					}
					TooltipMakerAPI grid = (TooltipMakerAPI) field(scrollPicker, "grid");
					if (scrollWheel("add-member", grid.getExternalScroller())) {
						scrollPicker.closeOverlays();
						scrollPicker = null;
						stage++;
					}
				} else if (stage == 6) {
					if (scrollPicker == null) {
						BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
						BattleNext_FleetMemberProvider source = new BattleNext_FleetMemberProvider(session.data().variants());
						List<String> selected = source.getOptions().stream().limit(60).map(BattleNext_OptionPicker.OptionChoice::id).toList();
						scrollPicker = new BattleNext_OptionPicker(new BattleNext_UiInputRouter(), root, source,
								BattleNext_OptionPicker.OptionSelectionMode.MULTIPLE, () -> selected, ignored -> {});
						scrollPicker.openAt(root, () -> new BattleNext_OptionPicker.Anchor(200f, 500f, 200f, 30f));
						((CustomPanelAPI) field(scrollPicker, "popup")).getPlugin().buttonPressed("browse:selected");
						results.put("selected_picker_items", selected.size());
					}
					TooltipMakerAPI grid = (TooltipMakerAPI) field(scrollPicker, "grid");
					if (scrollWheel("selected-members", grid.getExternalScroller())) {
						scrollPicker.close();
						scrollPicker = null;
						stage++;
					}
				} else if (stage == 7) {
					if (scrollTable == null) {
						JSONArray tableSizes = new JSONArray();
						for (int count : new int[]{0, 1, 50, 51}) {
							createScrollingTable(count);
							root.advance(1f / 60f);
							capture("quick-battle-" + count + "-initial.png");
							BattleNext_TableWidget table = (BattleNext_TableWidget) field(scrollTable, "tableWidget");
							Map<?, ?> mounted = (Map<?, ?>) field(table, "mountedRows");
							if (field(table, "visibleRows") == null || (count == 1 && mounted.size() != 1)
									|| (count >= 50 && (mounted.isEmpty() || mounted.size() >= count))) {
								throw new AssertionError("Native table must mount rows dynamically at count=" + count);
							}
							tableSizes.put(new JSONObject().put("rows", count).put("mounted", mounted.size()));
							scrollTable.close();
							root.removeComponent(tablePanel);
						}
						results.put("dynamic_table_sizes", tableSizes);
						createScrollingTable(100);
					}
					if (scrollWheel("quick-battle", scrollTable.getContent().getExternalScroller())) {
						if (!scrollTable.getRowsReadonly().get(0).text(Column.MAP).equals("17")
								|| scrollTable.getRowsReadonly().get(0).isEnabled()) throw new AssertionError("Native table scroll changed draft data");
						scrollTable.close();
						root.removeComponent(tablePanel);
						stage++;
					}
				} else if (stage == 8) {
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
				} else if (stage == 9) {
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
				} else if (stage == 10) {
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

		private boolean scrollWheel(String name, ScrollPanelAPI scroller) throws Exception {
			PositionAPI area = scroller.getPosition();
			BattleNext_SimulatorMouse.getInstance().setCursorPosition((int) area.getCenterX(), (int) area.getCenterY());
			int delta = (scrollFrame < 20 ? scrollFrame / 10 : scrollFrame / 3) % 2 == 0 ? -120 : 120;
			float before = scroller.getYOffset();
			int eventCount = scrollFrame < 20 ? 1 : scrollFrame < 40 ? 12 : 4;
			List<InputEventAPI> events = new ArrayList<>();
			for (int index = 0; index < eventCount; index++) events.add(wheelEvent(delta, area));
			root.processInput(events);
			root.advance(1f / 60f);
			manager.advance(1f / 60f);
			float after = scroller.getYOffset();
			if (events.stream().noneMatch(InputEventAPI::isConsumed) || (delta < 0 ? after < before : after > before)) {
				throw new AssertionError("Native wheel dispatch failed for " + name + " at " + scrollFrame);
			}
			VisibleRows rows = name.equals("quick-battle")
					? (VisibleRows) field(field(scrollTable, "tableWidget"), "visibleRows") : (VisibleRows) field(scrollPicker, "visibleRows");
			if (scrollFrame >= 40) {
				float fraction = new float[]{0f, 1f, 0.5f, 0.02f, 0.98f}[scrollFrame % 5];
				scroller.setYOffset(Math.max(0f, rows.height() - area.getHeight()) * fraction);
			}
			capture(name + "-wheel-" + scrollFrame + ".png");
			int visibleCount = verifyVisibleRows(rows, scroller, name.equals("quick-battle") ? 0f : 8f);
			scrollFrames.put(new JSONObject().put("frame", scrollFrame).put("delta", delta).put("events", eventCount)
					.put("scroll", scroller.getYOffset()).put("visible_rows", visibleCount));
			if (++scrollFrame < 60) return false;
			if (scroller.getYOffset() == 0f) throw new AssertionError("Native wheel did not move " + name);
			results.put(name + "_wheel_frames", scrollFrames);
			System.out.println("SCROLL VERIFIED kind=" + name + " events=" + scrollFrame);
			scrollFrame = 0;
			scrollFrames = new JSONArray();
			return true;
		}

		private int verifyVisibleRows(VisibleRows rows, ScrollPanelAPI scroller, float inset) throws Exception {
			PositionAPI content = rows.getPanel().getPosition();
			PositionAPI viewport = scroller.getPosition();
			float scroll = content.getY() + content.getHeight() - viewport.getY() - viewport.getHeight() - inset;
			float[] offsets = (float[]) field(rows, "offsets");
			Set<Integer> mounted = (Set<Integer>) field(rows, "mounted");
			int visible = 0;
			for (int index = 0; index + 1 < offsets.length; index++) {
				if (offsets[index + 1] <= scroll + 0.01f || offsets[index] >= scroll + viewport.getHeight() - 0.01f) continue;
				visible++;
				if (!mounted.contains(index)) throw new AssertionError("Visible row missing before draw: stage=" + stage
						+ ", frame=" + scrollFrame + ", row=" + index + ", actual_scroll=" + scroll + ", scroller=" + scroller.getYOffset());
			}
			List<?> children = (List<?>) rows.getPanel().getClass().getMethod("getChildrenCopy").invoke(rows.getPanel());
			for (Object child : children) {
				if (!(child instanceof UIComponentAPI component)) continue;
				PositionAPI position = component.getPosition();
				float top = content.getY() + content.getHeight() - position.getY() - position.getHeight() - inset;
				if (position.getY() >= viewport.getY() + viewport.getHeight() || position.getY() + position.getHeight() <= viewport.getY()) continue;
				boolean aligned = false;
				for (int index : mounted) {
					if (Math.abs(offsets[index] - top) < 0.1f) aligned = true;
				}
				if (!aligned) throw new AssertionError("Drawn row position differs from its mounted offset at " + top);
			}
			return visible;
		}

		private void createScrollingTable(int count) throws Exception {
			BattleNext_ManagementSession session = (BattleNext_ManagementSession) field(manager, "catalogs");
			List<BattleNext_FleetModel> fleets = session.data().fleets().getAllCopy();
			List<BattleNext_QuickBattleModel> entries = IntStream.range(0, count)
					.mapToObj(index -> new BattleNext_QuickBattleModel(fleets.get(0).getId(), fleets.get(1).getId(), index, 1f)).toList();
			tablePanel = root.createCustomPanel(root.getPosition().getWidth() - 32f, root.getPosition().getHeight() - 32f,
					new BaseCustomUIPanelPlugin() {
				@Override
				public void renderBelow(float alphaMult) {
					BattleNext_GeometryRenderer.fillRect(tablePanel.getPosition(), Color.BLACK, alphaMult);
				}
			});
			root.addComponent(tablePanel).inTL(16f, 16f);
			BattleNext_QuickBattleDisplay.Presentation presentation = new BattleNext_QuickBattleDisplay.Presentation(
					List.of(Column.NUMBER, Column.PLAYER, Column.ENEMY, Column.MAP, Column.SCALE, Column.STATUS, Column.ENABLED, Column.ACTIONS),
					List.of(0.05f, 0.2f, 0.2f, 0.08f, 0.08f, 0.15f, 0.08f, 0.16f), true,
					row -> new BattleNext_QuickBattleDisplay.Action("Delete", row.getIndex(), true), ignored -> {});
			scrollTable = new BattleNext_QuickBattleDisplay(new BattleNext_UiInputRouter(), entries, presentation,
					entry -> entry, row -> List.of(), session.data().fleets(), root);
			scrollTable.attach(tablePanel);
			if (count > 0) {
				scrollTable.getRowsReadonly().get(0).getField(Column.MAP).setText("17");
				scrollTable.getRowsReadonly().get(0).setEnabled(false);
			}
			results.put("scroll_table_rows", entries.size());
		}

		private InputEventAPI wheelEvent(int delta, PositionAPI bounds) throws ReflectiveOperationException {
			return (InputEventAPI) Class.forName("com.fs.starfarer.util.A.C")
					.getConstructor(InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class)
					.newInstance(InputEventClass.MOUSE_EVENT, InputEventType.MOUSE_SCROLL,
							(int) bounds.getCenterX(), (int) bounds.getCenterY(), delta, '\0');
		}

		private void verifyFleetScroll(InputEventAPI wheel, int delta, float before) throws Exception {
			float after = fleetScroller.getYOffset();
			if (!wheel.isConsumed()) throw new AssertionError("Fleet must consume the native wheel event at frame " + fleetFrame);
			if (delta < 0 ? after < before : after > before) {
				throw new AssertionError("Fleet scrolled against the wheel direction at frame " + fleetFrame);
			}
			fleetFrames.put(new JSONObject().put("frame", fleetFrame).put("wheel_delta", delta)
					.put("before", before).put("scroll", after));
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
