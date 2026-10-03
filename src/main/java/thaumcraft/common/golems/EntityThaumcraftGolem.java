package thaumcraft.common.golems;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.WallClimberNavigation;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.IGolemProperties;

/**
 * Replaces the old (1.12.2) {@code EntityThaumcraftGolem} (823 lines). Extends {@link TamableAnimal}
 * directly instead of porting the original's own {@code EntityOwnedConstruct} base class - vanilla's
 * {@code TamableAnimal} already ships real synced owner-UUID tracking, team deference, and (critically)
 * ready-made {@link OwnerHurtByTargetGoal}/{@link OwnerHurtTargetGoal}/{@link FollowOwnerGoal} that take
 * the exact same constructor shape ({@code (TamableAnimal, double, float, float)} for the follow goal) as
 * the original's own hand-rolled {@code AIOwnerHurtByTarget}/{@code AIOwnerHurtTarget}/{@code AIFollowOwner}
 * - reusing them is a straight, real code-savings win, not just a shortcut. The unused breeding/ageing
 * surface inherited from {@code Animal}/{@code AgeableMob} is simply never invoked (only {@link #isFood}
 * needed a real override, always {@code false}), same as every other "wrong-shaped but convenient" vanilla
 * base class already accepted elsewhere in this port. {@code EntityOwnedConstruct} itself is NOT ported as
 * a shared class this chunk - if a future entity (the original's arcane bore / crossbow turrets) needs
 * "owned by a player" behaviour, it can extend {@code TamableAnimal} the same way.
 *
 * The original's home-position tracking ({@code setHomePosAndDistance}/{@code getHomePosition}/
 * {@code hasHome}) is replaced with vanilla {@link Mob}'s own built-in {@code restrictTo}/
 * {@code getRestrictCenter}/{@code hasRestriction} - functionally identical, no reason to hand-roll it.
 * Golem "properties" (material/head/arms/legs/addon/rank, see {@link GolemProperties}) are synced as a
 * single raw {@code long} (modern {@code EntityDataSerializers.LONG} exists - no need for the original's
 * own two-{@code int} packing hack); colour and the following-owner/in-combat flags are two separate
 * synced bytes rather than one packed int, purely a modern-code-clarity choice with the same real content.
 *
 * <b>Real, documented cuts from the original</b> (status current as of the real ranged-combat chunk that
 * closed the 3rd bullet below - the task-assignment/seals system named in this comment's own earlier
 * version as "a separate, much larger arc... not touched" was, in fact, touched in the meantime and is
 * real now; see the 1st bullet):
 * <ul>
 *   <li>The {@code TaskHandler}/{@code Task} seal-assignment system IS real now
 *       ({@link thaumcraft.common.golems.ai.AIGoto}/{@code AIGotoBlock}/{@code AIGotoEntity}, wired into
 *       {@link #createAI} below) - a golem with a real assigned task actively walks to it, taking priority
 *       over the plain-wander fallback. {@code AIGotoHome} specifically is still real, just via vanilla
 *       {@link Mob#restrictTo} instead (see this class's own earlier note on that).</li>
 *   <li>No {@code PathNavigateGolemGround}/{@code PathNavigateGolemAir} - {@link #createGolemNavigation}
 *       swaps between plain vanilla {@link GroundPathNavigation} and {@link FlyingPathNavigation} on the
 *       {@code FLYER} trait (the true original's own hand-rolled subclasses turned out, on direct
 *       inspection, to carry almost no real trait-specific logic - mostly generic pathing quality-of-life
 *       already covered by modern vanilla's own navigation classes). {@code CLIMBER} golems DO get a real,
 *       genuine equivalent though: vanilla's own {@link net.minecraft.world.entity.ai.navigation.WallClimberNavigation}
 *       (the same real class {@link net.minecraft.world.entity.monster.Spider} itself uses), paired with a
 *       real {@link #onClimbable} override reading the {@link #isBesideClimbableBlock} state that was
 *       already tracked/synced but previously never actually wired into movement physics - see both methods'
 *       own doc comments. The true original's own {@code PathNavigateClimber} class is referenced by its own
 *       {@code EntityThaumcraftGolem.java} but is genuinely absent from the entire pristine-original tree
 *       this port was decompiled from (confirmed via exhaustive search) - not recoverable, so this is a real
 *       faithful modern substitute rather than a line-for-line port.</li>
 *   <li>A real ranged-attack {@link net.minecraft.world.entity.ai.goal.RangedAttackGoal} IS added now for
 *       {@code RANGED}-trait golems with a real {@code arms.function} (the "DARTS" arm's
 *       {@link thaumcraft.common.golems.parts.GolemArmDart}, the first real {@code IArmFunction} this port
 *       has shipped) - see {@link #createAI}. {@link #performRangedAttack} was already a real, working
 *       passthrough to {@code arms.function} before this; it just had nothing to call yet.</li>
 *   <li>Melee damage no longer hand-rolls enchantment/knockback math (fire aspect, sharpness, knockback
 *       enchants) - modern enchantments are data-driven and apply automatically through
 *       {@code super.doHurtTarget}; only the on-kill rank-XP hook is added on top.</li>
 *   <li>{@code processInteract} is all there (restored 2026-09-28): sneak-click to take the golem back as its
 *       placer item, the Golem Bell's follow/stay toggle (GOLEMDIRECT), and dye recolouring.</li>
 *   <li>As the original: on its first tick after loading, or when suffocating in a wall or falling out of the
 *       world, it goes home ({@link #goHome}); cactus can't hurt it; with PvP off it drops player targets; a DEFT
 *       golem leaves what it hits "recently hit" (so its kills drop XP and player-kill loot); only HEAVY golems
 *       make footsteps; its eyes sit 0.7 up.</li>
 * </ul>
 */
