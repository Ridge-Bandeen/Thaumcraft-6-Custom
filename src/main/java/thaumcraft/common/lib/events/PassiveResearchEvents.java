package thaumcraft.common.lib.events;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;

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

	private static void grant(ServerPlayer player, String researchKey, String messageKey) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (knowledge.isResearchKnown(researchKey)) return;
		knowledge.addResearch(researchKey);
		knowledge.sync(player);
		player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_PURPLE), true);
	}
}
