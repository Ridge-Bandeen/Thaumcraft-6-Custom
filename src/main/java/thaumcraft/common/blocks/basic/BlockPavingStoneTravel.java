package thaumcraft.common.blocks.basic;

import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the old (1.12.2) {@code BlockPavingStone} - but only its {@code paving_stone_travel} variant.
 * The original shared one class across two registered blocks, {@code paving_stone_travel} (a speed boost
 * underfoot) and {@code paving_stone_barrier} (a warding-network floor tile tied to {@code BlockBarrier}/
 * {@code TileBarrierStone}, a separate standalone subsystem for keeping mobs out of an area - not touched
 * this session, deferred like the Infusion Altar). {@code BlockPavingStoneTravel} only ever needed the
 * shared class's {@code onEntityWalk} behavior, so it's ported as its own plain class instead of carrying
 * the original's "compare against BlocksTC.pavingStoneBarrier" branching for a variant this session doesn't
 * build.
 *
 * Grants a short Speed II + Jump Boost I to any {@link LivingEntity} that walks across it - matches the
 * original's exact potion levels/durations. Reuses vanilla's own {@code block/farmland} model (the original
 * already did this for the same "thin slab" 15/16-height shape) - only a blockstate format conversion was
 * needed, no new geometry.
 */
public class BlockPavingStoneTravel extends Block {

	private static final VoxelShape SHAPE = Shapes.box(0.0, 0.0, 0.0, 1.0, 0.9375, 1.0);

	public BlockPavingStoneTravel(BlockBehaviour.Properties properties) {
		super(properties);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPE;
	}

	@Override
	public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
		if (!level.isClientSide && entity instanceof LivingEntity living) {
			living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 40, 1, false, false)); // as the original: no particles
			living.addEffect(new MobEffectInstance(MobEffects.JUMP, 40, 0, false, false));
		}
		super.stepOn(level, pos, state, entity);
	}
}
