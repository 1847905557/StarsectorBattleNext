package data.model;

import java.util.List;

public abstract class BattleNext_CsvModel extends BattleNext_BaseModel {

	public abstract void fromCsvLine(List<String> fields);

	public abstract String toCsvLine();
}
