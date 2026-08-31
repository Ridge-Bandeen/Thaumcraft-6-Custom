package thaumcraft.api.aura;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import thaumcraft.api.ThaumcraftApi;


/**
 * Delegates to {@code ThaumcraftApi.internalMethods} (see that class's own doc comment for why this
 * indirection exists: it keeps this API package from needing to reference {@code thaumcraft.common}
 * directly). Was a permanent stub for most of this port - {@code ThaumcraftApi.internalMethods} defaulted
 * to a no-op {@code DummyInternalMethodHandler} until {@code Thaumcraft}'s constructor installed the real
 * {@code ThaumcraftInternalMethodHandler}, which wires these straight through to the already-real
 * {@link thaumcraft.common.world.aura.AuraHandler}. Common-package consumers that were already bypassing
 * this class to call {@code AuraHandler} directly (e.g. {@code ItemCaster}, {@code TileArcaneWorkbench})
 * don't need to change - both paths reach the same place now.
 */
public class AuraHelper {

	/**
	 * Consume vis from the aura at the given location
	 * @param world
	 * @param pos
	 * @param amount
	 * @param simulate
	 * @return how much was actually drained
	 */
	public static float drainVis(Level world, BlockPos pos, float amount, boolean simulate) {
		return ThaumcraftApi.internalMethods.drainVis(world, pos, amount, simulate);
	}

	/**
	 * Consume flux from the aura at the given location
	 * Added for completeness, but should really not be used. Add instability instead.
	 * @param world
	 * @param pos
	 * @param amount
	 * @param simulate
	 * @return how much was actually drained
	 */
	public static float drainFlux(Level world, BlockPos pos, float amount, boolean simulate) {
		return ThaumcraftApi.internalMethods.drainFlux(world, pos, amount, simulate);
	}

	/**
	 * Adds vis to the aura at the given location.
	 *
	 * @param world
	 * @param pos
	 * @param amount
	 */
	public static void addVis(Level world, BlockPos pos, float amount) {
		ThaumcraftApi.internalMethods.addVis(world, pos, amount);
	}

	/**
	 * Get how much vis is in the aura at the given location.
	 * @param world
	 * @param pos
	 * @return
	 */
	public static float getVis(Level world, BlockPos pos) {
		return ThaumcraftApi.internalMethods.getVis(world, pos);
	}

	/**
	 * Adds flux to the aura at the specified block position.
	 * @param world
	 * @param pos
	 * @param amount how much stability to remove
	 * @param showEffect if set to true, a flux smoke effect and sound will also be displayed. Use in moderation.
	 */
	public static void polluteAura(Level world, BlockPos pos, float amount, boolean showEffect) {
		ThaumcraftApi.internalMethods.addFlux(world, pos, amount, showEffect);
	}

	/**
	 * Get how much flux is in the aura at the given location.
	 * @param world
	 * @param pos
	 * @return
	 */
	public static float getFlux(Level world, BlockPos pos) {
		return ThaumcraftApi.internalMethods.getFlux(world, pos);
	}

	/**
	 * Gets the general aura baseline at the given location
	 * @param world
	 * @param pos
	 * @return
	 */
	public static int getAuraBase(Level world, BlockPos pos) {
		return ThaumcraftApi.internalMethods.getAuraBase(world, pos);
	}

	/**
	 * Gets if the local aura for the given aspect is below 10% and that the player has the node preserver research.
	 * If the passed in player is null it will ignore the need for the research to be completed and just assume it is.
	 * @param world
	 * @param player
	 * @param pos
	 * @return
	 */
	public static boolean shouldPreserveAura(Level world, Player player, BlockPos pos) {
		return ThaumcraftApi.internalMethods.shouldPreserveAura(world, player, pos);
	}
}
