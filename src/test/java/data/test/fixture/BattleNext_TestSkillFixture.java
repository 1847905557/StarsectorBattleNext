package data.test.fixture;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.characters.*;
import com.fs.starfarer.api.characters.SkillSpecAPI.SkillEffectSpecAPI;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import data.scripts.ui.BattleNext_UiTheme;
import data.scripts.ui.widget.BattleNext_OptionPicker.OptionChoice;
import data.scripts.ui.widget.provider.BattleNext_CommanderSkillSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_OfficerSkillSourceProvider;
import data.scripts.ui.widget.provider.BattleNext_SkillSourceProvider;
import org.junit.jupiter.api.BeforeEach;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BattleNext_TestSkillFixture extends BattleNext_TestUiFixture {

	protected static final String SKILL_ID = "preview_skill";
	protected String description = "First paragraph 50%.\nSecond paragraph";
	protected String author = "Source author 20%";
	protected String scope = "Ship";
	protected String scope2 = "Fleet";
	protected final List<SkillEffectSpecAPI> effects = new ArrayList<>();
	protected final List<PreviewStats> previews = new ArrayList<>();
	protected SkillSpecAPI skill;

	@BeforeEach
	protected void configureSkill() {

		skill = proxy(SkillSpecAPI.class, (method, args) -> switch (method) {
			case "getId" -> SKILL_ID;
			case "getName" -> "Preview Skill";
			case "getDescription" -> description;
			case "getAuthor" -> author;
			case "getScopeStr" -> scope;
			case "getScopeStr2" -> scope2;
			case "getEffectsAPI" -> effects;
			case "getSpriteName" -> "graphics/skill.png";
			case "isCombatOfficerSkill" -> true;
			default -> null;
		});
		SettingsAPI previous = Global.getSettings();
		Global.setSettings((SettingsAPI) Proxy.newProxyInstance(SettingsAPI.class.getClassLoader(), new Class<?>[]{SettingsAPI.class},
				(instance, method, args) -> {
			return switch (method.getName()) {
				case "getSkillSpec" -> args[0].equals(SKILL_ID) ? skill : null;
				case "getSortedSkillIds" -> List.of(SKILL_ID);
				case "getCurrentState" -> GameState.TITLE;
				case "getString" -> BattleNext_TestTextCatalog.lookup(args[0], args[1]);
				case "createPerson" -> {
					PreviewStats stats = new PreviewStats();
					previews.add(stats);
					yield proxy(PersonAPI.class, (name, values) -> name.equals("getStats") ? stats.getApi() : null);
				}
				default -> {
					try {
						yield method.invoke(previous, args);
					} catch (InvocationTargetException exception) {
						throw exception.getCause();
					}
				}
			};
		}));
	}

	protected SkillEffectSpecAPI effect(SkillEffectType type, float required, String group, boolean levelBased, LevelBasedEffect script) {
		return proxy(SkillEffectSpecAPI.class, (method, args) -> switch (method) {
			case "getType" -> type;
			case "getRequiredSkillLevel" -> required;
			case "getName" -> group;
			case "isLevelBased" -> levelBased;
			case "getAsLevelBasedEffect" -> script;
			case "getEffectClass" -> script == null ? "ExcludedEffect" : script.getClass().getName();
			default -> null;
		});
	}

	protected LevelBasedEffect textEffect(String text, List<Float> receivedLevels) {
		return proxy(LevelBasedEffect.class, (method, args) -> {
			if (!method.equals("getEffectDescription")) return null;
			receivedLevels.add((Float) args[0]);
			return text;
		});
	}

	protected BattleNext_SkillSourceProvider provider(String kind) {
		return kind.equals("commander") ? new BattleNext_CommanderSkillSourceProvider() : new BattleNext_OfficerSkillSourceProvider();
	}

	protected TooltipMakerAPI renderSkill(BattleNext_SkillSourceProvider source) {
		TooltipMakerAPI tooltip = tooltip(source.getTooltipWidth(), 0f, false);
		BattleNext_UiTheme.configure(tooltip);
		source.createTooltip(tooltip, new OptionChoice(SKILL_ID, skill.getName()));
		return tooltip;
	}

	protected List<LabelAPI> textLabels(TooltipMakerAPI tooltip) {
		return tooltips.get(tooltip).getComponents().stream().filter(LabelAPI.class::isInstance).map(LabelAPI.class::cast).toList();
	}

	protected List<String> textValues(TooltipMakerAPI tooltip) {
		return textLabels(tooltip).stream().map(LabelAPI::getText).toList();
	}

	public class PreviewStats {
		private final Map<String, Float> levels = new LinkedHashMap<>();
		private final List<String> calls = new ArrayList<>();
		private boolean skip;
		private final MutableCharacterStatsAPI api = proxy(MutableCharacterStatsAPI.class, (method, args) -> switch (method) {
			case "getSkillLevel" -> levels.getOrDefault(args[0], 0f);
			case "isSkipRefresh" -> skip;
			case "setSkipRefresh" -> {
				skip = (Boolean) args[0];
				calls.add("skip:" + skip);
				yield null;
			}
			case "setSkillLevel" -> {
				if (!skip) throw new AssertionError("Preview levels require suspended effect application");
				levels.put((String) args[0], (Float) args[1]);
				calls.add(args[0] + ":" + args[1]);
				yield null;
			}
			case "refreshCharacterStatsEffects", "setFleet" -> throw new AssertionError("Preview owns only description state");
			default -> null;
		});

		public MutableCharacterStatsAPI getApi() {
			return api;
		}

		public List<String> getCalls() {
			return calls;
		}
	}
}
