package data.model.fleet;

import data.model.BattleNext_JsonModel;
import data.scripts.fleet.BattleNext_VariantAdapter;
import data.scripts.member.BattleNext_MemberModel;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.*;

public class BattleNext_FleetModel extends BattleNext_JsonModel {

	public static final int FORMAT_VERSION = 1;

	private String id;
	private String folderName;
	private String name;
	private List<String> skills;
	private List<String> tags;
	private List<BattleNext_MemberModel> members;
	private String author;
	private String description;
	private final Map<String, BattleNext_VariantAdapter> variants = new LinkedHashMap<>();
	private final List<String> loadErrors = new ArrayList<>();

	public BattleNext_FleetModel() {
		this("", "", "", List.of(), List.of(), List.of());
	}

	public BattleNext_FleetModel(String id, String name, List<String> skills, List<String> tags,
			List<BattleNext_MemberModel> members) {
		this(id, id, name, skills, tags, members);
	}

	public BattleNext_FleetModel(String id, String folderName, String name, List<String> skills, List<String> tags,
			List<BattleNext_MemberModel> members) {
		this.id = normalize(id);
		this.folderName = normalize(folderName);
		this.name = normalize(name);
		this.skills = copyStrings(skills);
		this.tags = copyStrings(tags);
		this.members = copyMembers(members);
		this.author = "";
		this.description = "";
	}

	@Override
	public void fromJson(JSONObject json) throws JSONException {
		int version = json.optInt("formatVersion", FORMAT_VERSION);
		if (version != FORMAT_VERSION) throw new IllegalArgumentException("Unsupported fleet formatVersion: " + version);
		List<BattleNext_MemberModel> members = new ArrayList<>();
		JSONArray memberArray = json.optJSONArray("members");
		if (memberArray != null) {
			for (int index = 0; index < memberArray.length(); index++) {
				JSONObject member = memberArray.optJSONObject(index);
				if (member != null) {
					members.add(BattleNext_MemberModel.fromJson(member));
				}
			}
		}

		setId(json.optString("id", ""));
		setName(json.optString("name", ""));
		setSkills(readStrings(json.optJSONArray("skills")));
		setTags(readStrings(json.optJSONArray("tags")));
		setMembers(members);
		author = json.optString("author", "");
		description = json.optString("description", "");
	}

	@Override
	public JSONObject toJson() throws JSONException {
		JSONObject json = new JSONObject();
		json.put("formatVersion", FORMAT_VERSION);
		json.put("id", id);
		json.put("name", name);
		json.put("skills", new JSONArray(skills));
		json.put("tags", new JSONArray(tags));
		JSONArray memberArray = new JSONArray();
		for (BattleNext_MemberModel member : members) {
			memberArray.put(member.toJson());
		}
		json.put("members", memberArray);
		json.put("author", author);
		json.put("description", description);
		return json;
	}

	@Override
	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = normalize(id);
	}

	public String getFolderName() {
		return folderName;
	}

	public void setFolderName(String folderName) {
		this.folderName = normalize(folderName);
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = normalize(name);
	}

	public List<String> getSkills() {
		return Collections.unmodifiableList(skills);
	}

	public void setSkills(List<String> skills) {
		this.skills = copyStrings(skills);
	}

	public List<String> getTags() {
		return Collections.unmodifiableList(tags);
	}

	public void setTags(List<String> tags) {
		this.tags = copyStrings(tags);
	}

	public List<BattleNext_MemberModel> getMembers() {
		return Collections.unmodifiableList(members);
	}

	public void setMembers(List<BattleNext_MemberModel> members) {
		this.members = copyMembers(members);
	}

	public String getAuthor() {
		return author;
	}

	public void setAuthor(String author) {
		this.author = normalize(author);
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = normalize(description);
	}

	public void putVariant(BattleNext_VariantAdapter variant) {
		if (variant != null && !variant.getId().isEmpty()) variants.put(variant.getId(), variant);
	}

	public BattleNext_VariantAdapter getVariant(String variantId) {
		return variants.get(variantId);
	}

	public List<BattleNext_VariantAdapter> getVariants() {
		return List.copyOf(variants.values());
	}

	public boolean hasLocalVariants() {
		return !variants.isEmpty();
	}

	public void addLoadError(String error) {
		if (error != null && !error.isEmpty()) loadErrors.add(error);
	}

	public List<String> getLoadErrors() {
		return Collections.unmodifiableList(loadErrors);
	}

	private static List<String> readStrings(JSONArray values) {
		List<String> result = new ArrayList<>();
		if (values == null) return result;
		for (int index = 0; index < values.length(); index++) {
			String value = values.optString(index, "").trim();
			if (!value.isEmpty()) result.add(value);
		}
		return result;
	}

	private static List<String> copyStrings(List<String> values) {
		List<String> result = new ArrayList<>();
		if (values == null) return result;
		for (String value : values) {
			if (value != null && !value.trim().isEmpty()) result.add(value.trim());
		}
		return result;
	}

	private static List<BattleNext_MemberModel> copyMembers(List<BattleNext_MemberModel> values) {
		List<BattleNext_MemberModel> result = new ArrayList<>();
		if (values != null) {
			for (BattleNext_MemberModel value : values) {
				if (value != null) {
					result.add(value);
				}
			}
		}
		return result;
	}

	private static String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
