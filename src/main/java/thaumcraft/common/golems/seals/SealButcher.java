package thaumcraft.common.golems.seals;

import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.GolemHelper;
import thaumcraft.api.golems.IGolemAPI;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealConfigArea;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealButcher} - a "population control" variant of {@code SealGuard}
 * (real target-then-suspend-immediately shape, see that class's own doc comment for how the combat handoff
 * to {@code AIGotoEntity}/the golem's own real melee AI works). A FIGHTER+SMART golem only culls an adult,
 * untamed, non-hostile animal when at least 3 of that same species are present in the seal's area (the
 * original's own overflow heuristic, kept exactly - real breeding stock is left alone until a farm is
 * actually overcrowded).
 *
 * Real, direct simplification: the original also defensively excluded {@code EntityGolem} targets
 * (vanilla's snow/iron golem base class in 1.12.2) - moot in modern code, since neither
 * {@link net.minecraft.world.entity.animal.IronGolem} nor {@link net.minecraft.world.entity.animal.SnowGolem}
 * extend {@link Animal} to begin with, so the existing {@code instanceof Animal} check already excludes
 * them without a separate check.
 */
public class SealButcher implements ISeal, ISealGui, ISealConfigArea {

	private int delay = new Random().nextInt(200);
	private boolean waiting;
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_butcher");

	@Override
	public String getKey() {
		return "thaumcraft:butcher";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 200 != 0 || waiting) return;
		AABB area = GolemHelper.getBoundsForArea(seal);
		List<LivingEntity> list = world.getEntitiesOfClass(LivingEntity.class, area);
		for (LivingEntity target : list) {
			if (!isValidTarget(target) || spared(seal, null, target)) continue;
			int count = 0;
			for (LivingEntity other : list) {
				if (other.getClass() == target.getClass() && isValidTarget(other)) {
					count++;
					if (count > 2) break;
				}
			}
			if (count > 2) {
				Task task = new Task(seal.getSealPos(), target);
				task.setPriority(seal.getPriority());
				task.setLifespan((short) 10);
				TaskHandler.addTask(world.dimension(), task);
				waiting = true;
				break;
			}
		}
	}

	/** Whether an addon spares this animal (see {@link thaumcraft.api.events.GolemButcherTargetEvent}). */
	private static boolean spared(ISealEntity seal, IGolemAPI golem, LivingEntity target) {
		return seal != null && net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new thaumcraft.api.events.GolemButcherTargetEvent(seal, golem, target)).isCanceled();
	}

	private static boolean isValidTarget(LivingEntity target) {
		if (!(target instanceof Animal animal) || target instanceof Enemy) return false;
		if (target instanceof TamableAnimal tameable && tameable.isTame()) return false;
		return !animal.isBaby();
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
		if (task.getEntity() instanceof LivingEntity target && isValidTarget(target) && golem.getGolemEntity() instanceof Mob mob
				&& !spared(SealHandler.getSealEntity(world.dimension(), task.getSealPos()), golem, target)) {
			mob.setTarget(target);
			golem.addRankXp(1);
		}
		task.setSuspended(true);
		waiting = false;
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		task.setSuspended(true);
		waiting = false;
		return true;
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		return true;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
		waiting = false;
	}

	@Override
	public void readCustomNBT(CompoundTag nbt) {
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
	}

	@Override
	public boolean canPlaceAt(Level world, BlockPos pos, Direction side) {
		return !world.isEmptyBlock(pos);
	}

	@Override
	public ResourceLocation getSealIcon() {
		return ICON;
	}

	@Override
	public void onRemoval(Level world, BlockPos pos, Direction side) {
		waiting = false;
	}

	@Override
	public Object returnContainer(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public Object returnGui(Level world, Player player, BlockPos pos, Direction side, ISealEntity seal) {
		return null;
	}

	@Override
	public EnumGolemTrait[] getRequiredTags() {
		return new EnumGolemTrait[] { EnumGolemTrait.FIGHTER, EnumGolemTrait.SMART };
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return null;
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_PRIORITY, CAT_TAGS };
	}
}
