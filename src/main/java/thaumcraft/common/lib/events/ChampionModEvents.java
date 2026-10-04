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
 * <p>The spawn roll is the original's: only monsters on the champion list
 * ({@link thaumcraft.common.registry.ThaumcraftDataMaps#CHAMPION_WEIGHT}) with at least 10 base health can roll; a d100
 * becomes a champion at 0 or below, +2 on Easy or with {@code allowChampionMobs} off, -2 on Hard (with it on), -3 in the
 * Outer Lands dimension, -2 (-1 with it off) in a spooky, Nether or End biome ({@code c:is_spooky}/{@code is_nether}/{@code
 * is_end}), and minus the species' weight - 1 (with it off, only for Thaumcraft bosses). The original's whitelist also
 * named the Eldritch Crab, Taintacle and Inhabited Zombie, but by names its entity lookup could not resolve, so only its
 * vanilla entries (and their subclasses, like the Inhabited Zombie) ever applied. Its "dangerous location" bonus was
 * a stub that never fired.
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

		if (!naturalRolls) {
			ChampionUtils.markNotChampion(mob);
			return;
		}

		Level level = event.getLevel();
		boolean allowed = ThaumcraftConfig.ALLOW_CHAMPION_MOBS.get();
		int c = level.getRandom().nextInt(100);
		if (level.getDifficulty() == net.minecraft.world.Difficulty.EASY || !allowed) c += 2;
		if (level.getDifficulty() == net.minecraft.world.Difficulty.HARD) c -= allowed ? 2 : 0;
		if (level.dimension().location().toString().equals(ThaumcraftConfig.DIMENSION_OUTER_ID.get())) c -= 3;
		var biome = level.getBiome(mob.blockPosition());
		if (biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_SPOOKY) || biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_NETHER)
				|| biome.is(net.neoforged.neoforge.common.Tags.Biomes.IS_END)) {
			c -= allowed ? 2 : 1;
		}
		var weight = mob.getType().builtInRegistryHolder().getData(thaumcraft.common.registry.ThaumcraftDataMaps.CHAMPION_WEIGHT);
		if (weight != null && (allowed || mob instanceof thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss)) {
			c -= Math.max(0, weight.weight() - 1);
		}

		if (weight != null && c <= 0 && mob.getAttributeBaseValue(Attributes.MAX_HEALTH) >= 10.0) {
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
