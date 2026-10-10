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
 * Also the original's aggro: damage dealt is tallied per attacker, the boss switches to whoever has out-damaged its
 * target by enough, and extra players fighting it add health/damage steps ({@link #HPBUFF}/{@link #DMGBUFF}, up to
 * 5 - the original's counting quirks kept). Anger is synced so clients show the angry particles, and an enraging
 * player is told. Bosses that don't override the drop leave a floating primordial pearl ({@link #dropSpecialItem})
 * and a rare loot bag (the Eldritch Warden and Golem dropped nothing in the port until 2026-09-30).
 */
public class EntityThaumcraftBoss extends Monster {

	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> AGGRO =
			net.minecraft.network.syncher.SynchedEntityData.defineId(EntityThaumcraftBoss.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

	/** The original's EntityUtils.HPBUFF/DMGBUFF: one step per extra player, +50 health / +0.5 damage each. */
	public static final net.minecraft.world.entity.ai.attributes.AttributeModifier[] HPBUFF = new net.minecraft.world.entity.ai.attributes.AttributeModifier[5];
	public static final net.minecraft.world.entity.ai.attributes.AttributeModifier[] DMGBUFF = new net.minecraft.world.entity.ai.attributes.AttributeModifier[5];
	static {
		for (int a = 0; a < 5; a++) {
			HPBUFF[a] = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "boss_health_buff_" + (a + 1)), 50.0,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
			DMGBUFF[a] = new net.minecraft.world.entity.ai.attributes.AttributeModifier(
					net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "boss_damage_buff_" + (a + 1)), 0.5,
					net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation.ADD_VALUE);
		}
	}

	protected final ServerBossEvent bossEvent;
	private int spawnTimer;
	/** Damage dealt by each attacker (entity id), as the original. */
	private final java.util.Map<Integer, Integer> aggro = new java.util.HashMap<>();

	protected EntityThaumcraftBoss(EntityType<? extends EntityThaumcraftBoss> type, Level level) {
		super(type, level);
		bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.PROGRESS);
		bossEvent.setDarkenScreen(true);
		xpReward = 50;
	}

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(AGGRO, 0);
	}

	public int getAnger() {
		return entityData.get(AGGRO);
	}

	public void setAnger(int ticks) {
		entityData.set(AGGRO, ticks);
	}

	/** The original's EntityUtils.entityDropSpecialItem: a floating, glowing drop rising from {@code dropHeight}. */
	public static net.minecraft.world.entity.item.ItemEntity dropSpecialItem(net.minecraft.world.entity.LivingEntity entity,
			net.minecraft.world.item.ItemStack stack, float dropHeight) {
		if (stack.isEmpty()) return null;
		thaumcraft.common.entities.EntitySpecialItem item = new thaumcraft.common.entities.EntitySpecialItem(entity.level(),
				entity.getX(), entity.getY() + dropHeight, entity.getZ(), stack);
		item.setDefaultPickUpDelay();
		item.setDeltaMovement(0.0, 0.1, 0.0);
		java.util.Collection<net.minecraft.world.entity.item.ItemEntity> captured = entity.captureDrops();
		if (captured != null) {
			captured.add(item);
		} else {
			entity.level().addFreshEntity(item);
		}
		return item;
	}

	@Override
	protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		dropSpecialItem(this, new net.minecraft.world.item.ItemStack(thaumcraft.api.items.ItemsTC.primordialPearl.get()), getBbHeight() / 2.0f);
		spawnAtLocation(new net.minecraft.world.item.ItemStack(thaumcraft.api.items.ItemsTC.lootBagRare.get()), 1.5f);
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

	public void setSpawnTimer(int ticks) {
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

	/** As the original's updateAITasks: no AI while the spawn timer runs (rising, or the golem losing its head). Being
	 * "immobile" skips the AI step and zeroes the movement input; gravity still applies. */
	@Override
	protected boolean isImmobile() {
		return super.isImmobile() || spawnTimer > 0;
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
		if (getAnger() > 0) {
			setAnger(getAnger() - 1);
		}
		if (level().isClientSide && random.nextInt(15) == 0 && getAnger() > 0) {
			level().addParticle(net.minecraft.core.particles.ParticleTypes.ANGRY_VILLAGER,
					getX() + random.nextFloat() * getBbWidth() - getBbWidth() / 2.0, getBoundingBox().minY + getBbHeight() + random.nextFloat() * 0.5,
					getZ() + random.nextFloat() * getBbWidth() - getBbWidth() / 2.0,
					random.nextGaussian() * 0.02, random.nextGaussian() * 0.02, random.nextGaussian() * 0.02);
		}
		if (!level().isClientSide) {
			bossEvent.setProgress(getHealth() / getMaxHealth());
			if (tickCount % 30 == 0) {
				heal(1.0f);
			}
			if (getTarget() != null && tickCount % 20 == 0) {
				updateAggro();
			}
		}
	}

	/** The original's target switching and multiplayer scaling, quirks kept: the entity looked up for a switch is the one
	 * under the previous id, and only players reached through a switch are counted (so one player never buffs it). */
	private void updateAggro() {
		java.util.List<Integer> dl = new java.util.ArrayList<>();
		int players = 0;
		int hei = getTarget().getId();
		int ld;
		int ad = ld = aggro.getOrDefault(hei, 0);
		Entity newTarget = null;
		for (Integer ei : aggro.keySet()) {
			int ca = aggro.get(ei);
			if (ca > ad + 25 && ca > ad * 1.1 && ca > ld) {
				newTarget = level().getEntity(hei);
				if (newTarget == null || !newTarget.isAlive() || distanceToSqr(newTarget) > 16384.0) {
					dl.add(ei);
				} else {
					hei = ei;
					ld = ei;
					if (newTarget instanceof net.minecraft.world.entity.player.Player) {
						++players;
					}
				}
			}
		}
		for (Integer ei : dl) {
			aggro.remove(ei);
		}
		if (newTarget instanceof net.minecraft.world.entity.LivingEntity living && hei != getTarget().getId()) {
			setTarget(living);
		}
		float om = getMaxHealth();
		var health = getAttribute(Attributes.MAX_HEALTH);
		var damage = getAttribute(Attributes.ATTACK_DAMAGE);
		for (int a = 0; a < 5; a++) {
			if (damage != null) damage.removeModifier(DMGBUFF[a].id());
			if (health != null) health.removeModifier(HPBUFF[a].id());
		}
		for (int a = 0; a < Math.min(5, players - 1); a++) {
			if (health != null) health.addTransientModifier(HPBUFF[a]);
			if (damage != null) damage.addTransientModifier(DMGBUFF[a]);
		}
		double mm = getMaxHealth() / om;
		setHealth((float) (getHealth() * mm));
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (!level().isClientSide && source.getEntity() instanceof net.minecraft.world.entity.LivingEntity attacker) {
			aggro.merge(attacker.getId(), (int) amount, Integer::sum);
		}
		if (!level().isClientSide && amount > 35.0f) {
			if (getAnger() == 0) {
				addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, (int) (amount / 15.0f)));
				addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 200, (int) (amount / 10.0f)));
				addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 200, (int) (amount / 40.0f)));
				setAnger(200);
				if (source.getEntity() instanceof net.minecraft.world.entity.player.Player player) {
					player.displayClientMessage(Component.empty().append(getName()).append(" ")
							.append(Component.translatable("tc.boss.enrage")), true);
				}
			}
			amount = 35.0f;
		}
		return super.hurt(source, amount);
	}

	@Override
	public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		thaumcraft.common.lib.utils.EntityChecks.saveHome(this, tag);
	}

	@Override
	public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		thaumcraft.common.lib.utils.EntityChecks.loadHome(this, tag);
	}
}
