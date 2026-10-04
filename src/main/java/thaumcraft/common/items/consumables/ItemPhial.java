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
 * As the original: an empty phial takes 10 essentia from an Alembic or a (normal or void) Warded Jar, a filled one
 * empties into a jar with room that accepts it. Uses NeoForge's {@code onItemUseFirst} (as the original did) so the
 * click happens before the block's own right-click.
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
		AspectList held = getAspects(stack);
		if (held == null && level.getBlockEntity(pos) instanceof thaumcraft.common.tiles.essentia.TileAlembic alembic) {
			AspectList in = alembic.getAspects();
			if (in.size() == 1 && in.getAmount(in.getAspects()[0]) >= BASE) {
				if (level.isClientSide) {
					player.swing(context.getHand());
					return InteractionResult.PASS;
				}
				Aspect aspect = in.getAspects()[0];
				if (alembic.takeFromContainer(aspect, BASE)) {
					ItemStack filled = new ItemStack(this);
					setAspects(filled, new AspectList().add(aspect, BASE));
					stack.shrink(1);
					if (!player.getInventory().add(filled)) player.drop(filled, false);
					level.playSound(null, player.blockPosition(), SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.25f, 1.0f);
					return InteractionResult.SUCCESS;
				}
			}
		}
		boolean jar = bs.getBlock() == BlocksTC.jarNormal.get() || bs.getBlock() == BlocksTC.jarVoid.get();
		if (!jar || !(level.getBlockEntity(pos) instanceof TileJarFillable tile)) return InteractionResult.PASS;

		if (held == null) {
			// Empty phial: fill from the jar.
			AspectList jarContents = tile.getAspects();
			if (jarContents.size() != 1) return InteractionResult.PASS;
			Aspect aspect = jarContents.getAspects()[0];
			if (jarContents.getAmount(aspect) < BASE) return InteractionResult.PASS;
			if (level.isClientSide) {
				player.swing(context.getHand());
				return InteractionResult.PASS;
			}
			if (!tile.takeFromContainer(aspect, BASE)) return InteractionResult.PASS;
			ItemStack filled = new ItemStack(this);
			setAspects(filled, new AspectList().add(aspect, BASE));
			giveOrDrop(player, stack, filled, pos);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.25f, 1.0f);
			return InteractionResult.SUCCESS;
		} else if (held.size() == 1) {
			// Filled phial: empty into the jar, if it has room.
			Aspect aspect = held.getAspects()[0];
			if (tile.getAspects().visSize() > 250 - BASE || !tile.doesContainerAccept(aspect)) return InteractionResult.PASS;
			if (level.isClientSide) {
				player.swing(context.getHand());
				return InteractionResult.PASS;
			}
			if (tile.addToContainer(aspect, BASE) != 0) return InteractionResult.PASS;
			giveOrDrop(player, stack, new ItemStack(this), pos);
			level.playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.PLAYERS, 0.25f, 1.0f);
			return InteractionResult.SUCCESS;
		}
		return InteractionResult.PASS;
	}

	/** Shrinks the held stack by 1 and either stacks the result into the player's inventory or drops it -
	 * same "consume one, hand back the transformed item" shape as vanilla's own bottle-filling. */
	private static void giveOrDrop(Player player, ItemStack held, ItemStack result, BlockPos jar) {
		held.shrink(1);
		if (!player.getInventory().add(result)) {
			// as the original: dropped at the jar
			player.level().addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(player.level(), jar.getX() + 0.5, jar.getY() + 0.5, jar.getZ() + 0.5, result));
		}
	}
}
