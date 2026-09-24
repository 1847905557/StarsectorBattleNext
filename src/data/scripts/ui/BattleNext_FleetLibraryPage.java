package data.scripts.ui;

import com.fs.starfarer.api.input.InputEventAPI;
import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.battlesnext.util.BattleNext_I18nSection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage {

	private final Runnable backAction;
	private final Consumer<Object> editorAction;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "");
	private CustomPanelAPI panel;
	private TooltipMakerAPI heading;
	private TooltipMakerAPI listContent;
	private TooltipMakerAPI detailContent;
	private TooltipMakerAPI footer;
	private BattleNext_FleetModel selected;
	private String status = "";

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Object> editorAction) {
		this.backAction = backAction;
		this.editorAction = editorAction;
	}

	public void attach(CustomPanelAPI panel) {
		if (this.panel != panel) {
			heading = null;
			listContent = null;
			detailContent = null;
			footer = null;
		}
		this.panel = panel;
		store.reload();
		selected = null;
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
		} else if ("new".equals(buttonId) || "edit".equals(buttonId) || "clone".equals(buttonId)) {
			editorAction.accept(new DraftRequest((String)buttonId, selected));
		} else if ("delete".equals(buttonId) && selected != null) {
			try {
				store.delete(selected);
				selected = null;
				rebuild();
			} catch (IOException exception) {
				status = text("BattleNext_ui_save_failed") + ": " + exception.getMessage();
				rebuild();
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
		if (heading != null) panel.removeComponent(heading);
		if (listContent != null) panel.removeComponent(listContent);
		if (detailContent != null) panel.removeComponent(detailContent);
		if (footer != null) panel.removeComponent(footer);

		heading = BattleNext_PageLayout.heading(panel, text("BattleNext_ui_page_fleets"));
		float listWidth = BattleNext_PageLayout.listWidth(panel);
		listContent = BattleNext_PageLayout.column(panel, 0f, listWidth);
		detailContent = BattleNext_PageLayout.column(panel, listWidth + 10f,
				panel.getPosition().getWidth() - listWidth - 10f);
		footer = BattleNext_PageLayout.footer(panel);
		BattleNext_PageLayout.button(footer, text("BattleNext_ui_back"), "back", 160f)
				.getPosition().inBL(0f, 0f);
		addList();
		addDetails();
	}

	private void addList() {
		listContent.addTitle(text("BattleNext_ui_page_fleets"));
		BattleNext_PageLayout.button(listContent, text("BattleNext_ui_new_fleet"), "new", 180f);
		for (BattleNext_FleetModel fleet : store.getAll()) {
			String source = "built_in".equals(fleet.getSource()) ? text("BattleNext_ui_read_only") : text("BattleNext_ui_user");
			BattleNext_PageLayout.button(listContent, fleet.getName() + " [" + source + "]",
					"fleet:" + fleet.getId(), listContent.getPosition().getWidth() - 20f);
		}
	}

	private void addDetails() {
		if (!status.isEmpty()) detailContent.addPara(status, 0f, BattleNext_UiTheme.MUTED);
		if (selected == null) {
			detailContent.addTitle(text("BattleNext_ui_select_fleet"));
			return;
		}
		detailContent.addTitle(selected.getName());
		detailContent.addPara(selected.getDescription(), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("BattleNext_ui_variant_count") + ": " + selected.getVariants().size(), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("BattleNext_ui_fleet_section_members"), 10f, BattleNext_UiTheme.ACCENT);
		for (String error : selected.getLoadErrors()) {
			detailContent.addPara(error, 6f, BattleNext_UiTheme.MUTED);
		}
		for (BattleNext_MemberModel member : selected.getMembers()) {
			detailContent.addPara(member.getVariantId() + " / " + member.getName() + " / " + member.getPersonality(), 6f,
					BattleNext_UiTheme.TEXT);
		}
		if ("user".equals(selected.getSource())) {
			BattleNext_PageLayout.button(detailContent, text("BattleNext_ui_edit_fleet"), "edit", 160f);
			BattleNext_PageLayout.button(detailContent, text("BattleNext_ui_clone_fleet"), "clone", 160f);
			BattleNext_PageLayout.button(detailContent, text("BattleNext_ui_delete"), "delete", 160f);
		} else {
			BattleNext_PageLayout.button(detailContent, text("BattleNext_ui_clone_fleet"), "clone", 160f);
		}
	}

	private String text(String key) {
		return strings.get(key);
	}

	public static final class DraftRequest {

		private final String action;
		private final BattleNext_FleetModel fleet;

		public DraftRequest(String action, BattleNext_FleetModel fleet) {
			this.action = action;
			this.fleet = fleet;
		}

		public String getAction() {
			return action;
		}

		public BattleNext_FleetModel getFleet() {
			return fleet;
		}
	}
}
