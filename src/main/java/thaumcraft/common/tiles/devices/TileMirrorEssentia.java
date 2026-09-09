package thaumcraft.common.tiles.devices;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileMirrorEssentia} - the Node Transducer's essentia-transport half (the
 * item-transport half is {@link TileMirror}, which this class mirrors structurally: same {@link MirrorLink}
 * pairing via {@link thaumcraft.common.blocks.devices.BlockMirrorEssentiaItem}'s "charge a copy, place it
 * elsewhere" workflow, same link-restore backoff cycle, same instability→{@link AuraHelper#polluteAura}
 * mechanic).
 *
 * Unlike {@link TileMirror} this tile holds no items and moves nothing on its own - it's a pure
 * {@link IAspectSource} pass-through, exactly matching the real design the research text itself describes
 * ({@code research.MIRRORESSENTIA.stage.2}: "I cannot pipe essentia directly into the mirrors - they are only
 * able to transport essentia that has been drawn from a source towards something like an Infusion Matrix. In
 * effect the mirrors allow these things to draw essentia from sources via the mirror."). A puller
 * ({@link thaumcraft.common.tiles.crafting.TileInfusionMatrix} via {@link thaumcraft.common.lib.events.EssentiaHandler})
 * calls {@link #takeFromContainer}/{@link #doesContainerAccept} on THIS tile; this tile, in turn, does its own
 * small local cube-scan for a real {@link IAspectContainer} near the LINKED partner's position (any
 * dimension) and forwards the request there - the original's own real "let things draw from sources via the
 * mirror" relay, just expressed as a direct local scan instead of the original's {@code EssentiaHandler}
 * overload (direction + range + a live {@code TileMirrorEssentia}-aware exclusion) that was never itself
 * ported (see this port's own {@code EssentiaHandler} doc comment, which explicitly flags exactly this gap).
 *
 * Real, documented simplifications vs. the original:
 * <ul>
 * <li>Omnidirectional scan around the linked partner (range {@value #SCAN_RANGE}) instead of the original's
 * single-facing cone off the partner's own facing - this port's {@code EssentiaHandler.drainEssentia} already
 * established the omnidirectional-cube-scan-instead-of-a-directional-cone simplification for the exact same
 * reason (its own doc comment); kept consistent here rather than inventing a second, narrower scan shape.</li>
 * <li>The scan explicitly skips any other {@code TileMirrorEssentia} it finds, so two mirrors placed near
 * each other can never accidentally chain a pull through one another - the original has no such guard, but
 * also has no real scenario needing one (mirrors are normally placed far apart); a defensive addition, not a
 * missing feature.</li>
 * </ul>
 */
public class TileMirrorEssentia extends BlockEntity implements IAspectSource {

	private static final int BASE_CYCLE = 40;
	private static final int MAX_CYCLE = 600;
	private static final int SCAN_RANGE = 8;

	@Nullable
	private MirrorLink link;
	private int instability;
	private int count;
	private int cycle = BASE_CYCLE;

	public TileMirrorEssentia(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.MIRROR_ESSENTIA.get(), pos, state);
	}

	/** Called by {@code BlockMirrorEssentiaItem#place} once a pre-charged mirror item is placed - see
	 * {@link TileMirror#applyLink} for the identical real pattern. */
	public void applyLink(MirrorLink newLink) {
		this.link = newLink;
		setChanged();
		tryRestoreLink();
	}

	@Nullable
	private TileMirrorEssentia targetTile() {
		if (link == null || !(level instanceof ServerLevel serverLevel)) {
			return null;
		}
		ServerLevel targetLevel = serverLevel.getServer().getLevel(link.dimension());
		if (targetLevel == null || !targetLevel.isLoaded(link.pos())) {
			return null;
		}
		return targetLevel.getBlockEntity(link.pos()) instanceof TileMirrorEssentia tile ? tile : null;
	}

	/** Mutual pairing confirmed - real port of {@link TileMirror#isLinkValid}. */
	public boolean isLinkValid() {
		TileMirrorEssentia target = targetTile();
		return target != null && target.link != null
				&& target.link.dimension().equals(level.dimension()) && target.link.pos().equals(worldPosition);
	}

	private void tryRestoreLink() {
		TileMirrorEssentia target = targetTile();
		if (target != null && !target.isLinkValid()) {
			target.link = new MirrorLink(level.dimension(), worldPosition.immutable());
			target.setChanged();
			setChanged();
		}
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileMirrorEssentia tile) {
		if (level.isClientSide) {
			return;
		}
		tile.checkInstability(level, pos);
		tile.count++;
		if (tile.count % tile.cycle == 0) {
			if (!tile.isLinkValid()) {
				tile.cycle = Math.min(MAX_CYCLE, tile.cycle + 20);
				tile.tryRestoreLink();
			} else {
				tile.cycle = BASE_CYCLE;
			}
		}
	}

	private void checkInstability(Level level, BlockPos pos) {
		if (instability > 64) {
			AuraHelper.polluteAura(level, pos, 1.0f, true);
			instability -= 64;
			setChanged();
		}
		if (instability > 0 && count % 100 == 0) {
			instability--;
			setChanged();
		}
	}

	private void addInstability(int amt) {
		instability += amt;
		setChanged();
	}

	@Nullable
	public MirrorLink getLink() {
		return link;
	}

	// --- IAspectSource / IAspectContainer: a pure relay, holds nothing of its own ---

	@Override
	public boolean isBlocked() {
		return false;
	}

	@Override
	public AspectList getAspects() {
		return null;
	}

	@Override
	public void setAspects(AspectList aspects) {
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		TileMirrorEssentia target = targetTile();
		return target != null && scanFor(target, tag, true) != null;
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		if (amount != 1 || !isLinkValid()) {
			return amount;
		}
		TileMirrorEssentia target = targetTile();
		IAspectContainer sink = target == null ? null : scanFor(target, tag, true);
		if (sink != null && sink.addToContainer(tag, 1) == 0) {
			addInstability(1);
			return 0;
		}
		return amount;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		if (amount != 1 || !isLinkValid()) {
			return false;
		}
		TileMirrorEssentia target = targetTile();
		IAspectContainer source = target == null ? null : scanFor(target, tag, false);
		if (source != null && source.takeFromContainer(tag, 1)) {
			addInstability(1);
			return true;
		}
		return false;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		if (amount != 1 || !isLinkValid()) {
			return false;
		}
		TileMirrorEssentia target = targetTile();
		return target != null && scanFor(target, tag, false) != null;
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return 0;
	}

	/** Cube-scans (range {@value #SCAN_RANGE}) around {@code target}'s own position for the nearest real
	 * {@link IAspectContainer} - excluding {@code target} itself and any other {@code TileMirrorEssentia} - that
	 * either accepts ({@code forAdd}) or holds ({@code !forAdd}) {@code tag}. */
	@Nullable
	private static IAspectContainer scanFor(TileMirrorEssentia target, Aspect tag, boolean forAdd) {
		Level targetLevel = target.getLevel();
		if (targetLevel == null) {
			return null;
		}
		BlockPos origin = target.getBlockPos();
		BlockPos best = null;
		double bestDist = Double.MAX_VALUE;
		for (BlockPos p : BlockPos.betweenClosed(origin.offset(-SCAN_RANGE, -SCAN_RANGE, -SCAN_RANGE), origin.offset(SCAN_RANGE, SCAN_RANGE, SCAN_RANGE))) {
			if (p.equals(origin)) continue;
			BlockEntity be = targetLevel.getBlockEntity(p);
			if (!(be instanceof IAspectContainer container) || be instanceof TileMirrorEssentia) continue;
			if (be instanceof IAspectSource source && source.isBlocked()) continue;
			boolean matches = forAdd ? container.doesContainerAccept(tag) : container.doesContainerContainAmount(tag, 1);
			if (!matches) continue;
			double dist = p.distSqr(origin);
			if (dist < bestDist) {
				bestDist = dist;
				best = p.immutable();
			}
		}
		return best == null ? null : (IAspectContainer) targetLevel.getBlockEntity(best);
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		if (link != null) {
			MirrorLink.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), link)
					.result().ifPresent(encoded -> tag.put("link", encoded));
		}
		tag.putInt("instability", instability);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		link = tag.contains("link")
				? MirrorLink.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("link")).result().orElse(null)
				: null;
		instability = tag.getInt("instability");
	}
}
