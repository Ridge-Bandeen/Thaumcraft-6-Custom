package thaumcraft.common.entities.construct;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Replaces the old (1.12.2) {@code ItemTurretPlacer} - places a real {@link EntityOwnedConstruct} turret
 * (2-tall-clearance, non-{@code DOWN}-face, no-entities-in-the-way checks all ported exactly) tamed to the
 * placing player. Ownership is handed off via vanilla {@link net.minecraft.world.entity.TamableAnimal#tame},
 * not the original's own {@code setOwned}/{@code setOwnerId} pair, same reasoning as {@code ItemGolemPlacer}'s
 * own doc comment; {@link EntityOwnedConstruct#setValidSpawn()} is still called explicitly since {@code tame}
 * doesn't know about that flag.
 *
 * Real, documented scope cut: the original's own {@code "turret"} item carried 3 metadata subtypes (basic/
 * advanced/bore, spawning {@code EntityTurretCrossbow}/{@code EntityTurretCrossbowAdvanced}/{@code
 * EntityArcaneBore} respectively) - this port instead registers one plain item per real variant (matching
 * this port's own established "no metadata subtypes" convention), all three sharing this one class
 * parameterized by {@link #entityType} (widened to {@link EntityOwnedConstruct} rather than {@link
 * EntityTurretCrossbow} specifically, since {@code EntityArcaneBore} is a sibling of that class, not a
 * subclass of it). {@code EntityArcaneBore} additionally needs a real facing at placement time - handled via
 * the {@code instanceof} check right after spawning, same real one-off special case the original's own
 * {@code switch} on damage value needed for its own bore branch.
 */
public class ItemTurretPlacer extends Item {

	private final Supplier<EntityType<? extends EntityOwnedConstruct>> entityType;

	public ItemTurretPlacer(Properties properties, Supplier<EntityType<? extends EntityOwnedConstruct>> entityType) {
		super(properties);
		this.entityType = entityType;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Direction side = context.getClickedFace();
		if (side == Direction.DOWN) {
			return InteractionResult.PASS;
		}
		Level level = context.getLevel();
		BlockPos clicked = context.getClickedPos();
		BlockState clickedState = level.getBlockState(clicked);
		BlockPos pos = clickedState.canBeReplaced() ? clicked : clicked.relative(side);

		Player player = context.getPlayer();
		ItemStack stack = context.getItemInHand();
		if (player == null || !player.mayUseItemAt(pos, side, stack)) {
			return InteractionResult.PASS;
		}

		BlockPos above = pos.above();
		boolean blocked = !level.isEmptyBlock(pos) && !level.getBlockState(pos).canBeReplaced();
		blocked |= !level.isEmptyBlock(above) && !level.getBlockState(above).canBeReplaced();
		if (blocked) {
			return InteractionResult.PASS;
		}

		AABB box = new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1.0, pos.getY() + 2.0, pos.getZ() + 1.0);
		List<Entity> occupants = level.getEntities((Entity) null, box);
		if (!occupants.isEmpty()) {
			return InteractionResult.PASS;
		}

		if (level.isClientSide) {
			return InteractionResult.SUCCESS;
		}

		level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
		level.setBlockAndUpdate(above, Blocks.AIR.defaultBlockState());

		EntityOwnedConstruct turret = entityType.get().create((ServerLevel) level);
		if (turret == null) {
			return InteractionResult.PASS;
		}
		turret.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0f, 0.0f);
		if (turret instanceof EntityArcaneBore bore) {
			bore.setFacing(player.getDirection());
		}
		if (!level.addFreshEntity(turret)) {
			return InteractionResult.PASS;
		}
		turret.tame(player);
		turret.setValidSpawn();
		level.playSound(null, turret.getX(), turret.getY(), turret.getZ(), SoundEvents.ARMOR_STAND_PLACE, SoundSource.BLOCKS, 0.75f, 0.8f);

		if (!player.getAbilities().instabuild) {
			stack.shrink(1);
		}
		return InteractionResult.SUCCESS;
	}
}
