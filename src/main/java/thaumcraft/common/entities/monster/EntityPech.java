package thaumcraft.common.entities.monster;

import java.util.List;
import java.util.Map;

import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.RangedAttackGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.items.casters.foci.FocusEffectAir;
import thaumcraft.common.items.casters.foci.FocusEffectCurse;
import thaumcraft.common.items.casters.foci.FocusEffectEarth;
import thaumcraft.common.items.casters.foci.FocusEffectFire;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import thaumcraft.common.items.casters.foci.FocusMediumProjectile;

/**
 * Replaces the old (1.12.2) {@code EntityPech} - a wandering goblin-trader that starts hostile, can be
 * tamed by feeding it a valued item, and picks up loose items into a personal 9-slot "loot bag" while
 * untamed. Real, faithfully-ported mechanics: 3 equipment-driven combat types (melee/bow/wand, switched
 * live whenever its mainhand item changes, exactly like the original's own {@code setCombatTask}), the
 * anger system (attacking one Pech alerts every other Pech within 32 blocks), heal-over-time, and the real
 * wand attack building a fresh {@link FocusPackage} each shot (root + projectile + one of 5 random
 * elemental/curse effects) via the same real {@link FocusEngine}/caster API this port's wand items already
 * use.
 *
 * <b>Real, documented simplifications (forced by missing dependencies, not design choices)</b>:
 * <ul>
 * <li>Taming only recognizes {@code Items.ENDER_PEARL} as a "valued" item (the original's own single
 * explicit {@code valuedItems} entry, ported verbatim) - the original's *real* general case was a fallback
 * onto {@code ThaumcraftCraftingManager#getObjectTags}, the same thousands-of-entries item-aspect ("object
 * tag") database blocking the Potion Sprayer's real aspect cost (see the port-tracking memory's "config
 * system" gap) - not available, so no other item can raise a Pech's Desire score yet.</li>
 * <li><b>No trading GUI yet</b> - {@code ContainerPech}/{@code InventoryPech}/{@code GuiPech} (a real
 * input-slot-plus-4-output-slots trade menu) and the {@code tradeInventory} reward tables are a separate,
 * real chunk of GUI work, deliberately not attempted in this pass. A tamed Pech's right-click currently
 * falls through to vanilla's default interact (nothing happens) - {@link #mobInteract} is the real hook
 * point for wiring the menu up later.</li>
 * <li>No enchantment-based arrow damage/knockback/fire-aspect scaling on the bow attack (Power/Punch/Flame)
 * - modern MC's data-component-based enchantment API is real extra complexity this pass skips; the arrow
 * still does real, difficulty-scaled damage and can still roll a poison tip.</li>
 * <li>No natural biome-based spawning - matches the session-wide precedent every other custom hostile mob
 * in this port already established (see {@code EntityCultist}'s own doc comment): no {@code
 * SpawnPlacements}/biome-tag wiring exists for any of them yet. Spawnable via spawn egg/summon only.</li>
 * <li>No custom model geometry - the original's own {@code ModelPech} was a {@code ModelBiped} with two
 * extra decorative cuboids (a jowl/jaw piece and a back-mounted "pack"); this port reuses vanilla's own
 * {@code HumanoidModel} baked from {@code ModelLayers.PLAYER}, the exact same "plain humanoid, real bundled
 * texture, no custom geometry" technique {@code RenderCultistKnight} already established - see {@code
 * thaumcraft.client.render.RenderPech}. The mouth-flap ("mumble") animation this dropped geometry would
 * have driven is gone, but the real particle/sound cues it accompanied (trading chime, anger alert) are
 * kept via {@link #handleEntityEvent}.</li>
 * <li>No custom {@code SoundEvent}s - like every sound-emitting entity in this port so far, no Thaumcraft-
 * specific {@code SoundEvent} registry exists yet (the original's own {@code pech_idle}/{@code pech_hit}/
 * {@code pech_death}/{@code pech_charge}/{@code pech_trade} .ogg files are still sitting unused in the
 * asset tree). Substituted with thematically close vanilla villager sounds.</li>
 * </ul>
 */
public class EntityPech extends Monster implements RangedAttackMob {

