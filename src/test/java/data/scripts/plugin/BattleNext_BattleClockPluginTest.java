package data.scripts.plugin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

class BattleNext_BattleClockPluginTest {

	@Test
	void timerAndTimeoutGraphicShareCompactTopCenter() {
		assertEquals(24f, BattleNext_BattleClockPlugin.TIMER_FONT_SIZE);
		assertEquals(220f, BattleNext_BattleClockPlugin.SPRITE_WIDTH);
		assertEquals(110f, BattleNext_BattleClockPlugin.SPRITE_HEIGHT);

		Vector2f fullHd = BattleNext_BattleClockPlugin.clockCenter(1920f, 1080f);
		assertEquals(960f, fullHd.x);
		assertEquals(1017f, fullHd.y);
		assertEquals(8f, 1080f - (fullHd.y + BattleNext_BattleClockPlugin.SPRITE_HEIGHT * 0.5f));

		Vector2f smaller = BattleNext_BattleClockPlugin.clockCenter(1280f, 720f);
		assertEquals(640f, smaller.x);
		assertEquals(657f, smaller.y);
		assertEquals(8f, 720f - (smaller.y + BattleNext_BattleClockPlugin.SPRITE_HEIGHT * 0.5f));
	}
}
