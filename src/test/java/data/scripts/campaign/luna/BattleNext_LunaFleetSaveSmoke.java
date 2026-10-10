package data.scripts.campaign.luna;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.FleetDataAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.characters.FullName;
import com.fs.starfarer.api.combat.BaseEveryFrameCombatPlugin;
import com.fs.starfarer.api.combat.ViewportAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.input.InputEventClass;
import com.fs.starfarer.api.input.InputEventType;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.PositionAPI;
import com.fs.starfarer.api.ui.ScrollPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.tools.BattleNext_GeometryRenderer;
import data.scripts.ui.widget.BattleNext_TextField;
import data.simulator.BattleNext_SimulatorLaunch;
import data.simulator.BattleNext_SimulatorNative;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.Display;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_LunaFleetSaveSmoke {

	private static volatile boolean verified;
	private static Path images;

	public static void main(String[] arguments) throws Throwable {

		if (arguments.length == 0) {
			Path core = Path.of("../..", "starsector-core").toAbsolutePath().normalize();
			Path output = Path.of("build/reports/luna-fleet-save").toAbsolutePath();
			Files.createDirectories(output);
			List<String> command = BattleNext_SimulatorLaunch.javaCommand(core);
			command.addAll(List.of("-Dcom.fs.starfarer.settings.paths.mods=" + core.getParent().resolve("mods"),
					"-Dcom.fs.starfarer.settings.paths.logs=" + output, "-cp", System.getProperty("java.class.path"),
					BattleNext_LunaFleetSaveSmoke.class.getName(), output.toString()));
			Process process = BattleNext_SimulatorLaunch.start(command, core);
			String result = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			if (process.waitFor() != 0 || !result.contains("Fleet save native scrolling and expansion verified")) {
				throw new AssertionError(result);
			}
			System.out.println("Fleet save native scrolling and expansion verified; images=" + output);
			return;
		}
		images = Path.of(arguments[0]);
		System.setProperty("launchDirect", "true");
		System.setProperty("startRes", "800x600");
		System.setProperty("startFS", "false");
		System.setProperty("startSound", "false");
		Logger.getRootLogger().addAppender(new ConsoleAppender(new PatternLayout("%p %c - %m%n")));
		Logger.getRootLogger().setLevel(Level.WARN);
		Display.setLocation(-20000, -20000);
		Thread driver = new Thread(BattleNext_LunaFleetSaveSmoke::drive, "Fleet save native verification");
		driver.setDaemon(true);
		driver.start();
		Class<?> launcher = Class.forName("com.fs.starfarer.StarfarerLauncher");
		MethodHandles.publicLookup().findStatic(launcher, "main", MethodType.methodType(void.class, String[].class))
				.invoke((Object) new String[0]);
		if (!verified) throw new AssertionError("Fleet save native verification did not finish");
	}

	private static void drive() {
		try {
			long deadline = System.nanoTime() + 120000000000L;
			while (Global.getSettings() == null || Global.getCurrentState() != GameState.TITLE || Global.getCombatEngine() == null) {
				if (System.nanoTime() > deadline) throw new AssertionError("Native title startup timed out");
				Thread.sleep(20L);
			}
			Thread.sleep(1000L);
			Global.getSettings().setBoolean("idleWhileWindowNotVisible", false);
			Global.getCombatEngine().addPlugin(new Preview());
			while (!verified) {
				if (System.nanoTime() > deadline) throw new AssertionError("Fleet save verification timed out");
				Thread.sleep(20L);
			}
			new BattleNext_SimulatorNative(BattleNext_LunaFleetSaveSmoke.class.getClassLoader()).exit();
		} catch (Throwable exception) {
			exception.printStackTrace();
			System.exit(1);
		}
	}

	public static class Preview extends BaseEveryFrameCombatPlugin {

		private CustomPanelAPI root;
		private BattleNext_LunaFleetSavePanel display;
		private int stage;
		private boolean rendered;
		private float nameY;
		private float offset;

		@Override
		public void advance(float amount, List<InputEventAPI> events) {
			try {
				if (root == null) {
					open();
				}
				root.getClass().getMethod("advance", float.class).invoke(root, amount);
				if (!rendered) return;
				rendered = false;
				if (stage == 0) {
					display.buttonPressed(0);
					display.buttonPressed(1);
				} else if (stage == 1) {
					verifyScroll();
				} else {
					display.close();
					if (!children(root).isEmpty()) throw new AssertionError("Fleet save root must release its contents");
					System.out.println("Fleet save native scrolling and expansion verified");
					verified = true;
					Global.getCombatEngine().removePlugin(this);
					return;
				}
				stage++;
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}

		private void open() throws Exception {

			var player = Global.getFactory().createPerson();
			player.setName(new FullName("Preview", "Commander", FullName.Gender.ANY));
			player.setPortraitSprite("graphics/portraits/portrait_generic.png");
			player.getStats().setSkillLevel("helmsmanship", 2f);
			List<FleetMemberAPI> members = new ArrayList<>();
			for (int index = 0; index < 12; index++) {
				var member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, index % 2 == 0 ? "wolf_CS" : "lasher_Standard");
				member.setShipName("Preview ship " + (index + 1));
				member.setCaptain(player);
				members.add(member);
			}
			FleetDataAPI data = (FleetDataAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{FleetDataAPI.class},
					(instance, method, args) -> method.getName().equals("getMembersListCopy") ? members : null);
			CampaignFleetAPI fleet = (CampaignFleetAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{CampaignFleetAPI.class},
					(instance, method, args) -> switch (method.getName()) {
						case "getFleetData" -> data;
						case "getId", "getName" -> "Preview fleet";
						default -> null;
					});
			SectorAPI previous = Global.getSector();
			display = new BattleNext_LunaFleetSavePanel(() -> {});
			try {
				Global.setSector((SectorAPI) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{SectorAPI.class},
						(instance, method, args) -> switch (method.getName()) {
							case "getPlayerFleet" -> fleet;
							case "getPlayerPerson" -> player;
							default -> null;
						}));
				display.open(members.get(0), members.get(0).getVariant(), "");
			} finally {
				Global.setSector(previous);
			}
			if (field(display, "snapshot") == null) throw new AssertionError("Native fleet capture: " + field(display, "status"));
			root = Global.getSettings().createCustom(560f, 400f, display);
			root.getPosition().setLocation(120f, 100f);
			display.attach(root, 560f, 400f);
			nameY = ((BattleNext_TextField) field(display, "name")).getPosition().getY();
		}

		private void verifyScroll() throws Exception {

			TooltipMakerAPI list = (TooltipMakerAPI) field(display, "membersContent");
			ScrollPanelAPI scroll = list.getExternalScroller();
			PositionAPI bounds = scroll.getPosition();
			Method input = List.of(scroll.getClass().getMethods()).stream().filter(method -> method.getName().equals("processInput")
					&& method.getParameterCount() == 1 && !method.getParameterTypes()[0].isInterface()).findFirst().orElseThrow();
			Class<?> listType = input.getParameterTypes()[0];
			Class<?> eventType = (Class<?>) ((ParameterizedType) listType.getGenericSuperclass()).getActualTypeArguments()[0];
			InputEventAPI wheel = event(eventType, InputEventType.MOUSE_SCROLL, bounds.getCenterX(), bounds.getCenterY(), -1);
			input.invoke(scroll, nativeEvents(listType, wheel));
			if (!wheel.isConsumed() || scroll.getYOffset() <= 0f) throw new AssertionError("Native mouse wheel must scroll the fleet list");
			float before = scroll.getYOffset();
			PositionAPI thumb = verticalThumb(scroll);
			input.invoke(scroll, nativeEvents(listType, event(eventType, InputEventType.MOUSE_DOWN, thumb.getCenterX(), thumb.getCenterY(), 0)));
			InputEventAPI move = event(eventType, InputEventType.MOUSE_MOVE, thumb.getCenterX(), thumb.getCenterY() - 25f, 0);
			eventType.getMethod("setDY", int.class).invoke(move, -25);
			input.invoke(scroll, nativeEvents(listType, move));
			input.invoke(scroll, nativeEvents(listType, event(eventType, InputEventType.MOUSE_UP, thumb.getCenterX(), thumb.getCenterY() - 25f, 0)));
			if (scroll.getYOffset() <= before) throw new AssertionError("Native scrollbar thumb dragging must change the offset");
			offset = scroll.getYOffset();
			CustomPanelAPI old = (CustomPanelAPI) field(display, "membersPanel");
			display.buttonPressed(2);
			list = (TooltipMakerAPI) field(display, "membersContent");
			if (list.getExternalScroller().getYOffset() != offset) throw new AssertionError("Expansion must preserve native scroll offset");
			if (old.getClass().getMethod("getParent").invoke(old) != null) throw new AssertionError("Refresh must detach the previous native container");
			if (children(field(display, "membersPanel")).size() != 1) throw new AssertionError("Fleet list must own one native scroll container");
			if (((BattleNext_TextField) field(display, "name")).getPosition().getY() != nameY) throw new AssertionError("Name input must remain fixed");
		}

		@Override
		public void renderInUICoords(ViewportAPI viewport) {
			if (root == null || rendered || verified) return;
			try {
				BattleNext_GeometryRenderer.fillRect(root.getPosition(), Color.BLACK, 1f);
				root.getClass().getMethod("render", float.class).invoke(root, 1f);
				capture(images.resolve(List.of("collapsed.png", "expanded.png", "scrolled.png").get(stage)));
				rendered = true;
			} catch (Throwable exception) {
				exception.printStackTrace();
				System.exit(1);
			}
		}
	}

	private static PositionAPI verticalThumb(ScrollPanelAPI scroll) throws Exception {
		PositionAPI bounds = scroll.getPosition();
		for (Method method : scroll.getClass().getDeclaredMethods()) {
			if (method.getParameterCount() != 0 || !PositionAPI.class.isAssignableFrom(method.getReturnType())) continue;
			method.setAccessible(true);
			PositionAPI candidate = (PositionAPI) method.invoke(scroll);
			if (candidate == null) continue;
			if (candidate.getHeight() < bounds.getHeight() && candidate.getWidth() < bounds.getWidth()
					&& candidate.getCenterX() > bounds.getX() + bounds.getWidth() * 0.8f) return candidate;
		}
		throw new AssertionError("Native fleet list must expose its vertical scrollbar thumb");
	}

	private static InputEventAPI event(Class<?> type, InputEventType kind, float x, float y, int value) throws Exception {
		return (InputEventAPI) type.getConstructor(InputEventClass.class, InputEventType.class, int.class, int.class, int.class, char.class)
				.newInstance(InputEventClass.MOUSE_EVENT, kind, (int) x, (int) y, value, '\0');
	}

	private static Object nativeEvents(Class<?> type, InputEventAPI event) throws Exception {
		Object list = type.getConstructor().newInstance();
		((List<Object>) list).add(event);
		return list;
	}

	private static Object field(Object target, String name) throws Exception {
		Field field = target.getClass().getDeclaredField(name);
		field.setAccessible(true);
		return field.get(target);
	}

	private static List<?> children(Object panel) throws Exception {
		return (List<?>) panel.getClass().getMethod("getChildrenCopy").invoke(panel);
	}

	private static void capture(Path path) throws Exception {
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
	}
}
