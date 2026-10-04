package thaumcraft.common.items.tools;

import java.util.ArrayList;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ShovelItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.items.IArchitect;
import thaumcraft.api.items.ThaumcraftDataComponents;

/**
 * Replaces the old (1.12.2) {@code ItemElementalShovel}, as the original: right-clicking a block (one without a block
 * entity) builds up to a 3x3 of that same block against the clicked face, each from one of that block taken from your
 * inventory (grass, if you have none, from dirt, placed as dirt), a point of durability apiece. The pattern lies flat on
 * the face, or (turned with the misc caster key, G) stands up across it either way. While sneaking it shows where the
 * blocks would go (an {@link IArchitect} using the block highlight).
 */
public class ItemElementalShovel extends ShovelItem implements IArchitect {

	public ItemElementalShovel(Tier tier, Item.Properties properties) {
		super(tier, properties);
	}

	public static int getOrientation(ItemStack stack) {
		return stack.getOrDefault(ThaumcraftDataComponents.SHOVEL_ORIENTATION.get(), 0);
	}

	/** The original's offset of cell (aa, bb) of the 3x3 for this orientation, clicked face and facing. */
	private static BlockPos cell(int aa, int bb, int o, Direction side, float yaw) {
		int xx = 0, yy = 0, zz = 0;
		int s = side.ordinal();
		if (o == 1) {
			yy = bb;
			if (s <= 1) {
				int l = Mth.floor(yaw * 4.0f / 360.0f + 0.5) & 3;
				if (l == 0 || l == 2) xx = aa;
				else zz = aa;
			} else if (s <= 3) {
				zz = aa;
			} else {
				xx = aa;
			}
		} else if (o == 2) {
			if (s <= 1) {
				int l = Mth.floor(yaw * 4.0f / 360.0f + 0.5) & 3;
				yy = bb;
				if (l == 0 || l == 2) xx = aa;
				else zz = aa;
			} else {
				zz = bb;
				xx = aa;
			}
		} else if (s <= 1) {
			xx = aa;
			zz = bb;
		} else if (s <= 3) {
			xx = aa;
			yy = bb;
		} else {
			zz = aa;
			yy = bb;
		}
		return new BlockPos(xx, yy, zz);
	}

	private static boolean canPlace(Level level, BlockState state, BlockPos p) {
		return level.getBlockState(p).canBeReplaced() && state.canSurvive(level, p);
	}

	/** The original's consumePlayerItem: takes one of {@code item} from the inventory. */
	private static boolean consume(Player player, Item item) {
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack s = player.getInventory().getItem(i);
			if (!s.isEmpty() && s.is(item)) {
				s.shrink(1);
				return true;
			}
		}
		return false;
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		BlockPos pos = context.getClickedPos();
		Direction side = context.getClickedFace();
		if (player == null || level.getBlockEntity(pos) != null) return InteractionResult.FAIL;
		BlockState bs = level.getBlockState(pos);
		ItemStack tool = context.getItemInHand();
		int o = getOrientation(tool);
		for (int aa = -1; aa <= 1; aa++) {
			for (int bb = -1; bb <= 1; bb++) {
				BlockPos p2 = pos.relative(side).offset(cell(aa, bb, o, side, player.getYRot()));
				if (!canPlace(level, bs, p2)) continue;
				BlockState place = null;
				if (player.getAbilities().instabuild || consume(player, bs.getBlock().asItem())) {
					place = bs;
				} else if (bs.is(Blocks.GRASS_BLOCK) && consume(player, Blocks.DIRT.asItem())) {
					place = Blocks.DIRT.defaultBlockState();
				}
				if (place == null) continue;
				level.playSound(player, p2, bs.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.6f, 0.9f + level.random.nextFloat() * 0.2f);
				level.setBlockAndUpdate(p2, place);
				tool.hurtAndBreak(1, player, LivingEntity.getSlotForHand(context.getHand()));
				if (level.isClientSide) {
					thaumcraft.client.fx.ClientFxHandlers.spawnBamf(level, p2.getX() + 0.5, p2.getY() + 0.5, p2.getZ() + 0.5, 8401408, false, false, side);
				}
				player.swing(context.getHand());
				if (tool.isEmpty()) return InteractionResult.FAIL;
			}
		}
		return InteractionResult.FAIL;
	}

	@Override
	public ArrayList<BlockPos> getArchitectBlocks(ItemStack stack, Level level, BlockPos pos, Direction side, Player player) {
		ArrayList<BlockPos> out = new ArrayList<>();
		if (!player.isShiftKeyDown()) return out;
		BlockState bs = level.getBlockState(pos);
		int o = getOrientation(stack);
		for (int aa = -1; aa <= 1; aa++) {
			for (int bb = -1; bb <= 1; bb++) {
				BlockPos p2 = pos.relative(side).offset(cell(aa, bb, o, side, player.getYRot()));
				if (canPlace(level, bs, p2)) out.add(p2);
			}
		}
		return out;
	}

	@Override
	public boolean showAxis(ItemStack stack, Level level, Player player, Direction side, EnumAxis axis) {
		return false;
	}

	@Override
	public HitResult getArchitectMOP(ItemStack stack, Level level, LivingEntity player) {
		double reach = player instanceof Player p ? p.blockInteractionRange() : 4.5;
		return player.pick(reach, 1.0f, false);
	}

	@Override
	public boolean useBlockHighlight(ItemStack stack) {
		return true;
	}
}
