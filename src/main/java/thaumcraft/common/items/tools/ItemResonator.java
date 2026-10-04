package thaumcraft.common.items.tools;

import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.IAspectContainer;
import thaumcraft.api.aspects.IEssentiaTransport;
import thaumcraft.common.tiles.devices.TileCondenser;
import thaumcraft.common.tiles.essentia.TileTubeBuffer;

/**
 * Replaces the old (1.12.2) {@code ItemResonator}. A pure diagnostic tool - right-click any block whose
 * {@code BlockEntity} implements {@link IEssentiaTransport} (a Tube, Tube Filter/Restrict/Buffer, Warded Jar,
 * or the Flux Condenser - all already ported) to report its essentia contents and suction demand on the
 * clicked face to chat, same as the original.
 *
 * Real {@link TileTubeBuffer} branch, restored 2026-09-15 now that block is real: a plain
 * {@link IEssentiaTransport#getEssentiaType}/{@code #getEssentiaAmount} query can only ever report ONE
 * aspect, which would silently hide the rest of a real multi-aspect buffer's own held essentia - matches the
 * true original's own real special-case instead, listing every real aspect the buffer actually holds (via
 * {@link IAspectContainer#getAspects()}), sorted by name, each as its own separate chat line.
 *
 * Simplified for this pass:
 * <ul>
 * <li>No CodeChicken raytrace face refinement ({@code RayTracer.retraceBlock}/{@code subHit}) - uses
 * {@link UseOnContext#getClickedFace()} directly, which is exactly what the original itself already fell
 * back to whenever that raytrace came back null, so this is a lossless simplification, not a cut feature.</li>
 * <li>The shield-block chime, as the original.</li>
 * </ul>
 */
public class ItemResonator extends Item {

	public ItemResonator(Properties properties) {
		super(properties.stacksTo(1));
	}

	@Override
	public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
		Level level = context.getLevel();
		Player player = context.getPlayer();
		if (player == null) return InteractionResult.PASS;
		if (!(level.getBlockEntity(context.getClickedPos()) instanceof IEssentiaTransport transport)) {
			return InteractionResult.FAIL;
		}
		if (level.isClientSide) {
			player.swing(context.getHand());
			return InteractionResult.SUCCESS;
		}

		Direction face = context.getClickedFace();
		if (level.getBlockEntity(context.getClickedPos()) instanceof TileTubeBuffer buffer) {
			for (Aspect aspect : buffer.getAspects().getAspectsSortedByName()) {
				player.sendSystemMessage(Component.translatable("tc.resonator1", buffer.getAspects().getAmount(aspect), aspect.getName()));
			}
		} else {
			Aspect essentiaType = transport.getEssentiaType(face);
			if (essentiaType != null) {
				player.sendSystemMessage(Component.translatable("tc.resonator1", transport.getEssentiaAmount(face), essentiaType.getName()));
			}
		}

		Aspect suctionType = transport.getSuctionType(face);
		String suctionLabel = suctionType != null ? suctionType.getName() : Component.translatable("tc.resonator3").getString();
		player.sendSystemMessage(Component.translatable("tc.resonator2", transport.getSuctionAmount(face), suctionLabel));

		level.playSound(null, context.getClickedPos(), SoundEvents.SHIELD_BLOCK, SoundSource.BLOCKS, 0.5f, 1.9f + level.random.nextFloat() * 0.1f);

		if (level.getBlockEntity(context.getClickedPos()) instanceof TileCondenser condenser) {
			player.sendSystemMessage(Component.translatable("tc.condenser1", condenser.getCost()));
			player.sendSystemMessage(Component.translatable("tc.condenser2", condenser.getInterval(), condenser.getInterval() / 20));
		}
		return InteractionResult.SUCCESS;
	}
}
