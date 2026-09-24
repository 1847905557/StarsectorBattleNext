package data.scripts.ui;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.member.BattleNext_MemberModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class BattleNext_FleetLibraryPage {

	private final Runnable backAction;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private CustomPanelAPI panel;
	private TooltipMakerAPI content;
	private BattleNext_FleetModel selected;

	public BattleNext_FleetLibraryPage(Runnable backAction) {
		this.backAction = backAction;
	}

	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		store.reload();
		rebuild();
	}

	public void processInput(List<InputEventAPI> events) {
	}

	public void advance() {
	}

	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("fleet:")) {
			selected = store.get(((String) buttonId).substring("fleet:".length()));
			rebuild();
		} else if ("delete".equals(buttonId) && selected != null) {
			try {
				store.delete(selected);
				selected = null;
				rebuild();
			} catch (IOException exception) {
				content.addPara(text("BattleNext_ui_save_failed"), 8f, BattleNext_UiTheme.MUTED);
			}
		} else if ("back".equals(buttonId)) {
			if (selected != null) {
				selected = null;
				rebuild();
			} else {
				backAction.run();
			}
		}
	}

	private void rebuild() {
		if (content != null) panel.removeComponent(content);
		content = panel.createUIElement(720f, 480f, false);
		BattleNext_UiTheme.configure(content);
		content.addTitle(text("BattleNext_ui_page_fleets"));
		if (selected == null) {
			addList();
		} else {
			addDetails();
		}
		content.addButton(text("BattleNext_ui_back"), "back", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 160f, 30f, 8f);
		panel.addUIElement(content).inMid();
	}

	private void addList() {
		for (BattleNext_FleetModel fleet : store.getAll()) {
			String source = "built_in".equals(fleet.getSource()) ? text("BattleNext_ui_read_only") : text("BattleNext_ui_user");
			content.addButton(fleet.getName() + " [" + source + "]", "fleet:" + fleet.getId(), BattleNext_UiTheme.ACCENT,
					BattleNext_UiTheme.PANEL, 420f, 32f, 6f);
		}
	}

	private void addDetails() {
		content.addPara(selected.getName(), 8f, BattleNext_UiTheme.TEXT);
		content.addPara(selected.getDescription(), 8f, BattleNext_UiTheme.MUTED);
		content.addPara(text("BattleNext_ui_variant_count") + ": " + selected.getVariants().size(), 8f, BattleNext_UiTheme.MUTED);
		for (String error : selected.getLoadErrors()) {
			content.addPara(error, 6f, BattleNext_UiTheme.MUTED);
		}
		for (BattleNext_MemberModel member : selected.getMembers()) {
			content.addPara(member.getVariantId() + " / " + member.getName() + " / " + member.getPersonality(), 6f,
					BattleNext_UiTheme.TEXT);
		}
		if ("user".equals(selected.getSource())) {
			content.addButton(text("BattleNext_ui_delete"), "delete", BattleNext_UiTheme.ACCENT, BattleNext_UiTheme.PANEL, 160f, 30f, 6f);
		}
	}

	private String text(String key) {
		try {
			return Global.getSettings().getString("AI_Battles_Next", key);
		} catch (RuntimeException exception) {
			return key;
		}
	}
}
