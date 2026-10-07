package data.model;

import data.model.pinnedvariant.BattleNext_PinnedVariantModel;
import data.model.pinnedvariant.BattleNext_PinnedVariantStore;
import data.model.pinnedvariant.io.BattleNext_PinnedVariantSaver;
import data.model.report.BattleNext_BattleReportModel;
import data.model.report.BattleNext_BattleReportStore;
import data.model.report.io.BattleNext_BattleReportSaver;
import data.model.rule.BattleNext_RuleModel;
import data.model.rule.BattleNext_RuleStore;
import data.model.rule.io.BattleNext_RuleSaver;
import data.model.tournament.BattleNext_TournamentModel;
import data.model.tournament.BattleNext_TournamentStore;
import data.model.tournament.io.BattleNext_TournamentSaver;
import data.test.fixture.BattleNext_TestGameFixture;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

class BattleNext_ResourceSaverContractTest extends BattleNext_TestGameFixture {

	@ParameterizedTest
	@ValueSource(strings = {"report", "rule", "tournament", "pin"})
	void optionalResourceRootsPublishLoadedEmptyCatalogsWithoutReadingFiles(String kind) throws Exception {
		var store = store(kind);
		store.reload();
		assertTrue(store.isLoaded());
		assertTrue(store.getAll().isEmpty());
		assertTrue(dataReads.isEmpty());
		assertFalse(Files.exists(directory.resolve(root(kind))));
	}

	@ParameterizedTest
	@ValueSource(strings = {"report", "rule", "tournament", "pin"})
	void storesLoadFlatResourcesInPathOrderAndPreserveTheirContentAndOrigins(String kind) throws Exception {

		hull("wolf", "Wolf", List.of());
		var root = directory.resolve(root(kind));
		Files.createDirectories(root.resolve("nested"));
		for (String id : List.of("b", "a")) {
			Files.writeString(root.resolve(id + ".json"), model(kind, id).toJson().toString(), StandardCharsets.UTF_8);
		}
		Files.writeString(root.resolve("nested/ignored.json"), "invalid", StandardCharsets.UTF_8);
		Files.writeString(root.resolve("ignored.txt"), "invalid", StandardCharsets.UTF_8);
		var store = store(kind);
		store.reload();
		assertTrue(store.isLoaded());
		assertEquals(List.of("a", "b"), store.getAll().stream().map(BattleNext_BaseModel::getId).toList());
		for (var entry : store.getAll()) {
			assertEquals(BattleNext_LoadSource.STANDARD, entry.getSourceType());
			assertEquals(root(kind) + "/" + entry.getId() + ".json", entry.getSourcePath());
			assertEquals(model(kind, entry.getId()).toJson().toString(), entry.toJson().toString());
		}
		assertEquals(List.of("loadJSON:" + root(kind) + "/a.json", "loadJSON:" + root(kind) + "/b.json"), dataReads);
	}

	@ParameterizedTest
	@ValueSource(strings = {"report", "rule", "tournament", "pin"})
	void saverWritesUtf8JsonAndDeletesOnlyItsResource(String kind) throws Exception {

		BattleNext_JsonModel model = model(kind, "entry");
		BattleNext_BaseSaver<BattleNext_JsonModel> saver = saver(kind);
		String path = saver.save(model);
		assertEquals(root(kind) + "/entry.json", path);
		byte[] bytes = Files.readAllBytes(directory.resolve(path));
		assertEquals(model.toJson().toString(), new JSONObject(new String(bytes, StandardCharsets.UTF_8)).toString());
		assertFalse(bytes.length >= 3 && bytes[0] == (byte) 0xef && bytes[1] == (byte) 0xbb && bytes[2] == (byte) 0xbf);
		var sibling = directory.resolve(root(kind) + "/keep.txt");
		Files.writeString(sibling, "保留", StandardCharsets.UTF_8);
		saver.delete(model);
		assertFalse(Files.exists(directory.resolve(path)));
		assertEquals("保留", Files.readString(sibling, StandardCharsets.UTF_8));
		saver.delete(model);
	}

	@ParameterizedTest
	@ValueSource(strings = {"report", "rule", "tournament", "pin"})
	void invalidIdentityFailsBeforeCreatingTheResourceDirectory(String kind) throws Exception {
		var model = model(kind, "");
		var saver = saver(kind);
		assertThrows(IOException.class, () -> saver.save(model));
		assertThrows(IOException.class, () -> saver.delete(model));
		assertFalse(Files.exists(directory.resolve(root(kind))));
	}

	@ParameterizedTest
	@ValueSource(strings = {"report", "rule", "tournament", "pin"})
	void filesystemFailurePreservesTheInputModel(String kind) throws Exception {
		var model = model(kind, "entry");
		model.setSourceType(BattleNext_LoadSource.STANDARD);
		model.setSourcePath("source.json");
		String before = model.toJson().toString();
		var blocked = directory.resolve(root(kind));
		Files.createDirectories(blocked.getParent());
		Files.writeString(blocked, "blocked", StandardCharsets.UTF_8);
		assertThrows(IOException.class, () -> saver(kind).save(model));
		assertEquals(before, model.toJson().toString());
		assertEquals("source.json", model.getSourcePath());
		assertEquals(BattleNext_LoadSource.STANDARD, model.getSourceType());
		assertEquals("blocked", Files.readString(blocked, StandardCharsets.UTF_8));
	}

	private BattleNext_BaseStore<? extends BattleNext_JsonModel> store(String kind) {
		return switch (kind) {
			case "rule" -> new BattleNext_RuleStore();
			case "tournament" -> new BattleNext_TournamentStore();
			case "pin" -> new BattleNext_PinnedVariantStore();
			default -> new BattleNext_BattleReportStore();
		};
	}

	private BattleNext_JsonModel model(String kind, String id) throws Exception {
		return switch (kind) {
			case "rule" -> new BattleNext_RuleModel(id, "中文规则");
			case "tournament" -> new BattleNext_TournamentModel(id, "中文赛事", BattleNext_TournamentModel.TournamentBracketType.SINGLE_ELIMINATION, 1, 2);
			case "pin" -> new BattleNext_PinnedVariantModel(id, "中文装配", new JSONObject().put("hullId", "wolf"));
			default -> {
				var report = new BattleNext_BattleReportModel();
				report.setId(id);
				report.setName("中文报告");
				yield report;
			}
		};
	}

	@SuppressWarnings("unchecked")
	private BattleNext_BaseSaver<BattleNext_JsonModel> saver(String kind) {
		BattleNext_BaseSaver<?> saver = switch (kind) {
			case "rule" -> new BattleNext_RuleSaver();
			case "tournament" -> new BattleNext_TournamentSaver();
			case "pin" -> new BattleNext_PinnedVariantSaver();
			default -> new BattleNext_BattleReportSaver();
		};
		return (BattleNext_BaseSaver<BattleNext_JsonModel>) saver;
	}

	private String root(String kind) {
		return "tournament/" + switch (kind) {
			case "rule" -> "_rules";
			case "tournament" -> "_tournaments";
			case "pin" -> "_pinnedVariants";
			default -> "_reports";
		};
	}
}
