package thaumcraft.common.entities.monster.mods;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * Replaces the old (1.12.2) {@code ChampionModInfested}. Type 2 (defensive) - real, as-is: a real 40% chance
 * to spawn a real {@link EntityTaintCrawler} on being hit. Drops the original's "gore" sound cue - no sound
 * registry exists yet in this port at all (see {@code SoundsTC}'s own stale, unported state).
 */
public class ChampionModInfested implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.getRandom().nextFloat() < 0.4f && !boss.level().isClientSide) {
			Level level = boss.level();
			EntityTaintCrawler crawler = new EntityTaintCrawler(ThaumcraftEntities.TAINT_CRAWLER.get(), level);
			crawler.moveTo(boss.getX(), boss.getY() + boss.getBbHeight() / 2.0, boss.getZ(), boss.getRandom().nextFloat() * 360.0f, 0.0f);
			level.addFreshEntity(crawler);
		}
		return amount;
	}
}
