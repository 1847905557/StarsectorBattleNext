package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.loading.Description;
import com.fs.starfarer.api.loading.Description.Type;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.battlesnext.util.BattleNext_I18nSection;
import data.scripts.tools.BattleNext_Logger;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class BattleNext_PortraitSourceProvider extends BattleNext_BaseSourceProvider {

	private static final String MOD_PORTRAIT_ROOT = "graphics/portraits";
	private static final float TOOLTIP_WIDTH = 520f;
	private final BattleNext_I18nSection memberStrings = BattleNext_I18nSection.getInstance("Member", "BattleNext_member_");

	@Override
	public int getMaxStack(String id) {
		return 1;
	}

	@Override
	public List<OptionChoice> getOptions() {

		List<OptionChoice> options = new ArrayList<>(selfPortraits());
		Set<String> loaded = new LinkedHashSet<>();

		List<FactionSpecAPI> specs = Global.getSettings().getAllFactionSpecs();
		FactionSpecAPI player = Global.getSettings().getFactionSpec(Factions.PLAYER);
		specs.remove(player);
		specs.add(player);

		for (FactionSpecAPI spec : specs) {

			for (String path : spec.getAllPortraits(Gender.MALE)) {
				if (!loaded.contains(path)) {
					loaded.add(path);
					options.add(new OptionChoice(path, spec.getDisplayName()));
				}
			}

			for (String path : spec.getAllPortraits(Gender.FEMALE)) {
				if (!loaded.contains(path)) {
					loaded.add(path);
					options.add(new OptionChoice(path, spec.getDisplayName()));
				}
			}

			for (String path : spec.getAllPortraits(Gender.ANY)) {
				if (!loaded.contains(path)) {
					loaded.add(path);
					options.add(new OptionChoice(path, spec.getDisplayName()));
				}
			}
		}

		return options;
	}

	private List<OptionChoice> selfPortraits() {
		List<OptionChoice> result = new ArrayList<>();
		try {
			for (String path : BattleNext_ModFileManager.list(MOD_PORTRAIT_ROOT, true)) {
				if (!isPortraitFile(path)) continue;
				BattleNext_Logger.operationStarted(BattleNext_PortraitSourceProvider.class, "texture.load",
						BattleNext_Logger.fields().put("path", path).put("root", MOD_PORTRAIT_ROOT).build());
				try {
					Global.getSettings().loadTexture(path);
				} catch (RuntimeException exception) {
					BattleNext_Logger.operationFailed(BattleNext_PortraitSourceProvider.class, "texture.load",
							BattleNext_Logger.fields().put("path", path).put("root", MOD_PORTRAIT_ROOT).build(), exception);
					throw exception;
				}
				BattleNext_Logger.operationSucceeded(BattleNext_PortraitSourceProvider.class, "texture.load",
						BattleNext_Logger.fields().put("path", path).build());
				result.add(new OptionChoice(path, fileName(path)));
			}
		} catch (IOException exception) {
			BattleNext_Logger.warn(BattleNext_PortraitSourceProvider.class, "portraits.load.degraded",
				BattleNext_Logger.fields().put("root", MOD_PORTRAIT_ROOT).put("loaded", result.stream().map(OptionChoice::getId).toList()).build(), exception);
		}
		return result;
	}

	private boolean isPortraitFile(String path) {
		String normalized = path.toLowerCase();
		return normalized.endsWith(".png") || normalized.endsWith(".jpg") || normalized.endsWith(".jpeg");
	}

	private String fileName(String path) {
		String name = path.substring(path.lastIndexOf('/') + 1);
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	@Override
	public String getIconPath(String id) {
		return id;
	}

	@Override
	public float getTooltipWidth() {
		return TOOLTIP_WIDTH;
	}

	@Override
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		addHeader(tooltip, option.getLabel(), option.getId());
		for (FactionSpecAPI spec : Global.getSettings().getAllFactionSpecs()) {
			if (!option.getLabel().equals(spec.getDisplayName())) continue;
			if (!spec.getAllPortraits(Gender.MALE).contains(option.getId())
					&& !spec.getAllPortraits(Gender.FEMALE).contains(option.getId())
					&& !spec.getAllPortraits(Gender.ANY).contains(option.getId())) continue;
			Description description = Global.getSettings().getDescription(spec.getId(), Type.FACTION);
			if (description != null) addDescription(tooltip, description.getText1());
			return;
		}
		addDescription(tooltip, memberStrings.get("custom_portrait"));
	}
}
