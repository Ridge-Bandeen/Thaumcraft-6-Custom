package thaumcraft.common.items.consumables;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IEssentiaContainerItem;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.tiles.essentia.TileJarFillable;

/**
 * Replaces the old (1.12.2) {@code ItemPhial} - a small essentia bottle, filled/emptied from a Warded Jar
 * (10 essentia per fill, matching the original's own {@code base}). Deliberately not a subclass of
 * {@link thaumcraft.api.items.ItemGenericEssentiaContainer} despite sharing its {@code IEssentiaContainerItem}
 * plumbing - that base class auto-assigns a random aspect on craft/tick (right for Crystal Essence's
 * "shimmering gem" flavor, wrong here: a phial needs to stay genuinely empty until manually filled).
 *
 * A single item, not the original's separate empty/filled damage-value variants - "filled" is just whether
 * the {@code ASPECTS} data component is present, matching every other essentia-container item this session
 * (Crystal Essence). The item model switches its icon on the same presence check, registered as an
 * {@code ItemProperties} predicate in {@code ThaumcraftClient} (the "focus" predicate on {@code caster_basic}
 * is the same technique, reused here as "filled").
 *
 * Simplified vs the original: no Alembic fill path (the Alembic itself isn't ported yet - deferred like every
 * other Alembic-dependent feature this session) - only the Jar fill/empty interactions are ported. Uses
 * NeoForge's {@code onItemUseFirst} (still present on {@code IItemExtension}, unchanged in spirit from the
 * original's own use of the same hook) to intercept the click before the jar's own right-click handling runs.
 */
public class ItemPhial extends Item implements IEssentiaContainerItem {

	/** As the original: "Glass Phial" empty, "Phial of Ignis Essentia" filled (the aspect's own, untranslated name). */
	@Override
	public net.minecraft.network.chat.Component getName(ItemStack stack) {
		thaumcraft.api.aspects.AspectList aspects = stack.get(ThaumcraftDataComponents.ASPECTS.get());
		if (aspects != null && aspects.size() > 0) {
			return net.minecraft.network.chat.Component.translatable("item.thaumcraft.phial.filled", aspects.getAspects()[0].getName());
		}
		return super.getName(stack);
	}

	public static final int BASE = 10;

	public ItemPhial(Properties properties) {
		super(properties);
	}

	public static ItemStack makeFilledPhial(Aspect aspect) {
		ItemStack stack = new ItemStack(thaumcraft.api.items.ItemsTC.phial.get());
		stack.set(ThaumcraftDataComponents.ASPECTS.get(), new AspectList().add(aspect, BASE));
		return stack;
	}

	@Override
	public AspectList getAspects(ItemStack itemstack) {
		AspectList aspects = itemstack.get(ThaumcraftDataComponents.ASPECTS.get());
		return aspects != null && aspects.size() > 0 ? aspects : null;
	}

	@Override
	public void setAspects(ItemStack itemstack, AspectList aspects) {
		itemstack.set(ThaumcraftDataComponents.ASPECTS.get(), aspects);
	}

	@Override
	public boolean ignoreContainedAspects() {
		return false;
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.PASS;
		BlockPos pos = context.getClickedPos();
		BlockState bs = level.getBlockState(pos);
		if (bs.getBlock() != BlocksTC.jarNormal.get()) return InteractionResult.PASS;
		if (!(level.getBlockEntity(pos) instanceof TileJarFillable tile)) return InteractionResult.PASS;

		AspectList held = getAspects(stack);
		if (held == null) {
			// Empty phial: fill from the jar.
			AspectList jarContents = tile.getAspects();
			if (jarContents.size() != 1) return InteractionResult.PASS;
			Aspect aspect = jarContents.getAspects()[0];
			if (jarContents.getAmount(aspect) < BASE) return InteractionResult.PASS;
			if (level.isClientSide) return InteractionResult.SUCCESS;
			if (!tile.takeFromContainer(aspect, BASE)) return InteractionResult.PASS;

			ItemStack filled = new ItemStack(this);
			setAspects(filled, new AspectList().add(aspect, BASE));
			giveOrDrop(player, stack, filled);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.25f, 1.0f);
			return InteractionResult.CONSUME;
		} else if (held.size() == 1) {
			// Filled phial: empty into the jar.
			Aspect aspect = held.getAspects()[0];
			if (!tile.doesContainerAccept(aspect)) return InteractionResult.PASS;
			if (level.isClientSide) return InteractionResult.SUCCESS;
			if (tile.addToContainer(aspect, BASE) != 0) return InteractionResult.PASS;

			ItemStack empty = new ItemStack(this);
			giveOrDrop(player, stack, empty);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.25f, 1.0f);
			return InteractionResult.CONSUME;
		}
		return InteractionResult.PASS;
	}

	/** Shrinks the held stack by 1 and either stacks the result into the player's inventory or drops it -
	 * same "consume one, hand back the transformed item" shape as vanilla's own bottle-filling. */
	private static void giveOrDrop(Player player, ItemStack held, ItemStack result) {
		held.shrink(1);
		if (!player.getInventory().add(result)) {
			player.drop(result, false);
		}
	}
}
