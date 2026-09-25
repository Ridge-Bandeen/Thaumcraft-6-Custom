package thaumcraft.common.entities;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusMediumRoot;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.potions.MobEffectFluxTaint;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.items.casters.foci.FocusEffectFlux;
import thaumcraft.common.items.casters.foci.FocusMediumCloud;
import thaumcraft.common.lib.network.fx.PacketFXBlockBamf;
import thaumcraft.common.lib.potions.MobEffectInfectiousVisExhaust;
import thaumcraft.common.lib.utils.RandomItemChooser;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.registry.ThaumcraftMobEffects;
import thaumcraft.common.world.aura.AuraHandler;

/**
 * Replaces the old (1.12.2) {@code EntityFluxRift} - a slow-moving aura hazard that eats nearby blocks,
 * periodically fires one of a few weighted "rift events", and eventually collapses, dropping the last two
 * prerequisite items ({@link thaumcraft.common.items.curios.ItemPrimordialPearl}/{@code ItemsTC.voidSeed})
 * and applying a stability-tiered debuff to anything nearby. This is the entity every other Flux Rift
 * prerequisite this session (see {@link EntityWisp}'s own doc comment for the full list) was building
 * toward - every one of its real dependencies is now real.
 *
 * A plain {@link Entity} (not living), same as the original - {@link #tick()} stands in for
 * {@code onUpdate}, {@link #move} is an intentional no-op (the rift never physically moves once spawned,
 * same as the original), and its bounding box is entirely custom, recomputed from {@link #points}/
 * {@link #pointsWidth} (a procedural tendril point-cloud, see {@link #calcSteps}) rather than driven by
 * {@code EntityType}'s normal fixed-size dimensions.
 *
 * {@link Vec3} lost {@code rotatePitch}/{@code rotateYaw} somewhere between 1.12.2 and this version -
 * {@link #rotatePitch}/{@link #rotateYaw} below are exact reimplementations of the old {@code Vec3d}
 * methods' own formulas (not a simplification, just a same-shape stand-in for a removed API), so
 * {@link #calcSteps} otherwise reads as a direct translation of the original.
 *
 * Real, documented cuts, each with precedent already set elsewhere this session:
 * <ul>
 * <li>Rift event index 1 (spawning {@code EntityTaintSeedPrime}) is dropped from the weighted event table
 * entirely rather than kept as a guaranteed no-op - that monster is Taint-subsystem-adjacent, itself a
 * confirmed hard blocker (the finite-fluid disqualifier). The other four entries keep their original
 * weights/costs unchanged.</li>
 * <li>{@code TaintHelper.isNearTaintSeed} (originally gating some rift events) is dropped rather than
 * ported - its real implementation depends on the same blocked Taint subsystem, and with no taint seeds
 * able to exist yet, it would always return {@code false} anyway.</li>
 * <li>No client-side tendril particle trail ({@code FXDispatcher.drawCurlyWisp}, driven by the unconverted
 * client FX dispatcher) - the same "no custom renderer/particle pipeline" boundary this whole port has
 * kept since Cinderpearl's own client FX.</li>
 * <li>Real custom {@code evilportal} sound restored 2026-09-15 (was a vanilla {@code PORTAL_AMBIENT}
 * stand-in) - see {@link thaumcraft.common.registry.ThaumcraftSounds}'s own doc comment.</li>
 * </ul>
 */
public class EntityFluxRift extends Entity {

