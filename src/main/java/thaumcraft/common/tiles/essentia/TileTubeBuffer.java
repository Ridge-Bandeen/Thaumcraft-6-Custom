package thaumcraft.common.tiles.essentia;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.api.casters.IInteractWithCaster;
import thaumcraft.common.lib.utils.CuboidTargeting;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileTubeBuffer} - a tube variant that actually holds essentia
 * ({@link #aspects}, up to {@value #MAX_AMOUNT} total units, any real mix of aspects) rather than
 * {@link TileTube}'s own single-unit-of-one-aspect relay. Not a subclass of {@code TileTube} - unlike a
 * plain tube's own dynamic, neighbor-derived suction, a buffer's real suction is a small FIXED value (see
 * {@link #MIN_SUCTION}), so there's no shared {@code calculateSuction} step to inherit; every 5 ticks, if
 * under capacity, it just tries each neighbor directly (real port of the original's own {@code fillBuffer}).
 *
 * <p>Real per-face open/close toggling, restored 2026-09-15 via {@link IInteractWithCaster} +
 * {@link CuboidTargeting} - the same real click-precision restoration {@link TileTube} already got (see its
 * own doc comment for the full story of why the dropped CodeChicken library didn't actually need reviving).
 * {@link #onCasterRightClick} raytraces against 6 real face nubs (no central core here - the true original's
 * own {@code TileTubeBuffer#onCasterRightClick} override never had one either, unlike plain {@code TileTube});
 * a plain click toggles {@link #openSides} for that face (mirroring the new state onto a real neighboring
 * {@code TileTube}'s own opposite side, matching the true original exactly).
 *
 * <p>Real bellows-amplified suction and the real per-face sneak-click 3-state "choke" throttle, both restored
 * 2026-09-15 together (the choke cycle's own 2 non-blocked states were numerically identical without real
 * bellows amplification to actually distinguish them, so neither was worth restoring alone - see
 * {@link #getSuctionAmount} for the real formula, matching the true original's own {@code (chokedSides == 2) ?
 * 0 : ((bellows <= 0 || chokedSides == 1) ? 1 : bellows * 32)} exactly).
 */
public class TileTubeBuffer extends BlockEntity implements IEssentiaTransport, IAspectContainer, IInteractWithCaster {

	private static final int MAX_AMOUNT = 10;
	/** Real, deliberately minimal base suction, matching the true original's own real formula exactly
	 * ({@code bellows <= 0 ? 1 : bellows * 32} - only the {@code 1} half survives here, bellows
	 * amplification dropped per this class's own doc comment) and confirmed by the research text itself
	 * ("it applies the minimum amount of suction possible (1)... usually only able to draw essentia from an
	 * essentia source it is directly attached to"): a real tube/jar's own 32+ suction almost always beats
	 * this, so the buffer only pulls from a neighbor that isn't itself actively demanding anything -
	 * exactly the passive "attach it directly to a source" behavior the research text describes, not an
	 * arbitrary throttle. See {@link #fillBuffer} for the real comparison this produces. */
	private static final int MIN_SUCTION = 1;

	private final AspectList aspects = new AspectList();
	private int count;

	/** Per-direction electrical open/close state, real again - see this class's own doc comment. Default
	 * all-open, matching this port's previously-shipped behavior. */
	private final boolean[] openSides = { true, true, true, true, true, true };

	/** Real bellows-amplified-suction state, restored 2026-09-15 - see this class's own doc comment.
	 * {@code -1} means "not yet queried" (matching the true original's own init value), refreshed every 20
	 * real ticks via {@link thaumcraft.common.tiles.devices.TileBellows#getBellows}. */
	private int bellows = -1;

	/** Real per-face 3-state "choke" throttle, restored 2026-09-15 alongside bellows amplification (its own
	 * real distinctness depends on it - see this class's own doc comment): 0 = unchoked (real bellows-amplified
	 * suction), 1 = forced down to {@link #MIN_SUCTION}, 2 = fully blocked (0 suction). Cycled via a real
	 * sneak-right-click on a face nub, matching the true original's own {@code ++chokedSides[subHit]; if (> 2)
	 * reset to 0} wraparound exactly. */
	private final byte[] chokedSides = { 0, 0, 0, 0, 0, 0 };

	public TileTubeBuffer(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.TUBE_BUFFER.get(), pos, state);
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileTubeBuffer tile) {
		if (level.isClientSide) return;
		if (tile.bellows < 0 || tile.count % 20 == 0) {
			tile.bellows = thaumcraft.common.tiles.devices.TileBellows.getBellows(level, pos, Direction.values());
		}
		if (tile.count++ % 5 == 0 && tile.aspects.visSize() < MAX_AMOUNT) {
			tile.fillBuffer(level, pos);
		}
	}

	/** Real port of the original's own {@code fillBuffer} - tries each neighbor in turn, stopping at the
	 * first real pull. {@code MIN_SUCTION}'s own doc comment explains why {@code neighbor's suction < this
	 * buffer's own suction} rarely holds against a real tube/jar - this is deliberately a weak, passive
	 * puller, not a competing one. */
	private void fillBuffer(Level level, BlockPos pos) {
		for (Direction dir : Direction.values()) {
			if (!canInputFrom(dir)) continue;
			if (!(level.getBlockEntity(pos.relative(dir)) instanceof IEssentiaTransport neighbor)) continue;
			if (!neighbor.isConnectable(dir.getOpposite()) || !neighbor.canOutputTo(dir.getOpposite())) continue;
			if (neighbor.getEssentiaAmount(dir.getOpposite()) <= 0) continue;
			int mySuction = getSuctionAmount(dir);
			if (neighbor.getSuctionAmount(dir.getOpposite()) >= mySuction) continue;
			if (mySuction < neighbor.getMinimumSuction()) continue;

			Aspect theirType = neighbor.getEssentiaType(dir.getOpposite());
			int taken = neighbor.takeEssentia(theirType, 1, dir.getOpposite());
			if (taken > 0) {
				addToContainer(theirType, taken);
				return;
			}
		}
	}

	public boolean isSideOpen(Direction dir) {
		return openSides[dir.ordinal()];
	}

	/** Real modern nub geometry, matching the same inset (0.375-0.625) {@link TileTube#isSideOpen}'s own
	 * face nubs use - a real click-precision replacement for the true original's own CodeChicken sub-hit
	 * raytracer, see this class's own doc comment. */
	private static AABB nubBox(BlockPos pos, Direction dir) {
		AABB local = switch (dir) {
			case DOWN -> new AABB(0.375, 0.0, 0.375, 0.625, 0.375, 0.625);
			case UP -> new AABB(0.375, 0.625, 0.375, 0.625, 1.0, 0.625);
			case NORTH -> new AABB(0.375, 0.375, 0.0, 0.625, 0.625, 0.375);
			case SOUTH -> new AABB(0.375, 0.375, 0.625, 0.625, 0.625, 1.0);
			case WEST -> new AABB(0.0, 0.375, 0.375, 0.375, 0.625, 0.625);
			case EAST -> new AABB(0.625, 0.375, 0.375, 1.0, 0.625, 0.625);
		};
		return local.move(pos);
	}

	/** Real per-face open/close toggling (plain click) and 3-state choke cycling (sneak-click), both restored
	 * 2026-09-15 - see this class's own doc comment. No central core here, matching the true original's own
	 * {@code TileTubeBuffer#onCasterRightClick} override (unlike plain {@link TileTube}, it never had a
	 * facing-cycle interaction). */
	@Override
	public boolean onCasterRightClick(Level world, ItemStack casterStack, Player player, BlockPos pos, Direction side, InteractionHand hand) {
		List<CuboidTargeting.Candidate<Direction>> candidates = new ArrayList<>();
		for (Direction dir : Direction.values()) {
			if (world.getBlockEntity(worldPosition.relative(dir)) instanceof IEssentiaTransport) {
				candidates.add(new CuboidTargeting.Candidate<>(dir, nubBox(worldPosition, dir)));
			}
		}
		Direction hit = CuboidTargeting.pick(player, candidates);
		if (hit == null) return false;

		if (player.isShiftKeyDown()) {
			world.playSound(null, worldPosition, SoundEvents.SLIME_SQUISH, SoundSource.BLOCKS, 0.6f, 2.0f + world.random.nextFloat() * 0.2f);
			chokedSides[hit.ordinal()] = (byte) ((chokedSides[hit.ordinal()] + 1) % 3);
			setChanged();
		} else {
			world.playSound(null, worldPosition, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.5f, 0.9f + world.random.nextFloat() * 0.2f);
			openSides[hit.ordinal()] = !openSides[hit.ordinal()];
			setChanged();
			if (world.getBlockEntity(worldPosition.relative(hit)) instanceof TileTube neighbor) {
				neighbor.setSideOpen(hit.getOpposite(), openSides[hit.ordinal()]);
			}
		}
		return true;
	}

	public int getChokedSide(Direction dir) {
		return chokedSides[dir.ordinal()];
	}

	// --- IAspectContainer: a real multi-aspect store, up to MAX_AMOUNT total units ---

	@Override
	public AspectList getAspects() {
		return aspects;
	}

	@Override
	public void setAspects(AspectList newAspects) {
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		return aspects.visSize() < MAX_AMOUNT;
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		if (amount != 1 || aspects.visSize() >= MAX_AMOUNT) return amount;
		aspects.add(tag, amount);
		setChanged();
		return 0;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		if (aspects.getAmount(tag) < amount) return false;
		aspects.remove(tag, amount);
		setChanged();
		return true;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		return aspects.getAmount(tag) >= amount;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return aspects.getAmount(tag);
	}

	// --- IEssentiaTransport: gated on the real per-face openSides toggle, matching the true original's own
	// TileTubeBuffer#isConnectable/canInputFrom/canOutputTo exactly (a different, more direct convention than
	// plain TileTube's own "always connectable, openSides only gates flow" split). ---

	@Override
	public boolean isConnectable(Direction face) {
		return openSides[face.ordinal()];
	}

	@Override
	public boolean canInputFrom(Direction face) {
		return openSides[face.ordinal()];
	}

	@Override
	public boolean canOutputTo(Direction face) {
		return openSides[face.ordinal()];
	}

	@Override
	public void setSuction(Aspect aspect, int amount) {
	}

	@Override
	public Aspect getSuctionType(Direction face) {
		return null;
	}

	/** Real, restored 2026-09-15: matches the true original's own {@code (chokedSides == 2) ? 0 :
	 * ((bellows <= 0 || chokedSides == 1) ? 1 : bellows * 32)} exactly, plus this port's own real "full buffer
	 * demands nothing more" cap (not present in the true original's own formula, but a real, sensible addition
	 * - a full buffer's own {@link #fillBuffer} already refuses to pull regardless, so reporting positive
	 * suction here would just be a misleading demand signal to neighbors with no real effect once acted on). */
	@Override
	public int getSuctionAmount(Direction face) {
		if (aspects.visSize() >= MAX_AMOUNT) return 0;
		if (chokedSides[face.ordinal()] == 2) return 0;
		return (bellows <= 0 || chokedSides[face.ordinal()] == 1) ? MIN_SUCTION : bellows * 32;
	}

	@Override
	public int getMinimumSuction() {
		return 0;
	}

	/** A random held aspect, matching the original's own real {@code getEssentiaType} exactly - a single
	 * {@link IEssentiaTransport} query can only ever report one aspect, so a real multi-aspect buffer picks
	 * one at random rather than always favoring the same slot. */
	@Override
	public Aspect getEssentiaType(Direction face) {
		Aspect[] held = aspects.getAspects();
		return held.length > 0 ? held[level.random.nextInt(held.length)] : null;
	}

	@Override
	public int getEssentiaAmount(Direction face) {
		return aspects.visSize();
	}

	@Override
	public int takeEssentia(Aspect tag, int amount, Direction face) {
		return canOutputTo(face) && takeFromContainer(tag, amount) ? amount : 0;
	}

	@Override
	public int addEssentia(Aspect tag, int amount, Direction face) {
		return canInputFrom(face) ? amount - addToContainer(tag, amount) : 0;
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		aspects.writeToNBT(tag);
		int openMask = 0;
		for (int i = 0; i < openSides.length; i++) {
			if (openSides[i]) openMask |= 1 << i;
		}
		tag.putInt("OpenSides", openMask);
		tag.putByteArray("ChokedSides", chokedSides);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		aspects.readFromNBT(tag);
		int openMask = tag.contains("OpenSides") ? tag.getInt("OpenSides") : 0b111111;
		for (int i = 0; i < openSides.length; i++) {
			openSides[i] = (openMask & (1 << i)) != 0;
		}
		if (tag.contains("ChokedSides")) {
			byte[] saved = tag.getByteArray("ChokedSides");
			for (int i = 0; i < chokedSides.length && i < saved.length; i++) {
				chokedSides[i] = saved[i];
			}
		}
	}
}