	private static final EntityDataAccessor<Integer> DATA_TYPE =
			SynchedEntityData.defineId(EntityPech.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_ANGER =
			SynchedEntityData.defineId(EntityPech.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Boolean> DATA_TAMED =
			SynchedEntityData.defineId(EntityPech.class, EntityDataSerializers.BOOLEAN);

	/** The original's own single real entry - see this class's own doc comment for why nothing else
	 * qualifies yet. */
	private static final Map<Item, Integer> VALUED_ITEMS = Map.of(Items.ENDER_PEARL, 15);

	public final NonNullList<ItemStack> loot = NonNullList.withSize(9, ItemStack.EMPTY);
	/** Set true/false by the (not yet built - see this class's own doc comment) trading menu while open;
	 * real field kept now so {@link TradePlayerGoal} and future menu code share it, matching the original. */
	public boolean trading;

	private int chargeCount;

	public EntityPech(EntityType<? extends EntityPech> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 30.0)
				.add(Attributes.ATTACK_DAMAGE, 6.0)
				.add(Attributes.MOVEMENT_SPEED, 0.5)
				.add(Attributes.ARMOR, 2.0);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_TYPE, 0);
		builder.define(DATA_ANGER, 0);
		builder.define(DATA_TAMED, false);
	}

	public int getPechType() {
		return entityData.get(DATA_TYPE);
	}

	public void setPechType(int type) {
		entityData.set(DATA_TYPE, type);
	}

	public int getAnger() {
		return entityData.get(DATA_ANGER);
	}

	public void setAnger(int anger) {
		entityData.set(DATA_ANGER, anger);
	}

	public boolean isTamed() {
		return entityData.get(DATA_TAMED);
	}

