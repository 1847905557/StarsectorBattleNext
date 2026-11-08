package data.scripts.ui.widget.provider;

import data.model.pinnedvariant.BattleNext_PinnedVariantStore;

public class BattleNext_FleetMemberProvider extends BattleNext_VariantSourceProvider {

	public BattleNext_FleetMemberProvider() {

	}

	public BattleNext_FleetMemberProvider(BattleNext_PinnedVariantStore pins) {
		super(pins);
	}

	public BattleNext_FleetMemberProvider(BattleNext_VariantCatalog catalog) {
		super(catalog);
	}

	@Override
	public int getMaxStack(String id) {
		return 64;
	}
}
