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
import thaumcraft.common.config.ThaumcraftConfig;
import thaumcraft.common.entities.monster.mods.ChampionModifier;
import thaumcraft.common.lib.utils.ChampionUtils;
import thaumcraft.api.ThaumcraftAttributes;

/**
 * Replaces the champion-mod dispatch fragments buried inside the old (1.12.2) {@code EntityEvents}' giant
 * {@code livingTick}/{@code entityHurt}/{@code livingDrops}/{@code entitySpawns} handlers - extracted into
 * their own focused class rather than porting that whole file (which also handles a dozen unrelated
 * mechanics, most already ported elsewhere for real, some genuinely unported).
 *
 * <p>The world-config on/off toggle ({@code allowChampionMobs}) is now a real, live {@link
 * thaumcraft.common.config.ThaumcraftConfig#ALLOW_CHAMPION_MOBS} check, wired in {@link #onJoinLevel} - since
 * {@code isDangerousLocation} was already dead code (an unconditional {@code return false}) even in the real
 * original, disabling the toggle there meant champions never spawned at all, which is exactly what this
 * does too. Real, deliberate cut kept: the original's whole {@code ConfigEntities.championModWhitelist}
 * per-species weighting is dropped (no per-species config surface in this port). The core real formula
 * - base roll, difficulty adjustment, nether/end adjustment, a real minimum-health floor - is kept as-is.
 * "Spooky" biomes (1.12's {@code BiomeDictionary.Type.SPOOKY}) have no direct modern tag equivalent and are
 * dropped rather than guessed at.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class ChampionModEvents {

	/** Turned off only by the GameTest batch hook: every monster a test spawns would otherwise have a small chance
	 * (about 1 in 50) of rolling as a champion, with +100 health or a champion effect, and failing whatever the test
	 * checks. With it off, spawned monsters are simply marked "not champion"; champion tests make champions directly. */
	public static boolean naturalRolls = true;

	@SubscribeEvent
	public static void onJoinLevel(EntityJoinLevelEvent event) {
		if (event.loadedFromDisk() || event.getLevel().isClientSide()) return;
		if (!(event.getEntity() instanceof Mob mob) || !(mob instanceof Monster)) return;

		AttributeInstance championAttr = mob.getAttribute(ThaumcraftAttributes.CHAMPION_MOD);
		if (championAttr == null || championAttr.getValue() > -2.0) return;

		if (!ThaumcraftConfig.ALLOW_CHAMPION_MOBS.get() || !naturalRolls) {
			ChampionUtils.markNotChampion(mob);
			return;
		}

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

		if (victim instanceof net.minecraft.world.entity.monster.Monster victimMob
				&& (championId(victimMob) >= 0 || victimMob instanceof thaumcraft.api.entities.IEldritchMob)) {
			int t = championId(victimMob);
			// as the original: a warded (5) or eldritch mob's absorption shows a shield flash and the shield sound...
			if ((t == 5 || victimMob instanceof thaumcraft.api.entities.IEldritchMob) && victimMob.getAbsorptionAmount() > 0.0f) {
				thaumcraft.common.lib.network.fx.PacketFXShield.send(victimMob, source);
				victimMob.playSound(thaumcraft.common.registry.ThaumcraftSounds.RUNICSHIELDCHARGE.get(), 0.66f, 1.1f + victimMob.getRandom().nextFloat() * 0.1f);
			} else if (t >= 0 && ChampionModifier.MODS[t].type == 2 && attackerEntity instanceof LivingEntity attacker) {
				// ...else the defensive champion effects
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
	 * formula) - the paired real loot-bag drop lives in {@link Thaumcraft#onLivingDrops} instead (restored
	 * 2026-09-15 now that a real loot bag item exists), matching the true original's own single
	 * {@code livingDrops} handler doing both under the identical real condition. Tainted (id 13) is excluded
	 * from this bonus, same real exclusion the original used. Bosses are excluded too (real, restored
	 * 2026-09-15 alongside making bosses champion-eligible in the first place - the true original's own
	 * condition explicitly excludes {@code EntityThaumcraftBoss}; bosses already grant their own real
	 * {@code xpReward} on death instead). */
	@SubscribeEvent
	public static void onDrops(LivingDropsEvent event) {
		LivingEntity entity = event.getEntity();
		if (!event.isRecentlyHit() || !(entity instanceof Mob mob) || !(entity.level() instanceof ServerLevel serverLevel)) return;
		if (mob instanceof thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss) return;
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
