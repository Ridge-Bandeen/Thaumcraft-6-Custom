package thaumcraft.common.lib.events;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.damagesource.ThaumcraftDamageTypes;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.common.entities.monster.EntityBrainyZombie;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.items.resources.ItemCrystalEssence;

/**
 * Replaces 2 of the small, real, independent death-drop fragments buried inside the old (1.12.2)
 * {@code EntityEvents}' giant {@code livingDrops} handler - same "extract the one real mechanic, not the
 * whole stale file" precedent {@code ChampionModEvents}/{@code FortressMaskEvents}/
 * {@code PassiveResearchEvents} already established for this same source file.
 *
 * A regular (non-Brainy) {@link Zombie} kill has the original's {@code rand.nextInt(10) - lootingLevel < 1} chance
 * (Looting read by {@link thaumcraft.common.lib.utils.LootingHelper}) to drop a {@link ItemsTC#brain}. A real
 * {@link EntityCultist} kill by a real player has a real chance to drop a real {@link ItemsTC#curio} -
 * 1-in-4 normally, 1-in-20 once the killer already knows the real {@code !CrimsonCultist} scan (matches the
 * original's own two-tier rate, adapted to that entry's real single-stage shape in this port's data - see
 * below).
 *
 * The drop is the Crimson Rites curiosity, and - as in the original - carrying one already makes another 1-in-50.
 * One adaptation: the original tested {@code "!CrimsonCultist@2"}, but that scan key has a single stage in this
 * port's data, so the check is plain "known".
 *
 * A third real fragment from the same original handler: a kill by {@link ThaumcraftDamageTypes#DISSOLVE}
 * (the Primal Crusher's own real disintegration effect) drops the victim's real {@link AspectHelper}-derived
 * essentia as {@link ItemCrystalEssence} stacks instead of its normal loot, same real
 * {@code getInt(1, 1+visSize/10)} roll-count formula the original used, ported onto {@link Mth#nextInt}
 * (the modern {@link RandomSource}-based equivalent of the original's own {@code MathHelper.getInt}).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public class MobDeathDropEvents {

	@SubscribeEvent
	public static void onLivingDrops(LivingDropsEvent event) {
		if (!event.isRecentlyHit() || event.getEntity().level().isClientSide()) return;
		LivingEntity entity = event.getEntity();

		if (entity instanceof Zombie && !(entity instanceof EntityBrainyZombie)
				&& entity.getRandom().nextInt(10) - thaumcraft.common.lib.utils.LootingHelper.level(event.getSource()) < 1) {
			drop(event, new ItemStack(ItemsTC.brain.get()));
		}

		if (entity instanceof EntityCultist && event.getSource().getEntity() instanceof Player player) {
			int chance = ThaumcraftCapabilities.knowsResearch(player, "!CrimsonCultist") ? 20 : 4;
			// the original's dampener: already carrying a copy of the Rites makes another much rarer
			if (player.getInventory().items.stream().anyMatch(s -> s.is(ItemsTC.curioRites.get()))) chance = 50;
			if (player.getRandom().nextInt(chance) == 0) {
				drop(event, new ItemStack(ItemsTC.curioRites.get()));
			}
		}

		if (event.getSource().is(ThaumcraftDamageTypes.DISSOLVE)) {
			AspectList aspects = AspectHelper.getEntityAspects(entity);
			if (aspects != null && aspects.size() > 0) {
				Aspect[] all = aspects.getAspects();
				RandomSource random = entity.getRandom();
				int rolls = Mth.nextInt(random, 1, 1 + aspects.visSize() / 10);
				for (int i = 0; i < rolls; i++) {
					Aspect aspect = all[random.nextInt(all.length)];
					drop(event, ItemCrystalEssence.of(aspect));
				}
			}
		}
	}

	private static void drop(LivingDropsEvent event, ItemStack stack) {
		LivingEntity entity = event.getEntity();
		event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY() + entity.getEyeHeight(), entity.getZ(), stack));
	}
}
