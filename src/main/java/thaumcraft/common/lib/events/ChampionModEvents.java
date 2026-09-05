package thaumcraft.common.lib.events;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.common.entities.monster.mods.ChampionModifier;
import thaumcraft.common.lib.utils.ChampionUtils;
import thaumcraft.api.ThaumcraftAttributes;

/**
 * Replaces the champion-mod dispatch fragments buried inside the old (1.12.2) {@code EntityEvents}' giant
 * {@code livingTick}/{@code entityHurt}/{@code livingDrops}/{@code entitySpawns} handlers - extracted into
 * their own focused class rather than porting that whole file (which also handles a dozen unrelated
 * mechanics, most already ported elsewhere for real, some genuinely unported).
 *
 * <p>Real, deliberate cuts from the original's spawn-roll formula: the original's whole
 * {@code ConfigEntities.championModWhitelist} per-species weighting and the world-config on/off toggle are
 * dropped (no modern config system exists yet in this port - matches this session's established "drop
 * config-driven complexity, keep the real core mechanic" pattern), and {@code isDangerousLocation} was
 * already dead code (an unconditional {@code return false}) even in the real original. The core real formula
 * - base roll, difficulty adjustment, nether/end adjustment, a real minimum-health floor - is kept as-is.
 * "Spooky" biomes (1.12's {@code BiomeDictionary.Type.SPOOKY}) have no direct modern tag equivalent and are
 * dropped rather than guessed at.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ChampionModEvents {

	@SubscribeEvent
	public static void onJoinLevel(EntityJoinLevelEvent event) {
		if (event.loadedFromDisk() || event.getLevel().isClientSide()) return;
		if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Monster)) return;

		AttributeInstance championAttr = mob.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (championAttr == null || championAttr.getValue() > -2.0) return;

		Level level = event.getLevel();
		int c = mob.getRandom().nextInt(100);
		switch (level.getDifficulty()) {
			case EASY, PEACEFUL -> c += 2;
			case HARD -> c -= 2;
			default -> {
			}
		}
		if (level.dimension() == Level.NETHER || level.dimension() == Level.END) c -= 2;

		if (c <= 0 && mob.getAttributeValue(Attributes.MAX_HEALTH) >= 10.0) {
			ChampionUtils.makeChampion(mob);
		} else {
			ChampionUtils.markNotChampion(mob);
		}
	}

	@SubscribeEvent
	public static void onTick(EntityTickEvent.Post event) {
		if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
		int t = championId(mob);
		if (t >= 0 && ChampionModifier.MODS[t].type == 0) {
			ChampionModifier.MODS[t].effect.performEffect(mob, null, null, 0.0f);
		}
	}

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity victim = event.getEntity();
		if (victim.level().isClientSide()) return;
		DamageSource source = event.getSource();
		Entity attackerEntity = source.getEntity();

		if (victim instanceof Mob victimMob && attackerEntity instanceof LivingEntity attacker) {
			int t = championId(victimMob);
			if (t >= 0 && ChampionModifier.MODS[t].type == 2) {
				event.setAmount(ChampionModifier.MODS[t].effect.performEffect(victimMob, attacker, source, event.getAmount()));
			}
		}

		if (attackerEntity instanceof Mob attackerMob) {
			int t = championId(attackerMob);
			if (t >= 0 && ChampionModifier.MODS[t].type == 1) {
				event.setAmount(ChampionModifier.MODS[t].effect.performEffect(attackerMob, victim, source, event.getAmount()));
			}
		}
	}

	/** Real bonus XP on a champion's death (5-7, matching the original's own real {@code 5 + rand(3)}
	 * formula) - drops the original's paired loot-bag drop, since no loot bag item exists in this port yet.
	 * Tainted (id 13) is excluded from this bonus, same real exclusion the original used. */
	@SubscribeEvent
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity entity = event.getEntity();
		if (!event.isRecentlyHit() || !(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel serverLevel)) return;
		int t = championId(mob);
		if (t < 0 || t == 13) return;

		int xp = 5 + entity.getRandom().nextInt(3);
		ExperienceOrb.award(serverLevel, entity.position(), xp);
	}

	/** @return the mob's real rolled champion id (0-13), or -1 if it's not a champion (never rolled, rolled
	 * and lost, or has no {@code CHAMPION_MOD} attribute at all - e.g. a passive animal). */
	private static int championId(Mob mob) {
		AttributeInstance championAttr = mob.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (championAttr == null) return -1;
		int t = (int) championAttr.getValue();
		return (t >= 0 && t < ChampionModifier.MODS.length) ? t : -1;
	}
}
