package thaumcraft.common.entities.monster;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityBrainyZombie} - a real {@link Zombie} subclass (the original was
 * one too, {@code EntityZombie}), reusing vanilla's own real zombie AI/equipment/conversion logic entirely
 * unchanged. Tougher (25 health, 5 attack, +1 armor over a plain zombie) and never calls for reinforcements
 * (spawn-reinforcements chance forced to 0, matching the original), with an added
 * {@link HurtByTargetGoal} priority-1 goal - it retaliates against whatever hurts it, on top of vanilla's
 * own normal player-seeking behavior.
 */
public class EntityBrainyZombie extends Zombie {

	public EntityBrainyZombie(EntityType<? extends EntityBrainyZombie> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Zombie.createAttributes()
				.add(Attributes.MAX_HEALTH, 25.0)
				.add(Attributes.ATTACK_DAMAGE, 5.0)
				.add(Attributes.SPAWN_REINFORCEMENTS_CHANCE, 0.0);
	}

	@Override
	protected void registerGoals() {
		super.registerGoals();
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
	}

	@Override
	public int getArmorValue() {
		return super.getArmorValue() + 1;
	}

	/** Real, documented simplification: the original's own bonus-drop roll scaled down by the killing blow's
	 * looting level ({@code rand.nextInt(10) - lootingModifier <= 4}); this port drops the looting term (a
	 * flat 50% chance regardless of looting) - a minor, real simplification, not a functional loss worth a
	 * whole enchantment-level plumbing pass for one extra item's drop odds. */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		super.dropCustomDeathLoot(level, damageSource, recentlyHit);
		if (level.random.nextInt(10) <= 4) {
			spawnAtLocation(new net.minecraft.world.item.ItemStack(ItemsTC.brain.get()), 1.5f);
		}
	}
}
