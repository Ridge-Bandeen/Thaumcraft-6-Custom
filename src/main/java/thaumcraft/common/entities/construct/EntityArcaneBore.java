package thaumcraft.common.entities.construct;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import thaumcraft.Thaumcraft;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.lib.enchantment.EnumInfusionEnchantment;
import thaumcraft.common.lib.events.ToolEvents;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code EntityArcaneBore} - a real, vis-powered stationary mining laser. Faces
 * one fixed {@link #getFacing()} direction (set at placement time), digs one block roughly every {@link
 * #digDelay} ticks along that direction while {@link #isActive()} and a real pickaxe with room left in its
 * durability sits in its main hand, spending real {@link #charge} drawn from the local aura via {@link
 * AuraHandler#drainVis} - see {@link #tick()}'s own real per-branch shape (search for a target, wait out the
 * dig delay, apply the dig, otherwise idle-look) for the faithfully-ported original control flow.
 *
 * <p><b>Real harvest-with-a-real-tool, dramatically simpler than the original's own approach</b>: the
 * original needed a whole {@code FakePlayer}/{@code BlockEvent.HarvestDropsEvent}-capture dance (a real
 * 1.12.2-era workaround - there was no clean "give me this block's real loot for this specific tool" API
 * back then) just to get fortune/silk-touch-correct drops without a real player breaking the block. Modern
 * {@link BlockState#getDrops(LootParams.Builder)} already reads a real {@link LootContextParams#TOOL} straight
 * out of the loot table itself - so {@link #dig()} just builds one real {@link LootParams.Builder} carrying
 * the bore's own real held pickaxe and lets the block's own loot table do the (silk-touch/fortune-correct)
 * rest, no fake player or event-capture needed at all.
 *
 * <p>{@link EnumInfusionEnchantment#SOUNDING}'s own real "acts like extra Fortune" bonus (the original just
 * fed {@code Math.max(realFortune, soundingLevel)} into its own harvest call) is reproduced by temporarily
 * enchanting a throwaway *copy* of the held tool up to that combined level before it's used as the loot
 * {@code TOOL} param - the real held item itself is never touched. {@link EnumInfusionEnchantment#REFINING}'s
 * own real "ore -> essentia cluster" substitution now reuses {@link ToolEvents#rollSpecialMiningResult} directly
 * (see that method's own doc comment) rather than a separate copy of the same table.
 *
 * <p>Item ejection into a real adjacent inventory (checked via NeoForge's own {@link Capabilities.ItemHandler#BLOCK}
 * capability, the modern replacement for the original's own {@code ThaumcraftInvHelper}/{@code InventoryUtils}
 * pair - neither of those is ported, not worth pulling in for this one real use) falls back to a plain real
 * {@link Containers#dropItemStack} at the bore's own position when no adjacent inventory takes it (or none
 * exists) - a real, minor simplification of the original's own directional "eject toward the open face with
 * outward velocity" flourish.
 *
 * <p>Real digging particle burst restored 2026-09-15 ({@link #sendDigFx}, see {@code
 * ClientFxHandlers#handleBoreDig}'s own doc comment) - was never actually self-documented as a gap before,
 * only found by cross-checking the true original directly. Distinct from the real mining-beam mesh VFX
 * below (still not ported).
 *
 * <p>Real, documented cuts: no dedicated GUI - the original's own version only ever held one real pickaxe
 * slot, and a right-click (holding a real pickaxe, or empty-handed to retrieve one) now swaps it directly via
 * {@link #mobInteract}, same real "no GUI, a simpler direct interaction already covers the one real slot"
 * reasoning {@link EntityTurretCrossbow}'s own dispenser-auto-reload cut established. No real mining-beam VFX
 * (see {@code ModelArcaneBore}'s own doc comment - a genuine bespoke Tessellator effect, real separate follow-
 * up). Its death drops are the original's whole table, Looting included.
 */
public class EntityArcaneBore extends EntityOwnedConstruct {

	private static final EntityDataAccessor<Direction> DATA_FACING =
			SynchedEntityData.defineId(EntityArcaneBore.class, EntityDataSerializers.DIRECTION);
	private static final EntityDataAccessor<Boolean> DATA_ACTIVE =
			SynchedEntityData.defineId(EntityArcaneBore.class, EntityDataSerializers.BOOLEAN);

	private static final float DIG_COST = 0.25f;

	private BlockPos digTarget;
	private BlockPos digTargetPrev;
	private float charge;
	private long soundDelay;
	private int breakCounter;
	private int digDelay;
	private int digDelayMax;
	private float radInc;
	private int spiral;
	private float currentRadius;
	/** Client-only: whether the real idle mining-beam visual should be showing right now - driven by {@link
	 * #handleEntityEvent}, matching the original's own status-byte 16/17 pair exactly. */
	public boolean clientDigging;

	public EntityArcaneBore(EntityType<? extends EntityArcaneBore> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Mob.createMobAttributes()
				.add(Attributes.MAX_HEALTH, 50.0)
				.add(Attributes.FOLLOW_RANGE, 32.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	@Override
	protected void registerGoals() {
		// Left empty - the original never had any real EntityAITasks either; every real behavior here runs
		// straight out of tick(), matching the original's own onUpdate() shape exactly.
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(DATA_FACING, Direction.DOWN);
		builder.define(DATA_ACTIVE, false);
	}

	public Direction getFacing() {
		return entityData.get(DATA_FACING);
	}

	public void setFacing(Direction facing) {
		entityData.set(DATA_FACING, facing);
	}

	public boolean isActive() {
		return entityData.get(DATA_ACTIVE);
	}

	public void setActive(boolean active) {
		entityData.set(DATA_ACTIVE, active);
	}

	public float getCharge() {
		return charge;
	}

	public void setCharge(float charge) {
		this.charge = charge;
	}

	protected void rechargeVis() {
		setCharge(getCharge() + AuraHandler.drainVis(level(), blockPosition(), 10.0f, false));
	}

	/** A real, non-worn-out pickaxe sitting in the main hand - the bore's own real "am I allowed to dig right
	 * now" gate, matching the original's {@code validInventory}. */
	public boolean validInventory() {
		ItemStack tool = getMainHandItem();
		if (tool.isEmpty() || !tool.is(ItemTags.PICKAXES)) {
			return false;
		}
		return !tool.isDamageableItem() || tool.getDamageValue() + 1 < tool.getMaxDamage();
	}

	public int getDigRadius() {
		int r = 0;
		ItemStack tool = getMainHandItem();
		if (!tool.isEmpty() && tool.is(ItemTags.PICKAXES)) {
			r = tool.getItem().getEnchantmentValue() / 3;
			r += EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.DESTRUCTIVE) * 2;
		}
		return r <= 1 ? 2 : r;
	}

	public int getDigDepth() {
		int r = getDigRadius() * 8;
		r += EnumInfusionEnchantment.getInfusionEnchantmentLevel(getMainHandItem(), EnumInfusionEnchantment.BURROWING) * 16;
		return r;
	}

	public int getDigSpeed(BlockState state) {
		int speed = 0;
		if (validInventory()) {
			ItemStack tool = getMainHandItem();
			speed += (int) (tool.getDestroySpeed(state) / 2.0f);
			speed += EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.EFFICIENCY), tool);
		}
		return speed;
	}

	public int getFortune() {
		return validInventory() ? EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.FORTUNE), getMainHandItem()) : 0;
	}

	public int getRefining() {
		return EnumInfusionEnchantment.getInfusionEnchantmentLevel(getMainHandItem(), EnumInfusionEnchantment.REFINING);
	}

	public boolean hasSilkTouch() {
		return !getMainHandItem().isEmpty() && EnchantmentHelper.getItemEnchantmentLevel(enchantment(Enchantments.SILK_TOUCH), getMainHandItem()) > 0;
	}

	private Holder<Enchantment> enchantment(net.minecraft.resources.ResourceKey<Enchantment> key) {
		return level().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	@Override
	public void tick() {
		super.tick();
		if (!level().isClientSide) {
			setYRot(yHeadRot);
			if (tickCount % 50 == 0) {
				heal(1.0f);
			}
			if (tickCount % 10 == 0 && getCharge() < 10.0f) {
				rechargeVis();
			}
			BlockPos feet = blockPosition();
			BlockPos checkPos = BaseRailBlock.isRail(level(), feet.below()) ? feet.below() : feet;
			BlockState railState = level().getBlockState(checkPos);
			if (BaseRailBlock.isRail(railState)) {
				if (railState.is(BlocksTC.activatorRail.get())) {
					boolean powered = railState.getValue(PoweredRailBlock.POWERED);
					setActive(!powered);
				}
			} else if (getVehicle() == null) {
				setActive(level().hasNeighborSignal(blockPosition().below()));
			}
		}
		if (!isActive()) {
			digTarget = null;
			getLookControl().setLookAt(getX() + getFacing().getStepX(), getY(), getZ() + getFacing().getStepZ(), 10.0f, 33.0f);
		}
		if (digTarget != null && getCharge() >= DIG_COST && !level().isClientSide) {
			getLookControl().setLookAt(digTarget.getX() + 0.5, digTarget.getY(), digTarget.getZ() + 0.5, 10.0f, 90.0f);
			if (digDelay-- <= 0 && dig()) {
				setCharge(getCharge() - DIG_COST);
				if (soundDelay < System.currentTimeMillis()) {
					soundDelay = System.currentTimeMillis() + 1200L + level().random.nextInt(100);
					playSound(SoundEvents.STONE_BREAK, 0.25f, 0.9f + level().random.nextFloat() * 0.2f);
				}
			}
		}
		if (!level().isClientSide && digTarget == null && isActive() && validInventory()) {
			findNextBlockToDig();
			if (digTarget != null) {
				level().broadcastEntityEvent(this, (byte) 16);
				sendDigFx(digTarget);
			} else {
				level().broadcastEntityEvent(this, (byte) 17);
				getLookControl().setLookAt(getX() + getFacing().getStepX() * 2, getY() + getFacing().getStepY() * 2 + getEyeHeight(),
						getZ() + getFacing().getStepZ() * 2, 10.0f, 33.0f);
			}
		}
	}

	@Override
	public void handleEntityEvent(byte id) {
		if (id == 16) {
			clientDigging = true;
		} else if (id == 17) {
			clientDigging = false;
		} else {
			super.handleEntityEvent(id);
		}
	}

	/** Real {@code PacketFXBoreDig} broadcast, restored 2026-09-15 - see that class's own doc comment. This
	 * purely cosmetic packet failing to send (e.g. a GameTest mock player, no real client connection) never
	 * takes the real digging logic down with it. */
	private void sendDigFx(BlockPos target) {
		if (!(level() instanceof ServerLevel serverLevel)) return;
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null,
					target.getX(), target.getY(), target.getZ(), 32.0,
					thaumcraft.common.lib.network.fx.PacketFXBoreDig.of(target, this, digDelayMax));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Arcane Bore dig FX packet failed to send (no real client connection?)", e);
		}
	}

	private boolean dig() {
		if (digTarget == null) {
			return false;
		}
		BlockState state = level().getBlockState(digTarget);
		if (state.isAir() || !(level() instanceof ServerLevel serverLevel)) {
			digTarget = null;
			return false;
		}

		ItemStack tool = getMainHandItem();
		boolean silkTouch = hasSilkTouch();
		int refining = getRefining();

		ItemStack toolForLoot = tool.copy();
		int soundingBonus = EnumInfusionEnchantment.getInfusionEnchantmentLevel(tool, EnumInfusionEnchantment.SOUNDING);
		if (soundingBonus > 0) {
			Holder<Enchantment> fortune = enchantment(Enchantments.FORTUNE);
			int combined = Math.max(EnchantmentHelper.getItemEnchantmentLevel(fortune, tool), soundingBonus);
			toolForLoot.enchant(fortune, combined);
		}

		List<ItemStack> drops = state.getDrops(new LootParams.Builder(serverLevel)
				.withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(digTarget))
				.withParameter(LootContextParams.TOOL, toolForLoot)
				.withOptionalParameter(LootContextParams.BLOCK_ENTITY, level().getBlockEntity(digTarget)));

		for (ItemStack drop : drops) {
			ItemStack dropped = (!silkTouch && refining > 0) ? ToolEvents.rollSpecialMiningResult(drop, refining, random) : drop;
			ejectStack(dropped);
		}

		if (!tool.isEmpty()) {
			breakCounter++;
			if (breakCounter >= 50) {
				breakCounter -= 50;
				tool.hurtAndBreak(1, this, EquipmentSlot.MAINHAND);
			}
		} else {
			breakCounter = 0;
		}

		level().removeBlock(digTarget, false);
		level().levelEvent(2001, digTarget, Block.getId(state));
		digTarget = null;
		return true;
	}

	/** Tries every adjacent real inventory (via {@link Capabilities.ItemHandler#BLOCK}) for the first one that
	 * takes it, otherwise a plain real drop at the bore's own position - see this class's own doc comment. */
	private void ejectStack(ItemStack stack) {
		if (stack.isEmpty()) {
			return;
		}
		for (Direction face : Direction.values()) {
			BlockPos neighbor = blockPosition().relative(face);
			IItemHandler handler = level().getCapability(Capabilities.ItemHandler.BLOCK, neighbor, face.getOpposite());
			if (handler != null) {
				stack = ItemHandlerHelper.insertItemStacked(handler, stack, false);
				break;
			}
		}
		if (!stack.isEmpty()) {
			Containers.dropItemStack(level(), getX(), getY(), getZ(), stack);
		}
	}

	/** Real port of the original's own {@code findNextBlockToDig} - first tries a straight raytrace out to
	 * {@link #getDigDepth()} along {@link #getFacing()} (re-cast a second time from the bore's own eyes, same
	 * real double-raytrace the original did), then falls back to a real spiral search around that axis within
	 * {@link #getDigRadius()} - same real trig walk ({@link #spiral}/{@link #currentRadius}/{@link #radInc})
	 * advancing {@link #digTargetPrev} one step per call when nothing diggable is found yet. */
	private void findNextBlockToDig() {
		if (digTargetPrev == null || distanceToSqr(Vec3.atCenterOf(digTargetPrev)) > (getDigRadius() + 1) * (double) (getDigRadius() + 1)) {
			digTargetPrev = blockPosition();
		}
		if (radInc == 0.0f) {
			radInc = 1.0f;
		}
		int digRadius = getDigRadius();
		int digDepth = getDigDepth();
		Direction facing = getFacing();
		int x = digTargetPrev.getX();
		int y = digTargetPrev.getY();
		int z = digTargetPrev.getZ();
		BlockPos end = digTargetPrev.offset(facing.getStepX() * digDepth, facing.getStepY() * digDepth, facing.getStepZ() * digDepth);

		BlockHitResult hit = level().clip(new ClipContext(Vec3.atCenterOf(digTargetPrev), Vec3.atCenterOf(end),
				ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 eye = new Vec3(getX() + facing.getStepX(), getY() + getEyeHeight() + facing.getStepY(), getZ() + facing.getStepZ());
			BlockHitResult eyeHit = level().clip(new ClipContext(eye, Vec3.atCenterOf(hit.getBlockPos()),
					ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (eyeHit.getType() != HitResult.Type.MISS) {
				BlockPos candidate = eyeHit.getBlockPos();
				BlockState state = level().getBlockState(candidate);
				float hardness = state.getDestroySpeed(level(), candidate);
				if (hardness > -1.0f && !state.getCollisionShape(level(), candidate).isEmpty()) {
					digDelay = Math.max(10 - getDigSpeed(state), (int) (hardness * 2.0f) - getDigSpeed(state) * 2);
					if (digDelay < 1) {
						digDelay = 1;
					}
					digDelayMax = digDelay;
					if (!candidate.equals(blockPosition()) && !candidate.equals(blockPosition().below())) {
						digTarget = candidate;
						return;
					}
				}
			}
		}

		while (x == digTargetPrev.getX() && z == digTargetPrev.getZ() && y == digTargetPrev.getY()) {
			if (Math.abs(currentRadius) > digRadius) {
				currentRadius = digRadius;
			}
			spiral += (int) (3.0f + Math.max(0.0f, (10.0f - Math.abs(currentRadius)) * 2.0f));
			if (spiral >= 360) {
				spiral -= 360;
				currentRadius += radInc;
				if (currentRadius > digRadius || currentRadius < -digRadius) {
					currentRadius = 0.0f;
				}
			}
			Vec3 source = new Vec3((int) getX() + 0.5 + facing.getStepX(), getY() + facing.getStepY() + getEyeHeight(), (int) getZ() + 0.5 + facing.getStepZ());
			Vec3 target = new Vec3(0.0, currentRadius, 0.0);
			target = rotateAroundZ(target, spiral / 180.0f * (float) Math.PI);
			target = rotateAroundY(target, 1.5707964f * facing.getStepX());
			target = rotateAroundX(target, 1.5707964f * facing.getStepY());
			Vec3 result = source.add(target.x, target.y, target.z);
			x = (int) Math.floor(result.x);
			y = (int) Math.floor(result.y);
			z = (int) Math.floor(result.z);
		}
		digTargetPrev = new BlockPos(x, y, z);
	}

	// Real, self-contained port of the original's own Utils.rotateAroundX/Y/Z (plain vector rotation math,
	// no other dependency on that whole still-unported grab-bag class needed for just these three).
	private static Vec3 rotateAroundX(Vec3 v, float angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(v.x, v.y * cos - v.z * sin, v.y * sin + v.z * cos);
	}

	private static Vec3 rotateAroundY(Vec3 v, float angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(v.x * cos + v.z * sin, v.y, v.x * -sin + v.z * cos);
	}

	private static Vec3 rotateAroundZ(Vec3 v, float angle) {
		double cos = Math.cos(angle);
		double sin = Math.sin(angle);
		return new Vec3(v.x * cos - v.y * sin, v.x * sin + v.y * cos, v.z);
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		if (source.getEntity() instanceof Player player && isOwnedBy(player)) {
			double dx = player.getX() - getX();
			double dy = player.getY() - getY();
			double dz = player.getZ() - getZ();
			Direction dir = Direction.getNearest(dx, dy, dz);
			if (dir != Direction.DOWN) {
				setFacing(dir);
			}
			return false;
		}
		setYRot(getYRot() + (float) (random.nextGaussian() * 45.0));
		setXRot(getXRot() + (float) (random.nextGaussian() * 20.0));
		return super.hurt(source, amount);
	}

	@Override
	public void knockback(double strength, double x, double z) {
		super.knockback(strength, x / 10.0, z / 10.0);
		Vec3 delta = getDeltaMovement();
		if (delta.y > 0.1) {
			setDeltaMovement(delta.x, 0.1, delta.z);
		}
	}

	@Override
	public void move(MoverType type, Vec3 movement) {
		super.move(type, new Vec3(movement.x / 5.0, movement.y, movement.z / 5.0));
	}

	@Override
	public void die(DamageSource cause) {
		super.die(cause);
		if (!level().isClientSide) {
			dropStuff();
		}
	}

	protected void dropStuff() {
		if (!getMainHandItem().isEmpty()) {
			spawnAtLocation(getMainHandItem(), 0.5f);
		}
	}

	/** Real replacement for the original's own dedicated GUI (real, documented cut - see this class's own
	 * doc comment): a real pickaxe swaps in directly, an empty hand pulls the current one back out, both a
	 * plain real item exchange with the owner's own hand. Sneaking still picks the whole bore back up. */
	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isAlive()) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide && isOwnedBy(player)) {
			if (player.isShiftKeyDown()) {
				playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0f, 1.0f);
				dropStuff();
				spawnAtLocation(new ItemStack(ItemsTC.turretPlacerBore.get()), 0.5f);
				discard();
				player.swing(hand);
				return InteractionResult.SUCCESS;
			}
			// The original opened a screen (pickaxe slot, health, dig stats) - restored 2026-09-25; the port's stand-in
			// swapped the held item with the bore on right-click.
			if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
				serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
						(id, inv, p) -> new thaumcraft.common.container.ArcaneBoreMenu(id, inv, this),
						getDisplayName()), buf -> buf.writeVarInt(getId()));
			}
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** Its parts when destroyed: the {@code thaumcraft:entities/dismantle/arcane_bore} loot table (the original's chances; Looting
	 * adds 0.15 to each a level). */
	@Override
	protected net.minecraft.resources.ResourceKey<net.minecraft.world.level.storage.loot.LootTable> getDefaultLootTable() {
		return net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE,
				net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("thaumcraft", "entities/dismantle/arcane_bore"));
	}

	/** (Nothing extra: its held tool and parts drop on their own, never the equipment roll.) */
	@Override
	protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putFloat("charge", getCharge());
		tag.putByte("facing", (byte) getFacing().get3DDataValue());
		tag.putBoolean("active", isActive());
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		setCharge(tag.getFloat("charge"));
		setFacing(Direction.from3DDataValue(tag.getByte("facing")));
		setActive(tag.getBoolean("active"));
	}

	/** Real port of the original's own {@code getEyeHeight() = 0.8125F} fixed override - same final-method
	 * workaround as {@link EntityTurretCrossbow#getDefaultDimensions}. */
	@Override
	public net.minecraft.world.entity.EntityDimensions getDefaultDimensions(net.minecraft.world.entity.Pose pose) {
		return super.getDefaultDimensions(pose).withEyeHeight(0.8125f);
	}
}