	public void setTamed(boolean tamed) {
		entityData.set(DATA_TAMED, tamed);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(1, new TradePlayerGoal(this));
		goalSelector.addGoal(3, new ItemGotoGoal(this));
		goalSelector.addGoal(5, new OpenDoorGoal(this, true));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.5));
		goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.6));
		goalSelector.addGoal(9, new LookAtPlayerGoal(this, Player.class, 3.0f, 1.0f));
		goalSelector.addGoal(10, new LookAtPlayerGoal(this, LivingEntity.class, 8.0f));
		goalSelector.addGoal(11, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this));
		targetSelector.addGoal(2, new PechTargetGoal(this));
		setCombatTask();
	}

	/** Live-swaps the active attack goal (and the avoid-player goal) to match the current mainhand item and
	 * tamed state - real port of the original's own {@code setCombatTask}. */
	private void setCombatTask() {
		if (level().isClientSide) return;
		// Fresh Goal instances each call, removed/matched by type rather than by a stable field reference -
		// registerGoals() (and so this, via the constructor) runs from inside Mob's own constructor, before
		// any of EntityPech's own instance field initializers would have run, so a cached "this attack
		// goal" field would still be null the first time through (a real NPE hit and fixed live via this
		// chunk's own GameTest, the same class of pitfall AltarFocusGoal's own "allocate fresh each call"
		// shape in EntityCultistCleric already avoids).
		goalSelector.removeAllGoals(g -> g instanceof RangedAttackGoal || g instanceof MeleeAttackGoal);
		ItemStack held = getMainHandItem();
		if (held.is(Items.BOW) || held.is(ItemsTC.pechWand.get())) {
			goalSelector.addGoal(2, new RangedAttackGoal(this, 0.6, 20, 50, 15.0f));
		} else {
			goalSelector.addGoal(2, new MeleeAttackGoal(this, 0.6, false));
		}
		goalSelector.removeAllGoals(g -> g instanceof AvoidEntityGoal);
		if (!isTamed()) {
			goalSelector.addGoal(4, new AvoidEntityGoal<>(this, Player.class, 8.0f, 0.5, 0.6));
		}
	}

	@Override
	public void setItemSlot(EquipmentSlot slot, ItemStack stack) {
		super.setItemSlot(slot, stack);
		if (!level().isClientSide && slot == EquipmentSlot.MAINHAND) {
			setCombatTask();
		}
	}

	/** Test-support accessor - {@code goalSelector} is protected on {@link net.minecraft.world.entity.Mob},
	 * not visible from the GameTest package. */
	public boolean hasActiveGoal(Class<? extends Goal> type) {
		return goalSelector.getAvailableGoals().stream().anyMatch(g -> type.isInstance(g.getGoal()));
	}

	@Override
	public void performRangedAttack(LivingEntity target, float distanceFactor) {
		if (getPechType() == 2) {
			ItemStack pickup = new ItemStack(Items.ARROW);
			if (random.nextFloat() < 0.2f) {
				pickup = new ItemStack(Items.TIPPED_ARROW);
				pickup.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
						PotionContents.EMPTY.withEffectAdded(new MobEffectInstance(MobEffects.POISON, 40)));
			}
			Arrow arrow = new Arrow(level(), getX(), getEyeY() - 0.1, getZ(), pickup, null);
			arrow.setOwner(this);
			double dx = target.getX() - getX();
			double dy = target.getBoundingBox().minY + target.getBbHeight() / 3.0 - arrow.getY();
			double dz = target.getZ() - getZ();
			double horizontal = Math.sqrt(dx * dx + dz * dz);
			int difficultyId = level().getDifficulty().ordinal();
			arrow.shoot(dx, dy + horizontal * 0.2, dz, 1.6f, (float) (14 - difficultyId * 4));
			arrow.setBaseDamage(distanceFactor * 2.0 + random.nextGaussian() * 0.25 + difficultyId * 0.11);
			playSound(SoundEvents.ARROW_SHOOT, 1.0f, 1.0f / (random.nextFloat() * 0.4f + 0.8f));
			level().addFreshEntity(arrow);
		} else if (getPechType() == 1) {
			swing(InteractionHand.MAIN_HAND);
			FocusPackage p = new FocusPackage(this);
			FocusMediumRoot root = new FocusMediumRoot();
			double offset = distanceTo(target) / 6.0f;
			root.setupFromCasterToTarget(this, target, offset);
			p.addNode(root);
			FocusMediumProjectile fp = new FocusMediumProjectile();
			fp.initialize();
			fp.getSetting("speed").setValue(2);
			p.addNode(fp);
			IFocusElement effect = switch (random.nextInt(5)) {
				case 0 -> new FocusEffectCurse();
				case 1 -> new FocusEffectFlux();
				case 2 -> new FocusEffectEarth();
				case 3 -> new FocusEffectAir();
				default -> new FocusEffectFire();
			};
			p.addNode(effect);
			FocusEngine.castFocusPackage(this, p, true);
		}
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		RandomSource rand = random;
		switch (rand.nextInt(20)) {
			case 0, 12 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemsTC.pechWand.get()));
			case 1 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_SWORD));
			case 3 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_AXE));
			case 5 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			case 6 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_AXE));
			case 7 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.FISHING_ROD));
			case 8 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE_PICKAXE));
			case 9 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_PICKAXE));
			case 2, 4, 10, 11, 13 -> setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
			default -> { }
		}
		ItemStack held = getMainHandItem();
		if (held.is(ItemsTC.pechWand.get())) {
			setPechType(1);
			setDropChance(EquipmentSlot.MAINHAND, 0.1f);
		} else if (!held.isEmpty() && held.is(Items.BOW)) {
			setPechType(2);
		}
		setCanPickUpLoot(rand.nextFloat() < 0.75f * difficulty.getSpecialMultiplier());
		setCombatTask();
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public boolean canBeLeashed() {
		return false;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		int stacks = 0;
		for (ItemStack stack : loot) {
			if (!stack.isEmpty()) stacks++;
		}
		return stacks < 5 && super.removeWhenFarAway(distanceSquared);
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		for (ItemStack stack : loot) {
			if (!stack.isEmpty() && random.nextFloat() < 0.33f) {
				spawnAtLocation(stack.copy());
			}
		}
		super.dropCustomDeathLoot(level, damageSource, recentlyHit);
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 16) {
			// idle mumble - no geometry to animate, nothing to do client-side
		} else if (id == 17) {
			// trade/anger chatter - no geometry to animate, nothing to do client-side
		} else if (id == 18) {
			spawnHappyParticles();
		} else if (id == 19) {
			spawnAngryParticles();
		} else {
			super.handleEntityEvent(id);
		}
	}

	private void spawnHappyParticles() {
		for (int i = 0; i < 5; i++) {
			double dx = random.nextGaussian() * 0.02;
			double dy = random.nextGaussian() * 0.02;
			double dz = random.nextGaussian() * 0.02;
			level().addParticle(ParticleTypes.HAPPY_VILLAGER,
					getX() + random.nextFloat() * getBbWidth() * 2.0f - getBbWidth(),
					getY() + 0.5 + random.nextFloat() * getBbHeight(),
					getZ() + random.nextFloat() * getBbWidth() * 2.0f - getBbWidth(), dx, dy, dz);
		}
	}

	private void spawnAngryParticles() {
		for (int i = 0; i < 5; i++) {
			double dx = random.nextGaussian() * 0.02;
			double dy = random.nextGaussian() * 0.02;
			double dz = random.nextGaussian() * 0.02;
			level().addParticle(ParticleTypes.ANGRY_VILLAGER,
					getX() + random.nextFloat() * getBbWidth() * 2.0f - getBbWidth(),
					getY() + 0.5 + random.nextFloat() * getBbHeight(),
					getZ() + random.nextFloat() * getBbWidth() * 2.0f - getBbWidth(), dx, dy, dz);
		}
	}

	/** Test-support accessor - {@code becomeAngryAt} is private, and GameTest has no reliable way to hand a
	 * real non-creative {@link Player} attacker through {@link #hurt} (a mock player is unavoidably
	 * creative). */
	public void debugBecomeAngryAt(Entity entity) {
		becomeAngryAt(entity);
	}

	private void becomeAngryAt(Entity entity) {
		if (entity instanceof Player player && player.isCreative()) return;
		if (getAnger() <= 0) {
			level().broadcastEntityEvent(this, (byte) 19);
			playSound(SoundEvents.VILLAGER_NO, getSoundVolume(), getVoicePitch());
		}
		if (entity instanceof LivingEntity living) setTarget(living);
		setAnger(400 + random.nextInt(400));
		setTamed(false);
		setCombatTask();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		Entity direct = source.getEntity();
		if (direct instanceof Player) {
			List<EntityPech> nearby = level().getEntitiesOfClass(EntityPech.class, getBoundingBox().inflate(32.0, 16.0, 32.0));
			for (EntityPech other : nearby) {
				other.becomeAngryAt(direct);
			}
			becomeAngryAt(direct);
		}
		return super.hurt(source, amount);
	}

	@Override
	public void aiStep() {
		if (getAnger() > 0) {
			setAnger(getAnger() - 1);
		}
		if (!level().isClientSide && getAnger() > 0 && getTarget() != null) {
			if (chargeCount > 0) chargeCount--;
			if (chargeCount == 0) {
				chargeCount = 100;
				playSound(SoundEvents.VILLAGER_NO, getSoundVolume(), getVoicePitch());
			}
			level().broadcastEntityEvent(this, (byte) 17);
		}
		if (level().isClientSide) {
			if (random.nextInt(15) == 0 && getAnger() > 0) spawnAngryParticles();
			if (random.nextInt(25) == 0 && isTamed()) spawnHappyParticles();
		}
		super.aiStep();
		if (!level().isClientSide && tickCount % 40 == 0) {
			heal(1.0f);
		}
	}

	public boolean canPickupItem(ItemStack stack) {
		if (stack.isEmpty()) return false;
		if (!isTamed() && isValued(stack)) return true;
		for (ItemStack slot : loot) {
			if (slot.isEmpty()) return true;
			if (ItemStack.isSameItemSameComponents(stack, slot) && stack.getCount() + slot.getCount() <= slot.getMaxStackSize()) return true;
		}
		return false;
	}

	/** Real port of the original's own {@code pickupItem} - returns whatever's left of {@code entityItem}
	 * (empty if fully absorbed). */
	public ItemStack tryPickupItem(ItemStack entityItem) {
		if (entityItem.isEmpty()) return ItemStack.EMPTY;
		if (isTamed() || !isValued(entityItem)) {
			for (int a = 0; a < loot.size(); a++) {
				ItemStack slot = loot.get(a);
				if (!entityItem.isEmpty() && !slot.isEmpty() && slot.getCount() < slot.getMaxStackSize()
						&& ItemStack.isSameItemSameComponents(entityItem, slot)) {
					int room = slot.getMaxStackSize() - slot.getCount();
					int move = Math.min(entityItem.getCount(), room);
					slot.grow(move);
					entityItem.shrink(move);
				}
			}
			for (int a = 0; a < loot.size(); a++) {
				if (!entityItem.isEmpty() && loot.get(a).isEmpty()) {
					loot.set(a, entityItem.copy());
					return ItemStack.EMPTY;
				}
			}
			return entityItem;
		}
		if (random.nextInt(10) < getValue(entityItem)) {
			setTamed(true);
			setCombatTask();
			level().broadcastEntityEvent(this, (byte) 18);
		}
		entityItem.shrink(1);
		return entityItem;
	}

	public boolean isValued(ItemStack stack) {
		return !stack.isEmpty() && VALUED_ITEMS.containsKey(stack.getItem());
	}

	public int getValue(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		return VALUED_ITEMS.getOrDefault(stack.getItem(), 0);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag nbt) {
		super.addAdditionalSaveData(nbt);
		nbt.putInt("PechType", getPechType());
		nbt.putInt("Anger", getAnger());
		nbt.putBoolean("Tamed", isTamed());
		ContainerHelper.saveAllItems(nbt, loot, level().registryAccess());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag nbt) {
		super.readAdditionalSaveData(nbt);
		if (nbt.contains("PechType")) setPechType(nbt.getInt("PechType"));
		setAnger(nbt.getInt("Anger"));
		setTamed(nbt.getBoolean("Tamed"));
		ContainerHelper.loadAllItems(nbt, loot, level().registryAccess());
		setCombatTask();
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.VILLAGER_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return SoundEvents.VILLAGER_HURT;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.VILLAGER_DEATH;
	}

	/** Pauses movement while a (not yet built) trading menu has this Pech open - real port of the original's
	 * own {@code AIPechTradePlayer}. */
	private static class TradePlayerGoal extends Goal {
		private final EntityPech pech;

		TradePlayerGoal(EntityPech pech) {
			this.pech = pech;
			setFlags(java.util.EnumSet.of(Flag.MOVE));
		}

		@Override
		public boolean canUse() {
			return pech.isAlive() && !pech.isInWater() && pech.isTamed() && pech.onGround() && pech.trading;
		}

		@Override
		public void start() {
			pech.getNavigation().stop();
		}

		@Override
		public void stop() {
			pech.trading = false;
		}
	}

	/** Only targets while genuinely angry - real port of the original's own {@code
	 * AINearestAttackableTargetPech}. */
	private static class PechTargetGoal extends NearestAttackableTargetGoal<Player> {
		private final EntityPech pech;

		PechTargetGoal(EntityPech pech) {
			super(pech, Player.class, true);
			this.pech = pech;
		}

		@Override
		public boolean canUse() {
			return pech.getAnger() != 0 && super.canUse();
		}
	}

	/** Walks to and absorbs the nearest pickupable dropped item within 16 blocks - real port of the
	 * original's own {@code AIPechItemEntityGoto}. */
	private static class ItemGotoGoal extends Goal {
		private final EntityPech pech;
		private static final float RANGE = 16.0f;
		private ItemEntity targetEntity;
		private int count;

		ItemGotoGoal(EntityPech pech) {
			this.pech = pech;
			setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.LOOK));
		}

		@Override
		public boolean canUse() {
			if (--count > 0) return false;
			double range = Double.MAX_VALUE;
			List<ItemEntity> items = pech.level().getEntitiesOfClass(ItemEntity.class, pech.getBoundingBox().inflate(RANGE));
			targetEntity = null;
			for (ItemEntity item : items) {
				if (!pech.canPickupItem(item.getItem())) continue;
				double distance = item.distanceToSqr(pech);
				if (distance >= range || distance > RANGE * RANGE) continue;
				range = distance;
				targetEntity = item;
			}
			return targetEntity != null;
		}

		@Override
		public boolean canContinueToUse() {
			return targetEntity != null && targetEntity.isAlive() && !pech.getNavigation().isDone()
					&& targetEntity.distanceToSqr(pech) < RANGE * RANGE;
		}

		@Override
		public void stop() {
			targetEntity = null;
		}

		@Override
		public void start() {
			pech.getNavigation().moveTo(targetEntity, 1.5 * pech.getAttributeValue(Attributes.MOVEMENT_SPEED));
			count = 0;
		}

		@Override
		public void tick() {
			pech.getLookControl().setLookAt(targetEntity, 30.0f, 30.0f);
			if (pech.hasLineOfSight(targetEntity) && --count <= 0) {
				count = 4 + pech.getRandom().nextInt(4);
				pech.getNavigation().moveTo(targetEntity, 1.5 * pech.getAttributeValue(Attributes.MOVEMENT_SPEED));
			}
			if (pech.distanceToSqr(targetEntity.getX(), targetEntity.getBoundingBox().minY, targetEntity.getZ()) <= 1.5) {
				count = 0;
				int before = targetEntity.getItem().getCount();
				ItemStack remainder = pech.tryPickupItem(targetEntity.getItem());
				if (!remainder.isEmpty()) {
					targetEntity.setItem(remainder);
				} else {
					targetEntity.discard();
				}
				if (remainder.isEmpty() || remainder.getCount() != before) {
					pech.level().playSound(null, targetEntity.getX(), targetEntity.getY(), targetEntity.getZ(),
							SoundEvents.ITEM_PICKUP, net.minecraft.sounds.SoundSource.NEUTRAL, 0.2f,
							((pech.getRandom().nextFloat() - pech.getRandom().nextFloat()) * 0.7f + 1.0f) * 2.0f);
				}
			}
		}
	}
}
