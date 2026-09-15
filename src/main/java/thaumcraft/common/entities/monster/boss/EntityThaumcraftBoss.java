package thaumcraft.common.entities.monster.boss;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import thaumcraft.api.entities.IEldritchMob;

/**
 * Replaces the old (1.12.2) {@code EntityThaumcraftBoss} - the shared base of this port's Thaumcraft bosses
 * (currently just {@link EntityCultistLeader}). Real {@link Monster} subclass (the original was {@code
 * EntityMob} too).
 *
 * Real, working boss bar via {@link ServerBossEvent} (the modern replacement for the original's own {@code
 * BossInfoServer}) - added/removed per-player via {@link #startSeenByPlayer}/{@link #stopSeenByPlayer}
 * (modern names for the original's {@code addTrackingPlayer}/{@code removeTrackingPlayer}), percentage kept
 * in sync every tick. Real spawn-timer invulnerability window ({@link #isInvulnerableTo}), real periodic
 * self-heal (1 HP every 30 ticks), real "enrage" mechanic: any single hit over 35 damage is capped at 35 and
 * grants a real Regeneration/Strength/Haste buff scaled to the excess damage, once every 200 ticks - a real,
 * portable subset of the original's own {@code attackEntityFrom} override. Real home-position restriction
 * comes free from vanilla's own {@link net.minecraft.world.entity.Mob#restrictTo} (see {@code
 * EntityCultist}'s own doc comment for why the original needed to hand-roll this and this port doesn't).
 *
 * Real champion-mod tie-in, restored 2026-09-15 now that {@code registerChampionAttribute} makes bosses
 * eligible: {@link #onChampionRolled} is called by {@link thaumcraft.common.lib.utils.ChampionUtils#makeChampion}
 * instead of the generic mob treatment (flat health/damage modifiers, a heal, a name overwrite) every other
 * real hostile creature gets - matches the true original's own {@code EntityUtils#makeChampion}'s
 * {@code instanceof EntityThaumcraftBoss} branch, which skipped all of that for bosses and called their own
 * {@code generateName()} instead (the original's real reason: a boss's own title/name is worth more than the
 * generic "&lt;Modifier&gt; &lt;Entity&gt;" naming, so only the champion mod's own one-time stat buffs and
 * per-event effects still apply - see {@code ChampionModifier}'s own doc comment for the type split). Default
 * no-op (this base boss has no title-based name of its own to re-derive); override to fold the rolled
 * champion mod's name into a real title-based display name, the way {@link EntityCultistLeader}/
 * {@link EntityEldritchWarden}/{@link EntityEldritchGolem} each do.
 *
 * Real, documented cuts vs the original: no multiplayer target-scaling (the original's own {@code
 * updateAITasks} tracked per-attacker aggro in a hashmap and buffed max health/attack damage based on how
 * many distinct players were fighting it, via {@code EntityUtils.HPBUFF}/{@code DMGBUFF} - that whole
 * mechanic lives in the original's own {@code EntityUtils.java}, a large, still-unported utility class with
 * real Baubles dependencies of its own; porting it just for this one mechanic isn't proportionate yet). No
 * special floating-item drop visual ({@code EntityUtils#entityDropSpecialItem}, same unported dependency -
 * {@link EntityCultistLeader}'s own primordial pearl drop uses a plain {@link #spawnAtLocation} instead).
 */
public class EntityThaumcraftBoss extends Monster {

	protected final ServerBossEvent bossEvent;
	private int spawnTimer;
	private int angerTimer;

	protected EntityThaumcraftBoss(EntityType<? extends EntityThaumcraftBoss> type, Level level) {
		super(type, level);
		bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
		bossEvent.setDarkenScreen(true);
		xpReward = 50;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.KNOCKBACK_RESISTANCE, 0.95)
				.add(Attributes.FOLLOW_RANGE, 40.0);
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

	public int getSpawnTimer() {
		return spawnTimer;
	}

	protected void setSpawnTimer(int ticks) {
		spawnTimer = ticks;
	}

	/** See this class's own doc comment for why bosses get this real hook instead of the generic mob
	 * champion treatment. */
	public void onChampionRolled(Component modName) {
	}

	@Override
	public boolean isInvulnerableTo(DamageSource source) {
		return super.isInvulnerableTo(source) || spawnTimer > 0;
	}

	@Override
	public boolean isPushable() {
		return super.isPushable() && !isInvulnerableTo(damageSources().starve());
	}

	@Override
	public boolean canPickUpLoot() {
		return false;
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof IEldritchMob || super.isAlliedTo(other);
	}

	@Override
	public void aiStep() {
		super.aiStep();
		if (spawnTimer > 0) {
			spawnTimer--;
		}
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
}
