package thaumcraft.common.blocks.devices;

import java.util.HashSet;
import java.util.Set;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.crafting.IInfusionStabiliserExt;
import thaumcraft.common.tiles.devices.TileStabilizer;

/**
 * Replaces the old (1.12.2) {@code BlockInlay} ("Redstone Inlay"), as the original: a wire laid like redstone dust that
 * carries a 0-15 charge out from a Vis Stabilizer's stored energy, losing one per block, through other inlays and
 * Pedestals (which carry {@link #CHARGE} too). Each side connects to an inlay or pedestal ({@code side}) or reaches up
 * onto a stabilizer ({@code ext}); the multipart blockstate draws the dot/line/extension pieces. Charge brightens the
 * tint ({@link #colorMultiplier}) and sends sparks along the line; a charged pedestal shows the offering rune, and
 * {@link TileStabilizer#findNearbyStabilizer} follows the network. A weak passive infusion stabiliser either way.
 * (A stateless decorative dot in the port until 2026-09-30.)
 */
public class BlockInlay extends Block implements IInfusionStabiliserExt {

	public enum AttachPosition implements StringRepresentable {
		SIDE("side"), NONE("none"), EXT("ext");

		private final String name;

		AttachPosition(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final MapCodec<BlockInlay> CODEC = simpleCodec(BlockInlay::new);

	public static final EnumProperty<AttachPosition> NORTH = EnumProperty.create("north", AttachPosition.class);
	public static final EnumProperty<AttachPosition> EAST = EnumProperty.create("east", AttachPosition.class);
	public static final EnumProperty<AttachPosition> SOUTH = EnumProperty.create("south", AttachPosition.class);
	public static final EnumProperty<AttachPosition> WEST = EnumProperty.create("west", AttachPosition.class);
	public static final IntegerProperty CHARGE = IntegerProperty.create("charge", 0, 15);

	/** The original's REDSTONE_WIRE_AABB, indexed by the connected-sides bit mask (S=1, W=2, N=4, E=8). */
	private static final VoxelShape[] SHAPES = new VoxelShape[16];
	static {
		double[][] boxes = {
				{0.1875, 0.1875, 0.8125, 0.8125}, {0.1875, 0.1875, 0.8125, 1.0}, {0.0, 0.1875, 0.8125, 0.8125}, {0.0, 0.1875, 0.8125, 1.0},
				{0.1875, 0.0, 0.8125, 0.8125}, {0.1875, 0.0, 0.8125, 1.0}, {0.0, 0.0, 0.8125, 0.8125}, {0.0, 0.0, 0.8125, 1.0},
				{0.1875, 0.1875, 1.0, 0.8125}, {0.1875, 0.1875, 1.0, 1.0}, {0.0, 0.1875, 1.0, 0.8125}, {0.0, 0.1875, 1.0, 1.0},
				{0.1875, 0.0, 1.0, 0.8125}, {0.1875, 0.0, 1.0, 1.0}, {0.0, 0.0, 1.0, 0.8125}, {0.0, 0.0, 1.0, 1.0}};
		for (int i = 0; i < 16; i++) {
			SHAPES[i] = Shapes.box(boxes[i][0], 0.0, boxes[i][1], boxes[i][2], 0.0625, boxes[i][3]);
		}
	}

	public BlockInlay(BlockBehaviour.Properties properties) {
		super(properties);
		registerDefaultState(stateDefinition.any().setValue(NORTH, AttachPosition.NONE).setValue(EAST, AttachPosition.NONE)
				.setValue(SOUTH, AttachPosition.NONE).setValue(WEST, AttachPosition.NONE).setValue(CHARGE, 0));
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(NORTH, EAST, SOUTH, WEST, CHARGE);
	}

	/** The original's charge tint for the inlay's glowing top layer (tint index 0); {@link BlockStabilizer} used
	 * the same formula for its own energy. Charge 0 is a dim grey, 1-15 runs from half to full brightness. */
	public static int colorMultiplier(int charge) {
		float f = charge == 0 ? 0.3f : charge / 15.0f * 0.5f + 0.5f;
		int c = Math.max(0, Math.min((int) (f * 255.0f), 255));
		return 0xFF000000 | c << 16 | c << 8 | c;
	}

	public static EnumProperty<AttachPosition> property(Direction dir) {
		return switch (dir) {
			case NORTH -> NORTH;
			case EAST -> EAST;
			case SOUTH -> SOUTH;
			default -> WEST;
		};
	}

	// ---- connections (the original's getActualState) ----

	private static boolean canConnectTo(BlockState state) {
		return state.is(BlocksTC.inlay.get()) || state.getBlock() instanceof BlockPedestal;
	}

	public static boolean isSourceBlock(BlockGetter level, BlockPos pos) {
		return level.getBlockState(pos).is(BlocksTC.stabilizer.get());
	}

	private static AttachPosition attachPosition(BlockGetter level, BlockPos pos, Direction dir) {
		BlockPos other = pos.relative(dir);
		if (canConnectTo(level.getBlockState(other))) return AttachPosition.SIDE;
		if (isSourceBlock(level, other)) return AttachPosition.EXT;
		return AttachPosition.NONE;
	}

	private static BlockState withConnections(BlockState state, BlockGetter level, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			state = state.setValue(property(dir), attachPosition(level, pos, dir));
		}
		return state;
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return withConnections(defaultBlockState(), context.getLevel(), context.getClickedPos());
	}

