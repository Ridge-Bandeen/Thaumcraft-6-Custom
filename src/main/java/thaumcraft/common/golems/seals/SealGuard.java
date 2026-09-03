package thaumcraft.common.golems.seals;

import java.util.List;
import java.util.Random;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
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
import thaumcraft.api.golems.seals.ISealConfigToggles;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.ISealGui;
import thaumcraft.api.golems.tasks.Task;
import thaumcraft.common.golems.tasks.TaskHandler;

/**
 * Replaces the old (1.12.2) {@code SealGuard} - the first real {@link ISeal} implementation this port
 * ships (see {@code SealHandler}/{@code TaskHandler}/{@code GolemHelper} for the infrastructure it runs
 * on). A FIGHTER golem assigned to guard an area scans it every 20 ticks for valid targets (hostile mobs by
 * default, optionally animals/players too) and, once a target task is picked up by
 * {@code thaumcraft.common.golems.ai.AIGotoEntity}, immediately sets it as the golem's real combat target -
 * the golem's own already-real {@code MeleeAttackGoal}/attack-damage logic (see
 * {@code EntityThaumcraftGolem}) takes it from there, exactly like the original (this seal never itself
 * makes the golem walk anywhere; it only ever assigns a target and instantly suspends the task).
 *
 * Real, documented cut: {@link #returnContainer}/{@link #returnGui} return {@code null} - no seal-config
 * GUI exists in this port yet (same boundary the whole golem arc has kept to); the 3 real toggles
 * ({@code pmob}/{@code panimal}/{@code pplayer}) are still fully real and functional, they're just only
 * reachable via NBT/creative-tools for now, not an in-game config screen.
 */
public class SealGuard implements ISeal, ISealGui, ISealConfigArea, ISealConfigToggles {

	private int delay = new Random().nextInt(22);
	private final ISealConfigToggles.SealToggle[] props = {
			new ISealConfigToggles.SealToggle(true, "pmob", "golem.prop.mob"),
			new ISealConfigToggles.SealToggle(false, "panimal", "golem.prop.animal"),
			new ISealConfigToggles.SealToggle(false, "pplayer", "golem.prop.player"),
	};
	private static final ResourceLocation ICON = ResourceLocation.fromNamespaceAndPath("thaumcraft", "items/seals/seal_guard");

	@Override
	public String getKey() {
		return "thaumcraft:guard";
	}

	@Override
	public void tickSeal(Level world, ISealEntity seal) {
		if (delay++ % 20 != 0) return;
		AABB area = GolemHelper.getBoundsForArea(seal);
		List<LivingEntity> targets = world.getEntitiesOfClass(LivingEntity.class, area);
		for (LivingEntity target : targets) {
			if (isValidTarget(world, target)) {
				Task task = new Task(seal.getSealPos(), target);
				task.setPriority(seal.getPriority());
				task.setLifespan((short) 10);
				TaskHandler.addTask(world.dimension(), task);
			}
		}
	}

	private boolean isValidTarget(Level world, LivingEntity target) {
		boolean valid = false;
		if (props[0].getValue() && target instanceof Enemy) {
			valid = true;
		}
		if (props[1].getValue() && target instanceof Animal) {
			valid = true;
		}
		if (props[2].getValue() && target instanceof Player && world instanceof net.minecraft.server.level.ServerLevel serverLevel
				&& isPvpAllowed(serverLevel.getServer())) {
			valid = true;
		}
		return valid;
	}

	private static boolean isPvpAllowed(MinecraftServer server) {
		return server != null && server.isPvpAllowed();
	}

	@Override
	public void onTaskStarted(Level world, IGolemAPI golem, Task task) {
		if (task.getEntity() instanceof LivingEntity target && isValidTarget(world, target) && golem.getGolemEntity() instanceof Mob mob) {
			mob.setTarget(target);
			golem.addRankXp(1);
		}
		task.setSuspended(true);
	}

	@Override
	public boolean onTaskCompletion(Level world, IGolemAPI golem, Task task) {
		task.setSuspended(true);
		return true;
	}

	@Override
	public void onTaskSuspension(Level world, Task task) {
	}

	@Override
	public boolean canGolemPerformTask(IGolemAPI golem, Task task) {
		return !golem.getGolemEntity().isAlliedTo(task.getEntity());
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
		return new EnumGolemTrait[] { EnumGolemTrait.FIGHTER };
	}

	@Override
	public EnumGolemTrait[] getForbiddenTags() {
		return null;
	}

	@Override
	public void readCustomNBT(CompoundTag nbt) {
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt) {
	}

	@Override
	public int[] getGuiCategories() {
		return new int[] { CAT_AREA, CAT_PRIORITY, CAT_TAGS };
	}

	@Override
	public ISealConfigToggles.SealToggle[] getToggles() {
		return props;
	}

	@Override
	public void setToggle(int index, boolean value) {
		props[index].setValue(value);
	}
}
