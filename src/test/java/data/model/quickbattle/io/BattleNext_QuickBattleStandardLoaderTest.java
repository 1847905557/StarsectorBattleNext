package data.model.quickbattle.io;

import data.model.quickbattle.io.BattleNext_QuickBattleStandardLoader;
import data.test.fixture.BattleNext_TestGameFixture;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BattleNext_QuickBattleStandardLoaderTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@ValueSource(strings = {"LF", "CRLF", "CR"})
	void lineEndingsKeepInteriorEmptyRowsAndStableIdentities(String ending) throws Exception {
		String separator = ending.equals("CRLF") ? "\r\n" : ending.equals("CR") ? "\r" : "\n";
		String csv = String.join(separator, "player,enemy,map,size", "a,b,1,1", "", "c,d,2,2", "");
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> {
			assertEquals("loadText", method);
			assertEquals("tournament/quick_battle.csv", args[0]);
			assertEquals("AI_Battles_Next", args[1]);
			return csv;
		}));
		var rows = new BattleNext_QuickBattleStandardLoader().load();
		assertEquals(List.of("quick-standard-0", "quick-standard-1", "quick-standard-2"), rows.stream().map(row -> row.getId()).toList());
		assertEquals("#", rows.get(1).toSourceLine());
		assertEquals("c", rows.get(2).getPlayer());
	}

	@Test
	void requiredStandardFileReadFailurePropagates() {
		Global.setSettings(proxy(SettingsAPI.class, (method, args) -> {
			throw new IOException("missing standard CSV");
		}));
		IOException failure = assertThrows(IOException.class, () -> new BattleNext_QuickBattleStandardLoader().load());
		assertEquals("missing standard CSV", failure.getMessage());
	}
}
