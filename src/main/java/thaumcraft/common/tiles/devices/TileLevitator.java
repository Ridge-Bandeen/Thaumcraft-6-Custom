package thaumcraft.common.tiles.devices;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileLevitator} - a real vis-powered "wind tunnel": pushes item entities
 * and any other pushable entity (including horses) along its real facing direction, auto-extending its real
 * push range up to the nearest solid obstruction (capped at one of 4 real tiers - 4/8/16/32 blocks,
 * right-click-cycled via {@link thaumcraft.common.blocks.devices.BlockLevitator#useWithoutItem}), fueled by
 * a real small local-aura-drained vis buffer (drains up to 1 vis/tick into a 10-vis buffer, spends
 * {@code tier*2} vis per entity lifted per tick - a real, higher tier costs real more to run continuously).
 * Sneaking near an real UP-facing levitator lets you resist its lift and descend slowly instead, matching
 * the original's own real "hover platform" use case.
 *
 * As the original, the lift runs on the client too (players move themselves), driven there by the synced
 * {@code ENABLED} state and the range the client works out itself, and motes drift from it and around whatever
 * it lifts ({@code drawFX}/{@code drawFXAt}).
 */
public class TileLevitator extends BlockEntity {

	private static final int[] RANGES = { 4, 8, 16, 32 };

	private int range = 0;
	private int rangeActual = 0;
	private int counter = 0;
	private int vis = 0;

	public TileLevitator(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.LEVITATOR.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileLevitator tile) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		if (tile.rangeActual > RANGES[tile.range]) {
			tile.rangeActual = 0;
		}
		int p = tile.counter % RANGES[tile.range];
		if (level.getBlockState(pos.relative(facing, 1 + p)).isCollisionShapeFullBlock(level, pos.relative(facing, 1 + p))) {
			if (1 + p < tile.rangeActual) {
				tile.rangeActual = 1 + p;
			}
			tile.counter = -1;
		} else if (1 + p > tile.rangeActual) {
			tile.rangeActual = 1 + p;
		}
		tile.counter++;

		if (!level.isClientSide && tile.vis < 10) {
			tile.vis += (int) (AuraHelper.drainVis(level, pos, 1.0f, false) * 1200.0f);
			tile.setChanged();
		}

		boolean active = tile.rangeActual > 0 && tile.vis > 0;
		if (!level.isClientSide && state.getValue(BlockStateProperties.ENABLED) != active) {
			level.setBlock(pos, state.setValue(BlockStateProperties.ENABLED, active), 3);
		}
		// as the original, the lift runs on both sides: a player's own motion is decided by their client
		if (level.isClientSide) active = tile.rangeActual > 0 && state.getValue(BlockStateProperties.ENABLED);
		if (!active) return;

		int r = tile.rangeActual;
		AABB area = new AABB(
				pos.getX() - (facing.getStepX() < 0 ? r : 0), pos.getY() - (facing.getStepY() < 0 ? r : 0), pos.getZ() - (facing.getStepZ() < 0 ? r : 0),
				pos.getX() + 1 + (facing.getStepX() > 0 ? r : 0), pos.getY() + 1 + (facing.getStepY() > 0 ? r : 0), pos.getZ() + 1 + (facing.getStepZ() > 0 ? r : 0));
		List<Entity> targets = level.getEntitiesOfClass(Entity.class, area);
		boolean lifted = false;
		for (Entity e : targets) {
			if (!(e instanceof ItemEntity) && !e.isPushable() && !(e instanceof AbstractHorse)) continue;
			lifted = true;
			if (level.isClientSide) {
				drawFXAt(level, e);
				drawFX(level, pos, facing, 0.6);
			}
			if (e.isShiftKeyDown() && facing == Direction.UP) {
				Vec3 motion = e.getDeltaMovement();
				if (motion.y < 0.0) {
					e.setDeltaMovement(motion.x, motion.y * 0.9, motion.z);
				}
			} else {
				Vec3 motion = e.getDeltaMovement();
				double mx = motion.x + 0.1 * facing.getStepX();
				double my = motion.y + 0.1 * facing.getStepY();
				double mz = motion.z + 0.1 * facing.getStepZ();
				if (facing.getAxis() != Direction.Axis.Y && !e.onGround()) {
					if (my < 0.0) my *= 0.9;
					my += 0.08;
				}
				mx = Math.clamp(mx, -0.35, 0.35);
				my = Math.clamp(my, -0.35, 0.35);
				mz = Math.clamp(mz, -0.35, 0.35);
				e.setDeltaMovement(mx, my, mz);
			}
			e.fallDistance = 0.0f;
			if (!level.isClientSide) {
				tile.vis -= tile.getCost();
				if (tile.vis <= 0) break;
			}
		}
		if (level.isClientSide) drawFX(level, pos, facing, 0.1);
		if (lifted && !level.isClientSide && tile.counter % 20 == 0) {
			tile.setChanged();
		}
	}

	/** The original's drawFX: now and then a pale mote drifting out of the levitator along its facing. */
	private static void drawFX(Level level, BlockPos pos, Direction facing, double chance) {
		if (level.random.nextFloat() < chance) {
			thaumcraft.client.fx.ClientFxHandlers.drawLevitatorParticles(level, pos.getX() + 0.25 + level.random.nextFloat() * 0.5,
					pos.getY() + 0.25 + level.random.nextFloat() * 0.5, pos.getZ() + 0.25 + level.random.nextFloat() * 0.5,
					facing.getStepX() / 50.0, facing.getStepY() / 50.0, facing.getStepZ() / 50.0);
		}
	}

	/** The original's drawFXAt: motes around something being lifted. */
	private static void drawFXAt(Level level, Entity e) {
		if (level.random.nextFloat() < 0.1f) {
			var r = level.random;
			thaumcraft.client.fx.ClientFxHandlers.drawLevitatorParticles(level, e.getX() + (r.nextFloat() - r.nextFloat()) * e.getBbWidth(),
					e.getY() + r.nextFloat() * e.getBbHeight(), e.getZ() + (r.nextFloat() - r.nextFloat()) * e.getBbWidth(),
					(r.nextFloat() - r.nextFloat()) * 0.01, (r.nextFloat() - r.nextFloat()) * 0.01, (r.nextFloat() - r.nextFloat()) * 0.01);
		}
	}

	public int getCost() {
		return RANGES[range] * 2;
	}

	public void increaseRange(Player player) {
		rangeActual = 0;
		range++;
		if (range >= RANGES.length) {
			range = 0;
		}
		setChanged();
		if (level != null && !level.isClientSide) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
		player.displayClientMessage(Component.translatable("tc.levitator", RANGES[range], getCost()), true);
	}

	/** The client needs the range setting to run the lift (see the class doc). */
	@Override
	public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
		return saveWithoutMetadata(registries);
	}

	@Override
	public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
		return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		range = tag.getByte("range");
		vis = tag.getInt("vis");
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putByte("range", (byte) range);
		tag.putInt("vis", vis);
	}
}
