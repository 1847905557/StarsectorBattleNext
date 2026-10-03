package data.missions;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.loading.HullModSpecAPI;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.mission.MissionDefinitionAPI;
import data.scripts.battlesnext.util.annotation.LifecycleEntry;
import data.scripts.tools.BattleNext_Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class BattleNext_MissionRestrictedUnlock {

	private static final BattleNext_MissionRestrictedUnlock INSTANCE = new BattleNext_MissionRestrictedUnlock();

	private final List<Set<String>> changedTags = new ArrayList<>();
	private boolean enabled;
	private Object preview;

	private BattleNext_MissionRestrictedUnlock() {
	}

	@LifecycleEntry
	public static BattleNext_MissionRestrictedUnlock getInstance() {
		return INSTANCE;
	}

	public void enable(MissionDefinitionAPI preview) {
		this.preview = preview;
		enable(Global.getSettings());
	}

	public void resume(Object currentPreview) {
		if (preview == null || preview != currentPreview) return;
		enable(Global.getSettings());
	}

	public void observe(Object currentPreview, boolean visible, boolean refitOpen) {
		if (!owns(currentPreview)) {
			forget();
			return;
		}
		if (visible || refitOpen) {
			resume(currentPreview);
		} else {
			restore();
		}
	}

	void enable(SettingsAPI settings) {

		if (enabled) return;
		enabled = true;
		try {
			for (WeaponSpecAPI weapon : settings.getAllWeaponSpecs()) {
				removeRestricted(weapon.getTags());
			}
			for (FighterWingSpecAPI wing : settings.getAllFighterWingSpecs()) {
				removeRestricted(wing.getTags());
			}
			for (HullModSpecAPI hullmod : settings.getAllHullModSpecs()) {
				removeRestricted(hullmod.getTags());
			}
		} catch (RuntimeException exception) {
			restore();
			throw exception;
		}
		BattleNext_Logger.info(BattleNext_MissionRestrictedUnlock.class, "restricted_assets.unlocked",
				BattleNext_Logger.fields().put("preview", preview).put("changed_count", changedTags.size()).build());
	}

	private void removeRestricted(Set<String> tags) {
		if (!tags.remove(Tags.RESTRICTED)) return;
		changedTags.add(tags);
	}

	public void restore() {

		if (!enabled) return;
		for (Set<String> tags : changedTags) {
			tags.add(Tags.RESTRICTED);
		}
		changedTags.clear();
		enabled = false;
		BattleNext_Logger.info(BattleNext_MissionRestrictedUnlock.class, "restricted_assets.restored",
				BattleNext_Logger.fields().put("preview", preview).build());
	}

	public void forget() {
		restore();
		preview = null;
	}

	public boolean owns(Object currentPreview) {
		return preview != null && preview == currentPreview;
	}

	public boolean hasPreview() {
		return preview != null;
	}

	public boolean isEnabled() {
		return enabled;
	}
}
