package thaumcraft.common.golems;

import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.golems.ISealDisplayer;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.common.golems.seals.SealHandler;

/**
 * Replaces the old (1.12.2) {@code ItemSealPlacer}. The original shared ONE item with a metadata subtype
 * per registered seal type (dynamically sized off {@code SealHandler.getRegisteredSeals()}, {@code 0}
 * meaning "blank"); metadata subtypes don't exist anymore, and this port's own established precedent is to
 * split metadata variants into separate registered items instead (see {@code GolemProperties}' own doc
 * comment) - so this ships as one small, reusable item class parameterized by a {@link Supplier}&lt;{@link ISeal}&gt;,
 * with one registered {@link net.minecraft.world.item.DeferredItem} per real seal type in {@code ItemsTC}
 * (only {@code sealGuard} so far - see {@code SealHandler#init}'s own doc comment for the rest).
 *
 * Right-click places a fresh seal instance (a NEW one every placement - {@link ISeal} implementations can
 * hold real per-placement mutable state, see {@code SealEntity}'s own doc comment) against the clicked
 * block face, same real {@code canPlaceAt} + {@code player.mayUseItemAt} checks the original made. Sneaking
 * bypasses placement entirely (matches the original's own {@code doesSneakBypassUse} + sneak-guard, keeping
 * sneak free for other seal interactions like the still-unported {@code ItemGolemBell}'s removal gesture).
 */
public class ItemSealPlacer extends Item implements ISealDisplayer {

	private final Supplier<ISeal> sealFactory;

	/** The key of the seal this places (e.g. {@code thaumcraft:provider}). */
	public String getSealKey() {
		return sealFactory.get().getKey();
	}

	/** The icon of the seal this places. */
	public net.minecraft.resources.ResourceLocation getSealIcon() {
		return sealFactory.get().getSealIcon();
	}

	public ItemSealPlacer(Properties properties, Supplier<ISeal> sealFactory) {
		super(properties);
		this.sealFactory = sealFactory;
	}

	/** The original did this in {@code onItemUseFirst}, before the clicked block's own use - so a seal goes onto a chest instead of opening it. (Fixed 2026-09-29: it was only on {@link #useOn}, which runs after the block.) */
	@Override
	public InteractionResult onItemUseFirst(net.minecraft.world.item.ItemStack stack, UseOnContext context) {
		return useOn(context);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Player player = context.getPlayer();
		Level level = context.getLevel();
		if (level.isClientSide || player == null || player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		BlockPos pos = context.getClickedPos();
		Direction face = context.getClickedFace();
		if (!player.mayUseItemAt(pos, face, context.getItemInHand())) {
			return InteractionResult.FAIL;
		}
		ISeal seal = sealFactory.get();
		if (seal == null || !seal.canPlaceAt(level, pos, face)) {
			return InteractionResult.FAIL;
		}
		if (SealHandler.addSealEntity(level, pos, face, seal, player)) {
			if (!player.getAbilities().instabuild) {
				context.getItemInHand().shrink(1);
			}
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.FAIL;
	}
}
