package thaumcraft.common.lib.utils;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Creeper;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.mods.ChampionModifier;
import thaumcraft.api.ThaumcraftAttributes;

/**
 * Replaces the champion-mod half of the old (1.12.2) {@code EntityUtils} - a new, focused class rather than
 * porting that whole 1.12-era grab-bag utility file (baubles helpers, block-breaking helpers, item-dropping
 * helpers, none of which any real ported code needs yet).
 */
public final class ChampionUtils {

	private static final AttributeModifier CHAMPION_HEALTH = new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_health"), 100.0, AttributeModifier.Operation.ADD_VALUE);
	private static final AttributeModifier CHAMPION_DAMAGE = new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_damage"), 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
	private static final AttributeModifier BOLD_SPEED = new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_bold_speed"), 0.3, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
	private static final AttributeModifier MIGHTY_DAMAGE = new AttributeModifier(
			ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "champion_mighty_damage"), 2.0, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);

	private ChampionUtils() {
	}

	/**
	 * Turns a real hostile mob into a real champion: picks a random real {@link ChampionModifier}, applies
	 * its real attribute mod (readable back off {@link ThaumcraftAttributes#CHAMPION_MOD} as the mod's own
	 * id), a real flat +100 health / real x3 damage buff, a real +25 heal, a real custom name, and (for the
	 * 3 real mods with no per-event dispatch of their own - bold/mighty/warded) their own real one-time extra
	 * buff. Real, as-is from the original's own {@code EntityUtils#makeChampion} (minus the boss-entity and
	 * persistence-flag branches - no {@code EntityThaumcraftBoss} champion tie-in yet, and callers that want
	 * persistence can call {@link Mob#setPersistenceRequired()} themselves).
	 */
	public static void makeChampion(Mob entity) {
		AttributeInstance championAttr = entity.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (championAttr == null || championAttr.getValue() > -2.0) return;

		int type = entity instanceof Creeper ? 0 : entity.getRandom().nextInt(ChampionModifier.MODS.length);
		ChampionModifier mod = ChampionModifier.MODS[type];
		championAttr.addOrReplacePermanentModifier(mod.attributeMod);

		AttributeInstance health = entity.getAttribute(Attributes.MAX_HEALTH);
		if (health != null) health.addOrReplacePermanentModifier(CHAMPION_HEALTH);
		AttributeInstance damage = entity.getAttribute(Attributes.ATTACK_DAMAGE);
		if (damage != null) damage.addOrReplacePermanentModifier(CHAMPION_DAMAGE);

		entity.heal(25.0f);
		entity.setCustomName(mod.getModNameLocalized().copy().append(" ").append(entity.getType().getDescription()));
		entity.setCustomNameVisible(true);

		switch (type) {
			case 0 -> {
				AttributeInstance speed = entity.getAttribute(Attributes.MOVEMENT_SPEED);
				if (speed != null) speed.addOrReplacePermanentModifier(BOLD_SPEED);
			}
			case 3 -> {
				if (damage != null) damage.addOrReplacePermanentModifier(MIGHTY_DAMAGE);
			}
			case 5 -> {
				if (health != null) {
					float bonusHealth = (float) (health.getBaseValue() / 2.0);
					entity.setAbsorptionAmount(entity.getAbsorptionAmount() + bonusHealth);
				}
			}
			default -> {
			}
		}
	}

	/** Marks a mob as having already rolled for champion status (and lost) - see
	 * {@link ChampionModifier#ATTRIBUTE_NOT_CHAMPION}'s own doc comment for why this is a real, separate
	 * state from "never yet rolled". */
	public static void markNotChampion(Mob entity) {
		AttributeInstance championAttr = entity.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (championAttr != null) championAttr.addOrReplacePermanentModifier(ChampionModifier.ATTRIBUTE_NOT_CHAMPION);
	}
}
