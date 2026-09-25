package data.scripts.battle.io;

import data.scripts.battle.BattleNext_Match;

import java.util.List;

public record BattleNext_QuickBattleSnapshot(List<BattleNext_Match> matches, List<String> comments) {
}
