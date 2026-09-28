package thaumcraft.common.lib.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.common.items.curios.ItemThaumonomicon;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/**
 * Replaces the "gameplay experience teaches you something" fragment of the old (1.12.2) {@code EntityEvents}'
 * giant {@code entityHurt} handler (see {@code ChampionModEvents}/{@code FortressMaskEvents}' own doc
 * comments for the same "extract the one real mechanic, not the whole stale file" precedent this follows).
 *
 * Real, load-bearing gap this closes: {@code data/thaumcraft/research/auromancy.json} already gates real
 * research nodes behind these exact "f_onfire"/"f_arrow"/"f_fireball"/"f_spit" prerequisite flags - without
 * this hook granting them, those nodes could never be unlocked by any current gameplay path. Matches the
 * original exactly: getting burned while knowing "BASEAUROMANCY@2" grants "f_onfire"; getting hit by an
 * arrow/fireball/llama spit while knowing "FOCUSPROJECTILE@2" grants the matching "f_arrow"/"f_fireball"/
 * "f_spit" - each a one-time flag (checked via {@link IPlayerKnowledge#isResearchKnown}), synced to the
 * client, with the original's own real status message.
 *
 * Real, documented cut: no direct-fire-damage vs. real "isFireDamage()" distinction beyond the tag check
 * itself - {@code DamageTypeTags.IS_FIRE} is the real modern equivalent of the original's
 * {@code DamageSource#isFireDamage()}.
 *
 * Also covers 2 of the old {@code PlayerEvents#pickupItem}'s own real "gameplay experience" flags for the
 * same reason - picking up a real {@link ItemCrystalEssence} grants "!gotcrystals" (with the original's own
 * real status message), picking up a real {@link ItemThaumonomicon} grants "!gotthaumonomicon" (no message
 * in the original either). Real, documented cut: the original's own "!gotcrystals" branch also gave the
 * player a Dream Journal book (via the wake-up-triggered {@code giveDreamJournal}, itself gated on
 * {@code ModConfig.CONFIG_MISC.noSleep}) - dropped here, {@code ConfigItems.startBook} isn't ported (part of
 * the still-open config-system gap tracked elsewhere in this session's memory), and the flag itself is the
 * part with real, independent value (nothing currently requires the Dream Journal item specifically).
 *
 * Also covers the stale {@code ConfigResearch#checkPeriodicStuff} - 8 more real "gameplay experience teaches
 * you something" flags (checked every 200 real ticks, same real cadence the original used), same real
 * pattern as everything else in this class: {@code data/thaumcraft/research/*.json} already gates real nodes
 * behind all 8 of these exact keys. {@link #onTick} real-ifies "visited the Nether"/"visited the End"
 * (checked directly against the real dimension key, the same simplification {@code ChampionModEvents} already
 * made for the same original {@code BiomeDictionary.Type.NETHER}/{@code END} check - no modern biome-tag
 * equivalent is any more precise for an entire-dimension condition like this), "gone below Y=10"/"climbed
 * above 40% world height" (both real, gated the same real way the original did - only once
 * {@code UNLOCKAUROMANCY} stage 1 is known but stage 2 isn't), and 4 silent (no message, same as the
 * original) vanilla-stat thresholds (walked/sprinted/jumped/swum enough) via real {@link Stats}.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class PassiveResearchEvents {

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		DamageSource source = event.getSource();

		if (source.is(DamageTypeTags.IS_FIRE) && ThaumcraftCapabilities.knowsResearchStrict(player, "BASEAUROMANCY@2")) {
			grant(player, "f_onfire", "got.onfire");
		}

		Entity direct = source.getDirectEntity();
		if (direct != null && ThaumcraftCapabilities.knowsResearchStrict(player, "FOCUSPROJECTILE@2")) {
			if (direct instanceof AbstractArrow) {
				grant(player, "f_arrow", "got.projectile");
			} else if (direct instanceof Fireball) {
				grant(player, "f_fireball", "got.projectile");
			} else if (direct instanceof LlamaSpit) {
				grant(player, "f_spit", "got.projectile");
			}
		}
	}

	@SubscribeEvent
	public static void onItemPickup(ItemEntityPickupEvent.Post event) {
		if (!(event.getPlayer() instanceof ServerPlayer player)) return;

		if (event.getOriginalStack().getItem() instanceof ItemCrystalEssence
				&& !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!gotcrystals")) {
			grant(player, "!gotcrystals", null);
			player.sendSystemMessage(Component.translatable("got.crystals").withStyle(ChatFormatting.DARK_PURPLE));
			if (thaumcraft.common.config.ThaumcraftConfig.NO_SLEEP.get() && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!gotdream")) {
				giveDreamJournal(player);
			}
		}
		if (event.getOriginalStack().getItem() instanceof ItemThaumonomicon) {
			grant(player, "!gotthaumonomicon", null);
		}
	}

	@SubscribeEvent
	public static void onTick(PlayerTickEvent.Post event) {
		Player playerEntity = event.getEntity();
		if (playerEntity.level().isClientSide || playerEntity.tickCount % 200 != 0 || !(playerEntity instanceof ServerPlayer player)) return;

		Level level = player.level();
		if (level.dimension() == Level.NETHER) {
			grant(player, "m_hellandback", "got.hellandback");
		}
		if (level.dimension() == Level.END) {
			grant(player, "m_endoftheworld", "got.endoftheworld");
		}

		if (ThaumcraftCapabilities.knowsResearchStrict(player, "UNLOCKAUROMANCY@1") && !ThaumcraftCapabilities.knowsResearchStrict(player, "UNLOCKAUROMANCY@2")) {
			if (player.getY() < 10.0 && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("m_deepdown")) {
				grant(player, "m_deepdown", "got.deepdown");
			}
			if (player.getY() > level.getHeight() * 0.4 && !ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("m_uphigh")) {
				grant(player, "m_uphigh", "got.uphigh");
			}
		}

		if (statAtLeast(player, Stats.WALK_ONE_CM, 160000)) grant(player, "m_walker", null);
		if (statAtLeast(player, Stats.SPRINT_ONE_CM, 80000)) grant(player, "m_runner", null);
		if (statAtLeast(player, Stats.JUMP, 500)) grant(player, "m_jumper", null);
		if (statAtLeast(player, Stats.SWIM_ONE_CM, 8000)) grant(player, "m_swimmer", null);
	}

	private static boolean statAtLeast(ServerPlayer player, net.minecraft.resources.ResourceLocation stat, int threshold) {
		return player.getStats().getValue(Stats.CUSTOM.get(stat)) > threshold;
	}

	/** The original's {@code PlayerEvents#wakeUp}: the first sleep after touching an essence crystal brings the dream -
	 * the {@code !gotdream} flag (which lets Salis Mundus turn a bookshelf into the Thaumonomicon) and "Strange Dreams",
	 * a written journal describing how to make Salis Mundus and what to do with it. */
	@SubscribeEvent
	public static void onWakeUp(net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent event) {
		if (!(event.getEntity() instanceof ServerPlayer player)) return;
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (knowledge.isResearchKnown("!gotcrystals") && !knowledge.isResearchKnown("!gotdream")) giveDreamJournal(player);
	}

	public static void giveDreamJournal(ServerPlayer player) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		knowledge.addResearch("!gotdream");
		knowledge.sync(player);
		net.minecraft.world.item.ItemStack book = dreamJournal(player.getGameProfile().getName());
		if (!player.getInventory().add(book)) player.drop(book, false);
		player.sendSystemMessage(Component.translatable("got.dream").withStyle(ChatFormatting.DARK_PURPLE));
	}

	/** ConfigItems.startBook: a written book, generation 3 ("tattered"), titled "Strange Dreams", three pages. */
	public static net.minecraft.world.item.ItemStack dreamJournal(String author) {
		net.minecraft.world.item.ItemStack book = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITTEN_BOOK);
		java.util.List<net.minecraft.server.network.Filterable<Component>> pages = new java.util.ArrayList<>();
		for (int i = 1; i <= 3; i++) {
			// the lang strings are the original's JSON-text pages: quoted, with escaped newlines
			String page = Component.translatable("book.start." + i).getString();
			if (page.length() >= 2 && page.startsWith("\"") && page.endsWith("\"")) page = page.substring(1, page.length() - 1);
			pages.add(net.minecraft.server.network.Filterable.passThrough(Component.literal(page.replace("\\n", "\n"))));
		}
		book.set(net.minecraft.core.component.DataComponents.WRITTEN_BOOK_CONTENT, new net.minecraft.world.item.component.WrittenBookContent(
				net.minecraft.server.network.Filterable.passThrough(Component.translatable("book.start.title").getString()), author, 3, pages, true));
		return book;
	}

	private static void grant(ServerPlayer player, String researchKey, String messageKey) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (knowledge.isResearchKnown(researchKey)) return;
		knowledge.addResearch(researchKey);
		knowledge.sync(player);
		if (messageKey != null) {
			player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_PURPLE), true);
		}
	}
}