public class EntityThaumcraftGolem extends TamableAnimal implements IGolemAPI, RangedAttackMob {

	private static final EntityDataAccessor<Long> DATA_PROPS =
			SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.LONG);
	private static final EntityDataAccessor<Byte> DATA_COLOR =
			SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Byte> DATA_FLAGS =
			SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.BYTE);
	private static final EntityDataAccessor<Boolean> DATA_CLIMBING =
			SynchedEntityData.defineId(EntityThaumcraftGolem.class, EntityDataSerializers.BOOLEAN);

	private static final int FLAG_FOLLOWING_OWNER = 1;
	private static final int FLAG_IN_COMBAT = 2;

	private static final EquipmentSlot[] CARRY_SLOTS = { EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND };

	/** Not synced - matches the original's own plain (non-{@code DataParameter}) field. */
	private int rankXp;

	/** The task this golem is currently working, if any - see {@code thaumcraft.common.golems.ai.AIGoto}'s
	 * own doc comment. Not synced or persisted (matches the original's own plain field): a task is
	 * short-lived, server-side-only bookkeeping, not real entity state worth surviving a reload. */
	private thaumcraft.api.golems.tasks.Task task;

	public EntityThaumcraftGolem(EntityType<? extends EntityThaumcraftGolem> type, Level level) {
		super(type, level);
	}

	public thaumcraft.api.golems.tasks.Task getTask() {
		return task;
	}

	public void setTask(thaumcraft.api.golems.tasks.Task task) {
		this.task = task;
	}

	/** Real translation of the original's own speed-multiplier formula, used by {@code AIGoto}'s family of
	 * task-seeking goals in place of a flat speed constant. */
	public double getGolemMoveSpeed() {
		IGolemProperties props = getProperties();
		double speed = 1.0 + props.getRank() * 0.025;
		if (props.hasTrait(EnumGolemTrait.LIGHT)) speed += 0.2;
		if (props.hasTrait(EnumGolemTrait.HEAVY)) speed -= 0.175;
		if (props.hasTrait(EnumGolemTrait.FLYER)) speed -= 0.33;
		if (props.hasTrait(EnumGolemTrait.WHEELED)) speed += 0.25;
		return speed;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MOVEMENT_SPEED, 0.3)
				.add(Attributes.MAX_HEALTH, 10.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.FOLLOW_RANGE, 40.0)
				.add(Attributes.STEP_HEIGHT, 0.6);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_PROPS, GolemProperties.fromLong(0L).toLong());
		builder.define(DATA_COLOR, (byte) 0);
		builder.define(DATA_FLAGS, (byte) 0);
		builder.define(DATA_CLIMBING, false);
	}

	@Override
	protected void registerGoals() {
		// left empty - createAI() (called from updateEntityAttributes(), itself called from setProperties())
		// rebuilds both selectors for real once the golem's parts are actually known, matching the original's
		// own tasks.taskEntries.clear() + rebuild-in-createAI() approach.
	}

	/** Real entry point for constructing an assembled golem (the future {@code TileGolemBuilder}/
	 * {@code ItemGolemPlacer} chunk will call this) - sets its parts, restores any carried rank XP, and
	 * anchors its home restriction to wherever it was built. */
	public void initializeGolem(IGolemProperties properties, int startingRankXp) {
		this.rankXp = startingRankXp;
		restrictTo(blockPosition(), properties.hasTrait(EnumGolemTrait.SCOUT) ? 48 : 32);
		setProperties(properties);
	}

	@Override
	public IGolemProperties getProperties() {
		return GolemProperties.fromLong(entityData.get(DATA_PROPS));
	}

	@Override
	public void setProperties(IGolemProperties prop) {
		entityData.set(DATA_PROPS, prop.toLong());
		updateEntityAttributes();
	}

	@Override
	public byte getGolemColor() {
		return entityData.get(DATA_COLOR);
	}

	public void setGolemColor(byte color) {
		entityData.set(DATA_COLOR, color);
	}

	private boolean getFlag(int bit) {
		return (entityData.get(DATA_FLAGS) & bit) != 0;
	}

	private void setFlag(int bit, boolean value) {
		byte flags = entityData.get(DATA_FLAGS);
		entityData.set(DATA_FLAGS, (byte) (value ? (flags | bit) : (flags & ~bit)));
	}

	public boolean isFollowingOwner() {
		return getFlag(FLAG_FOLLOWING_OWNER);
	}

	public void setFollowingOwner(boolean following) {
		setFlag(FLAG_FOLLOWING_OWNER, following);
		createAI();
	}

	@Override
	public boolean isInCombat() {
		return getFlag(FLAG_IN_COMBAT);
	}

	public void setInCombat(boolean combat) {
		setFlag(FLAG_IN_COMBAT, combat);
	}

	@Override
	public void setTarget(LivingEntity target) {
		super.setTarget(target);
		setInCombat(target != null);
	}

	public boolean isBesideClimbableBlock() {
		return entityData.get(DATA_CLIMBING);
	}

	public void setBesideClimbableBlock(boolean climbing) {
		entityData.set(DATA_CLIMBING, climbing);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	/** Golems never breed - inherited only because {@link net.minecraft.world.entity.animal.Animal}
	 * declares it abstract. */
	@Override
	public net.minecraft.world.entity.AgeableMob getBreedOffspring(ServerLevel level, net.minecraft.world.entity.AgeableMob otherParent) {
		return null;
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	private void updateEntityAttributes() {
		IGolemProperties props = getProperties();
		int mh = 10 + props.getMaterial().healthMod;
		if (props.hasTrait(EnumGolemTrait.FRAGILE)) {
			mh = (int) (mh * 0.75);
		}
		mh += props.getRank();
		getAttribute(Attributes.MAX_HEALTH).setBaseValue(mh);
		getAttribute(Attributes.STEP_HEIGHT).setBaseValue(props.hasTrait(EnumGolemTrait.WHEELED) ? 0.5 : 0.6);
		BlockPos home = hasRestriction() ? getRestrictCenter() : blockPosition();
		restrictTo(home, props.hasTrait(EnumGolemTrait.SCOUT) ? 48 : 32);
		getAttribute(Attributes.FOLLOW_RANGE).setBaseValue(props.hasTrait(EnumGolemTrait.SCOUT) ? 56.0 : 40.0);
		this.navigation = createGolemNavigation(props);
		if (props.hasTrait(EnumGolemTrait.FIGHTER)) {
			double damage = props.getMaterial().damage;
			if (props.hasTrait(EnumGolemTrait.BRUTAL)) {
				damage = Math.max(damage * 1.5, damage + 1.0);
			}
			damage += props.getRank() * 0.25;
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(damage);
		} else {
			getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(0.0);
		}
		createAI();
	}

	private net.minecraft.world.entity.ai.navigation.PathNavigation createGolemNavigation(IGolemProperties props) {
		if (props.hasTrait(EnumGolemTrait.FLYER)) {
			return new FlyingPathNavigation(this, level());
		}
		if (props.hasTrait(EnumGolemTrait.CLIMBER)) {
			// Real vanilla WallClimberNavigation - the same real class Spider itself uses for "climb any
			// wall it's pushed against" pathing. When plain ground pathing can't find a real route (e.g. a
			// sheer wall blocks it), it falls back to steering straight at the target instead of giving up -
			// combined with the real onClimbable() override below, physics (not custom node logic) does the
			// actual climbing. The true original's own PathNavigateClimber is a real class referenced by its
			// own EntityThaumcraftGolem.java but never actually present anywhere in the pristine-original
			// tree this port was decompiled from (confirmed via exhaustive search - a real gap in the
			// original snapshot itself, not something this port lost) - WallClimberNavigation is this port's
			// own real, faithful modern substitute rather than a guess at the missing class's content.
			return new WallClimberNavigation(this, level());
		}
		return new GroundPathNavigation(this, level());
	}

	/** Real vanilla climbing hook (see {@link net.minecraft.world.entity.monster.Spider#onClimbable} for the
	 * exact same real precedent) - {@link #isBesideClimbableBlock} was already tracked and synced
	 * ({@link #setBesideClimbableBlock} is called every tick for {@code CLIMBER}-trait golems in
	 * {@link #aiStep}) but nothing ever read it back into real movement physics until now. Once this returns
	 * {@code true}, vanilla's own {@code LivingEntity#travel}/{@code handleOnClimbable} takes over: fall
	 * distance resets, horizontal friction stops applying, and a small upward nudge lets the golem actually
	 * ascend a sheer wall it's pushed against - matching the true original's own real
	 * {@code setBesideClimbableBlock(collidedHorizontally)} trick exactly (any sturdy wall counts, not just
	 * a real climbable block, same as vanilla's own Spider). */
	@Override
	public boolean onClimbable() {
		return isBesideClimbableBlock() || super.onClimbable();
	}

	private void createAI() {
		goalSelector.removeAllGoals(g -> true);
		targetSelector.removeAllGoals(g -> true);
		IGolemProperties props = getProperties();

		if (isFollowingOwner()) {
			goalSelector.addGoal(4, new FollowOwnerGoal(this, 1.0, 10.0f, 2.0f));
		} else {
			// higher priority than the wander fallback - a golem with a real task to do (see getTask()/
			// setTask() and thaumcraft.common.golems.ai's own doc comments) takes it over just wandering.
			// Entity-position tasks (SealGuard) are tried slightly before block-position ones (SealLumber),
			// matching the original's own AIGotoEntity=3/AIGotoBlock=4 ordering.
			goalSelector.addGoal(3, new thaumcraft.common.golems.ai.AIGotoEntity(this));
			goalSelector.addGoal(4, new thaumcraft.common.golems.ai.AIGotoBlock(this));
			goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 1.0));
		}
		goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0f));
		goalSelector.addGoal(9, new RandomLookAroundGoal(this));

		if (props.hasTrait(EnumGolemTrait.FIGHTER)) {
			// Real, matches the original's own AIArrowAttack=1/EntityAIAttackMelee=2 priority exactly - a
			// ranged-capable golem prefers to keep its distance, only falling back to melee once vanilla's
			// own RangedAttackGoal#canUse finds it can't. Only added if a real arm function actually exists
			// to drive it (see GolemArmDart, the first real IArmFunction this port has shipped) - a RANGED
			// golem with no wired-in function just falls through to melee, same real original behavior.
			if (props.hasTrait(EnumGolemTrait.RANGED) && props.getArms().function != null) {
				goalSelector.addGoal(1, props.getArms().function.getRangedAttackAI(this));
			}
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.15, false));
			if (isFollowingOwner()) {
				targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
				targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
			}
			targetSelector.addGoal(3, new HurtByTargetGoal(this));
		}
	}

	@Override
	public void aiStep() {
		super.aiStep();
		IGolemProperties props = getProperties();
		if (props.hasTrait(EnumGolemTrait.FLYER)) {
			setNoGravity(true);
		}
		// as the original: each part's own per-tick function (the roller legs' dust, the flyer legs' motes)
		if (props.getHead().function != null) props.getHead().function.onUpdateTick(this);
		if (props.getArms().function != null) props.getArms().function.onUpdateTick(this);
		if (props.getLegs().function != null) props.getLegs().function.onUpdateTick(this);
		if (props.getAddon().function != null) props.getAddon().function.onUpdateTick(this);
		if (level().isClientSide) {
			return;
		}
		if (firstRun) {
			firstRun = false;
			if (hasRestriction() && !blockPosition().equals(getRestrictCenter())) goHome();
		}
		if (getTarget() != null && getTarget().isDeadOrDying()) {
			setTarget(null);
		}
		if (getTarget() instanceof Player && level().getServer() != null && !level().getServer().isPvpAllowed()) {
			setTarget(null);
		}
		if (getTarget() != null && props.hasTrait(EnumGolemTrait.RANGED) && distanceToSqr(getTarget()) > 1024.0) {
			setTarget(null);
		}
		if (tickCount % (props.hasTrait(EnumGolemTrait.REPAIR) ? 40 : 100) == 0) {
			heal(1.0f);
		}
		if (props.hasTrait(EnumGolemTrait.CLIMBER)) {
			setBesideClimbableBlock(horizontalCollision);
		}
	}

	@Override
	public boolean causeFallDamage(float fallDistance, float multiplier, DamageSource source) {
		IGolemProperties props = getProperties();
		if (props.hasTrait(EnumGolemTrait.FLYER) || props.hasTrait(EnumGolemTrait.CLIMBER)) {
			return false;
		}
		return super.causeFallDamage(fallDistance, multiplier, source);
	}

	private boolean firstRun = true;

	/** The original's goHome: to the top of the column at its home, if there's room there; otherwise it stays put. */
	private void goHome() {
		BlockPos home = getRestrictCenter();
		double x = home.getX() + 0.5;
		double y = home.getY();
		double z = home.getZ() + 0.5;
		BlockPos p = home;
		boolean roof = false;
		while (!roof && p.getY() < level().getMaxBuildHeight()) {
			BlockPos up = p.above();
			if (level().getBlockState(up).blocksMotion()) {
				roof = true;
			} else {
				y++;
				p = up;
			}
		}
		if (!roof) return;
		double ox = getX(), oy = getY(), oz = getZ();
		teleportTo(x, y, z);
		if (!level().noCollision(this)) {
			teleportTo(ox, oy, oz);
		} else {
			getNavigation().stop();
		}
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		IGolemProperties props = getProperties();
		if (source.is(DamageTypeTags.IS_FIRE) && props.hasTrait(EnumGolemTrait.FIREPROOF)) {
			return false;
		}
		if (source.is(net.minecraft.world.damagesource.DamageTypes.CACTUS)) {
			return false;
		}
		if (hasRestriction() && (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
				|| source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD))) {
			goHome();
		}
		if (source.is(DamageTypeTags.IS_EXPLOSION) && props.hasTrait(EnumGolemTrait.BLASTPROOF)) {
			amount = Math.min(getMaxHealth() / 2.0f, amount * 0.3f);
		}
		return super.hurt(source, amount);
	}

	@Override
	public boolean doHurtTarget(Entity target) {
		boolean success = super.doHurtTarget(target);
		if (success && target instanceof net.minecraft.world.entity.LivingEntity living && getProperties().hasTrait(EnumGolemTrait.DEFT)) {
			living.lastHurtByPlayerTime = 100;
		}
		if (success && target instanceof Mob mobTarget && mobTarget.isDeadOrDying()) {
			addRankXp(8);
		}
		return success;
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (level().isClientSide || !isOwnedBy(player)) {
			return super.mobInteract(player, hand);
		}
		ItemStack held = player.getItemInHand(hand);
		if (held.getItem() instanceof net.minecraft.world.item.NameTagItem) {
			return super.mobInteract(player, hand);
		}
		if (player.isShiftKeyDown()) {
			// back into a placer item, keeping its parts and rank progress
			playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 1.0f);
			if (getTask() != null) getTask().setReserved(false);
			for (int a = 0; a < CARRY_SLOTS.length; a++) {
				ItemStack carried = getItemBySlot(CARRY_SLOTS[a]);
				if (!carried.isEmpty()) {
					spawnAtLocation(carried.copy(), 0.25f);
					setItemSlot(CARRY_SLOTS[a], ItemStack.EMPTY);
				}
			}
			ItemStack placer = new ItemStack(thaumcraft.api.items.ItemsTC.golemPlacer.get());
			placer.set(thaumcraft.api.items.ThaumcraftDataComponents.GOLEM_PLACER_DATA.get(),
					new thaumcraft.api.items.ThaumcraftDataComponents.GolemPlacerData(getProperties().toLong(), rankXp));
			spawnAtLocation(placer, 0.5f);
			discard();
			player.swing(hand, true);
			return InteractionResult.SUCCESS;
		}
		if (held.getItem() instanceof thaumcraft.common.golems.ItemGolemBell
				&& thaumcraft.api.capabilities.ThaumcraftCapabilities.knowsResearch(player, "GOLEMDIRECT")) {
			if (getTask() != null) getTask().setReserved(false);
			playSound(thaumcraft.common.registry.ThaumcraftSounds.SCAN.get(), 1.0f, 1.0f);
			setFollowingOwner(!isFollowingOwner());
			if (isFollowingOwner()) {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable("golem.follow", ""), true);
				emote(EMOTE_ORDER);
				clearRestriction();
			} else {
				player.displayClientMessage(net.minecraft.network.chat.Component.translatable("golem.stay", ""), true);
				emote(EMOTE_STAY);
				restrictTo(blockPosition(), getProperties().hasTrait(EnumGolemTrait.SCOUT) ? 48 : 32);
			}
			player.swing(hand, true);
			return InteractionResult.SUCCESS;
		}
		if (held.getItem() instanceof DyeItem dye) {
			setGolemColor((byte) (16 - dye.getDyeColor().getId()));
			held.shrink(1);
			playSound(thaumcraft.common.registry.ThaumcraftSounds.ZAP.get(), 1.0f, 1.0f);
			player.swing(hand, true);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.SUCCESS;
	}

	/** Emote particles over the golem's head (the original's entity states 5-9): an order taken, a task given up, the
	 * golem told to stay, a rank gained. */
	public static final byte EMOTE_ORDER = 5;
	public static final byte EMOTE_GAVE_UP = 6;
	public static final byte EMOTE_STAY = 8;
	public static final byte EMOTE_RANK_UP = 9;

	/** Shows an emote to everyone nearby - if the {@code showGolemEmotes} option allows. */
	public void emote(byte emote) {
		if (!level().isClientSide && thaumcraft.common.config.ThaumcraftConfig.SHOW_GOLEM_EMOTES.get()) {
			level().broadcastEntityEvent(this, emote);
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id >= EMOTE_ORDER && id <= EMOTE_RANK_UP) {
			thaumcraft.client.fx.ClientFxHandlers.golemEmote(this, id);
		} else {
			super.handleEntityEvent(id);
		}
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		float b = thaumcraft.common.lib.utils.LootingHelper.level(damageSource) * 0.15f;
		super.dropCustomDeathLoot(level, damageSource, recentlyHit);
		for (ItemStack stack : getProperties().generateComponents()) {
			if (level.random.nextFloat() < 0.3f + b) {
				ItemStack copy = stack.copy();
				if (copy.getCount() > 1) {
					copy.shrink(level.random.nextInt(copy.getCount()));
				}
				if (!copy.isEmpty()) {
					spawnAtLocation(copy, 0.25f);
				}
			}
		}
		for (ItemStack carried : getCarrying()) {
			if (!carried.isEmpty()) {
				spawnAtLocation(carried, 0.25f);
			}
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putLong("props", getProperties().toLong());
		if (hasRestriction()) {
			tag.putLong("homepos", getRestrictCenter().asLong());
			tag.putInt("homerad", (int) getRestrictRadius());
		}
		tag.putBoolean("following", isFollowingOwner());
		tag.putInt("rankXP", rankXp);
		tag.putByte("color", getGolemColor());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		rankXp = tag.getInt("rankXP");
		setGolemColor(tag.getByte("color"));
		if (tag.contains("homepos")) {
			restrictTo(BlockPos.of(tag.getLong("homepos")), tag.contains("homerad") ? tag.getInt("homerad") : 32);
		}
		setFollowingOwner(tag.getBoolean("following"));
		setProperties(GolemProperties.fromLong(tag.getLong("props")));
	}

	// ---- IGolemAPI ----

	@Override
	public LivingEntity getGolemEntity() {
		return this;
	}

	@Override
	public Level getGolemWorld() {
		return level();
	}

	@Override
	public void swingArm() {
		swing(InteractionHand.MAIN_HAND);
	}

	@Override
	public void addRankXp(int xp) {
		IGolemProperties props = getProperties();
		if (!props.hasTrait(EnumGolemTrait.SMART) || level().isClientSide) {
			return;
		}
		int rank = props.getRank();
		if (rank >= 10) {
			return;
		}
		rankXp += xp;
		int xn = (rank + 1) * (rank + 1) * 1000;
		if (rankXp >= xn) {
			rankXp -= xn;
			props.setRank(rank + 1);
			setProperties(props);
			if (thaumcraft.common.config.ThaumcraftConfig.SHOW_GOLEM_EMOTES.get()) {
				emote(EMOTE_RANK_UP);
				playSound(SoundEvents.PLAYER_LEVELUP, 0.25f, 1.0f);
			}
		}
	}

	@Override
	public ItemStack holdItem(ItemStack stack) {
		if (stack.isEmpty()) {
			return stack;
		}
		int slots = getProperties().hasTrait(EnumGolemTrait.HAULER) ? 2 : 1;
		for (int a = 0; a < slots; a++) {
			ItemStack current = getItemBySlot(CARRY_SLOTS[a]);
			if (current.isEmpty()) {
				setItemSlot(CARRY_SLOTS[a], stack);
				return ItemStack.EMPTY;
			}
			if (current.getCount() < current.getMaxStackSize() && ItemStack.isSameItemSameComponents(current, stack)) {
				int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
				stack.shrink(moved);
				current.grow(moved);
				if (stack.isEmpty()) {
					return ItemStack.EMPTY;
				}
			}
		}
		return stack;
	}

	@Override
	public ItemStack dropItem(ItemStack stack) {
		ItemStack out = ItemStack.EMPTY;
		int slots = getProperties().hasTrait(EnumGolemTrait.HAULER) ? 2 : 1;
		for (int a = 0; a < slots; a++) {
			ItemStack current = getItemBySlot(CARRY_SLOTS[a]);
			if (current.isEmpty()) {
				continue;
			}
			if (stack.isEmpty()) {
				out = current.copy();
				setItemSlot(CARRY_SLOTS[a], ItemStack.EMPTY);
			} else if (ItemStack.isSameItemSameComponents(current, stack)) {
				out = current.copy();
				out.setCount(Math.min(stack.getCount(), out.getCount()));
				current.shrink(stack.getCount());
				if (current.isEmpty()) {
					setItemSlot(CARRY_SLOTS[a], ItemStack.EMPTY);
				}
			}
			if (!out.isEmpty()) {
				break;
			}
		}
		if (getProperties().hasTrait(EnumGolemTrait.HAULER) && getItemBySlot(CARRY_SLOTS[0]).isEmpty()
				&& !getItemBySlot(CARRY_SLOTS[1]).isEmpty()) {
			setItemSlot(CARRY_SLOTS[0], getItemBySlot(CARRY_SLOTS[1]).copy());
			setItemSlot(CARRY_SLOTS[1], ItemStack.EMPTY);
		}
		return out;
	}

	@Override
	public int canCarryAmount(ItemStack stack) {
		int ss = 0;
		int slots = getProperties().hasTrait(EnumGolemTrait.HAULER) ? 2 : 1;
		for (int a = 0; a < slots; a++) {
			ItemStack current = getItemBySlot(CARRY_SLOTS[a]);
			if (current.isEmpty()) {
				// the original called .getMaxStackSize() on the (possibly empty) slot stack directly,
				// relying on ItemStack.EMPTY's own max-stack-size being a sensible 64 - not guaranteed
				// modern behaviour, so this uses the candidate stack's own max size for an empty slot instead.
				ss += stack.isEmpty() ? 64 : stack.getMaxStackSize();
			} else if (ItemStack.isSameItemSameComponents(current, stack)) {
				ss += current.getMaxStackSize() - current.getCount();
			}
		}
		return ss;
	}

	@Override
	public boolean canCarry(ItemStack stack, boolean partial) {
		int ca = canCarryAmount(stack);
		if (ca <= 0) {
			return false;
		}
		return partial || ca >= stack.getCount();
	}

	@Override
	public boolean isCarrying(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		int slots = getProperties().hasTrait(EnumGolemTrait.HAULER) ? 2 : 1;
		for (int a = 0; a < slots; a++) {
			ItemStack current = getItemBySlot(CARRY_SLOTS[a]);
			if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
				return true;
			}
		}
		return false;
	}

	@Override
	public NonNullList<ItemStack> getCarrying() {
		if (getProperties().hasTrait(EnumGolemTrait.HAULER)) {
			NonNullList<ItemStack> stacks = NonNullList.withSize(2, ItemStack.EMPTY);
			stacks.set(0, getItemBySlot(CARRY_SLOTS[0]));
			stacks.set(1, getItemBySlot(CARRY_SLOTS[1]));
			return stacks;
		}
		return NonNullList.withSize(1, getItemBySlot(CARRY_SLOTS[0]));
	}

	// ---- RangedAttackMob ----

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		var arms = getProperties().getArms();
		if (arms.function != null) {
			arms.function.onRangedAttack(this, target, distanceFactor);
		}
	}

	/** As the original's canTriggerWalking: only a heavy, earthbound golem makes footsteps. */
	@Override
	protected Entity.MovementEmission getMovementEmission() {
		IGolemProperties props = getProperties();
		return props.hasTrait(EnumGolemTrait.HEAVY) && !props.hasTrait(EnumGolemTrait.FLYER) ? Entity.MovementEmission.ALL : Entity.MovementEmission.NONE;
	}
}
