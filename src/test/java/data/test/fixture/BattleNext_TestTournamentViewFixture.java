package data.test.fixture;

import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.ui.*;
import data.scripts.ui.widget.view.BattleNext_TournamentView;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.function.BiFunction;

public class BattleNext_TestTournamentViewFixture {

	private static final Color SEMITRANSPARENT_GRAY = new Color(175, 175, 175, 180);

	public static SettingsAPI settings() {
		return proxy(SettingsAPI.class, (name, args) -> switch (name) {
			case "getString" -> BattleNext_TestTextCatalog.lookup(args[0], args[1]);
			case "getColor" -> switch (args[0].toString()) {
				case "textGrayColor" -> SEMITRANSPARENT_GRAY;
				case "standardTextColor" -> Color.WHITE;
				case "textFriendColor" -> Color.GREEN;
				case "textEnemyColor" -> Color.RED;
				case "buttonShortcut" -> Color.YELLOW;
				default -> Color.CYAN;
			};
			case "getScreenScaleMult" -> 1f;
			case "getFloat" -> 1f;
			case "getInt" -> 0;
			case "getBoolean" -> false;
			case "getScreenWidth", "getScreenHeight" -> 800f;
			case "getScreenWidthPixels", "getScreenHeightPixels" -> 800f;
			default -> null;
		});
	}

	private static <T> T proxy(Class<T> type, BiFunction<String, Object[], Object> callback) {
		return type.cast(Proxy.newProxyInstance(BattleNext_TestTournamentViewFixture.class.getClassLoader(),
				new Class<?>[]{type}, (instance, method, args) -> callback.apply(method.getName(), args)));
	}

	public static class Bounds {
		private float x;
		private float y;
		private float width;
		private float height;
		private Bounds parent;
		private float leftPad;
		private float topPad;
		private final PositionAPI api = proxy(PositionAPI.class, this::call);

		public float getX() {
			return x;
		}

		public float getY() {
			return y;
		}

		public float getWidth() {
			return width;
		}

		public float getHeight() {
			return height;
		}

		public float getLeftPad() {
			return leftPad;
		}

		public float getTopPad() {
			return topPad;
		}

		public PositionAPI getApi() {
			return api;
		}

		private Object call(String name, Object[] args) {
			return switch (name) {
				case "getX" -> x;
				case "getY" -> y;
				case "getWidth" -> width;
				case "getHeight" -> height;
				case "setLocation" -> {
					x = (Float) args[0];
					y = (Float) args[1];
					yield api;
				}
				case "setSize" -> {
					width = (Float) args[0];
					height = (Float) args[1];
					yield api;
				}
				case "inTL" -> {
					leftPad = (Float) args[0];
					topPad = (Float) args[1];
					if (parent != null) {
						x = parent.x + (Float) args[0];
						y = parent.y + parent.height - height - (Float) args[1];
					}
					yield api;
				}
				case "inLMid" -> api;
				default -> null;
			};
		}
	}

	public static class TestButton {
		private boolean checked;
		private boolean enabled = true;
		private final Bounds bounds = new Bounds();
		private final ButtonAPI api = proxy(ButtonAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> bounds.api;
			case "isChecked" -> checked;
			case "setChecked" -> {
				checked = (Boolean) args[0];
				yield null;
			}
			case "setEnabled" -> {
				enabled = (Boolean) args[0];
				yield null;
			}
			default -> null;
		});
		public Bounds getBounds() {
			return bounds;
		}

		public ButtonAPI getApi() {
			return api;
		}

		public boolean isChecked() {
			return checked;
		}

