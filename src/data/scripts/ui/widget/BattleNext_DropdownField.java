package data.scripts.ui.widget;

import com.fs.starfarer.api.campaign.BaseCustomUIPanelPlugin;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.*;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiInputRouter;
import org.lwjgl.input.Keyboard;

import java.util.ArrayList;
import java.util.List;

public class BattleNext_DropdownField extends BattleNext_PanelBasedWidget {

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

	private final CustomPanelAPI overlayHost;
	private final DropdownSelectionListener listener;
	private final List<DropdownChoice> options = new ArrayList<>();

	private CustomPanelAPI row;
	private ButtonAPI button;
	private CustomPanelAPI popup;
	private PopupOverlay popupOverlay;
	private String value = "";
	private boolean enabled = true;

	@Override
	protected void buildContent(CustomPanelAPI panel) {
		float width = panel.getPosition().getWidth();
		float height = panel.getPosition().getHeight();
		row = ownPanel(panel, panel.createCustomPanel(width, height, new DropdownButtonPlugin()));
		button = BattleNext_PageLayout.fixedButton(row,
				new UIRect(0f, 0f, width, height), "", DropdownAction.TOGGLE, CutStyle.ALL, false);
		panel.addComponent(row).inTL(0f, 0f);
		refreshLabel();
	}

	public BattleNext_DropdownField(BattleNext_UiInputRouter inputRouter,
			CustomPanelAPI overlayHost, DropdownSelectionListener listener) {
		super(inputRouter);
		this.overlayHost = overlayHost;
		this.listener = listener;
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
		if (!enabled) closePopup();
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

	@Override
	protected void clearOverlays() {
		closePopup();
	}

	@Override
	protected void clearContent() {
		closePopup();
		row = null;
		button = null;
	}

	private void closePopup() {
		if (popup != null) releasePanel(popup);
		if (popupOverlay != null) inputRouter.pop(popupOverlay);
		popup = null;
		popupOverlay = null;
	}

	private final class DropdownButtonPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			if (buttonId != DropdownAction.TOGGLE) return;
			if (popup == null) openPopup();
			else closePopup();
		}
	}

	public boolean isOpen() {
		return popup != null;
	}

	@Override
	protected boolean handleWidgetInput(List<InputEventAPI> events) {
		if (popup == null || !inputRouter.isTop(popupOverlay)) return false;
		for (InputEventAPI event : events) {
			if (event.isConsumed()) continue;
			if (event.isKeyboardEvent() && event.isKeyDownEvent()
					&& event.getEventValue() == Keyboard.KEY_ESCAPE) {
				closePopup();
				event.consume();
				return true;
			}
			if (event.isMouseDownEvent()
					&& !contains(popup, event.getX(), event.getY())
					&& !contains(button, event.getX(), event.getY())) {
				closePopup();
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
		closePopup();
		if (!enabled || row == null) return;
		inputRouter.releaseFocus();
		float hostHeight = overlayHost.getPosition().getHeight();
		PositionAPI rowPosition = row.getPosition();
		float width = rowPosition.getWidth();
		PositionAPI ownerPosition = overlayHost.getPosition();
		float rowTop = ownerPosition.getY() + hostHeight - rowPosition.getY() - rowPosition.getHeight();
		float rowX = rowPosition.getX() - ownerPosition.getX();
		float dropdownY = rowTop + rowPosition.getHeight() + POPUP_ROW_GAP;
		float belowSpace = hostHeight - dropdownY - POPUP_SCREEN_MARGIN;
		float aboveSpace = rowTop - POPUP_SCREEN_MARGIN;
		int optionCount = Math.max(1, options.size());
		float desiredHeight = Math.min(POPUP_MAX_HEIGHT, optionCount * POPUP_OPTION_HEIGHT);
		boolean above = belowSpace < desiredHeight && aboveSpace > belowSpace;
		float available = Math.max(POPUP_OPTION_HEIGHT, above ? aboveSpace : belowSpace);
		float height = Math.min(desiredHeight, available);
		float y = above ? rowTop - height - POPUP_ROW_GAP : dropdownY;
		popup = ownPanel(overlayHost, panel.createCustomPanel(width, height, new DropdownPopupPlugin()));
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
		overlayHost.addComponent(popup).inTL(rowX, Math.max(POPUP_SCREEN_MARGIN, y));
		overlayHost.bringComponentToTop(popup);
		popupOverlay = new PopupOverlay(popup);
		inputRouter.push(popupOverlay);
	}

	private final class PopupOverlay implements BattleNext_UiInputRouter.Overlay {
		private final CustomPanelAPI panel;

		private PopupOverlay(CustomPanelAPI panel) {
			this.panel = panel;
		}

		@Override
		public PositionAPI getBounds() {
			return panel.getPosition();
		}

		@Override
		public boolean handleGlobalInput(List<InputEventAPI> events) {
			return BattleNext_DropdownField.this.handleWidgetInput(events);
		}

		@Override
		public void close() {
			if (popup != null) BattleNext_DropdownField.this.closePopup();
		}
	}

	private final class DropdownPopupPlugin extends BaseCustomUIPanelPlugin {
		@Override
		public void buttonPressed(Object buttonId) {
			if (!(buttonId instanceof DropdownChoice option)) return;
			if (!option.enabled) return;
			value = option.id;
			inputRouter.releaseFocus();
			closePopup();
			refreshLabel();
			if (listener != null) listener.onSelected(value);
		}
	}
}
