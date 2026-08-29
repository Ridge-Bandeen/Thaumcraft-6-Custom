package thaumcraft.api.casters;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;


public class NodeSetting {

	int value;
	public String key;
	String description;
	INodeSettingType type;
	String research;

	public NodeSetting(String key, String description, INodeSettingType setting, String research) {
		this.key = key;
		type = setting;
		value = setting.getDefault();
		this.description = description;
		this.research = research;
	}

	public NodeSetting(String key, String description, INodeSettingType setting) {
		this(key, description, setting, null);
	}

	public int getValue() {
		return type.getValue(value);
	}

	public void setValue(int truevalue) {
		int lv = -1;
		value=0;
		while (getValue()!=truevalue && lv!=value) {
			lv = value;
			increment();
		}
	}

	/**
	 * This setting will only be visible if this research is unlocked. If not the default will be used.
	 * @return
	 */
	public String getResearch() {
		return research;
	}

	/** See {@link thaumcraft.api.aspects.Aspect#getLocalizedDescription()} for why this returns a Component now. */
	public Component getValueText() {
		return type.getValueText(value);
	}

	public void increment() {
		value++;
		value = getType().clamp(value);
	}

	public void decrement() {
		value--;
		value = getType().clamp(value);
	}

	public INodeSettingType getType() {
		return type;
	}

	public Component getLocalizedName() {
		return Component.translatable(description);
	}


	public interface INodeSettingType {
		public int getDefault();

		public int clamp(int i);

		public int getValue(int value);
		/** Descriptive text for this value - a translation key for {@link NodeSettingIntList}, plain literal text for {@link NodeSettingIntRange}. */
		public Component getValueText(int value);
	}

	public static class NodeSettingIntList implements INodeSettingType {
		int[] values;
		String[] descriptions;

		public NodeSettingIntList(int[] values, String[] descriptions) {
			this.values = values;
			this.descriptions = descriptions;
		}

		@Override
		public int getDefault() {
			return 0;
		}

		@Override
		public int clamp(int old) {
			return Mth.clamp(old, 0, values.length-1);
		}

		@Override
		public int getValue(int value) {
			return values[clamp(value)];
		}

		@Override
		public Component getValueText(int value) {
			return Component.translatable(descriptions[value]);
		}
	}

	public static class NodeSettingIntRange implements INodeSettingType {
		int min, max;

		public NodeSettingIntRange(int min, int max) {
			this.min = min;
			this.max = max;
		}

		@Override
		public int getDefault() {
			return min;
		}

		@Override
		public int clamp(int old) {
			return Mth.clamp(old, min, max);
		}

		@Override
		public int getValue(int value) {
			return clamp(value);
		}

		@Override
		public Component getValueText(int value) {
			return Component.literal(""+getValue(value));
		}
	}



}
