package data.scripts.campaign.simulation;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.fleet.FleetMemberType;
import com.fs.starfarer.api.impl.SimulatorPluginImpl;
import com.fs.starfarer.api.plugins.SimulatorPlugin.SimCategoryData;
import com.fs.starfarer.api.util.Misc;
import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import org.boxutil.backends.reflect.BUtil_RefMethod;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class BattleNext_SimulationInjection {

	static final String CATEGORY_ID = "BattleNext_pinned_variants";
	private final Object screen;
	private final BattleNext_PinnedVariantStore store = new BattleNext_PinnedVariantStore();
	private final String categoryName;
	private Object picker;
	private Object table;
	private Object originalRenderer;
	private Object renderer;
	private Object options;
	private Object groupButton;
	private Object originalGroup;
	private Object groupListener;
	private Object resetButton;
	private Object originalReset;
	private Object resetListener;
	private Object deployButton;
	private Object originalDeploy;
	private Object deployListener;
	private Object addButton;
	private Object originalAdd;
	private Object addListener;

	public BattleNext_SimulationInjection(Object screen, String categoryName) throws Throwable {
		this.screen = screen;
		this.categoryName = categoryName;
		store.reload();
		refresh();
	}

	public void refresh() throws Throwable {

		Object next = invoke(screen, "getFactionPicker");
		if (next == null) return;
		if (picker != next) {
			releasePicker();
			picker = next;
			table = invoke(picker, "getTable");
			SimCategoryData category = new SimCategoryData();
			category.id = CATEGORY_ID;
			category.name = categoryName;
			category.nameColor = Misc.getBasePlayerColor();
			category.iconName = Global.getSettings().getSpriteName("icon", "BattleNext_Crest");
			category.nonFactionCategory = true;
			category.faction = SimulatorPluginImpl.createCustomFaction();
			category.variants = new ArrayList<>(store.getAll().stream()
					.sorted(Comparator.comparing(BattleNext_PinnedVariantModel::getDisplayName)
							.thenComparing(BattleNext_PinnedVariantModel::getPinId))
					.map(BattleNext_PinnedVariantModel::getPinId).toList());
			Object header = invoke(picker, "getHeader");
			Class<?> row = Class.forName("com.fs.starfarer.ui.impl.K$o", false, picker.getClass().getClassLoader());
			Object entry = MethodHandles.publicLookup().findConstructor(row,
					MethodType.methodType(void.class, header.getClass(), SimCategoryData.class)).invoke(header, category);
			invoke(table, "addRow", entry);
			Class<?> rendererType = Class.forName("com.fs.starfarer.campaign.ui.UITable$Oo", false, table.getClass().getClassLoader());
			originalRenderer = field(table, "\u00d3O0o00", rendererType);
			renderer = proxy(rendererType, this::categorySelected, originalRenderer);
			setField(table, "\u00d3O0o00", rendererType, renderer);
			addButton = field(picker, "\u00f5\u00d4\u00d8000", type("com.fs.starfarer.ui.n"));
			originalAdd = invoke(addButton, "getListener");
			addListener = proxy(type("com.fs.starfarer.ui.U"), this::addSelected, originalAdd);
			invoke(addButton, "setListener", addListener);
			releaseOptions();
			if (CATEGORY_ID.equals(Misc.getSimulatorPlugin().getUIStateData().selectedCategory)) {
				invoke(picker, "select", CATEGORY_ID);
			}
		}
		Object nextDeploy = field(screen, "\u00d3O\u00d6000", type("com.fs.starfarer.ui.n"));
		if (deployButton != nextDeploy) {
			releaseDeploy();
			deployButton = nextDeploy;
			originalDeploy = invoke(deployButton, "getListener");
			deployListener = proxy(type("com.fs.starfarer.ui.U"), this::deploySelected, originalDeploy);
			invoke(deployButton, "setListener", deployListener);
		}
		if (isSelected() && options != invoke(screen, "getAdvOpts")) {
			configureOptions();
			showVariants();
		}
	}

	public boolean isSelected() throws Throwable {
		if (picker == null) return false;
		SimCategoryData selected = (SimCategoryData) invoke(picker, "getSelected");
		return selected != null && CATEGORY_ID.equals(selected.id)
				&& (Boolean) invoke(screen, "isMultiDeploy");
	}

	private Object categorySelected(Object original, Object method, Object[] arguments) throws Throwable {

		String name = BUtil_RefMethod.java_lang_reflect_Method_getName(method);
		if (name.equals("tableRowSelected")) {
			SimCategoryData category = (SimCategoryData) invoke(arguments[1], "getData");
			if (CATEGORY_ID.equals(category.id)) {
				setField(picker, "do.this$String", SimCategoryData.class, category);
				invoke(screen, "updateReserves", List.of(), category);
				showVariants();
				invoke(invoke(picker, "getTable2"), "select", null, category);
				configureOptions();
				return null;
			}
			releaseOptions();
		}
		return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
	}

	private void showVariants() throws Throwable {

		if (!(Boolean) invoke(screen, "isMultiDeploy")) return;
		Object list = field(screen, "O\u00d2\u00d6000", type("com.fs.starfarer.campaign.ui.S"));
		invoke(list, "clear");
		int side = ((Number) invoke(screen, "getOwnerId")).intValue();
		var manager = Global.getCombatEngine().getFleetManager(side);
		for (FleetMemberAPI member : manager.getReservesCopy()) {
			manager.removeFromReserves(member);
		}
		SimCategoryData category = (SimCategoryData) invoke(picker, "getSelected");
		for (String id : category.variants) {
			FleetMemberAPI member = createMember(id);
			manager.addToReserves(member);
			invoke(list, "addIconFor", member);
			Object icon = invoke(list, "getIconForMember", member);
			for (String flag : List.of("\u00d3\u00d50000", "\u00d8\u00d50000", "while.String", "o\u00d50000")) {
				setField(icon, flag, boolean.class, true);
			}
			Object button = invoke(list, "getButtonForMember", member);
			Object logic = invoke(button, "getLogic");
			Class<?> mode = type("com.fs.starfarer.ui.int$o");
			Object both = MethodHandles.publicLookup().findStaticGetter(mode, "BOTH", mode).invoke();
			invoke(logic, "o00000", both);
		}
	}

	private FleetMemberAPI createMember(String id) throws Throwable {

		ShipVariantAPI variant = store.resolve(id);
		variant.setVariantDisplayName(store.get(id).getDisplayName());
		variant.setMayAutoAssignWeapons(false);
		FleetMemberAPI member = Global.getFactory().createFleetMember(FleetMemberType.SHIP, variant);
		member.setOwner(((Number) invoke(screen, "getOwnerId")).intValue());
		member.getCrewComposition().addCrew(member.getMinCrew());
		member.updateStats();
		member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		member.setShipName(((SimCategoryData) invoke(picker, "getSelected")).faction.pickRandomShipName("SIM"));
		return member;
	}

	private void configureOptions() throws Throwable {

		Object current = invoke(screen, "getAdvOpts");
		if (current == null) return;
		releaseOptions();
		invoke(current, "updateForCategory", Misc.getSimulatorPlugin().getCustomCategory());
		groupButton = field(current, "private.float$do", type("com.fs.starfarer.ui.n"));
		originalGroup = invoke(groupButton, "getListener");
		groupListener = proxy(type("com.fs.starfarer.ui.U"), this::groupSelected, originalGroup);
		invoke(groupButton, "setListener", groupListener);
		resetButton = field(current, "super.for$do", type("com.fs.starfarer.ui.n"));
		originalReset = invoke(resetButton, "getListener");
		resetListener = proxy(type("com.fs.starfarer.ui.U"), this::resetSelected, originalReset);
		invoke(resetButton, "setListener", resetListener);
		options = current;
	}

	private Object addSelected(Object original, Object method, Object[] arguments) throws Throwable {
		if (BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("actionPerformed") && isSelected()) return null;
		return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
	}

	private Object deploySelected(Object original, Object method, Object[] arguments) throws Throwable {
		if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("actionPerformed")) return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
		if (!isSelected()) return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
		deploy();
		return null;
	}

	private Object resetSelected(Object original, Object method, Object[] arguments) throws Throwable {
		if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("actionPerformed")) return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
		BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
		configureOptions();
		return null;
	}

	private Object groupSelected(Object original, Object method, Object[] arguments) throws Throwable {

		if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals("actionPerformed")) return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, original, arguments);
		int budget = ((Number) invoke(options, "getGroupSize")).intValue();
		Object list = field(screen, "O\u00d2\u00d6000", type("com.fs.starfarer.campaign.ui.S"));
		List<FleetMemberAPI> candidates = new ArrayList<>();
		for (Object value : (List<?>) invoke(list, "getMembers")) {
			FleetMemberAPI member = (FleetMemberAPI) value;
			if (member.getDeploymentPointsCost() <= 0f || member.getDeploymentPointsCost() > budget) continue;
			candidates.add(member);
		}
		invoke(screen, "clearSelection");
		while (!candidates.isEmpty()) {
			FleetMemberAPI member = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
			Object icon = invoke(list, "getIconForMember", member);
			int before = ((Number) field(icon, "O\u00f50000", int.class)).intValue();
			invoke(screen, "clickVariant", member.getVariant().getHullVariantId(), false);
			int after = ((Number) field(icon, "O\u00f50000", int.class)).intValue();
			if (after == before) {
				candidates.remove(member);
				continue;
			}
			budget -= (int) member.getDeploymentPointsCost();
			int remaining = budget;
			candidates.removeIf(candidate -> candidate.getDeploymentPointsCost() > remaining || after == 9 && candidate == member);
		}
		return null;
	}

	public void deploy() throws Throwable {

		Object list = field(screen, "O\u00d2\u00d6000", type("com.fs.starfarer.campaign.ui.S"));
		Class<?> specType = type("com.fs.starfarer.combat.CombatFleetManager$DeploymentSpec");
		Object spec = MethodHandles.publicLookup().findConstructor(specType, MethodType.methodType(void.class)).invoke();
		List<FleetMemberAPI> members = new ArrayList<>();
		for (Object value : (List<?>) invoke(list, "getMembers")) {
			FleetMemberAPI preview = (FleetMemberAPI) value;
			int count = ((Number) field(invoke(list, "getIconForMember", preview), "O\u00f50000", int.class)).intValue();
			for (int index = 0; index < count; index++) {
				FleetMemberAPI member = createMember(preview.getVariant().getHullVariantId());
				members.add(member);
				invoke(spec, "new", member);
			}
		}
		if (members.isEmpty()) return;
		@SuppressWarnings("unchecked")
		Map<String, String> settings = (Map<String, String>) invoke(options, "getSettings");
		var plugin = Misc.getSimulatorPlugin();
		plugin.applySettingsToFleetMembers(members, plugin.getCustomCategory(), settings);
		for (FleetMemberAPI member : members) {
			member.updateStats();
			member.getRepairTracker().setCR(member.getRepairTracker().getMaxCR());
		}
		int side = ((Number) invoke(screen, "getOwnerId")).intValue();
		var engine = Global.getCombatEngine();
		var manager = engine.getFleetManager(side);
		if (side != engine.getPlayerShip().getOwner()) {
			manager.setDeploymentYOffset(-8500f);
		}
		invoke(spec, "o00000");
		invoke(manager, "deploy", spec);
		plugin.applySettingsToDeployed(manager.getDeployedCopyDFM().stream()
				.filter(deployed -> members.contains(deployed.getMember())).toList(), settings);
		Object deployedList = field(screen, "void.null$Object", type("com.fs.starfarer.campaign.ui.S"));
		for (int index = members.size() - 1; index >= 0; index--) {
			invoke(deployedList, "addIconFor", members.get(index));
		}
		invoke(screen, "clearSelection");
		invoke(engine, "executeAdds");
		invoke(screen, "dismiss", 0);
	}

	public void cleanup() throws Throwable {
		releaseDeploy();
		releasePicker();
		releaseOptions();
	}

	private void releaseOptions() throws Throwable {
		if (options == null) return;
		if (invoke(groupButton, "getListener") == groupListener) {
			invoke(groupButton, "setListener", originalGroup);
		}
		if (invoke(resetButton, "getListener") == resetListener) {
			invoke(resetButton, "setListener", originalReset);
		}
		options = null;
		groupButton = null;
		resetButton = null;
	}

	private void releaseDeploy() throws Throwable {
		if (deployButton == null) return;
		if (invoke(deployButton, "getListener") == deployListener) {
			invoke(deployButton, "setListener", originalDeploy);
		}
		deployButton = null;
	}

	private void releasePicker() throws Throwable {

		if (table == null) return;
		Class<?> rendererType = type("com.fs.starfarer.campaign.ui.UITable$Oo");
		if (field(table, "\u00d3O0o00", rendererType) == renderer) {
			setField(table, "\u00d3O0o00", rendererType, originalRenderer);
		}
		if (invoke(addButton, "getListener") == addListener) {
			invoke(addButton, "setListener", originalAdd);
		}
		for (Object row : new ArrayList<>((List<?>) invoke(table, "getRows"))) {
			Object data = invoke(row, "getData");
			if (data instanceof SimCategoryData category && CATEGORY_ID.equals(category.id)) {
				invoke(table, "removeRow", row);
			}
		}
		table = null;
		picker = null;
	}

	private static Object proxy(Class<?> contract, Callback callback, Object original) throws Throwable {

		Class<?> handler = Class.forName("java.lang.reflect.InvocationHandler", false, null);
		Class<?> method = Class.forName("java.lang.reflect.Method", false, null);
		MethodHandle dispatch = MethodHandles.lookup().findVirtual(Callback.class, "invoke",
				MethodType.methodType(Object.class, Object.class, Object.class, Object[].class))
				.bindTo(callback).bindTo(original);
		dispatch = MethodHandles.dropArguments(dispatch, 0, Object.class);
		Object invocation = MethodHandleProxies.asInterfaceInstance(handler,
				dispatch.asType(MethodType.methodType(Object.class, Object.class, method, Object[].class)));
		return MethodHandles.lookup().findStatic(Class.forName("java.lang.reflect.Proxy", false, null), "newProxyInstance",
				MethodType.methodType(Object.class, ClassLoader.class, Class[].class, handler))
				.invoke(contract.getClassLoader(), new Class<?>[]{contract}, invocation);
	}

	private Class<?> type(String name) throws ClassNotFoundException {
		return Class.forName(name, false, screen.getClass().getClassLoader());
	}

	private static Object field(Object target, String name, Class<?> type) throws Throwable {
		return MethodHandles.privateLookupIn(target.getClass(), MethodHandles.lookup())
				.findGetter(target.getClass(), name, type).invoke(target);
	}

	private static void setField(Object target, String name, Class<?> type, Object value) throws Throwable {
		MethodHandles.privateLookupIn(target.getClass(), MethodHandles.lookup())
				.findSetter(target.getClass(), name, type).invoke(target, value);
	}

	static Object invoke(Object target, String name, Object... arguments) throws Throwable {

		Class<?> methodClass = Class.forName("java.lang.reflect.Method", false, null);
		for (Object method : target.getClass().getMethods()) {
			if (!BUtil_RefMethod.java_lang_reflect_Method_getName(method).equals(name)) continue;
			Class<?>[] parameters = (Class<?>[]) MethodHandles.publicLookup().findVirtual(methodClass, "getParameterTypes",
					MethodType.methodType(Class[].class)).invoke(method);
			if (parameters.length != arguments.length) continue;
			boolean matches = true;
			for (int index = 0; index < parameters.length; index++) {
				if (arguments[index] == null) continue;
				Class<?> parameter = parameters[index].isPrimitive()
						? MethodType.methodType(void.class, parameters[index]).wrap().parameterType(0) : parameters[index];
				matches &= parameter.isInstance(arguments[index]);
			}
			if (!matches) continue;
			return BUtil_RefMethod.java_lang_reflect_Method_invoke().invoke(method, target, arguments);
		}
		throw new NoSuchMethodException(target.getClass().getName() + "." + name);
	}

	@FunctionalInterface
	private interface Callback {
		Object invoke(Object original, Object method, Object[] arguments) throws Throwable;
	}
}
