package thaumcraft.common.lib.research;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.PotionContents;
import thaumcraft.api.research.IScanThing;
import thaumcraft.api.research.ScanningManager;

/**
 * Replaces the old (1.12.2) {@code ScanPotion} - scanning a real living entity that currently has this
 * effect on it, or a real potion item that would apply it, counts as discovering that effect. One real
 * instance is registered per real {@link MobEffect} (see {@code Thaumcraft#commonSetup}), exactly like the
 * original: mob effects are still a real code-registered {@link BuiltInRegistries#MOB_EFFECT} registry in
 * modern Minecraft, so unlike its {@link ScanEnchantment} sibling this one can still be enumerated at
 * setup and keeps the original's own "one scan discovers every effect present at once" behaviour.
 *
 * <p>The item branch is reachable now that {@link ScanningManager}'s own real inventory recursion and
 * {@link ScanningManager#getItemFromParms} are ported. Research keys are the effect's own real namespaced
 * id ({@code !minecraft:speed}) rather than the original's own translation key ({@code !effect.moveSpeed}).
 */
public class ScanPotion implements IScanThing {

	private final Holder<MobEffect> effect;

	public ScanPotion(Holder<MobEffect> effect) {
		this.effect = effect;
	}

	@Override
	public boolean checkThing(Player player, Object obj) {
		return hasEffect(player, obj);
	}

	@Override
	public String getResearchKey(Player player, Object obj) {
		return effect.unwrapKey().map(key -> "!" + key.location()).orElse(null);
	}

	private boolean hasEffect(Player player, Object obj) {
		if (obj == null) return false;
		if (obj instanceof LivingEntity living) {
			for (MobEffectInstance active : living.getActiveEffects()) {
				if (active.getEffect() == effect) return true;
			}
			return false;
		}
		ItemStack stack = ScanningManager.getItemFromParms(player, obj);
		if (stack.isEmpty()) return false;
		PotionContents contents = stack.get(DataComponents.POTION_CONTENTS);
		if (contents == null) return false;
		for (MobEffectInstance held : contents.getAllEffects()) {
			if (held.getEffect() == effect) return true;
		}
		return false;
	}
}
