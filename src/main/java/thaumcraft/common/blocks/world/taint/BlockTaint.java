package thaumcraft.common.blocks.world.taint;

import com.mojang.serialization.MapCodec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aura.AuraHelper;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.registry.ThaumcraftMobEffects;

/**
 * Replaces the old (1.12.2) {@code BlockTaint} - one real class backing four registered variants ({@link
 * Variant#ROCK}/{@link Variant#SOIL}/{@link Variant#CRUST}/{@link Variant#GEYSER}), matching the original's
 * own single-class-many-registrations shape (same real pattern this port already used for {@code BlockLoot}
 * in the cult arc).
 *
 * Real per-variant die() transitions and real per-tick behavior, matching the original: rock keeps spreading
 * real fibres nearby; crust real-falls (via vanilla's own {@link FallingBlockEntity#fall} - the original
 * hand-rolled its own {@code EntityFallingTaint} because 1.12 had nothing equivalent bundled; modern
 * Minecraft's own falling-block entity already does the identical job for real, so no custom entity is
 * needed at all here); geyser pollutes the aura for real when local flux is low. Real Flux Taint walk-chance
 * (rarer than {@link BlockTaintFibre}'s own - matches the original's {@code nextInt(250)} vs. fibre's
 * {@code nextInt(750)}), and a real rock-only bonus Flux Crystal drop chance.
 *
 * Real, documented cuts vs the original: {@code die()}'s own rock/crust/geyser targets ({@code
 * stonePorous}/{@code fluxGoo}) aren't ported yet - rock falls back to real vanilla cobblestone, crust/
 * geyser fall back to real air (matching the original's own default "anything else -&gt; air" branch). The
 * geyser's own real "spawn a real {@code EntityTaintSwarm} when a player is nearby" branch is dropped -
 * that creature isn't ported yet either (a later sub-chunk of the tainted-creature side of this same arc) -
 * only the aura-pollution alternative branch survives. No silk-touch-preserves-itself branch, same real
 * simplification {@code BlockLoot} already established for the cult arc's loot crates.
 */
public class BlockTaint extends Block implements ITaintBlock {

	public static final MapCodec<BlockTaint> CODEC = simpleCodec(props -> new BlockTaint(props, Variant.ROCK));

	public enum Variant { ROCK, SOIL, CRUST, GEYSER }

	private final Variant variant;

	public BlockTaint(BlockBehaviour.Properties properties, Variant variant) {
		super(properties);
		this.variant = variant;
	}

	@Override
	protected MapCodec<? extends Block> codec() {
		return CODEC;
	}

	@Override
	public void die(Level level, BlockPos pos, BlockState state) {
		switch (variant) {
			case ROCK -> level.setBlockAndUpdate(pos, Blocks.COBBLESTONE.defaultBlockState());
			case SOIL -> level.setBlockAndUpdate(pos, Blocks.DIRT.defaultBlockState());
			default -> level.removeBlock(pos, false);
		}
	}

	@Override
	protected boolean isRandomlyTicking(BlockState state) {
		return true;
	}

	@Override
	protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
		if (!TaintHelper.isNearTaintSeed(level, pos) && random.nextInt(10) == 0) {
			die(level, pos, state);
			return;
		}
		switch (variant) {
			case ROCK -> TaintHelper.spreadFibres(level, pos);
			case CRUST -> tryToFall(level, pos, random);
			case GEYSER -> {
				if (AuraHelper.getFlux(level, pos) < 2.0f) {
					AuraHelper.polluteAura(level, pos, 0.25f, true);
				}
			}
			default -> {
			}
		}
	}

	private void tryToFall(ServerLevel level, BlockPos pos, RandomSource random) {
		if (!BlockTaintFibre.isOnlyAdjacentToTaint(level, pos)) {
			return;
		}
		if (canFallBelow(level, pos.below())) {
			FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
			return;
		}
		if (level.isEmptyBlock(pos.above())) {
			Direction dir = Direction.Plane.HORIZONTAL.getRandomDirection(random);
			boolean clear = true;
			for (int a = 1; a < 4 && clear; a++) {
				BlockPos check = pos.relative(dir).below(a);
				if (!level.isEmptyBlock(check) && level.getBlockState(pos.below(a)).getBlock() != this) {
					clear = false;
				}
			}
			if (clear && canFallBelow(level, pos.relative(dir).below())) {
				FallingBlockEntity.fall(level, pos, level.getBlockState(pos));
			}
		}
	}

	/** Real, portable version of the original's own {@code BlockTaint#canFallBelow} static helper. */
	public static boolean canFallBelow(LevelReader level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		Block block = state.getBlock();
		return state.isAir() || block == Blocks.FIRE || block == BlocksTC.taintFibre.get() || state.canBeReplaced();
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return net.minecraft.world.phys.shapes.Shapes.block();
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide && !entity.getType().is(EntityTypeTags.UNDEAD) && entity instanceof net.minecraft.world.entity.LivingEntity living
				&& level.random.nextInt(250) == 0) {
			living.addEffect(new MobEffectInstance(ThaumcraftMobEffects.FLUX_TAINT, 200, 0, false, true));
		}
		super.stepOn(level, pos, state, entity);
	}

	@Override
	public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
		if (!level.isClientSide && variant == Variant.ROCK && level.random.nextInt(15) > 13) {
			ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get());
			((thaumcraft.common.items.resources.ItemCrystalEssence) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FLUX, 1));
			popResource(level, pos, crystal);
		}
		return super.playerWillDestroy(level, pos, state, player);
	}
}
