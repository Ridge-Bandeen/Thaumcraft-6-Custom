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
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
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
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.casters.IFocusElement;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.container.PechMenu;
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
 * <b>Trading (chunk 2)</b>: a tamed Pech opens {@link thaumcraft.common.container.PechMenu} on right-click -
 * feed a valued item into the input slot and click "Trade" (real {@code clickMenuButton} id 0, the modern
 * replacement for the original's own {@code enchantItem}/{@code sendEnchantPacket} pair) to receive a
 * random mix of rewards (drawn from {@link #TRADE_REWARDS}, one real table per {@link #getPechType()}) or
 * an item straight out of the loot bag, exactly matching the original's own {@code
 * ContainerPech#generateContents} value-tier algorithm. A successful high-value trade re-arms the Pech's
 * hostility (matches the original's own quirk of a trade sometimes "using up" the taming).
 *
 * <b>Real, documented simplifications (forced by missing dependencies, not design choices)</b>:
 * <ul>
 * <li>{@code Items.ENDER_PEARL} is still the original's own single explicit {@code valuedItems} entry
 * (ported verbatim), but the original's real general case - any item whose {@code Desire} aspect exceeds 1,
 * via {@code ThaumcraftCraftingManager#getObjectTags} - is real now too, wired to
 * {@link thaumcraft.api.aspects.AspectHelper#getObjectAspects} once {@code ThaumcraftObjectTags} existed to
 * back it (added retroactively - this simplification was believed permanent when chunk 1 shipped, before
 * that class was found already real and modern from an earlier session).</li>
 * <li>{@link #TRADE_REWARDS} is a real, representative reward set per type, not an exhaustive port of the
 * original's own {@code ConfigEntities#tradeInventory} tables - several original reward items (ore
 * clusters, the Eldritch Eye, the Void amulet, enchanted books) reference either unported items or the
 * modern data-component enchantment API's real extra complexity, so this substitutes already-real items of
 * the same rough tier/flavor instead (crystal essence and elemental crystals, cloth armor, Thaumium tools,
 * baubles, candles, experience bottles, golden apples), same "substitute the nearest already-real
 * equivalent" precedent every other chunk this session has used.</li>
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

	private record TradeReward(int tier, java.util.function.Supplier<ItemStack> stack) {
	}

	/** One real reward table per {@link #getPechType()} - see this class's own doc comment for why this is a
	 * representative set, not an exhaustive port of the original's own {@code tradeInventory}. */
	private static final Map<Integer, List<TradeReward>> TRADE_REWARDS = Map.of(
			0, List.of( // forage/melee
					new TradeReward(1, () -> new ItemStack(thaumcraft.api.items.ItemsTC.crystalEssence.get())),
					new TradeReward(1, () -> new ItemStack(Items.BLAZE_POWDER)),
					new TradeReward(2, () -> new ItemStack(Items.BLAZE_ROD)),
					new TradeReward(2, () -> new ItemStack(thaumcraft.api.blocks.BlocksTC.saplingGreatwood.get())),
					new TradeReward(3, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
					new TradeReward(3, () -> new ItemStack(Items.GOLDEN_APPLE)),
					new TradeReward(4, () -> new ItemStack(thaumcraft.api.items.ItemsTC.thaumiumPick.get())),
					new TradeReward(4, () -> new ItemStack(thaumcraft.api.items.ItemsTC.thaumiumAxe.get())),
					new TradeReward(5, () -> new ItemStack(Items.TOTEM_OF_UNDYING)),
					new TradeReward(5, () -> new ItemStack(thaumcraft.api.items.ItemsTC.curiosityBand.get()))),
			1, List.of( // thaum/wand
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.AIR)),
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.EARTH)),
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.FIRE)),
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.WATER)),
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.ORDER)),
					new TradeReward(1, () -> EntityPech.makeCrystal(Aspect.ENTROPY)),
					new TradeReward(2, () -> EntityPech.makeCrystal(Aspect.FLUX)),
					new TradeReward(3, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
					new TradeReward(3, () -> EntityPech.makeCrystal(Aspect.AURA)),
					new TradeReward(4, () -> new ItemStack(thaumcraft.api.items.ItemsTC.clothBoots.get())),
					new TradeReward(4, () -> new ItemStack(thaumcraft.api.items.ItemsTC.clothChest.get())),
					new TradeReward(4, () -> new ItemStack(thaumcraft.api.items.ItemsTC.clothLegs.get())),
					new TradeReward(5, () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)),
					new TradeReward(5, () -> new ItemStack(thaumcraft.api.items.ItemsTC.pechWand.get())),
					new TradeReward(5, () -> new ItemStack(thaumcraft.api.items.ItemsTC.amuletVisCrafted.get()))),
			2, List.of( // stalker/bow
					new TradeReward(1, () -> new ItemStack(thaumcraft.api.blocks.BlocksTC.candles.get(DyeColor.WHITE).get())),
					new TradeReward(2, () -> new ItemStack(Items.GHAST_TEAR)),
					new TradeReward(2, () -> new ItemStack(Items.COMPASS)),
					new TradeReward(3, () -> new ItemStack(Items.EXPERIENCE_BOTTLE)),
					new TradeReward(3, () -> new ItemStack(Items.GOLDEN_APPLE)),
					new TradeReward(4, () -> new ItemStack(Items.SPECTRAL_ARROW, 8)),
					new TradeReward(4, () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE)),
					new TradeReward(5, () -> new ItemStack(thaumcraft.api.items.ItemsTC.voidseerCharm.get()))));

	/** Small, local stand-in for the original's {@code ThaumcraftApiHelper.makeCrystal} - same duplicate
	 * {@code EntityWisp}/{@code ToolEvents} already carry locally rather than porting the whole unconverted
	 * class for one method. */
	private static ItemStack makeCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	public final NonNullList<ItemStack> loot = NonNullList.withSize(9, ItemStack.EMPTY);
	/** Set true/false by {@link thaumcraft.common.container.PechMenu} while open - real field shared with
	 * {@link TradePlayerGoal}, matching the original's own {@code EntityPech.trading} flag. */
	public boolean trading;
	/** The trade menu's own 5 slots (0 = input, 1-4 = output) - a real purpose-built {@link ItemStackHandler}
	 * replacing the original's shared-base {@code InventoryPech}, same "no shared inventory-tile base"
	 * precedent {@code GolemBuilderMenu}'s own doc comment already established. */
	public final ItemStackHandler tradeItems = new ItemStackHandler(5) {
		@Override
		public boolean isItemValid(int slot, ItemStack stack) {
			return slot == 0;
		}
	};

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
		if (stack.isEmpty()) return false;
		if (VALUED_ITEMS.containsKey(stack.getItem())) return true;
		thaumcraft.api.aspects.AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(stack);
		return tags != null && tags.getAmount(thaumcraft.api.aspects.Aspect.DESIRE) > 1;
	}

	public int getValue(ItemStack stack) {
		if (stack.isEmpty()) return 0;
		Integer explicit = VALUED_ITEMS.get(stack.getItem());
		if (explicit != null) return explicit;
		thaumcraft.api.aspects.AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(stack);
		return tags == null ? 0 : Math.min(32, tags.getAmount(thaumcraft.api.aspects.Aspect.DESIRE) / 2);
	}

	public IItemHandler getTradeItems() {
		return tradeItems;
	}

	public boolean hasLootInBag() {
		for (ItemStack stack : loot) {
			if (!stack.isEmpty()) return true;
		}
		return false;
	}

	/** Removes and returns a single item from a random filled loot-bag slot - real port of the original's
	 * own random loot pick inside {@code ContainerPech#generateContents}. */
	public ItemStack takeRandomLootItem() {
		List<Integer> filled = new java.util.ArrayList<>();
		for (int i = 0; i < loot.size(); i++) {
			if (!loot.get(i).isEmpty()) filled.add(i);
		}
		if (filled.isEmpty()) return ItemStack.EMPTY;
		int index = filled.get(random.nextInt(filled.size()));
		ItemStack taken = loot.get(index).copy();
		taken.setCount(1);
		loot.get(index).shrink(1);
		if (loot.get(index).isEmpty()) loot.set(index, ItemStack.EMPTY);
		return taken;
	}

	/** Real port of the original's own {@code ContainerPech#generateContents} - consumes the input slot (if
	 * it's a real valued item and the 4 output slots are all empty) and fills the output slots with a random
	 * value-tier mix of rewards drawn from {@link #TRADE_REWARDS} (or straight out of the loot bag). A
	 * successful high-value roll re-arms the Pech's hostility, matching the original's own real quirk. */
	public void generateTradeContents() {
		ItemStack payment = tradeItems.getStackInSlot(0);
		if (payment.isEmpty() || !isValued(payment)) return;
		for (int i = 1; i <= 4; i++) {
			if (!tradeItems.getStackInSlot(i).isEmpty()) return;
		}

		int value = getValue(payment);
		if (random.nextInt(100) <= value / 2) {
			setTamed(false);
			setCombatTask();
			playSound(SoundEvents.VILLAGER_YES, getSoundVolume(), getVoicePitch());
		}
		if (random.nextInt(5) == 0) {
			value += random.nextInt(3);
		} else if (random.nextBoolean()) {
			value -= Math.min(value, random.nextInt(3));
		}

		List<TradeReward> pool = TRADE_REWARDS.getOrDefault(getPechType(), List.of());
		int guard = 0;
		while (value > 0 && guard++ < 50) {
			int roll = random.nextInt(Math.max(value, 1)) + 1;
			int amount = Math.min(5, Math.max((value + 1) / 2, roll));
			value -= amount;
			ItemStack reward = ItemStack.EMPTY;
			if (amount == 1 && random.nextBoolean() && hasLootInBag()) {
				reward = takeRandomLootItem();
			} else if (!(amount >= 4 && random.nextBoolean())) {
				reward = rollReward(pool, amount);
			}
			if (!reward.isEmpty() && !addRewardToOutput(reward)) break;
		}
		tradeItems.setStackInSlot(0, ItemStack.EMPTY);
	}

	private boolean addRewardToOutput(ItemStack stack) {
		for (int i = 1; i <= 4; i++) {
			ItemStack slot = tradeItems.getStackInSlot(i);
			if (slot.isEmpty()) {
				tradeItems.setStackInSlot(i, stack);
				return true;
			}
			if (ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() + stack.getCount() <= slot.getMaxStackSize()) {
				slot.grow(stack.getCount());
				return true;
			}
		}
		return false;
	}

	private ItemStack rollReward(List<TradeReward> pool, int tier) {
		List<TradeReward> matching = pool.stream().filter(r -> r.tier() == tier).toList();
		return matching.isEmpty() ? ItemStack.EMPTY : matching.get(random.nextInt(matching.size())).stack().get();
	}

	@Override
	protected InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (player.isShiftKeyDown() || player.getItemInHand(hand).getItem() instanceof NameTagItem) {
			return super.mobInteract(player, hand);
		}
		if (isTamed()) {
			if (!level().isClientSide) {
				trading = true;
				MenuProvider provider = new SimpleMenuProvider(
						(id, inv, p) -> new PechMenu(id, inv, this),
						net.minecraft.network.chat.Component.translatable("entity.thaumcraft.pech"));
				player.openMenu(provider);
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
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
