package data.scripts.ui.page;

import com.fs.starfarer.api.ui.CustomPanelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.battlesnext.util.ui.UIRect;
import data.scripts.fleet.BattleNext_FleetModel;
import data.scripts.fleet.BattleNext_FleetStore;
import data.scripts.member.BattleNext_MemberModel;
import data.scripts.ui.BattleNext_PageLayout;
import data.scripts.ui.BattleNext_UiTheme;

import java.io.IOException;
import java.util.function.Consumer;

public class BattleNext_FleetLibraryPage implements BattleNext_PageView {

	private final Runnable backAction;
	private final Consumer<Object> editorAction;
	private final BattleNext_FleetStore store = new BattleNext_FleetStore();
	private final BattleNext_I18nSection strings = BattleNext_I18nSection.getInstance("Misc", "BattleNext_ui_");
	private CustomPanelAPI panel;
	private UIRect listRect;
	private UIRect detailRect;
	private BattleNext_FleetModel selected;
	private String status = "";

	public BattleNext_FleetLibraryPage(Runnable backAction, Consumer<Object> editorAction) {
		this.backAction = backAction;
		this.editorAction = editorAction;
	}

	@Override
	public void attach(CustomPanelAPI panel) {
		this.panel = panel;
		store.reload();
		selected = null;
		createContents();
	}

	@Override
	public void createContents() {
		// left list / right details / bottom back button
		UIRect root = new UIRect(0f, 0f, panel.getPosition().getWidth(), panel.getPosition().getHeight());
		UIRect[] header = root.splitVertically(BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] rest = header[1].splitVertically(BattleNext_PageLayout.CONTENT_TOP - BattleNext_PageLayout.HEADER_HEIGHT);
		UIRect[] bottom = rest[1].splitVerticallyReverse(BattleNext_PageLayout.CONTENT_BOTTOM);
		BattleNext_PageLayout.heading(header[0], panel, text("page_fleets"));

		float listWidth = BattleNext_PageLayout.listWidth(panel);
		UIRect[] columns = bottom[0].splitHorizontally(listWidth + 10f);
		listRect = columns[0];
		detailRect = columns[1];

		UIRect footerRect = bottom[1].shrink(0f, BattleNext_PageLayout.FOOTER_MARGIN, 0f, 0f);
		TooltipMakerAPI footer = BattleNext_PageLayout.footer(footerRect, panel);
		BattleNext_PageLayout.button(footer, text("back"), "back", 160f).getPosition().inBL(0f, 0f);

		refreshContent();
	}

	@Override
	public void refreshContent() {
		listRect.removeLatestSubPanel();
		CustomPanelAPI listPanel = listRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI listContent = listPanel.createUIElement(listRect.getWidth(), listRect.getHeight(), true);
		addList(listContent);
		listPanel.addUIElement(listContent).inTL(0f, 0f);

		detailRect.removeLatestSubPanel();
		CustomPanelAPI detailPanel = detailRect.createSubPanel(panel, null).getPanel();
		TooltipMakerAPI detailContent = detailPanel.createUIElement(detailRect.getWidth(), detailRect.getHeight(), true);
		addDetails(detailContent);
		detailPanel.addUIElement(detailContent).inTL(0f, 0f);
	}

	@Override
	public void buttonPressed(Object buttonId) {
		if (buttonId instanceof String && ((String) buttonId).startsWith("fleet:")) {
			selected = store.get(((String) buttonId).substring("fleet:".length()));
			refreshContent();
		} else if ("new".equals(buttonId) || "edit".equals(buttonId) || "clone".equals(buttonId)) {
			editorAction.accept(new DraftRequest((String)buttonId, selected));
		} else if ("delete".equals(buttonId) && selected != null) {
			try {
				store.delete(selected);
				selected = null;
				refreshContent();
			} catch (IOException exception) {
				status = text("save_failed") + ": " + exception.getMessage();
				refreshContent();
			}
		} else if ("back".equals(buttonId)) {
			if (selected != null) {
				selected = null;
				refreshContent();
			} else {
				backAction.run();
			}
		}
	}

	private void addList(TooltipMakerAPI listContent) {
		listContent.addTitle(text("page_fleets"));
		BattleNext_PageLayout.button(listContent, text("new_fleet"), "new", 180f);
		for (BattleNext_FleetModel fleet : store.getAll()) {
			String source = "built_in".equals(fleet.getSource()) ? text("read_only") : text("user");
			BattleNext_PageLayout.button(listContent, fleet.getName() + " [" + source + "]",
					"fleet:" + fleet.getId(), listContent.getPosition().getWidth() - 20f);
		}
	}

	private void addDetails(TooltipMakerAPI detailContent) {
		if (!status.isEmpty()) detailContent.addPara(status, 0f, BattleNext_UiTheme.MUTED);
		if (selected == null) {
			detailContent.addTitle(text("select_fleet"));
			return;
		}
		detailContent.addTitle(selected.getName());
		detailContent.addPara(selected.getDescription(), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("variant_count") + ": " + selected.getVariants().size(), 8f, BattleNext_UiTheme.MUTED);
		detailContent.addPara(text("fleet_section_members"), 10f, BattleNext_UiTheme.ACCENT);
		for (String error : selected.getLoadErrors()) {
			detailContent.addPara(error, 6f, BattleNext_UiTheme.MUTED);
		}
		for (BattleNext_MemberModel member : selected.getMembers()) {
			detailContent.addPara(member.getVariantId() + " / " + member.getName() + " / " + member.getPersonality(), 6f,
					BattleNext_UiTheme.TEXT);
		}
		if ("user".equals(selected.getSource())) {
			BattleNext_PageLayout.button(detailContent, text("edit_fleet"), "edit", 160f);
			BattleNext_PageLayout.button(detailContent, text("clone_fleet"), "clone", 160f);
			BattleNext_PageLayout.button(detailContent, text("delete"), "delete", 160f);
		} else {
			BattleNext_PageLayout.button(detailContent, text("clone_fleet"), "clone", 160f);
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
