package thaumcraft.common.tiles.devices;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import thaumcraft.common.lib.events.EssentiaHandler;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
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
 * calls {@link #takeFromContainer}/{@link #doesContainerAccept} on THIS tile, which, as the original, looks for a
 * source in the 8-deep box in front of its LINKED partner (through {@code EssentiaHandler}, skipping other mirrors)
 * and forwards the request there. Each unit moved adds instability.
 */
public class TileMirrorEssentia extends BlockEntity implements IAspectSource {

	private static final int BASE_CYCLE = 40;
	private static final int MAX_CYCLE = 600;
	private static final int RANGE = 8;

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

	/** The partner's facing - where its essentia is looked for. */
	private static Direction facingOf(TileMirrorEssentia target) {
		BlockState state = target.getBlockState();
		return state.hasProperty(BlockStateProperties.FACING) ? state.getValue(BlockStateProperties.FACING) : Direction.UP;
	}

	@Override
	public boolean doesContainerAccept(Aspect tag) {
		// as the original: a mirror without a partner says yes (and then takes nothing)
		TileMirrorEssentia target = targetTile();
		return target == null || EssentiaHandler.canAcceptEssentia(target, tag, facingOf(target), RANGE, true);
	}

	@Override
	public int addToContainer(Aspect tag, int amount) {
		if (!isLinkValid() || amount > 1) {
			return amount;
		}
		TileMirrorEssentia target = targetTile();
		if (target == null) return amount;
		boolean added = EssentiaHandler.addEssentia(target, tag, facingOf(target), RANGE, true, 5);
		if (added) addInstability(amount);
		return added ? 0 : 1;
	}

	@Override
	public boolean takeFromContainer(Aspect tag, int amount) {
		if (!isLinkValid() || amount > 1) {
			return false;
		}
		TileMirrorEssentia target = targetTile();
		if (target == null) return false;
		boolean taken = EssentiaHandler.drainEssentia(target, tag, facingOf(target), RANGE, true, 5);
		if (taken) addInstability(amount);
		return taken;
	}

	@Override
	public boolean takeFromContainer(AspectList ot) {
		return false;
	}

	@Override
	public boolean doesContainerContainAmount(Aspect tag, int amount) {
		if (!isLinkValid() || amount > 1) {
			return false;
		}
		TileMirrorEssentia target = targetTile();
		return target != null && EssentiaHandler.findEssentia(target, tag, facingOf(target), RANGE, true);
	}

	@Override
	public boolean doesContainerContain(AspectList ot) {
		return false;
	}

	@Override
	public int containerContains(Aspect tag) {
		return 0;
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

	@Override
	public void setRemoved() {
		super.setRemoved();
		thaumcraft.common.lib.events.EssentiaHandler.forget(this);
	}
}
