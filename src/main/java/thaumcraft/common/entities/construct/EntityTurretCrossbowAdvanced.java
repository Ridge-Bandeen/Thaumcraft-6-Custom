package thaumcraft.common.entities.construct;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Team;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityTurretCrossbowAdvanced} - the base turret with real armor plating
 * (40 max health, 8 armor - real {@link #createAttributes} additions on top of {@link EntityTurretCrossbow}'s
 * own), a faster fire rate ({@code RangedAttackGoal}'s own 20-40 tick cooldown vs the base turret's 20-60),
 * and a real, player-configurable target filter synced as a 4-bit {@code FLAGS} byte (animal/mob/player/
 * friendly, exactly matching the original's own {@code Utils.getBit}/{@code setBit}/{@code clearBit} bit
 * layout) - opens a real {@link thaumcraft.common.container.TurretConfigMenu} on a non-sneak interact,
 * matching the original's own GUI id 17.
 *
 * <p>Completely replaces {@link EntityTurretCrossbow}'s own goal set in {@link #registerGoals} rather than
 * calling {@code super} (matching the original's own {@code initEntityAI} fully clearing and rebuilding both
 * task lists) - reuses the base class's own {@link EntityTurretCrossbow.WatchTargetGoal} directly rather than
 * the original's own verbatim-duplicated inner class of the same shape, a real, small improvement over the
 * original's own copy-paste.
 *
 * <p>The original's own hand-rolled {@code EntityAINearestValidTarget} (a whole custom {@code EntityAITarget}
 * subclass, needed in 1.12 because {@code EntityAINearestAttackableTarget} had no predicate hook) collapses
 * here into vanilla's own {@link NearestAttackableTargetGoal}'s real {@code Predicate<LivingEntity>} parameter
 * - {@link #isGoodTarget} carries the real team/ownership/friendly-fire logic the original spread across
 * {@code canAttackClass}/{@code isGoodTarget}/{@code shouldContinueExecuting}, using real {@link TamableAnimal}
 * ownership (matches {@link EntityOwnedConstruct}'s own reuse) instead of the original's own {@code
 * IEntityOwnable}. Real, documented cut: the original's own extra player-specific distance scaling (sneaking
 * players need to be 20% closer, invisible players scaled by their own armor-derived visibility fraction)
 * has no simple modern equivalent and is dropped - a player target still needs real line of sight and real
 * detection range, just without that extra fudge factor.
 *
 * <p>Real, documented cut, same reasoning as the base turret: no ammo slot in the GUI (real dispenser auto-
 * reload already covers it, matching {@link EntityTurretCrossbow}'s own documented cut). Real detailed,
 * non-cuboid OBJ mesh renderer restored 2026-09-16 - see {@link
 * thaumcraft.client.render.RenderTurretCrossbowAdvanced}'s own doc comment for the real modern OBJ-loading
 * path (the vanilla {@code ModelPart}/{@code CubeListBuilder} cuboid system this port used for every other
 * mob model genuinely can't represent this one's real curved/angled bow limbs - a real, separate "genuine
 * OBJ-mesh renderer" task, not a same-day model port like {@link EntityTurretCrossbow}'s own {@code
 * ModelCrossbow} was).
 */
public class EntityTurretCrossbowAdvanced extends EntityTurretCrossbow implements MenuProvider {

	private static final EntityDataAccessor<Byte> DATA_FLAGS =
			SynchedEntityData.defineId(EntityTurretCrossbowAdvanced.class, EntityDataSerializers.BYTE);

	public EntityTurretCrossbowAdvanced(EntityType<? extends EntityTurretCrossbowAdvanced> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityTurretCrossbow.createAttributes()
				.add(Attributes.MAX_HEALTH, 40.0)
				.add(Attributes.ARMOR, 8.0)
				.add(Attributes.STEP_HEIGHT, 0.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FLAGS, (byte) 0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new RangedAttackGoal(this, 0.0, 20, 40, 24.0f));
		goalSelector.addGoal(2, new WatchTargetGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, true, false, this::isGoodTarget));
		setTargetMob(true);
	}

	// --- real synced target-filter flags, exactly matching the original's own bit layout. Plain inline
	// bit ops rather than reusing the original's own Utils.getBit/setBit/clearBit - that whole grab-bag
	// utility class isn't ported yet (still 1.12-style, a real separate task), not worth pulling in for
	// three one-line helpers this class can trivially reproduce itself (same real "don't drag in a huge
	// unported dependency for a tiny piece of it" reasoning EntityThaumcraftGolem's own DATA_FLAGS already
	// established). ---

	public boolean getTargetAnimal() {
		return getFlag(1);
	}

	public void setTargetAnimal(boolean value) {
		setFlag(1, value);
	}

	public boolean getTargetMob() {
		return getFlag(2);
	}

	public void setTargetMob(boolean value) {
		setFlag(2, value);
	}

	public boolean getTargetPlayer() {
		return getFlag(4);
	}

	public void setTargetPlayer(boolean value) {
		setFlag(4, value);
	}

	public boolean getTargetFriendly() {
		return getFlag(8);
	}

	public void setTargetFriendly(boolean value) {
		setFlag(8, value);
	}

	private boolean getFlag(int bit) {
		return (entityData.get(DATA_FLAGS) & bit) != 0;
	}

	private void setFlag(int bit, boolean value) {
		byte flags = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, (byte) (value ? (flags | bit) : (flags & ~bit)));
		setTarget(null);
	}

	/** Real port of the original's own {@code canAttackClass} - which broad category a candidate falls into,
	 * gated on the matching target-filter flag; a targeted player additionally needs either real PVP to be
	 * server-enabled or the friendly-fire flag on (matching the original's own real "auto-disable player
	 * targeting the moment PVP turns out to be off" self-correction). */
	private boolean canAttackType(LivingEntity candidate) {
		if (candidate instanceof Animal && !(candidate instanceof Enemy) && getTargetAnimal()) {
			return true;
		}
		if (candidate instanceof Enemy && getTargetMob()) {
			return true;
		}
		if (candidate instanceof Player && getTargetPlayer()) {
			if (level() instanceof ServerLevel serverLevel && !serverLevel.getServer().isPvpAllowed() && !getTargetFriendly()) {
				setTargetPlayer(false);
				return false;
			}
			return true;
		}
		return false;
	}

	/** Real port of the original's own {@code isGoodTarget} - team allegiance and real {@link TamableAnimal}
	 * ownership both gate friendly-fire the same way the original's own {@code IEntityOwnable} checks did (see
	 * this class's own doc comment for why ownership is checked via {@link TamableAnimal} here instead). */
	private boolean isGoodTarget(LivingEntity candidate) {
		if (candidate == this || !candidate.isAlive() || !canAttackType(candidate)) {
			return false;
		}
		Team myTeam = getTeam();
		Team theirTeam = candidate.getTeam();
		if (myTeam != null && theirTeam == myTeam && !getTargetFriendly()) {
			return false;
		}
		if (myTeam != null && theirTeam != myTeam && getTargetFriendly()) {
			return false;
		}
		if (isTame()) {
			UUID myOwner = getOwnerUUID();
			if (candidate instanceof TamableAnimal candidateTame && candidateTame.isTame()) {
				boolean sameOwner = myOwner != null && myOwner.equals(candidateTame.getOwnerUUID());
				if (sameOwner && !getTargetFriendly()) {
					return false;
				}
				if (!sameOwner && getTargetFriendly()) {
					return false;
				}
			} else if (!(candidate instanceof Player) && getTargetFriendly()) {
				return false;
			}
			if (candidate instanceof Player player && myOwner != null && myOwner.equals(player.getUUID()) && !getTargetFriendly()) {
				return false;
			}
		} else if (candidate instanceof Player player && player.getAbilities().invulnerable && !getTargetFriendly()) {
			return false;
		}
		return true;
	}

	@Override
	public void tick() {
		super.tick();
		// Real port of the original's own onUpdate check: PVP turning off mid-fight against a real player
		// (who isn't the owner) drops the target immediately, rather than waiting for the target-selector's
		// own next scheduled reassessment.
		if (!level().isClientSide && getTarget() instanceof Player player && !isOwnedBy(player)
				&& level() instanceof ServerLevel serverLevel && !serverLevel.getServer().isPvpAllowed()) {
			setTarget(null);
		}
	}

	/** Real port of the original's own {@code getEyeHeight() = 1.0F} fixed override - same final-method
	 * workaround as {@link EntityTurretCrossbow#getDefaultDimensions}. */
	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(1.0f);
	}

	/** Real port of the original's own {@code knockBack} override - armor plating soaks most of the shove. */
	@Override
	public void knockback(double strength, double x, double z) {
		super.knockback(strength, x / 10.0, z / 10.0);
	}

	/** Real port of the original's own {@code move} override - same near-stationary shape as {@link
	 * EntityTurretCrossbow#move}, just an even smaller real divisor (heavier chassis). */
	@Override
	public void move(MoverType type, Vec3 movement) {
		super.move(type, new Vec3(movement.x / 15.0, movement.y, movement.z / 15.0));
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!level().isClientSide && isOwnedBy(player) && isAlive()) {
			if (player.isShiftKeyDown()) {
				playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
				dropAmmo();
				spawnAtLocation(new ItemStack(ItemsTC.turretPlacerAdvanced.get()), 0.5f);
				discard();
				player.swing(hand);
			} else if (player instanceof ServerPlayer serverPlayer) {
				serverPlayer.openMenu(this);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public Component getDisplayName() {
		return getName();
	}

	@Override
	public net.minecraft.world.inventory.AbstractContainerMenu createMenu(int containerId, net.minecraft.world.entity.player.Inventory inventory, Player player) {
		return new thaumcraft.common.container.TurretConfigMenu(containerId, inventory, this);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putByte("targets", entityData.get(DATA_FLAGS));
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		entityData.set(DATA_FLAGS, tag.getByte("targets"));
	}

	/** Its parts when destroyed: the {@code thaumcraft:entities/dismantle/turret_crossbow_advanced} loot table (the original's chances; Looting
	 * adds 0.15 to each a level). */
	@Override
	protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable() {
		return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "entities/dismantle/turret_crossbow_advanced"));
	}

	/** (Nothing extra: its held tool and parts drop on their own, never the equipment roll.) */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
	}
}
