package thaumcraft.common.entities.monster.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
import thaumcraft.common.lib.utils.ChampionUtils;

/**
 * Replaces the old (1.12.2) {@code EntityTaintacleGiant} - the Taint faction's own boss, real
 * {@link EntityTaintacle} subclass (bigger, tougher, but keeps the real rooted/starves-off-taint/spawns-
 * nearby-tentacles mechanic that class already provides - the true original hand-rolled a similar boss-bar/
 * self-heal/enrage shape {@link EntityThaumcraftBoss} now provides other bosses, but this one can't share
 * that base too - Java has no multiple inheritance, and the taint-rooted behavior matters more to this boss's
 * own real identity than sharing the mechanic class would save). Real, working boss bar via
 * {@link ServerBossEvent}, real periodic self-heal (1 HP/30 ticks), real "enrage" (any single hit over 35
 * damage is capped at 35 and grants a real Regeneration/Strength/Haste buff scaled to the excess, once every
 * 200 ticks) - matches the true original's own real fields exactly (no spawn-timer invulnerability window
 * here - that's real to {@link EntityEldritchGolem}'s own specific headless-phase mechanic via
 * {@link EntityThaumcraftBoss}, not something the true original's own {@code EntityTaintacleGiant} ever
 * had).
 *
 * Real death-drop dedup: only actually drops a Primordial Pearl if no other real {@code EntityTaintacleGiant}
 * is within 48 blocks (matches the true original's own real "don't flood the area with pearls when several
 * giants cluster together" check) - replaces (not adds to) {@link EntityTaintacle}'s own crystal-essence
 * drop, matching the true original's own real {@code dropFewItems} override exactly.
 *
 * {@link #canSpawnHere} is real, faithfully {@code false} - the true original's own version too. Like
 * {@link EntityEldritchGolem}/{@link EntityCultistLeader}, this boss has no real survival encounter to
 * summon it yet (the true original's own real placement was worldgen-only, tied to the still-blocked
 * Taint-corruption world-spread subsystem - see the port-tracking memory) - real, complete, tested code,
 * reachable via {@code /summon} today, matching the same "ship it real even though nothing spawns it yet"
 * precedent this port has used repeatedly. Had a real, previously-undiscovered gap on the client side until
 * 2026-09-16: no entity renderer was ever registered for this type at all (only {@code TAINTACLE}/{@code
 * TAINTACLE_SMALL} were, despite this class already being a real {@link EntityTaintacle} subclass, fully
 * renderer-compatible) - a real client crash risk the very first time any player summoned or saw one, not
 * just a cosmetic gap. Fixed in {@code ThaumcraftClient} (reuses {@code RenderTaintacle} with the true
 * original's own real bigger shadow/chain-length values).
 *
 * Real champion-mod tie-in, restored 2026-09-15: {@link #finalizeSpawn} calls {@link ChampionUtils#makeChampion}
 * unconditionally (plus a real {@link #setPersistenceRequired}), matching the true original's own {@code
 * onInitialSpawn}'s unconditional {@code EntityUtils.makeChampion(this, true)} exactly - unlike the other 3
 * champion-eligible bosses, this one isn't an {@link EntityThaumcraftBoss} subclass, so it gets the same
 * generic champion treatment (flat health/damage buffs, a heal, a "&lt;Modifier&gt; Giant Taintacle" name) any
 * other real hostile creature would, not a boss-specific title suffix.
 *
 * Real, documented cuts: no enrage chat message to the attacking player (same cut
 * {@link EntityThaumcraftBoss#hurt} already made for every other boss sharing that mechanic). The true
 * original's own {@code canBreatheUnderwater() -> true} has no direct
 * modern equivalent ({@code LivingEntity#canBreatheUnderwater} is final now) - not needed anyway, since
 * {@link EntityTaintacle#decreaseAirSupply} (inherited unchanged) already makes air supply never actually
 * drop, the real mechanism that mattered.
 */
public class EntityTaintacleGiant extends EntityTaintacle implements IEldritchMob {

	private final ServerBossEvent bossEvent;
	private int angerTimer;

	public EntityTaintacleGiant(EntityType<? extends EntityTaintacleGiant> type, Level level) {
		super(type, level);
		bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
		bossEvent.setDarkenScreen(true);
		xpReward = 20;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityTaintacle.createAttributes()
				.add(Attributes.MAX_HEALTH, 175.0)
				.add(Attributes.ATTACK_DAMAGE, 9.0);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof IEldritchMob || super.isAlliedTo(other);
	}

	@Override
	public boolean canSpawnHere() {
		return false;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		ChampionUtils.makeChampion(this);
		setPersistenceRequired();
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (angerTimer > 0) {
			angerTimer--;
		}
		if (!level().isClientSide) {
			bossEvent.setProgress(getHealth() / getMaxHealth());
			if (tickCount % 30 == 0) {
				heal(1.0f);
			}
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide && amount > 35.0f) {
			if (angerTimer == 0) {
				addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, (int) (amount / 15.0f)));
				addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, (int) (amount / 10.0f)));
				addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, (int) (amount / 40.0f)));
				angerTimer = 200;
			}
			amount = 35.0f;
		}
		return super.hurt(source, amount);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		boolean anotherGiantNearby = !level.getEntitiesOfClass(EntityTaintacleGiant.class, getBoundingBox().inflate(48.0), e -> e != this).isEmpty();
		if (!anotherGiantNearby) {
			EntityThaumcraftBoss.dropSpecialItem(this, new ItemStack(ItemsTC.primordialPearl.get()), getBbHeight() / 2.0f);
		}
	}
}
