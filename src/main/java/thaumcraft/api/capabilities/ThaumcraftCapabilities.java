package thaumcraft.api.capabilities;
import javax.annotation.Nonnull;

import java.util.function.Supplier;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import thaumcraft.common.lib.research.PlayerKnowledge;
import thaumcraft.common.lib.research.PlayerWarp;



/**
 * Replaces the old (1.12.2) {@code ThaumcraftCapabilities}, which used {@code @CapabilityInject}/
 * {@code Capability<T>} to attach per-player data. That's exactly what NeoForge's Data Attachments
 * replaced for this use case (arbitrary persisted per-entity data, as opposed to the newer Capability
 * system's actual current purpose: pluggable *behavioural* interfaces like item handlers).
 *
 * Unlike {@code thaumcraft.api.aura.AuraHelper} (which stays a stub because it was always meant to be a
 * thin pass-through to {@code ThaumcraftApi.internalMethods}, an indirection layer purely so the API
 * package wouldn't need to reference {@code thaumcraft.common}), this class is the actual canonical
 * definition site for player knowledge/warp - Data Attachments fuse "define the attachment" and "supply
 * its default value" into one registration call, so *something* has to reference the concrete
 * {@link PlayerKnowledge}/{@link PlayerWarp} classes. Putting that here, next to the accessor methods
 * addon code is meant to call, is the least contorted place for it - the original design didn't make
 * these addon-swappable either.
 *
 * @author Azanor
 *
 */
public class ThaumcraftCapabilities {

	public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "thaumcraft");

	private static final Supplier<IPlayerKnowledge> DEFAULT_KNOWLEDGE = PlayerKnowledge::new;
	private static final Supplier<IPlayerWarp> DEFAULT_WARP = PlayerWarp::new;

	//PLAYER RESEARCH/////////////////////////////////////////

	/**
	 * The attachment type for {@link IPlayerKnowledge}. Persists across death (research shouldn't be lost
	 * on respawn).
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<IPlayerKnowledge>> KNOWLEDGE = ATTACHMENT_TYPES.register("knowledge",
			() -> AttachmentType.serializable(DEFAULT_KNOWLEDGE).copyOnDeath().build());

	/**
	 * Retrieves the knowledge capability handler for the supplied player
	 */
	public static IPlayerKnowledge getKnowledge(@Nonnull Player player)
	{
		return player.getData(KNOWLEDGE.get());
	}

	/**
	 * Shortcut method to check if player knows the passed research entries. All must be true
	 * Research does not need to be complete, just 'in progress'
	 * Individual entries can also contain && to do a 'and' check, e.g. "basicgolemancy&&infusion"
	 * Handy for recipes where multiple researches need to be true to be craftable
	 * Individual entries can also contain || to do an 'or' check, e.g. "basicgolemancy||infusion"
	 * Queries should NOT contain both && and || - shennanigans will occur.
	 * @param player
	 * @param research
	 * @return
	 */
	public static boolean knowsResearch(@Nonnull Player player, @Nonnull String... research) {
		for (String r : research) {
			if (r.contains("&&")) {
				String[] rr = r.split("&&");
				if (!knowsResearch(player,rr)) return false;
			} else
			if (r.contains("||")) {
				String[] rr = r.split("\\|\\|");
				boolean any = false;
				for (String str : rr)
					if (knowsResearch(player,str)) { any = true; break; }
				if (!any) return false;
			} else
			if (!getKnowledge(player).isResearchKnown(r)) return false;
		}
		return true;
	}

	/**
	 * Shortcut method to check if player knows all the passed research entries.
	 * Research needs to be complete and 'in progress' research will only count if a stage is passed in the research paramater (using @, eg. "FOCUSFIRE@2")
	 * Individual entries can also contain && to do a 'and' check, e.g. "basicgolemancy&&infusion"
	 * Handy for recipes where multiple researches need to be true to be craftable
	 * Individual entries can also contain || to do an 'or' check, e.g. "basicgolemancy||infusion"
	 * Queries should NOT contain both && and || - shennanigans will occur.
	 * @param player
	 * @param research
	 * @return
	 */
	public static boolean knowsResearchStrict(@Nonnull Player player, @Nonnull String... research) {
		for (String r : research) {
			if (r.contains("&&")) {
				String[] rr = r.split("&&");
				if (!knowsResearchStrict(player,rr)) return false;
			} else
			if (r.contains("||")) {
				String[] rr = r.split("\\|\\|");
				boolean any = false;
				for (String str : rr)
					if (knowsResearchStrict(player,str)) { any = true; break; }
				if (!any) return false;
			} else
			if (r.contains("@")) {
				if (!getKnowledge(player).isResearchKnown(r)) return false;
			} else {
				if (!getKnowledge(player).isResearchComplete(r)) return false;
			}
		}
		return true;
	}


	//PLAYER WARP/////////////////////////////////////////

	/**
	 * The attachment type for {@link IPlayerWarp}. Persists across death.
	 */
	public static final DeferredHolder<AttachmentType<?>, AttachmentType<IPlayerWarp>> WARP = ATTACHMENT_TYPES.register("warp",
			() -> AttachmentType.serializable(DEFAULT_WARP).copyOnDeath().build());

	/**
	 * Retrieves the warp capability handler for the supplied player
	 */
	public static IPlayerWarp getWarp(@Nonnull Player player)
	{
		return player.getData(WARP.get());
	}




}
