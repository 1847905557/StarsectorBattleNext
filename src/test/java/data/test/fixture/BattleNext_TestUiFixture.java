package data.test.fixture;

import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import static org.junit.jupiter.api.Assertions.*;

import java.awt.Color;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class BattleNext_TestUiFixture extends BattleNext_TestGameFixture {

	protected final List<String> labels = new ArrayList<>();
	protected final Map<PositionAPI, Alignment> labelAlignments = new IdentityHashMap<>();
	protected final Map<PositionAPI, String> labelFonts = new IdentityHashMap<>();
	protected final Map<PositionAPI, Color> labelColors = new IdentityHashMap<>();
	protected final Map<String, AtomicBoolean> buttons = new LinkedHashMap<>();
	protected final Map<String, PositionAPI> buttonPositions = new LinkedHashMap<>();
	protected final Map<String, String> buttonLabels = new LinkedHashMap<>();
	protected final Map<String, Object> buttonIds = new LinkedHashMap<>();
	protected final List<UIComponentAPI> removedComponents = new ArrayList<>();
	protected final Map<UIComponentAPI, String> imagePaths = new IdentityHashMap<>();
	protected final Map<UIComponentAPI, TooltipMakerAPI.TooltipCreator> attachedTooltips = new IdentityHashMap<>();
	protected final Map<UIComponentAPI, TooltipBinding> tooltipBindings = new IdentityHashMap<>();
	protected boolean deferTooltipWidth;
	protected boolean shortenLabels;
	protected float paragraphLineHeight = 20f;

	protected record TooltipBinding(TooltipState owner, TooltipMakerAPI.TooltipLocation location,
			boolean recreateEveryFrame) { }

	protected BattleNext_OptionPicker.OptionChoiceSource optionSource() {
		return new BattleNext_OptionPicker.OptionChoiceSource() {
			@Override
			public List<BattleNext_OptionPicker.OptionChoice> getOptions() {
				return List.of(new BattleNext_OptionPicker.OptionChoice("first", "First"),
						new BattleNext_OptionPicker.OptionChoice("second", "Second"));
			}

			@Override
			public int getMaxCount(String id) {
				return 1;
			}

			@Override
			public String getIconPath(String id) {
				return null;
			}

			@Override
			public void createTooltip(TooltipMakerAPI tooltip, BattleNext_OptionPicker.OptionChoice option) {
				tooltip.addTitle(option.getLabel());
			}
		};
	}
	protected final Map<CustomPanelAPI, CustomUIPanelPlugin> plugins = new IdentityHashMap<>();
	protected final Map<CustomPanelAPI, List<Object>> children = new IdentityHashMap<>();
	protected final Map<PositionAPI, Bounds> geometry = new IdentityHashMap<>();
	protected final Map<TooltipMakerAPI, TooltipState> tooltips = new IdentityHashMap<>();

	protected CustomPanelAPI panel(float width, float height, CustomUIPanelPlugin plugin) {

		PositionAPI position = position(width, height);
		Map<TooltipMakerAPI, Boolean> owned = new IdentityHashMap<>();
		CustomPanelAPI[] holder = new CustomPanelAPI[1];
		CustomPanelAPI result = proxy(CustomPanelAPI.class, (method, args) -> switch (method) {
			case "getPosition" -> position;
			case "getPlugin" -> plugin;
			case "createCustomPanel" -> panel((Float) args[0], (Float) args[1], (CustomUIPanelPlugin) args[2]);
			case "createUIElement" -> {
				TooltipMakerAPI tooltip = tooltip((Float) args[0], (Float) args[1], (Boolean) args[2]);
				owned.put(tooltip, true);
				yield tooltip;
			}
			case "addUIElement" -> {
				assertTrue(owned.containsKey(args[0]), "UI element must be mounted by its creating panel");
				TooltipState state = tooltips.get(args[0]);
				state.mount(position);
				children.computeIfAbsent(holder[0], ignored -> new ArrayList<>()).add(args[0]);
				yield state.viewport;
			}
			case "addComponent" -> {
				children.computeIfAbsent(holder[0], ignored -> new ArrayList<>()).add(args[0]);
				PositionAPI child = ((UIComponentAPI) args[0]).getPosition();
				geometry.get(child).parent = position;
				yield child;
			}
			case "removeComponent" -> {
				List<Object> mounted = children.get(holder[0]);
				assertNotNull(mounted, "Removal must use the actual parent");
				assertTrue(mounted.remove(args[0]), "Component must be mounted by its removing parent");
				removedComponents.add((UIComponentAPI) args[0]);
				yield null;
			}
			default -> null;
		});
		holder[0] = result;
		plugins.put(result, plugin);
		if (plugin != null) {
			plugin.positionChanged(position);
		}
		return result;
	}

	protected TooltipMakerAPI tooltip(float width, float height, boolean scrollable) {
		TooltipState state = new TooltipState(width, height, scrollable);
		TooltipMakerAPI tooltip = proxy(TooltipMakerAPI.class, state::call);
		tooltips.put(tooltip, state);
		return tooltip;
	}

	protected class TooltipState {
		private final PositionAPI position;
		private final PositionAPI viewport;
		private final boolean scrollable;
		private final float width;
		private final List<UIComponentAPI> components = new ArrayList<>();
		private final ScrollPanelAPI scroller;
		private float usedHeight;
		private float mountedHeight;
		private float heightAtMount;
		private float textWidth;
		private float offset;
		private boolean mounted;
		private UIComponentAPI previous;
		private String paraFont = "default";
		private String titleFont = "default-title";
		private Color paraColor = Color.WHITE;
		private Color titleColor = Color.WHITE;

		protected TooltipState(float width, float height, boolean scrollable) {
			this.width = width;
			this.position = position(deferTooltipWidth ? 0f : width, height);
			this.viewport = position(width, height);
			this.textWidth = Math.max(1f, width - 10f);
			this.scrollable = scrollable;
			this.scroller = proxy(ScrollPanelAPI.class, (method, args) -> switch (method) {
				case "getPosition" -> viewport;
				case "getYOffset" -> offset;
				case "setYOffset" -> {
					offset = Math.max(0f, Math.min((Float) args[0], Math.max(0f, mountedHeight - viewport.getHeight())));
					geometry.get(position).top = -offset;
					yield null;
				}
				default -> null;
			});
		}

		public PositionAPI getViewport() {
			return viewport;
		}

		public boolean isScrollable() {
			return scrollable;
		}

		public List<UIComponentAPI> getComponents() {
			return components;
		}

		public ScrollPanelAPI getScroller() {
			return scroller;
		}

		public float getUsedHeight() {
			return usedHeight;
		}

		public float getMountedHeight() {
			return mountedHeight;
		}

		public float getHeightAtMount() {
			return heightAtMount;
		}

		public boolean isMounted() {
			return mounted;
		}

		protected void mount(PositionAPI owner) {
			assertFalse(mounted, "Each tooltip must be mounted once");
			mountedHeight = usedHeight;
			heightAtMount = position.getHeight();
			float height = position.getHeight() == 0f ? usedHeight : position.getHeight();
			viewport.setSize(width, height);
			geometry.get(viewport).parent = owner;
			geometry.get(position).parent = viewport;
			position.setSize(width, Math.max(height, usedHeight));
			mounted = true;
		}

		protected UIComponentAPI append(UIComponentAPI component, float pad) {
			assertFalse(mounted, "Contents must be complete before mounting");
			geometry.get(component.getPosition()).parent = position;
			component.getPosition().inTL(5f, usedHeight + pad);
			usedHeight += pad + component.getPosition().getHeight();
			components.add(component);
			previous = component;
			return component;
		}

		protected Object call(String method, Object[] args) {
			return switch (method) {
				case "getExternalScroller" -> mounted && scrollable ? scroller : null;
				case "getPosition" -> position;
				case "setParaFontDefault" -> { paraFont = "default"; yield null; }
				case "setParaSmallInsignia" -> { paraFont = "small-insignia"; yield null; }
				case "setTitleSmallOrbitron" -> { titleFont = "small-orbitron"; yield null; }
				case "setTitleOrbitronLarge" -> { titleFont = "large-orbitron"; yield null; }
				case "setParaFontColor" -> { paraColor = (Color) args[0]; yield null; }
				case "setTitleFontColor" -> { titleColor = (Color) args[0]; yield null; }
				case "addTitle", "addPara", "addSectionHeading" -> {
					String value = (String) args[0];
					labels.add(value);
					float pad = args[args.length - 1] instanceof Float number ? number : 0f;
					LabelAPI label = label(value, textWidth);
					boolean title = method.equals("addTitle");
					labelFonts.put(label.getPosition(), title ? titleFont : paraFont);
					labelColors.put(label.getPosition(), title ? titleColor
							: args.length > 1 && args[1] instanceof Color color ? color : paraColor);
					append((UIComponentAPI) label, pad);
					yield label;
				}
				case "getPrev" -> previous;
				case "addImage" -> {
					PositionAPI image = position((Float) args[1], (Float) args[2]);
					UIComponentAPI component = proxy(UIComponentAPI.class,
							(name, values) -> "getPosition".equals(name) ? image : null);
					append(component, (Float) args[3]);
					imagePaths.put(component, (String) args[0]);
					yield null;
				}
				case "addTooltipToPrevious" -> {
					attachedTooltips.put(previous, (TooltipMakerAPI.TooltipCreator) args[0]);
					yield null;
				}
				case "addTooltipTo" -> {
					UIComponentAPI target = (UIComponentAPI) args[1];
					assertTrue(components.contains(target), "Tooltip target must belong to its binding host");
					attachedTooltips.put(target, (TooltipMakerAPI.TooltipCreator) args[0]);
					tooltipBindings.put(target, new TooltipBinding(this, (TooltipMakerAPI.TooltipLocation) args[2],
							args.length == 4 ? (Boolean) args[3] : true));
					yield null;
				}
				case "getHeightSoFar" -> usedHeight;
				case "getWidthSoFar" -> width;
				case "setHeightSoFar" -> {
					assertFalse(mounted);
					usedHeight = (Float) args[0];
					yield null;
				}
				case "setTextWidthOverride" -> {
					textWidth = (Float) args[0];
					yield null;
				}
				case "createRect" -> proxy(UIComponentAPI.class, (name, values) ->
						"getPosition".equals(name) ? position(1f, 1f) : null);
				case "addCustomDoNotSetPosition" -> {
					geometry.get(((UIComponentAPI) args[0]).getPosition()).parent = position;
					components.add((UIComponentAPI) args[0]);
					yield args[0];
				}
				case "addComponent" -> {
					PositionAPI child = ((UIComponentAPI) args[0]).getPosition();
					geometry.get(child).parent = position;
					components.add((UIComponentAPI) args[0]);
					yield child;
				}
				case "removeComponent" -> {
					assertTrue(components.remove(args[0]), "Component must be mounted by its removing tooltip");
					removedComponents.add((UIComponentAPI) args[0]);
					yield null;
				}
				case "shortenString" -> {
					String text = (String) args[0];
					int count = Math.max(0, (int) ((Float) args[1] / 7f));
					if (!shortenLabels || text.length() <= count) yield text;
					if (count < 3) yield ".".repeat(count);
					yield text.substring(0, count - 3) + "...";
				}
				case "addCustom" -> append((UIComponentAPI) args[0], (Float) args[1]);
				case "addSpacer" -> {
					PositionAPI spacer = position(1f, (Float) args[0]);
					yield append(proxy(UIComponentAPI.class, (name, values) -> "getPosition".equals(name) ? spacer : null), 0f);
				}
				case "addButton", "addCheckbox" -> {
					AtomicBoolean enabled = new AtomicBoolean(true);
					AtomicBoolean checked = new AtomicBoolean();
					boolean checkbox = "addCheckbox".equals(method);
					String id = String.valueOf(args[checkbox ? 3 : 1]);
					buttons.put(id, enabled);
					buttonLabels.put(id, (String) args[checkbox ? 2 : 0]);
					buttonIds.put(id, args[checkbox ? 3 : 1]);
					PositionAPI buttonPosition = position((Float) args[checkbox ? 0 : args.length - 3],
							(Float) args[checkbox ? 1 : args.length - 2]);
					buttonPositions.put(id, buttonPosition);
					ButtonAPI button = proxy(ButtonAPI.class, (name, values) -> {
						if ("setEnabled".equals(name)) {
							enabled.set((Boolean) values[0]);
						}
						if ("setChecked".equals(name)) {
							checked.set((Boolean) values[0]);
						}
						if ("isChecked".equals(name)) return checked.get();
						if ("isEnabled".equals(name)) return enabled.get();
						if ("setText".equals(name)) buttonLabels.put(id, (String) values[0]);
						if ("getText".equals(name)) return buttonLabels.get(id);
						if ("getPosition".equals(name)) return buttonPosition;
						return null;
					});
					yield append(button, (Float) args[args.length - 1]);
				}
				default -> null;
			};
		}
	}

	protected LabelAPI label(String text, float width) {
		float height = 0f;
		for (String line : text.split("\\n", -1)) {
			height += paragraphLineHeight * Math.max(1, Math.ceil(line.length() * 7f / Math.max(1f, width)));
		}
		PositionAPI position = position(width, height);
		String[] value = {text};
		return proxy(LabelAPI.class, (method, args) -> switch (method) {
			case "getPosition" -> position;
			case "setAlignment" -> {
				labelAlignments.put(position, (Alignment) args[0]);
				yield null;
			}
			case "computeTextWidth" -> ((String) args[0]).length() * 7f;
			case "computeTextHeight" -> position.getHeight();
			case "getText" -> value[0];
			case "setText" -> {
				value[0] = (String) args[0];
				yield null;
			}
			default -> null;
		});
	}

	protected PositionAPI position(float width, float height) {
		Bounds bounds = new Bounds(width, height);
		PositionAPI position = proxy(PositionAPI.class, bounds::call);
		geometry.put(position, bounds);
		return position;
	}

	protected static class Bounds {
		private PositionAPI parent;
		private float left;
		private float top;
		private float width;
		private float height;
		private boolean middleY;
		private boolean middleX;

		protected Bounds(float width, float height) {
			this.width = width;
			this.height = height;
		}

		public PositionAPI getParent() {
			return parent;
		}

		protected Object call(String method, Object[] args) {
			float x = left + (parent == null ? 0f : parent.getX()
					+ (middleX ? (parent.getWidth() - width) * 0.5f : 0f));
			float y = parent == null ? 0f - top : parent.getY()
					+ (middleY ? (parent.getHeight() - height) * 0.5f : parent.getHeight() - height - top);
			return switch (method) {
				case "getX" -> x;
				case "getY" -> y;
				case "getWidth" -> width;
				case "getHeight" -> height;
				case "getCenterX" -> x + width * 0.5f;
				case "getCenterY" -> y + height * 0.5f;
				case "setSize" -> {
					width = (Float) args[0];
					height = (Float) args[1];
					yield null;
				}
				case "inTL" -> {
					left = (Float) args[0];
					top = (Float) args[1];
					middleY = false;
					middleX = false;
					yield null;
				}
				case "inLMid" -> {
					left = (Float) args[0];
					middleY = true;
					middleX = false;
					yield null;
				}
				case "inMid" -> {
					left = 0f;
					middleX = true;
					middleY = true;
					yield null;
				}
				case "containsEvent" -> {
					InputEventAPI event = (InputEventAPI) args[0];
					yield event.getX() >= x && event.getX() < x + width
							&& event.getY() >= y && event.getY() < y + height;
				}
				default -> null;
			};
		}
	}

}
