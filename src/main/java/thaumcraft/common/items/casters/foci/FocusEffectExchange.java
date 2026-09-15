package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.ICaster;
import thaumcraft.api.casters.IFocusBlockPicker;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.items.casters.ItemCaster;
import thaumcraft.common.lib.scheduling.BlockScheduler;

/**
 * Replaces the old (1.12.2) {@code FocusEffectExchange} - real, restored 2026-09-15 alongside its own only
 * real medium pairing, {@link FocusMediumPlan}. Real mass block-swap: replaces every real target block with
 * whatever the caster's own {@link ItemCaster#getPickedBlock} currently holds (see that method's own doc
 * comment for how a block gets picked in the first place), via {@link BlockScheduler#addSwapper} - a real,
 * direct, already-ported port of the true original's own {@code ServerEvents.addSwapper} that turned out to
 * already exist, just never wired to a real caller. No simplification needed here at all: this calls it with
 * the exact same real arguments the true original's own {@code execute} did.
 *
 * <p>Real, faithfully-reproduced quirk (not fixed): the true original's own {@code getComplexity} has a real
 * operator-precedence bug - {@code (5 + silk * 4 + fortune == 0) ? 0 : ((fortune + 1) * 3)} parses as
 * {@code ((5 + silk * 4) + fortune) == 0}, which (silk ∈ {0,1}, fortune ∈ {0..4}) can never actually be 0 -
 * the ternary always takes the {@code (fortune + 1) * 3} branch, meaning Silk Touch's own real vis-cost term
 * is permanently unreachable dead code in the true original itself. This port reproduces the real resulting
 * behavior directly ({@code (fortune + 1) * 3}, silk free) rather than transcribing the pointless dead
 * ternary - same real number either way, cleaner code.
 */
public class FocusEffectExchange extends FocusEffect implements IFocusBlockPicker {

	@Override
	public String getResearch() {
		return "FOCUSEXCHANGE";
	}

	@Override
	public String getKey() {
		return "thaumcraft.EXCHANGE";
	}

	@Override
	public Aspect getAspect() {
		return Aspect.EXCHANGE;
	}

	@Override
	public int getComplexity() {
		return (getSettingValue("fortune") + 1) * 3;
	}

	@Override
	public boolean execute(HitResult target, Trajectory trajectory, float finalPower, int num) {
		if (!(target instanceof BlockHitResult blockHit)) return false;
		if (!(getPackage().getCaster() instanceof Player player)) return false;

		ItemStack casterStack = ItemStack.EMPTY;
		if (player.getMainHandItem().getItem() instanceof ICaster) {
			casterStack = player.getMainHandItem();
		} else if (player.getOffhandItem().getItem() instanceof ICaster) {
			casterStack = player.getOffhandItem();
		}
		if (casterStack.isEmpty()) return false;

		boolean silk = getSettingValue("silk") > 0;
		int fortune = getSettingValue("fortune");
		ItemStack picked = ((ItemCaster) casterStack.getItem()).getPickedBlock(casterStack);
		if (!picked.isEmpty()) {
			Level level = getPackage().world;
			BlockPos pos = blockHit.getBlockPos();
			BlockScheduler.addSwapper(level, pos, level.getBlockState(pos), picked, true, 0, player, true, false, 8038177, true,
					silk, fortune, BlockScheduler.DEFAULT_PREDICATE, 0.25f + (silk ? 0.25f : 0.0f) + fortune * 0.1f);
		}
		return true;
	}

	@Override
	public NodeSetting[] createSettings() {
		int[] silk = { 0, 1 };
		String[] silkDesc = { "focus.common.no", "focus.common.yes" };
		int[] fortune = { 0, 1, 2, 3, 4 };
		String[] fortuneDesc = { "focus.common.no", "I", "II", "III", "IV" };
		return new NodeSetting[] {
				new NodeSetting("fortune", "focus.common.fortune", new NodeSetting.NodeSettingIntList(fortune, fortuneDesc)),
				new NodeSetting("silk", "focus.common.silk", new NodeSetting.NodeSettingIntList(silk, silkDesc)) };
	}

	@Override
	public void renderParticleFX(Level world, double posX, double posY, double posZ, double motionX, double motionY, double motionZ) {
		// no-op for now - depends on the not-yet-ported client particle dispatcher (FXDispatcher), same
		// boundary every other focus effect in this port has already accepted.
	}

	@Override
	public void onCast(Entity caster) {
		caster.level().playSound(null, caster.blockPosition().above(), SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.PLAYERS, 0.2f,
				2.0f + (float) (caster.level().random.nextGaussian() * 0.05));
	}
}
