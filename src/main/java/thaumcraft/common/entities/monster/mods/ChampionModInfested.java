package thaumcraft.common.entities.monster.mods;

import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code ChampionModInfested}. Type 2 (defensive) - real, as-is: a real 40% chance
 * to spawn a real {@link EntityTaintCrawler} on being hit, now with the true original's own real "gore" sound
 * cue restored 2026-09-17 (corrects a stale doc-comment claim here that no sound registry existed yet in this
 * port - {@link ThaumcraftSounds#GORE} has been real since the "Custom sounds" chunk, this call site had just
 * never been revisited). The interface's own real {@code showFX}/{@code preRender} client-only hooks stay
 * dropped - see {@link IChampionModifierEffect}'s own doc comment; restoring those means changing a shared
 * interface every champion-mod effect implements, a real, separate, bigger decision than this one call site's
 * own sound cue.
 */
public class ChampionModInfested implements IChampionModifierEffect {
	@Override
	public float performEffect(LivingEntity boss, LivingEntity target, DamageSource source, float amount) {
		if (boss.getRandom().nextFloat() < 0.4f && !boss.level().isClientSide) {
			Level level = boss.level();
			EntityTaintCrawler crawler = new EntityTaintCrawler(ThaumcraftEntities.TAINT_CRAWLER.get(), level);
			crawler.moveTo(boss.getX(), boss.getY() + boss.getBbHeight() / 2.0, boss.getZ(), boss.getRandom().nextFloat() * 360.0f, 0.0f);
			level.addFreshEntity(crawler);
			boss.playSound(ThaumcraftSounds.GORE.get(), 0.5f, 1.0f);
		}
		return amount;
	}
}