	@Override
	protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
		if (direction == Direction.DOWN && !canSurvive(state, level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction.getAxis().isHorizontal()) {
			return state.setValue(property(direction), attachPosition(level, pos, direction));
		}
		return state;
	}

	@Override
	protected BlockState rotate(BlockState state, Rotation rotation) {
		BlockState out = state;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			out = out.setValue(property(rotation.rotate(dir)), state.getValue(property(dir)));
		}
		return out;
	}

	@Override
	protected BlockState mirror(BlockState state, Mirror mirror) {
		BlockState out = state;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			out = out.setValue(property(mirror.mirror(dir)), state.getValue(property(dir)));
		}
		return out;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		boolean n = state.getValue(NORTH) != AttachPosition.NONE;
		boolean e = state.getValue(EAST) != AttachPosition.NONE;
		boolean s = state.getValue(SOUTH) != AttachPosition.NONE;
		boolean w = state.getValue(WEST) != AttachPosition.NONE;
		int i = 0;
		if (n || (s && !n && !e && !w)) i |= 1 << Direction.NORTH.get2DDataValue();
		if (e || (w && !n && !e && !s)) i |= 1 << Direction.EAST.get2DDataValue();
		if (s || (n && !e && !s && !w)) i |= 1 << Direction.SOUTH.get2DDataValue();
		if (w || (e && !n && !s && !w)) i |= 1 << Direction.WEST.get2DDataValue();
		return SHAPES[i];
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
	}

	// ---- charge (the original's redstone-style propagation, kept as it was) ----

	@Override
	protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
		if (!level.isClientSide && !oldState.is(state.getBlock())) {
			// the original derived the connections on every render (getActualState); placed by other means than an item
			// (commands, structures) the state wouldn't have them yet
			BlockState connected = withConnections(state, level, pos);
			if (connected != state) level.setBlock(pos, connected, Block.UPDATE_CLIENTS);
			chargeChanged(level, pos);
		}
	}

	@Override
	protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
		super.onRemove(state, level, pos, newState, movedByPiston);
		if (!level.isClientSide && !state.is(newState.getBlock())) {
			chargeRemoved(level, pos, state, this);
		}
	}

	@Override
	protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean movedByPiston) {
		if (!level.isClientSide) {
			if (canSurvive(state, level, pos)) {
				updateSurroundingInlay(level, pos, state);
			} else {
				dropResources(state, level, pos);
				level.removeBlock(pos, false);
			}
		}
	}

	/** The original's onBlockAdded, shared with the pedestal. */
	public static void chargeChanged(Level level, BlockPos pos) {
		updateSurroundingInlay(level, pos, level.getBlockState(pos));
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			notifyInlayNeighborsOfStateChange(level, pos.relative(dir));
		}
	}

	/** The original's breakBlock, shared with the pedestal. */
	public static void chargeRemoved(Level level, BlockPos pos, BlockState state, Block block) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			level.updateNeighborsAt(pos.relative(dir), block);
		}
		updateSurroundingInlay(level, pos, state);
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			notifyInlayNeighborsOfStateChange(level, pos.relative(dir));
		}
	}

	public static void notifyInlayNeighborsOfStateChange(Level level, BlockPos pos) {
		BlockState bs = level.getBlockState(pos);
		if (bs.is(BlocksTC.inlay.get()) || bs.getBlock() instanceof BlockPedestal) {
			level.updateNeighborsAt(pos, bs.getBlock());
			for (Direction dir : Direction.Plane.HORIZONTAL) {
				level.updateNeighborsAt(pos.relative(dir), bs.getBlock());
			}
		}
	}

	public static BlockState updateSurroundingInlay(Level level, BlockPos pos, BlockState state) {
		Set<BlockPos> blocksNeedingUpdate = new HashSet<>();
		state = calculateChanges(level, pos, pos, state, blocksNeedingUpdate);
		Block self = level.getBlockState(pos).getBlock();
		for (BlockPos p : blocksNeedingUpdate) {
			level.updateNeighborsAt(p, self);
		}
		return state;
	}

	private static int getMaxStrength(Level level, BlockPos pos, int strength) {
		BlockState bs = level.getBlockState(pos);
		if (!bs.hasProperty(CHARGE) || !(bs.is(BlocksTC.inlay.get()) || bs.getBlock() instanceof BlockPedestal)) return strength;
		return Math.max(bs.getValue(CHARGE), strength);
	}

	public static int getSourceStrength(Level level, BlockPos pos) {
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			int e = getSourceStrengthAt(level, pos.relative(dir));
			if (e > 0) return e;
		}
		return 0;
	}

	public static int getSourceStrengthAt(Level level, BlockPos pos) {
		if (isSourceBlock(level, pos) && level.getBlockEntity(pos) instanceof TileStabilizer tile) {
			return tile.getEnergy();
		}
		return 0;
	}

	private static BlockState calculateChanges(Level level, BlockPos pos1, BlockPos pos2, BlockState state, Set<BlockPos> blocksNeedingUpdate) {
		if (!state.hasProperty(CHARGE)) return state;
		BlockState before = state;
		int current = state.getValue(CHARGE);
		int max = getMaxStrength(level, pos2, 0);
		int source = getSourceStrength(level, pos1);
		if (source > 0 && source > max - 1) max = source;
		int neighbour = 0;
		for (Direction dir : Direction.Plane.HORIZONTAL) {
			BlockPos p = pos1.relative(dir);
			if (p.getX() != pos2.getX() || p.getZ() != pos2.getZ()) {
				neighbour = getMaxStrength(level, p, neighbour);
			}
		}
		if (neighbour > max) {
			max = neighbour - 1;
		} else if (max > 0) {
			--max;
		} else {
			max = 0;
		}
		if (source > max - 1) max = source;
		if (current != max) {
			state = state.setValue(CHARGE, max);
			if (level.getBlockState(pos1) == before) {
				level.setBlock(pos1, state, Block.UPDATE_CLIENTS);
			}
			blocksNeedingUpdate.add(pos1);
			for (Direction dir : Direction.values()) {
				blocksNeedingUpdate.add(pos1.relative(dir));
			}
		}
		return state;
	}

	// ---- client ----

	/** The original's randomDisplayTick: a charged inlay sends sparks out along a random connected side. */
	@Override
	public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
		int charge = state.getValue(CHARGE);
		if (charge > 0 && random.nextInt(20 - charge) == 0) {
			Direction face = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			if (state.getValue(property(face)) != AttachPosition.NONE) {
				double x = pos.getX() + 0.5 + random.nextGaussian() * 0.08;
				double y = pos.getY() + 0.025f;
				double z = pos.getZ() + 0.5 + random.nextGaussian() * 0.08;
				double fx = face.getStepX() / 70.0 * (1.0 - random.nextFloat() * 0.1);
				double fz = face.getStepZ() / 70.0 * (1.0 - random.nextFloat() * 0.1);
				float r = Mth.nextInt(random, 150, 200) / 255.0f;
				float g = Mth.nextInt(random, 0, 200) / 255.0f;
				thaumcraft.client.fx.ClientFxHandlers.spawnLineSparkle(level, x, y, z, fx, 0.0, fz, 0.33f, r, g, g / 2.0f, 0, 1.0f, 0.0f, 16);
			}
		}
	}

	@Override
	public boolean canStabaliseInfusion(Level world, BlockPos pos) {
		return true;
	}

	@Override
	public float getStabilizationAmount(Level world, BlockPos pos) {
		return 0.025f;
	}
}
