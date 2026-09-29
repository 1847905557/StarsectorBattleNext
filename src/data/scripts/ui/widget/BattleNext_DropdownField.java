package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.campaign.CustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.util.ArrayList;
import java.util.List;

public final class BattleNext_DropdownField {

	private static final float POPUP_ROW_GAP = 2f;
	private static final float POPUP_SCREEN_MARGIN = 8f;
	private static final float POPUP_MAX_HEIGHT = 240f;
	private static final float POPUP_OPTION_HEIGHT = 35f;
	private static final float POPUP_SCROLLBAR_RESERVE = 5f;
	private static final float POPUP_BUTTON_WIDTH_RESERVE = 15f;

	public static final class DropdownChoice {

		private final String id;
		private final String label;
		private final boolean enabled;
		private final String disabledReason;

		public DropdownChoice(String id, String label) {
			this(id, label, true, "");
		}

		public DropdownChoice(String id, String label, boolean enabled, String disabledReason) {
			this.id = id == null ? "" : id;
			this.label = label == null ? "" : label;
			this.enabled = enabled;
			this.disabledReason = disabledReason == null ? "" : disabledReason;
		}
	}

	public interface DropdownSelectionListener {
		void onSelected(String id);
	}

	private enum DropdownAction { TOGGLE }

	private final CustomPanelAPI host;
	private final CustomUIPanelPlugin inputOwner;
	private final float width;
	private final DropdownSelectionListener listener;
	private final List<DropdownChoice> options = new ArrayList<>();
	private UIRect rect;
	private CustomPanelAPI row;
	private ButtonAPI button;
	private CustomPanelAPI popup;
	private String value = "";
	private boolean enabled = true;

	public BattleNext_DropdownField(CustomPanelAPI host, CustomUIPanelPlugin inputOwner,
			float width, DropdownSelectionListener listener) {
		this.host = host;
		this.inputOwner = inputOwner;
		this.width = width;
		this.listener = listener;
	}

	public void attach(TooltipMakerAPI parent, UIRect rect) {
		if (row != null) host.removeComponent(row);
		this.rect = rect;
		row = host.createCustomPanel(rect.getWidth(), rect.getHeight(), new DropdownButtonPlugin());
		button = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, rect.getWidth(), rect.getHeight()), "", DropdownAction.TOGGLE, CutStyle.ALL, false);
		host.addComponent(row).inTL(rect.getX(), rect.getY());
		refreshLabel();
	}

	public void setValue(String id) {
		value = id == null ? "" : id;
		refreshLabel();
	}

	public String getValue() {
		return value;
	}

	public void setOptions(List<DropdownChoice> values) {
		options.clear();
		if (values != null) options.addAll(values);
		if (popup != null) openPopup();
		refreshLabel();
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
		if (button != null) button.setEnabled(enabled);
		if (!enabled) close();
	}

	public void refreshLabel() {
		if (button == null) return;
		String label = value;
		for (DropdownChoice option : options) {
			if (option.id.equals(value)) {
				label = option.label;
				break;
			}
		}
		button.setText(label);
	}

	public void close() {
		if (popup != null) host.removeComponent(popup);
		popup = null;
	}

	private final class DropdownButtonPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId != DropdownAction.TOGGLE) return;
			if (popup == null) openPopup();
			else close();
		}
	}

	public boolean isOpen() {
		return popup != null;
	}

	public boolean handleGlobalInput(List<InputEventAPI> events) {
		if (popup == null) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent()
					&& event.getEventValue() == org.lwjgl.input.Keyboard.KEY_ESCAPE) {
				close();
				event.consume();
				return true;
			}
			if (event.isMouseDownEvent()
					&& !contains(popup, event.getX(), event.getY())
					&& !contains(button, event.getX(), event.getY())) {
				close();
			}
		}
		return false;
	}

	private boolean contains(UIComponentAPI component, float x, float y) {
		if (component == null) return false;
		PositionAPI position = component.getPosition();
		return x >= position.getX() && x < position.getX() + position.getWidth()
				&& y >= position.getY() && y < position.getY() + position.getHeight();
	}

	private void openPopup() {
		close();
		if (!enabled || rect == null) return;
		float hostHeight = host.getPosition().getHeight();
		float dropdownY = rect.getY() + rect.getHeight() + POPUP_ROW_GAP;
		float belowSpace = hostHeight - dropdownY - POPUP_SCREEN_MARGIN;
		float aboveSpace = rect.getY() - POPUP_SCREEN_MARGIN;
		int optionCount = Math.max(1, options.size());
		float desiredHeight = Math.min(POPUP_MAX_HEIGHT, optionCount * POPUP_OPTION_HEIGHT);
		boolean above = belowSpace < desiredHeight && aboveSpace > belowSpace;
		float available = Math.max(POPUP_OPTION_HEIGHT, above ? aboveSpace : belowSpace);
		float height = Math.min(desiredHeight, available);
		float y = above ? rect.getY() - height - POPUP_ROW_GAP : dropdownY;
		popup = host.createCustomPanel(width, height, new DropdownPopupPlugin());
		TooltipMakerAPI list = popup.createUIElement(width - POPUP_SCROLLBAR_RESERVE, height, true);
		list.setButtonFontOrbitron20();
		if (options.isEmpty()) {
			list.addPara("", 0f);
		} else {
			for (DropdownChoice option : options) {
				ButtonAPI item = BattleNext_PageLayout.button(list, option.label, option,
						width - POPUP_BUTTON_WIDTH_RESERVE);
				item.setEnabled(option.enabled);
			}
		}
		popup.addUIElement(list).inTL(0f, 0f);
		host.addComponent(popup).inTL(rect.getX(), Math.max(POPUP_SCREEN_MARGIN, y));
	}

	private final class DropdownPopupPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			if (!(buttonId instanceof DropdownChoice)) return;
			DropdownChoice option = (DropdownChoice) buttonId;
			if (!option.enabled) return;
			value = option.id;
			close();
			refreshLabel();
			if (listener != null) listener.onSelected(value);
		}
	}
}
