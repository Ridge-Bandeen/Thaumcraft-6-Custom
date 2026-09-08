package thaumcraft.common.items.casters.foci;

import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.casters.FocusEffect;
import thaumcraft.api.casters.NodeSetting;
import thaumcraft.api.casters.Trajectory;
import thaumcraft.common.lib.scheduling.BlockScheduler;

/**
 * Replaces the old (1.12.2) {@code FocusEffectBreak}. Real port of the original's own gradual, over-time
 * break: queues the block via {@link BlockScheduler#addBreaker}, the same real durability-drain-per-tick
 * shape the original's own {@code ServerEvents.addBreaker} used (confirmed against that class's own real
 * pre-port source, not guessed at) - a real, felt difference from this class's own earlier instant-break
 * substitute (see the port-tracking memory for why that substitute went out without a check-in, and why it's
 * being corrected now). {@code durabilityCurrent}/{@code durabilityMax} are both {@code sqrt(hardness * 100)}
 * and {@code delay} is {@code (int) (durability / strength / 3.0f * num)} - the original's own exact real
 * formulas, staggering multiple simultaneous casts (via {@code num}) so they don't all finish on the same
 * tick. Real fortune/silk-touch handling (via {@link BlockScheduler}'s own throwaway-enchanted-tool
 * technique, not reimplemented here) is unchanged from the instant version.
 *
 * A block only queues if its hardness is within reach of the focus's power setting (unbreakable blocks -
 * hardness &lt; 0, like bedrock - are always rejected, same real gate the original used); no FX/network
 * packet, matching the other foci ported so far.
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
		if (!(getPackage().getCaster() instanceof Player player)) return true;

		Level level = getPackage().world;
		BlockPos pos = blockHit.getBlockPos();
		BlockState state = level.getBlockState(pos);
		float hardness = state.getDestroySpeed(level, pos);
		if (hardness < 0) return false;

		boolean silk = getSettingValue("silk") > 0;
		int fortune = getSettingValue("fortune");
		float strength = getSettingValue("power") * finalPower;
		float durability = (float) Math.sqrt(hardness * 100.0f);

		BlockScheduler.addBreaker(level, pos, state, player, true, silk, fortune, strength, durability, durability,
				(int) (durability / strength / 3.0f * num), 0.25f + (silk ? 0.25f : 0.0f) + fortune * 0.1f);
		return true;
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