	private static final EntityDataAccessor<Integer> DATA_SEED =
			SynchedEntityData.defineId(EntityFluxRift.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Integer> DATA_SIZE =
			SynchedEntityData.defineId(EntityFluxRift.class, EntityDataSerializers.INT);
	private static final EntityDataAccessor<Float> DATA_STABILITY =
			SynchedEntityData.defineId(EntityFluxRift.class, EntityDataSerializers.FLOAT);
	private static final EntityDataAccessor<Boolean> DATA_COLLAPSE =
			SynchedEntityData.defineId(EntityFluxRift.class, EntityDataSerializers.BOOLEAN);

	private static final List<RandomItemChooser.Item> EVENTS = List.of(
			new FluxEventEntry(0, 50, 5),
			new FluxEventEntry(1, 20, 10),
			new FluxEventEntry(2, 20, 10),
			new FluxEventEntry(3, 1, 0));

	private int maxSize;
	private int lastSize = -1;
	public final List<Vec3> points = new ArrayList<>();
	public final List<Float> pointsWidth = new ArrayList<>();

	public EntityFluxRift(EntityType<? extends EntityFluxRift> type, Level level) {
		super(type, level);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		builder.define(DATA_SEED, 0);
		builder.define(DATA_SIZE, 5);
		builder.define(DATA_STABILITY, 0.0f);
		builder.define(DATA_COLLAPSE, false);
	}

	public boolean getCollapse() {
		return entityData.get(DATA_COLLAPSE);
	}

	public void setCollapse(boolean collapse) {
		if (collapse) {
			maxSize = getRiftSize();
		}
		entityData.set(DATA_COLLAPSE, collapse);
	}

	public float getRiftStability() {
		return entityData.get(DATA_STABILITY);
	}

	public void setRiftStability(float stability) {
		entityData.set(DATA_STABILITY, Mth.clamp(stability, -100.0f, 100.0f));
	}

	public int getRiftSize() {
		return entityData.get(DATA_SIZE);
	}

	public void setRiftSize(int size) {
		entityData.set(DATA_SIZE, size);
		recomputeShape();
	}

	public int getRiftSeed() {
		return entityData.get(DATA_SEED);
	}

	public void setRiftSeed(int seed) {
		entityData.set(DATA_SEED, seed);
	}

	/** Recomputes {@link #points}/{@link #pointsWidth} from the current seed/size and rebuilds the real
	 * bounding box from their extent - the original's own {@code setSize}. */
	private void recomputeShape() {
		calcSteps(points, pointsWidth, new Random(getRiftSeed()));
		lastSize = getRiftSize();
		double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE;
		double x2 = -Double.MAX_VALUE, y2 = -Double.MAX_VALUE, z2 = -Double.MAX_VALUE;
		for (Vec3 v : points) {
			x0 = Math.min(x0, v.x);
			x2 = Math.max(x2, v.x);
			y0 = Math.min(y0, v.y);
			y2 = Math.max(y2, v.y);
			z0 = Math.min(z0, v.z);
			z2 = Math.max(z2, v.z);
		}
		setBoundingBox(new AABB(getX() + x0, getY() + y0, getZ() + z0, getX() + x2, getY() + y2, getZ() + z2));
	}

	@Override
	public void setPos(double x, double y, double z) {
		super.setPos(x, y, z);
		// entityData/points are still null the very first time this runs - Entity's own constructor calls
		// setPos before this subclass's field initializers have executed (points/pointsWidth are declared
		// with `= new ArrayList<>()`, which only runs once super() returns) - recomputeShape() is safe to
		// skip here since setRiftSize (called separately once construction finishes) always triggers it.
		if (entityData != null && points != null) {
			recomputeShape();
		}
	}

	@Override
	public void move(MoverType type, Vec3 pos) {
		// intentional no-op - the rift never physically moves once spawned, same as the original.
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	protected void addAdditionalSaveData(CompoundTag tag) {
		tag.putInt("MaxSize", maxSize);
		tag.putInt("RiftSize", getRiftSize());
		tag.putInt("RiftSeed", getRiftSeed());
		tag.putFloat("Stability", getRiftStability());
		tag.putBoolean("collapse", getCollapse());
	}

	@Override
	protected void readAdditionalSaveData(CompoundTag tag) {
		maxSize = tag.getInt("MaxSize");
		setRiftSize(tag.getInt("RiftSize"));
		setRiftSeed(tag.getInt("RiftSeed"));
		setRiftStability(tag.getFloat("Stability"));
		setCollapse(tag.getBoolean("collapse"));
	}

	@Override
	public void tick() {
		super.tick();
		if (lastSize != getRiftSize()) {
			recomputeShape();
		}
		if (level().isClientSide) {
			return;
		}
		ServerLevel level = (ServerLevel) level();

		if (getRiftSeed() == 0) {
			setRiftSeed(random.nextInt());
		}
		if (points.size() > 1) {
			int pi = random.nextInt(points.size() - 1);
			Vec3 v1 = points.get(pi).add(getX(), getY(), getZ());
			Vec3 v2 = points.get(pi + 1).add(getX(), getY(), getZ());
			BlockHitResult hit = level.clip(new ClipContext(v1, v2, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
			if (hit.getType() != HitResult.Type.MISS) {
				BlockPos p = hit.getBlockPos();
				BlockState bs = level.getBlockState(p);
				if (!bs.isAir() && bs.getDestroySpeed(level, p) >= 0.0f) {
					level.levelEvent(2001, p, Block.getId(bs));
					level.removeBlock(p, false);
				}
			}
			for (Entity e : level.getEntitiesOfClass(Entity.class, new AABB(v1.x - 0.5, v1.y - 0.5, v1.z - 0.5, v1.x + 0.5, v1.y + 0.5, v1.z + 0.5))) {
				if (e.isRemoved()) {
					continue;
				}
				if (e instanceof Player player && player.isCreative()) {
					continue;
				}
				try {
					e.hurt(level.damageSources().fellOutOfWorld(), 2.0f);
					if (e instanceof ItemEntity) {
						e.discard();
					}
				} catch (Exception ignored) {
					// mirrors the original's own defensive try/catch here - a hit against something odd
					// (a boss, a not-yet-fully-ported entity) should never take the rift's own tick down.
				}
			}
		}

		if (points.size() < 3 && !getCollapse()) {
			setCollapse(true);
		}
		if (getCollapse()) {
			setRiftSize(getRiftSize() - 1);
			if (random.nextBoolean()) {
				AuraHelper.addVis(level, blockPosition(), 1.0f);
			} else {
				AuraHelper.polluteAura(level, blockPosition(), 1.0f, false);
			}
			if (random.nextInt(10) == 0) {
				level.explode(this, getX() + random.nextGaussian() * 2.0, getY() + random.nextGaussian() * 2.0,
						getZ() + random.nextGaussian() * 2.0, random.nextFloat() / 2.0f, Level.ExplosionInteraction.NONE);
			}
			if (getRiftSize() <= 1) {
				completeCollapse();
				return;
			}
		}
		if (tickCount % 120 == 0) {
			setRiftStability(getRiftStability() - 0.2f);
		}
		if (tickCount % 600 == getId() % 600) {
			float flux = AuraHandler.getFlux(level, blockPosition());
			double size = Math.sqrt(getRiftSize() * 2.0);
			if (flux >= size && getRiftSize() < 100 && getStability() != EnumStability.VERY_STABLE) {
				AuraHandler.drainFlux(level, blockPosition(), (float) size, false);
				setRiftSize(getRiftSize() + 1);
			}
			if (getRiftStability() < 0.0f && random.nextInt(1000) < Math.abs(getRiftStability()) + getRiftSize()) {
				executeRiftEvent();
			}
		}
		if (isAlive() && tickCount % 300 == 0) {
			playSound(thaumcraft.common.registry.ThaumcraftSounds.EVILPORTAL.get(), 0.15f + (float) (random.nextGaussian() * 0.066), 0.75f + (float) (random.nextGaussian() * 0.1));
		}
	}

	public static void createRift(ServerLevel level, BlockPos pos) {
		pos = pos.offset(level.random.nextInt(16), 0, level.random.nextInt(16));
		BlockPos p2 = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, pos);
		if (!level.dimensionType().hasSkyLight()) {
			p2 = new BlockPos(p2.getX(), 10, p2.getZ());
			while (!level.isEmptyBlock(p2)) {
				if (p2.getY() > level.getMaxBuildHeight() - 5) {
					return;
				}
				p2 = p2.above(level.random.nextInt(5) + 1);
			}
		}
		if (p2.getY() >= level.getMaxBuildHeight() - 4) {
			return;
		}
		if (!level.getEntitiesOfClass(EntityFluxRift.class, new AABB(p2).inflate(32.0)).isEmpty()) {
			return;
		}
		EntityFluxRift rift = new EntityFluxRift(ThaumcraftEntities.FLUX_RIFT.get(), level);
		rift.setRiftSeed(level.random.nextInt());
		rift.moveTo(p2.getX() + 0.5, p2.getY() + 0.5, p2.getZ() + 0.5, level.random.nextInt(360), 0.0f);
		float flux = AuraHandler.getFlux(level, p2);
		double size = Math.sqrt(flux * 3.0f);
		if (size > 5.0 && level.addFreshEntity(rift)) {
			rift.setRiftSize((int) size);
			AuraHandler.drainFlux(level, p2, (float) size, false);
			for (ServerPlayer target : level.getEntitiesOfClass(ServerPlayer.class, new AABB(p2).inflate(32.0))) {
				IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(target);
				if (!knowledge.isResearchKnown("f_toomuchflux")) {
					target.displayClientMessage(Component.translatable("tc.fluxevent.3").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
					ThaumcraftApi.internalMethods.completeResearch(target, "f_toomuchflux");
				}
			}
		}
	}

	private void executeRiftEvent() {
		RandomItemChooser.Item chosen = new RandomItemChooser().chooseOnWeight(EVENTS);
		if (chosen == null) {
			return;
		}
		FluxEventEntry ei = (FluxEventEntry) chosen;
		ServerLevel level = (ServerLevel) level();
		boolean didit = false;
		switch (ei.event) {
			case 0 -> {
				EntityWisp wisp = new EntityWisp(ThaumcraftEntities.WISP.get(), level);
				wisp.moveTo(getX() + random.nextGaussian() * 5.0, getY() + random.nextGaussian() * 5.0, getZ() + random.nextGaussian() * 5.0, 0.0f, 0.0f);
				if (random.nextInt(5) == 0) {
					wisp.setWispType(Aspect.FLUX.getTag());
				}
				if (wisp.canSpawnHere() && level.addFreshEntity(wisp)) {
					didit = true;
				}
			}
			case 1 -> {
				for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(16.0))) {
					didit = true;
					if (target instanceof ServerPlayer player) {
						player.displayClientMessage(Component.translatable("tc.fluxevent.2").withStyle(net.minecraft.ChatFormatting.DARK_PURPLE, net.minecraft.ChatFormatting.ITALIC), true);
					}
					try {
						target.addEffect(new MobEffectInstance(ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST, 3000, 2, false, true));
					} catch (Exception ignored) {
					}
				}
			}
			case 2 -> {
				Player target2 = thaumcraft.common.entities.PlayerTargeting.nearestTargetable(level, this, 16.0);
				if (target2 != null) {
					FocusPackage p = new FocusPackage(target2);
					FocusMediumRoot root = new FocusMediumRoot();
					root.setupFromCasterToTarget(target2, target2, 0.5);
					p.addNode(root);
					FocusMediumCloud fp = new FocusMediumCloud();
					fp.getSetting("radius").setValue(Mth.nextInt(random, 1, 3));
					fp.getSetting("duration").setValue(Mth.nextInt(random, Math.min(getRiftSize() / 2, 30), Math.min(getRiftSize(), 120)));
					p.addNode(fp);
					p.addNode(new FocusEffectFlux());
					FocusEngine.castFocusPackage(target2, p, true);
					didit = true;
				}
			}
			case 3 -> setCollapse(true);
			default -> {
			}
		}
		if (didit) {
			setRiftStability(getRiftStability() + ei.cost);
		}
	}

	/** Direct translation of the original's own {@code calcSteps} - two mirrored random-walk "tendrils"
	 * (left/right) built from a shared seeded {@link Random}, each step narrowing toward the tips. Purely
	 * cosmetic/collision shape, no gameplay meaning of its own beyond driving the bounding box. */
	private void calcSteps(List<Vec3> pp, List<Float> ww, Random rr) {
		pp.clear();
		ww.clear();
		Vec3 right = new Vec3(rr.nextGaussian(), rr.nextGaussian(), rr.nextGaussian()).normalize();
		Vec3 left = right.scale(-1.0);
		Vec3 lr = Vec3.ZERO;
		Vec3 ll = Vec3.ZERO;
		int steps = Mth.ceil(getRiftSize() / 3.0f);
		float girth = getRiftSize() / 300.0f;
		double angle = 0.33;
		float dec = steps == 0 ? girth : girth / steps;
		for (int a = 0; a < steps; a++) {
			girth -= dec;
			right = rotatePitch(right, (float) (rr.nextGaussian() * angle));
			right = rotateYaw(right, (float) (rr.nextGaussian() * angle));
			lr = lr.add(right.scale(0.2));
			pp.add(lr);
			ww.add(girth);
			left = rotatePitch(left, (float) (rr.nextGaussian() * angle));
			left = rotateYaw(left, (float) (rr.nextGaussian() * angle));
			ll = ll.add(left.scale(0.2));
			pp.add(0, ll);
			ww.add(0, girth);
		}
		lr = lr.add(right.scale(0.1));
		pp.add(lr);
		ww.add(0.0f);
		ll = ll.add(left.scale(0.1));
		pp.add(0, ll);
		ww.add(0, 0.0f);
	}

	/** See the class doc comment - {@link Vec3} no longer has this method, this is the exact formula the
	 * old {@code Vec3d#rotatePitch} used. */
	private static Vec3 rotatePitch(Vec3 v, float pitch) {
		float f = Mth.cos(pitch);
		float f1 = Mth.sin(pitch);
		return new Vec3(v.x, v.y * f + v.z * f1, v.z * f - v.y * f1);
	}

	/** See the class doc comment - the exact formula the old {@code Vec3d#rotateYaw} used. */
	private static Vec3 rotateYaw(Vec3 v, float yaw) {
		float f = Mth.cos(yaw);
		float f1 = Mth.sin(yaw);
		return new Vec3(v.x * f + v.z * f1, v.y, v.z * f - v.x * f1);
	}

	public EnumStability getStability() {
		float stability = getRiftStability();
		if (stability > 50.0f) return EnumStability.VERY_STABLE;
		if (stability >= 0.0f) return EnumStability.STABLE;
		if (stability > -25.0f) return EnumStability.UNSTABLE;
		return EnumStability.VERY_UNSTABLE;
	}

	private void completeCollapse() {
		int qq = (int) Math.sqrt(maxSize);
		if (random.nextInt(100) < qq) {
			ItemStack pearl = new ItemStack(ItemsTC.primordialPearl.get());
			pearl.setDamageValue(4 + random.nextInt(4));
			spawnAtLocation(pearl, 0.0f);
		}
		for (int a = 0; a < qq; a++) {
			spawnAtLocation(new ItemStack(ItemsTC.voidSeed.get()), 0.0f);
		}
		try {
			PacketDistributor.sendToPlayersNear((ServerLevel) level(), null, getX(), getY(), getZ(), 64.0,
					new PacketFXBlockBamf(getX(), getY(), getZ(), 0, true, true));
		} catch (Exception e) {
			Thaumcraft.LOGGER.debug("Flux rift collapse FX packet failed to send (no real client connection?)", e);
		}

		EnumStability stability = getStability();
		List<LivingEntity> nearby = level().getEntitiesOfClass(LivingEntity.class, new AABB(getX(), getY(), getZ(), getX(), getY(), getZ()).inflate(32.0));
		if (stability == EnumStability.VERY_UNSTABLE || stability == EnumStability.UNSTABLE) {
			for (LivingEntity p : nearby) {
				int w = (int) ((1.0 - p.distanceToSqr(this) / 32.0) * 300.0);
				if (w > 0) {
					p.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, w * 20, 0));
				}
			}
		}
		if (stability == EnumStability.VERY_UNSTABLE) {
			for (LivingEntity p : nearby) {
				int w = (int) ((1.0 - p.distanceToSqr(this) / 32.0) * 120.0);
				if (w > 0) {
					p.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, w * 20, 0));
				}
			}
		} else if (stability == EnumStability.STABLE) {
			for (LivingEntity p : nearby) {
				if (p instanceof ServerPlayer player) {
					int w = (int) ((1.0 - p.distanceToSqr(this) / 32.0) * 25.0);
					if (w > 0) {
						ThaumcraftApi.internalMethods.addWarpToPlayer(player, w, IPlayerWarp.EnumWarpType.NORMAL);
						ThaumcraftApi.internalMethods.addWarpToPlayer(player, w, IPlayerWarp.EnumWarpType.TEMPORARY);
					}
				}
			}
		}
		discard();
	}

	private record FluxEventEntry(int event, double weight, int cost) implements RandomItemChooser.Item {
		@Override
		public double getWeight() {
			return weight;
		}
	}

	public enum EnumStability {
		VERY_STABLE, STABLE, UNSTABLE, VERY_UNSTABLE
	}
}
