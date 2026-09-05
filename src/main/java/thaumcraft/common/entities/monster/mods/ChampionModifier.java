package thaumcraft.common.entities.monster.mods;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code ChampionModifier}. Same real 14 mods, same real names/types, same real
 * effect-class wiring - just modern {@link AttributeModifier} (a {@link ResourceLocation} id + {@link
 * AttributeModifier.Operation} enum, replacing the original's raw UUID + numeric-operation-code pair).
 *
 * {@code attributeMod} is {@code ADD_VALUE} of {@code (id + 2)} - applied on top of
 * {@link thaumcraft.common.registry.ThaumcraftAttributes#CHAMPION_MOD}'s real base value of -2, this makes
 * the final attribute value read back out exactly as {@code id}, same arithmetic trick the original used.
 *
 * {@code type}: 0 = self (dispatched once per tick, no target), 1 = offensive (dispatched when this mob deals
 * damage), 2 = defensive (dispatched when this mob takes damage), -1 = passive (no per-event dispatch at all
 * - {@link ChampionUtils#makeChampion} applies these mods' one-time buffs directly at spawn instead).
 */
public class ChampionModifier {

	public final int id;
	public final String name;
	public final int type;
	public final IChampionModifierEffect effect;
	public final AttributeModifier attributeMod;

	private ChampionModifier(int id, String name, int type, IChampionModifierEffect effect) {
		this.id = id;
		this.name = name;
		this.type = type;
		this.effect = effect;
		this.attributeMod = new AttributeModifier(
				ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_" + name),
				id + 2, AttributeModifier.Operation.ADD_VALUE);
	}

	public Component getModNameLocalized() {
		return Component.translatable("champion.mod." + name);
	}

	public static final ChampionModifier[] MODS = {
			new ChampionModifier(0, "bold", -1, new ChampionModBold()),
			new ChampionModifier(1, "spine", 2, new ChampionModSpined()),
			new ChampionModifier(2, "armor", 2, new ChampionModArmored()),
			new ChampionModifier(3, "mighty", -1, new ChampionModMighty()),
			new ChampionModifier(4, "grim", 1, new ChampionModGrim()),
			new ChampionModifier(5, "warded", 0, new ChampionModWarded()),
			new ChampionModifier(6, "warp", 1, new ChampionModWarp()),
			new ChampionModifier(7, "undying", 0, new ChampionModUndying()),
			new ChampionModifier(8, "fiery", 1, new ChampionModFire()),
			new ChampionModifier(9, "sickly", 1, new ChampionModSickly()),
			new ChampionModifier(10, "venomous", 1, new ChampionModPoison()),
			new ChampionModifier(11, "vampiric", 1, new ChampionModVampire()),
			new ChampionModifier(12, "infested", 2, new ChampionModInfested()),
			new ChampionModifier(13, "tainted", 0, new ChampionModTainted()),
	};

	/** Applied (permanently) to mark "already rolled for champion status, decided no" without disturbing the
	 * real -2 base value's meaning as "never yet rolled" - bumps the readable value from -2 to -1. */
	public static final AttributeModifier ATTRIBUTE_NOT_CHAMPION = new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_none"), 1.0, AttributeModifier.Operation.ADD_VALUE);
}
