package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;

/**
 * Replaces the old (1.12.2) {@code FocusEffectBreak}. The original queued the block for gradual, over-time
 * breaking via {@code ServerEvents.addBreaker} - a whole scheduler subsystem of its own that isn't ported.
 * This is an instant break instead, but still does real fortune/silk-touch handling: builds a throwaway
 * enchanted "tool" stack and hands it to {@code Block#dropResources}, so the block's actual loot table
 * (ore doubling, silk-touch variants, etc.) is honoured properly rather than reimplemented by hand.
 *
 * A block only breaks if its hardness is within reach of the focus's power setting (unbreakable blocks -
 * hardness &lt; 0, like bedrock - are always rejected); no FX/network packet, matching the other foci ported
 * so far.
 */
public class FocusEffectBreak extends FocusEffect {

	@Override
	public String getResearch() {
		return "FOCUSBREAK";
	}

	@Override
	public String getKey() {
		return "thaumcraft.BREAK";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.ENTROPY;
	}

	@Override
	public int getComplexity() {
		return getSettingValue("power") * 3 + getSettingValue("silk") * 4 + (getSettingValue("fortune") == 0 ? 0 : (getSettingValue("fortune") + 1) * 3);
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		if (!(target instanceof BlockHitResult blockHit)) return false;

		Level level = getPackage().world;
		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0) return false;

		float power = getSettingValue("power") * finalPower;
		if (hardness > power * 2.0f) return false;

		boolean silk = getSettingValue("silk") > 0;
		int fortune = getSettingValue("fortune");

		ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
		if (silk) {
			tool.enchant(enchant(level, Enchantments.SILK_TOUCH), 1);
		} else if (fortune > 0) {
			tool.enchant(enchant(level, Enchantments.FORTUNE), fortune);
		}

		Entity caster = getPackage().getCaster();
		BlockEntity blockEntity = level.getBlockEntity(pos);
		Block.dropResources(state, level, pos, blockEntity, caster instanceof LivingEntity living ? living : null, tool);
		level.removeBlock(pos, false);
		level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.6f, 1.0f);
		return true;
	}

	private static Holder<Enchantment> enchant(Level level, net.minecraft.resources.ResourceKey<Enchantment> key) {
		return level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(key);
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] silk = { 0, 1 };
		String[] silkDesc = { "focus.common.no", "focus.common.yes" };
		int[] fortune = { 0, 1, 2, 3, 4 };
		String[] fortuneDesc = { "focus.common.no", "I", "II", "III", "IV" };
		return new NodeSetting[] {
				new NodeSetting("power", "focus.break.power", new NodeSetting.NodeSettingIntRange(1, 5)),
				new NodeSetting("fortune", "focus.common.fortune", new NodeSetting.NodeSettingIntList(fortune, fortuneDesc)),
				new NodeSetting("silk", "focus.common.silk", new NodeSetting.NodeSettingIntList(silk, silkDesc))
		};
	}

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher)
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.END_GATEWAY_SPAWN, SoundSource.PLAYERS, 0.1f,
				2.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}
