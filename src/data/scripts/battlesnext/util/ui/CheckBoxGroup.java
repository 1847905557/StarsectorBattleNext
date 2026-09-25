package data.scripts.battlesnext.util.ui;
// originally by Originem, forked and modified by AnyIDElse under license
import com.fs.starfarer.api.ui.ButtonAPI;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class CheckBoxGroup {

	private Map<ButtonAPI, Object> checkBoxMap = new HashMap<>();

	private ButtonAPI lastCheckedButton;

	public Map<ButtonAPI, Object> getCheckBoxMap() {
		return checkBoxMap;
	}

	public void addCheckBox(ButtonAPI checkBox, Object data) {
		checkBoxMap.put(checkBox, data);
	}

	public void clear() {
		checkBoxMap.clear();
	}

	public ButtonAPI getCheckedButton() {
		return lastCheckedButton;
	}

	public Object getCheckedValue() {
		return checkBoxMap.get(lastCheckedButton);
	}

	public void tryCheckValue(Object value) {
		for (Map.Entry<ButtonAPI, Object> entry : checkBoxMap.entrySet()) {
			Object entryValue = entry.getValue();
			if (entryValue == null) {
				if (value == null) {
					entry.getKey().setChecked(true);
					break;
				}
			} else {
				if (entryValue.equals(value)) {
					entry.getKey().setChecked(true);
					break;
				}
			}
		}
	}

	public void updateCheck() {
		Set<ButtonAPI> checkBoxes = checkBoxMap.keySet();

		if (lastCheckedButton == null) {
			boolean foundBox = false;
			for (ButtonAPI checkBox : checkBoxes) {
				if (!foundBox && checkBox.isChecked()) {
					lastCheckedButton = checkBox;
					foundBox = true;
				} else {
					checkBox.setChecked(false);
				}
			}
		}

		int count = 0;
		for (ButtonAPI checkBox : checkBoxes) {
			if (checkBox.isChecked()) {
				count++;
			}
		}
		if (count >= 2) {
			ButtonAPI targetButton = null;
			for (ButtonAPI checkBox : checkBoxes) {
				if (checkBox.isChecked()) {
					if (checkBox != lastCheckedButton) {
						targetButton = checkBox;
					}
					checkBox.setChecked(false);
				}
			}
			if (targetButton != null) {
				lastCheckedButton = targetButton;
			}
		}
		if (lastCheckedButton != null)
			lastCheckedButton.setChecked(true);
	}

	public void setToEmpty() {
		for (ButtonAPI checkBox : checkBoxMap.keySet()) {
			checkBox.setChecked(false);
		}
		lastCheckedButton = null;
	}

	public boolean contains(ButtonAPI checkBox) {
		return checkBoxMap.containsKey(checkBox);
	}
}
