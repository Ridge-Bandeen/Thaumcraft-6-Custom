package thaumcraft.common.lib.events;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.EntityBrainyZombie;
import thaumcraft.common.entities.monster.cult.EntityCultist;

/**
 * Replaces 2 of the small, real, independent death-drop fragments buried inside the old (1.12.2)
 * {@code EntityEvents}' giant {@code livingDrops} handler - same "extract the one real mechanic, not the
 * whole stale file" precedent {@code ChampionModEvents}/{@code FortressMaskEvents}/
 * {@code PassiveResearchEvents} already established for this same source file.
 *
 * A regular (non-Brainy) {@link Zombie} kill has a real 1-in-10 chance (the original's own
 * {@code rand.nextInt(10) - lootingLevel < 1} formula, minus the Looting-level bonus - modern
 * {@link LivingDropsEvent} doesn't expose a looting level the way the 1.12.2 event did, and this port has no
 * other real "resolve the killer's Looting enchant level" helper to reach for one; a real, minor, disclosed
 * simplification) to drop a real {@link ItemsTC#brain}. A real
 * {@link EntityCultist} kill by a real player has a real chance to drop a real {@link ItemsTC#curio} -
 * 1-in-4 normally, 1-in-20 once the killer already knows the real {@code !CrimsonCultist} scan (matches the
 * original's own two-tier rate, adapted to that entry's real single-stage shape in this port's data - see
 * below).
 *
 * Real, documented adaptations from the original: the original checked {@code "!CrimsonCultist@2"} (stage 2)
 * and used a metadata-6 curio variant ("Crimson Rites") for this specific drop; this port's real
 * {@code !CrimsonCultist} scan entry only has 1 stage (see {@code data/thaumcraft/research/scans.json}), so
 * the check here is the real unqualified {@code knowsResearch} (any progress at all) instead of a stage-2
 * check that could never be satisfied, and the drop is a plain {@link ItemsTC#curio} - {@code ItemCurio}'s
 * own doc comment already explains why the metadata-variant system (and its Crimson-Rites-specific branch)
 * isn't ported. The original's extra "already carrying one, make it rarer still" dampener
 * ({@code InventoryUtils.getPlayerSlotFor}) is dropped - that whole utility class isn't ported, and without
 * the metadata-6 variant to specifically check for, there's nothing left to key the dampener off.
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class MobDeathDropEvents {

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!event.isRecentlyHit() || event.getEntity().level().isClientSide()) return;
		LivingEntity entity = event.getEntity();

		if (entity instanceof Zombie && !(entity instanceof EntityBrainyZombie) && entity.getRandom().nextInt(10) < 1) {
			drop(event, new ItemStack(ItemsTC.brain.get()));
		}

		if (entity instanceof EntityCultist && event.getSource().getEntity() instanceof Player player) {
			int chance = ThaumcraftCapabilities.knowsResearch(player, "!CrimsonCultist") ? 20 : 4;
			if (player.getRandom().nextInt(chance) == 0) {
				drop(event, new ItemStack(ItemsTC.curio.get()));
			}
		}
	}

	private static void drop(LivingDropsEvent event, ItemStack stack) {
		LivingEntity entity = event.getEntity();
		event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ(), stack));
	}
}