		public boolean isEnabled() {
			return enabled;
		}

	}

	public static class TestLabel {
		private final Bounds bounds = new Bounds();
		private String text;
		private Color color;
		private Alignment alignment;
		private final LabelAPI api = proxy(LabelAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> bounds.api;
			case "getText" -> text;
			case "setText" -> {
				text = (String) args[0];
				yield null;
			}
			case "setColor" -> {
				color = (Color) args[0];
				yield null;
			}
			case "setAlignment" -> {
				alignment = (Alignment) args[0];
				yield null;
			}
			case "computeTextWidth" -> textWidth((String) args[0]);
			default -> null;
		});

		public Bounds getBounds() {
			return bounds;
		}

		public String getText() {
			return text;
		}

		public Color getColor() {
			return color;
		}

		public Alignment getAlignment() {
			return alignment;
		}

		private float textWidth(String value) {
			float width = 0f;
			for (int point : value.codePoints().toArray()) {
				width += point == '1' ? 4f : 8f;
			}
			return width;
		}
	}

	public static class Harness {
		private final List<TestLabel> nodeLabels = new ArrayList<>();
		private boolean shortenNodeText;
		private final Bounds chartBounds = new Bounds();
		private final Bounds rowBounds = new Bounds();
		private final Bounds summaryBounds = new Bounds();
		private final List<TextBlock> summaryBlocks = new ArrayList<>();
		private final Bounds viewport = new Bounds();
		private final Bounds target = new Bounds();
		private final List<TestButton> buttons = new ArrayList<>();
		private final List<String> paragraphs = new ArrayList<>();
		private final List<Object> buttonIds = new ArrayList<>();
		private boolean scrollingHostCreated;
		private boolean customChartAdded;
		private float contentHeight;
		private float reservedChartHeight;
		private boolean summaryScope;
		private boolean summaryAdded;
		private boolean shrinkChildren;
		private CustomUIPanelPlugin displayPlugin;
		private final Bounds hostBounds = new Bounds();
		private TooltipMakerAPI.TooltipCreator tooltip;
		private final UIComponentAPI targetApi = proxy(UIComponentAPI.class, (name, args) ->
				name.equals("getPosition") ? target.api : null);
		private final TooltipMakerAPI host = proxy(TooltipMakerAPI.class, this::hostCall);
		private final CustomPanelAPI panel = proxy(CustomPanelAPI.class, this::panelCall);
		private final CustomPanelAPI row = proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
			case "getPosition", "addUIElement" -> rowBounds.api;
			case "getPlugin" -> displayPlugin;
			case "createUIElement" -> panelCall(name, args);
			case "createCustomPanel" -> {
				summaryBounds.api.setSize((Float) args[0], (Float) args[1]);
				yield this.summary;
			}
			case "addComponent" -> {
				Bounds child = args[0] == panel ? chartBounds : summaryBounds;
				child.parent = rowBounds;
				yield child.api;
			}
			default -> null;
		});
		private final CustomPanelAPI summary = proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
			case "getPosition" -> summaryBounds.api;
			case "createUIElement" -> {
				assertFalse((Boolean) args[2]);
				TextBlock block = new TextBlock();
				block.owner = this.summary;
				block.layoutWidth = (Float) args[0];
				summaryBlocks.add(block);
				yield block.api;
			}
			case "addUIElement" -> {
				TextBlock block = summaryBlocks.stream().filter(item -> item.api == args[0]).findFirst().orElseThrow();
				assertSame(this.summary, block.owner, "The target panel must create its UI element");
				block.bounds.parent = summaryBounds;
				yield block.bounds.api;
			}
			default -> null;
		});

		public class TextBlock {
			private CustomPanelAPI owner;
			private float layoutWidth;
			private final Bounds bounds = new Bounds();
			private final List<String> text = new ArrayList<>();
			private final List<String> titles = new ArrayList<>();
			private float height;
			private final TooltipMakerAPI api = proxy(TooltipMakerAPI.class, (name, args) -> {
				if (name.equals("getPosition")) return bounds.api;
				if (name.equals("getHeightSoFar")) return height;
				if (name.equals("setHeightSoFar")) {
					height = (Float) args[0];
					return null;
				}

				if (name.equals("addTitle") || name.equals("addPara")) {
					if (name.equals("addTitle")) {
						titles.add(args[0].toString());
					}
					text.add(args[0].toString());
					height += (name.equals("addPara") ? (Float) args[1] : 0f)
							+ 18f * Math.max(1, (int) Math.ceil(args[0].toString().length() * 8f / layoutWidth));
					paragraphs.add(args[0].toString());
					Bounds label = new Bounds();
					return proxy(LabelAPI.class, (method, values) -> method.equals("getPosition") ? label.api : null);
				}
				if (name.equals("addButton")) {
					assertTrue((Float) args[6] > 0f && (Float) args[6] <= layoutWidth);
					height += (Float) args[7] + (Float) args[8];
				}
				return hostCall(name, args);
			});

			public Bounds getBounds() {
				return bounds;
			}

			public List<String> getText() {
				return text;
			}

			public List<String> getTitles() {
				return titles;
			}

			public float getHeight() {
				return height;
			}
		}

		public Harness() {
			viewport.width = 700f;
			viewport.height = 380f;
		}

		public List<TestLabel> getNodeLabels() {
			return nodeLabels;
		}

		public Bounds getChartBounds() {
			return chartBounds;
		}

		public Bounds getRowBounds() {
			return rowBounds;
		}

		public Bounds getSummaryBounds() {
			return summaryBounds;
		}

		public List<TextBlock> getSummaryBlocks() {
			return summaryBlocks;
		}

		public Bounds getViewport() {
			return viewport;
		}

		public Bounds getTarget() {
			return target;
		}

		public List<TestButton> getButtons() {
			return buttons;
		}

		public List<String> getParagraphs() {
			return paragraphs;
		}

		public List<Object> getButtonIds() {
			return buttonIds;
		}

		public boolean isScrollingHostCreated() {
			return scrollingHostCreated;
		}

		public boolean isCustomChartAdded() {
			return customChartAdded;
		}

		public float getContentHeight() {
			return contentHeight;
		}

		public float getReservedChartHeight() {
			return reservedChartHeight;
		}

		public boolean isSummaryAdded() {
			return summaryAdded;
		}

		public CustomUIPanelPlugin getDisplayPlugin() {
			return displayPlugin;
		}

		public TooltipMakerAPI.TooltipCreator getTooltip() {
			return tooltip;
		}

		public TooltipMakerAPI getHost() {
			return host;
		}

		public CustomPanelAPI getPanel() {
			return panel;
		}

		public void setCustomChartAdded(boolean value) {
			customChartAdded = value;
		}

		public void setShrinkChildren(boolean value) {
			shrinkChildren = value;
		}

		public void setShortenNodeText(boolean value) {
			shortenNodeText = value;
		}

		public CustomPanelAPI detailHost(float width, float height) {
			Bounds bounds = new Bounds();
			bounds.api.setSize(width, height);
			return proxy(CustomPanelAPI.class, (name, args) -> switch (name) {
				case "getPosition" -> bounds.api;
				case "getPlugin" -> new BaseCustomUIPanelPlugin();
				case "createCustomPanel" -> {
					chartBounds.api.setSize((Float) args[0], (Float) args[1]);
					yield panel;
				}
				case "addComponent" -> {
					chartBounds.parent = bounds;
					yield chartBounds.api;
				}
				default -> null;
			});
		}

		private Object panelCall(String name, Object[] args) {
			return switch (name) {
				case "createCustomPanel" -> {
					if (args[2] == null) {
						rowBounds.api.setLocation(chartBounds.x, chartBounds.y).setSize((Float) args[0], (Float) args[1]);
						yield row;
					}
					if (!(args[2] instanceof BattleNext_TournamentView)) {
						displayPlugin = (CustomUIPanelPlugin) args[2];
					}
					chartBounds.width = (Float) args[0];
					chartBounds.height = (Float) args[1];
					yield panel;
				}
				case "getPosition", "addComponent" -> chartBounds.api;
				case "getPlugin" -> displayPlugin;
				case "addUIElement" -> {
					if (shrinkChildren) {
						chartBounds.width = 1f;
						chartBounds.height = 1f;
					}
					yield chartBounds.api;
				}
				case "createUIElement" -> {
					if ((Boolean) args[2]) {
						scrollingHostCreated = true;
					}
					yield host;
				}
				default -> null;
			};
		}

		private Object hostCall(String name, Object[] args) {
			return switch (name) {
				case "getExternalScroller" -> proxy(ScrollPanelAPI.class, (method, values) -> method.equals("getYOffset") ? 0f : null);
				case "getPosition" -> hostBounds.api;
				case "beginSubTooltip" -> {
					TextBlock block = new TextBlock();
					block.bounds.width = (Float) args[0];
					block.layoutWidth = (Float) args[0];
					summaryBlocks.add(block);
					yield block.api;
				}
				case "endSubTooltip" -> {
					summaryScope = false;
					yield null;
				}
				case "getHeightSoFar" -> contentHeight;
				case "setHeightSoFar" -> {
					contentHeight = (Float) args[0];
					yield null;
				}
				case "createLabel" -> {
					TestLabel label = new TestLabel();
					label.text = (String) args[0];
					label.color = (Color) args[1];
					label.bounds.width = (Float) args[2];
					nodeLabels.add(label);
					yield label.api;
				}
				case "addSectionHeading", "addTitle", "addPara" -> {
					if (name.equals("addTitle") && args[0].equals(BattleNext_TestTextCatalog.text("Tournament", "BattleNext_tournament_summary"))) {
						assertTrue(summaryScope, "The title must belong to the summary tooltip");
					}
					if (name.equals("addPara")) {
						paragraphs.add(args[0].toString());
					}
					Bounds label = new Bounds();
					yield proxy(LabelAPI.class, (method, values) -> method.equals("getPosition") ? label.api : null);
				}
				case "createRect" -> targetApi;
				case "addCustomDoNotSetPosition" -> {
					if (args[0] instanceof CustomPanelAPI) {
						customChartAdded = true;
						assertTrue(reservedChartHeight > 0f);
					}
					yield args[0];
				}
				case "addSpacer" -> {
					reservedChartHeight = (Float) args[0];
					contentHeight += reservedChartHeight;
					yield targetApi;
				}
				case "addCustom" -> {
					if (args[0] instanceof CustomPanelAPI custom) {
						if (summaryBlocks.isEmpty()) {
							customChartAdded = true;
							reservedChartHeight = custom.getPosition().getHeight() + (Float) args[1];
						} else {
							summaryAdded = true;
							assertTrue(customChartAdded);
						}
						contentHeight += custom.getPosition().getHeight() + (Float) args[1];
					} else {
						summaryAdded = true;
						assertTrue(customChartAdded);
					}
					yield args[0];
				}
				case "addTooltipTo" -> {
					tooltip = (TooltipMakerAPI.TooltipCreator) args[0];
					yield null;
				}
				case "shortenString" -> {
					String value = (String) args[0];
					if (!shortenNodeText) yield value;
					int length = Math.max(3, (int) (((Number) args[1]).floatValue() / 8f));
					yield BattleNext_TournamentView.shorten(value, length);
				}
				case "addButton" -> {
					buttonIds.add(args[1]);
					TestButton button = new TestButton();
					button.bounds.api.setSize((Float) args[6], (Float) args[7]);
					buttons.add(button);
					yield button.api;
				}
				default -> null;
			};
		}
	}
}
