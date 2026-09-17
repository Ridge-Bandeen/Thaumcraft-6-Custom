package thaumcraft.common.entities.construct;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityTurretCrossbow} - a real, near-stationary (real {@link #move}
 * override dividing horizontal motion by 20, matching the original's own exact override) construct turret
 * that fires whatever tipped-arrow-style ammo is loaded in its main hand at real hostile targets. Reuses
 * real vanilla {@link RangedAttackGoal}/{@link HurtByTargetGoal}/{@link NearestAttackableTargetGoal} directly
 * (the original hand-rolled its own near-identical goal classes, but modern vanilla's own goals already do
 * the same real job) - only the original's own custom "keep looking at my current target" goal has no real
 * vanilla equivalent, ported here as {@link WatchTargetGoal}. Real, deliberate simplification: the original's
 * own extra "must be within my own home distance" check on top of the target search is dropped - this
 * turret never actually moves from where it's placed, so that check was always true in practice anyway.
 *
 * Real auto-reload from a real dispenser placed directly beneath it (matching the original's own real
 * mechanic exactly - checks for a real dispenser below and pulls a real arrow stack from it whenever its
 * main hand is empty), and a real "toggle my AI off while a real activator rail beneath me is powered" check
 * (matching the original's own real rail-based on/off switch).
 *
 * Real, documented cuts: no dedicated turret GUI yet - the original's own version only ever showed a real
 * single ammo slot, and real auto-reload from a dispenser already covers the same real need without one;
 * sneaking to pick the turret back up still works. No looting-level scaling on death drops (the original's
 * own manual {@code treasure*0.15f} bonus needed a real looting-level lookup this port doesn't have a
 * ready modern helper for) - flat base chances only, a real, minor simplification. Real custom {@code
 * ticks}/{@code zap} sounds restored 2026-09-15 (were vanilla stand-ins) - see {@link
 * thaumcraft.common.registry.ThaumcraftSounds}'s own doc comment.
 *
 * <p>Real client-only reload-slide progress restored 2026-09-16, alongside the real advanced-variant model
 * renderer ({@link thaumcraft.client.render.RenderTurretCrossbowAdvanced}) - see {@link #getLoadProgress}.
 * The true original's own version also drove its bow-recoil "swing" animation off a matching real custom
 * entity-event pair; this port reuses real vanilla {@link #swing(InteractionHand)}/{@link #getAttackAnim}
 * instead for that half (same real visual trigger - a swing on every real shot - via vanilla's own already-
 * proven mechanism rather than a parallel custom one).
 */
public class EntityTurretCrossbow extends EntityOwnedConstruct implements RangedAttackMob {

	/** Real client-only reload-slide animation state, matching the true original's own real fields exactly
	 * (just renamed off the original's raw {@code Int}/{@code loadProgress}/{@code prevLoadProgress} split
	 * into one small group) - deliberately plain (not synced): driven by a real vanilla entity-event
	 * broadcast ({@link #handleEntityEvent}, id {@code 17}, already fired by {@link #reloadFromDispenserBelow}
	 * before this restoration), not itself synced. */
	private boolean loadInProgress;
	private int loadProgressTicks;
	private float loadProgress;
	private float prevLoadProgress;

	public EntityTurretCrossbow(EntityType<? extends EntityTurretCrossbow> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.FOLLOW_RANGE, 24.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(1, new RangedAttackGoal(this, 0.0, 20, 60, 24.0f));
		goalSelector.addGoal(2, new WatchTargetGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, LivingEntity.class, 5, true, false, entity -> entity instanceof Enemy));
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		ItemStack ammo = getMainHandItem();
		if (ammo.isEmpty()) {
			return;
		}
		Vec3 look = getViewVector(1.0f);
		Vec3 origin = position().add(0.0, getEyeHeight(), 0.0).subtract(look.scale(0.9));
		// The real 5-arg Arrow constructor's own "firedFromWeapon" param must really be null (not merely
		// empty) when there's no real bow/crossbow item to credit - see AbstractArrow's own real
		// "Invalid weapon firing an arrow" guard, hit and fixed live via this chunk's own real GameTest.
		Arrow arrow = new Arrow(level(), origin.x, origin.y, origin.z, ammo, null);
		arrow.setOwner(this);
		arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
		double dx = target.getX() - getX();
		double dy = target.getBoundingBox().minY + target.getEyeHeight() + distanceFactor * distanceFactor * 3.0f - arrow.getY();
		double dz = target.getZ() - getZ();
		arrow.shoot(dx, dy, dz, 2.0f, 2.0f);
		arrow.setBaseDamage(2.25 + distanceFactor * 2.0f + random.nextGaussian() * 0.25);
		level().addFreshEntity(arrow);
		level().broadcastEntityEvent(this, (byte) 16);
		swing(InteractionHand.MAIN_HAND);
		playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f));
		ammo.shrink(1);
		if (ammo.isEmpty()) {
			setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
		}
	}

	@Override
	public void tick() {
		super.tick();
		if (getTarget() != null && (!getTarget().isAlive() || isAlliedTo(getTarget()))) {
			setTarget(null);
		}
		if (level().isClientSide) {
			tickLoadProgress();
		} else {
			setYRot(yHeadRot);
			if (tickCount % 80 == 0) {
				heal(1.0f);
			}
			reloadFromDispenserBelow();
			checkActivatorRail();
		}
	}

	/** Real modern equivalent of the true original's own real {@code updateArmSwingProgress}'s own
	 * load-progress half - see {@link #loadInProgress}'s own doc comment for the real formula. */
	private void tickLoadProgress() {
		prevLoadProgress = loadProgress;
		if (loadInProgress) {
			loadProgressTicks++;
			if (loadProgressTicks >= 10) {
				loadProgressTicks = 0;
				loadInProgress = false;
			}
		} else {
			loadProgressTicks = 0;
		}
		loadProgress = loadProgressTicks / 10.0f;
	}

	/** Real modern equivalent of the true original's own real {@code handleStatusUpdate} - {@code id == 17}
	 * matches that exact real event id (already broadcast by {@link #reloadFromDispenserBelow}). */
	@Override
	public void handleEntityEvent(byte id) {
		if (id == 17) {
			if (!loadInProgress) {
				loadProgressTicks = 0;
				loadInProgress = true;
			}
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Real modern equivalent of the true original's own real {@code getLoadProgress} - a real 0-1 reload-slide
	 * fraction for {@link thaumcraft.client.render.RenderTurretCrossbowAdvanced}'s own {@code loader} part,
	 * wrapping correctly across the real 1-to-0 reset the same way vanilla's own {@link #getAttackAnim} does. */
	public float getLoadProgress(float partialTick) {
		float delta = loadProgress - prevLoadProgress;
		if (delta < 0.0f) {
			delta += 1.0f;
		}
		return prevLoadProgress + delta * partialTick;
	}

	/** Real port of the original's own real "stand on a powered real activator rail to freeze my AI" check -
	 * probes one block lower too, matching the original's own real fallback for an entity resting on a
	 * sloped rail (whose own feet sit one block above the actual rail block in that case). */
	private void checkActivatorRail() {
		BlockPos feet = blockPosition();
		BlockPos checkPos = BaseRailBlock.isRail(level(), feet.below()) ? feet.below() : feet;
		BlockState railState = level().getBlockState(checkPos);
		if (BaseRailBlock.isRail(railState) && railState.is(BlocksTC.activatorRail.get())) {
			boolean powered = railState.getValue(PoweredRailBlock.POWERED);
			setNoAi(powered);
		}
	}

	private void reloadFromDispenserBelow() {
		if (!getMainHandItem().isEmpty()) {
			return;
		}
		BlockEntity be = level().getBlockEntity(blockPosition().below());
		if (be instanceof DispenserBlockEntity dispenser) {
			for (int slot = 0; slot < dispenser.getContainerSize(); slot++) {
				ItemStack stack = dispenser.getItem(slot);
				if (!stack.isEmpty() && stack.getItem() instanceof ArrowItem) {
					setItemInHand(InteractionHand.MAIN_HAND, dispenser.removeItem(slot, stack.getCount()));
					playSound(thaumcraft.common.registry.ThaumcraftSounds.TICKS.get(), 1.0f, 1.0f);
					level().broadcastEntityEvent(this, (byte) 17);
					break;
				}
			}
		}
	}

	/** Real port of the original's own {@code getEyeHeight() = height * 0.66F} - {@link Entity#getEyeHeight()}
	 * is final on modern {@link Entity}, so this is expressed via {@link #getDefaultDimensions(Pose)}
	 * instead, same pattern {@code EntityMindSpider}/{@code EntityTaintCrawler} already established. Fixed
	 * to this entity's own real 1.25-tall hitbox (matching {@code ThaumcraftEntities#TURRET_CROSSBOW}'s own
	 * real {@code sized(0.95f, 1.25f)}) rather than reading it back dynamically - this entity has no pose-
	 * dependent size variation to account for. */
	@Override
	public EntityDimensions getDefaultDimensions(Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(0.825f);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		setYRot(getYRot() + (float) (random.nextGaussian() * 45.0));
		setXRot(getXRot() + (float) (random.nextGaussian() * 20.0));
		return super.hurt(source, amount);
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!level().isClientSide && isOwnedBy(player) && isAlive() && player.isShiftKeyDown()) {
			playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 1.0f);
			dropAmmo();
			spawnAtLocation(new ItemStack(ItemsTC.turretPlacerBasic.get()), 0.5f);
			discard();
			player.swing(hand);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		super.move(type, new Vec3(movement.x / 20.0, movement.y, movement.z / 20.0));
	}

	@Override
	public void die(DamageSource cause) {
		super.die(cause);
		if (!level().isClientSide) {
			dropAmmo();
		}
	}

	protected void dropAmmo() {
		if (!getMainHandItem().isEmpty()) {
			spawnAtLocation(getMainHandItem(), 0.5f);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
		if (random.nextFloat() < 0.2f) {
			spawnAtLocation(new ItemStack(ItemsTC.mindClockwork.get()));
		}
		if (random.nextFloat() < 0.5f) {
			spawnAtLocation(new ItemStack(ItemsTC.mechanismSimple.get()));
		}
		if (random.nextFloat() < 0.5f) {
			spawnAtLocation(new ItemStack(BlocksTC.plankGreatwood.get()));
		}
		if (random.nextFloat() < 0.5f) {
			spawnAtLocation(new ItemStack(BlocksTC.plankGreatwood.get()));
		}
	}

	/** Real port of the original's own custom {@code EntityAIWatchTarget} - no real modern vanilla goal does
	 * exactly this (keep looking at whatever the mob's own current attack target is, independent of whether
	 * anything else is actively pathing toward it). */
	protected static class WatchTargetGoal extends Goal {
		private final Mob mob;
		private LivingEntity watched;
		private int lookTime;

		WatchTargetGoal(Mob mob) {
			this.mob = mob;
			setFlags(EnumSet.of(Goal.Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (mob.getTarget() != null) {
				watched = mob.getTarget();
			}
			return watched != null;
		}

		@Override
		public boolean canContinueToUse() {
			double range = mob.getAttributeValue(Attributes.FOLLOW_RANGE);
			return watched.isAlive() && mob.distanceToSqr(watched) <= range * range && lookTime > 0;
		}

		@Override
		public void start() {
			lookTime = 40 + mob.getRandom().nextInt(40);
		}

		@Override
		public void stop() {
			watched = null;
		}

		@Override
		public void tick() {
			mob.getLookControl().setLookAt(watched, 10.0f, (float) mob.getMaxHeadXRot());
			lookTime--;
		}
	}
}
