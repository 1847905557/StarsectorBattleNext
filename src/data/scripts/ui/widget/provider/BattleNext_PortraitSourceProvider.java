package data.scripts.ui.widget.provider;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionSpecAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.tools.BattleNext_ModFileManager;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;

import java.io.IOException;
import java.util.*;

public class BattleNext_PortraitSourceProvider implements BattleNext_OptionPicker.OptionChoiceSource {

	private static final String MOD_PORTRAIT_ROOT = "graphics/portraits";

	@Override
	public int getMaxCount(String id) {
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
				Global.getSettings().loadTexture(path);
				result.add(new OptionChoice(path, fileName(path)));
			}
		} catch (IOException exception) {
			Global.getLogger(BattleNext_PortraitSourceProvider.class).warn(
					"Mod portraits are unavailable: " + exception.getMessage());
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
	public void createTooltip(TooltipMakerAPI tooltip, OptionChoice option) {
		tooltip.addTitle(option.getLabel());
		tooltip.addPara(option.getId(), 5f, BattleNext_UiTheme.MUTED);
	}
}
