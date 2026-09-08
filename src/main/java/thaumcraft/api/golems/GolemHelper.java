package thaumcraft.api.golems;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import thaumcraft.api.golems.seals.ISeal;
import thaumcraft.api.golems.seals.ISealEntity;
import thaumcraft.api.golems.seals.SealPos;
import thaumcraft.api.golems.tasks.Task;



public class GolemHelper {

	/**
	 * Real state now (was a permanent stub delegating to a never-built {@code ThaumcraftApi.internalMethods}
	 * indirection layer - same "make it real, no more permanent stub" fix {@code AspectHelper}'s own
	 * object-tag lookup already got earlier this session). This class is the single source of truth for
	 * registered seal types, placed seal instances, and per-dimension golem task queues -
	 * {@code thaumcraft.common.golems.seals.SealHandler}/{@code tasks.TaskHandler} are thin common-side
	 * convenience wrappers built on top of the real accessors here (not the other way around - api-package
	 * code never depends on common-package code).
	 */
	private static final Map<String, ISeal> SEAL_TYPES = new LinkedHashMap<>();
	private static final Map<ResourceKey<Level>, Map<SealPos, ISealEntity>> SEAL_ENTITIES = new HashMap<>();
	private static final Map<ResourceKey<Level>, Map<Integer, Task>> GOLEM_TASKS = new HashMap<>();

	/**
	 * Make sure to register your seals during the preInit phase before TC is loaded
	 * @param seal
	 */
	public static void registerSeal(ISeal seal) {
		SEAL_TYPES.putIfAbsent(seal.getKey(), seal);
	}

	public static ISeal getSeal(String key) {
		return SEAL_TYPES.get(key);
	}

	public static String[] getRegisteredSealKeys() {
		return SEAL_TYPES.keySet().toArray(new String[0]);
	}

	/** Reflectively creates a fresh {@link ISeal} instance of whatever class was registered under this key -
	 * every placed seal (and every seal restored from NBT) needs its own live instance, since implementations
	 * like the original's {@code SealHarvest} hold real per-placement mutable state (its replant-tracking
	 * map). Same reflective-fresh-instance shape the original's own {@code SealHandler}/{@code SealEntity}
	 * used ({@code getSeal(key).getClass().newInstance()}), just via modern {@code getDeclaredConstructor()}
	 * instead of the deprecated no-arg {@code Class#newInstance()}. Returns {@code null} (rather than
	 * throwing) if the key isn't registered or the class has no accessible no-arg constructor - callers
	 * already treat a {@code null}/failed seal as "can't place/restore here", matching the original. */
	public static ISeal createSeal(String key) {
		ISeal prototype = SEAL_TYPES.get(key);
		if (prototype == null) return null;
		try {
			return prototype.getClass().getDeclaredConstructor().newInstance();
		} catch (InstantiationException | IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
			return null;
		}
	}

	/** No generic per-key placer-item lookup yet (unlike the original's shared metadata-variant
	 * {@code ItemsTC.seals} item, this port ships one dedicated {@code ItemSealPlacer} instance per seal
	 * type - see that class's own doc comment) - real follow-up work once enough seal types exist to make a
	 * generic lookup worthwhile. */
	public static ItemStack getSealStack(String key) {
		return null;
	}

	public static ISealEntity getSealEntity(ResourceKey<Level> dim, SealPos pos) {
		if (pos == null) return null;
		Map<SealPos, ISealEntity> m = SEAL_ENTITIES.get(dim);
		return m != null ? m.get(pos) : null;
	}

	/** The live, mutable per-dimension seal map - not a defensive copy, callers (this port's own
	 * {@code SealHandler}) iterate/insert/remove directly, same as the original's own {@code sealEntities}
	 * field was used. */
	public static Map<SealPos, ISealEntity> getSealEntities(ResourceKey<Level> dim) {
		return SEAL_ENTITIES.computeIfAbsent(dim, d -> new LinkedHashMap<>());
	}

	public static void addGolemTask(ResourceKey<Level> dim, Task task) {
		getTasks(dim).put(task.getId(), task);
	}

	/** The live, mutable per-dimension task map - see {@link #getSealEntities}'s own doc comment for why
	 * this isn't a defensive copy. */
	public static Map<Integer, Task> getTasks(ResourceKey<Level> dim) {
		return GOLEM_TASKS.computeIfAbsent(dim, d -> new LinkedHashMap<>());
	}

	public static HashMap<ResourceKey<Level>,ArrayList<ProvisionRequest>> provisionRequests = new HashMap<>();
	static int LISTLIMIT = 1000;

	/** Drops this dimension's live in-memory seal/task/provision-request state - called from
	 * {@code Thaumcraft}'s real {@code LevelEvent.Unload} handler (the modern replacement for the original's
	 * own {@code WorldEvents#worldUnload}), one dimension at a time. Safe to call even if nothing was ever
	 * loaded for this dimension - every map here lazily recreates its entry on next real access
	 * ({@link #getSealEntities}/{@link #getTasks} both {@code computeIfAbsent}), so this never permanently
	 * loses state, it just stops holding memory for a dimension that's no longer loaded. */
	public static void unloadDimension(ResourceKey<Level> dim) {
		SEAL_ENTITIES.remove(dim);
		GOLEM_TASKS.remove(dim);
		provisionRequests.remove(dim);
	}

	/**
	 *
	 * @param world
	 * @param seal
	 * @param stack the stack requested. Can accept wildcard values.
	 */
	public static void requestProvisioning(Level world, ISealEntity seal, ItemStack stack) {
		if (!provisionRequests.containsKey(world.dimension()))
			provisionRequests.put(world.dimension(), new ArrayList<ProvisionRequest>());
		ArrayList<ProvisionRequest> list = provisionRequests.get(world.dimension());
		ProvisionRequest pr = new ProvisionRequest(seal,stack.copy());
		if (!list.contains(pr)) {
			list.add(pr);
		}
		if (list.size()>LISTLIMIT) list.remove(0);
	}

	/**
	 *
	 * @param world
	 * @param pos
	 * @param side
	 * @param stack the stack requested. Can accept wildcard values.
	 */
	public static void requestProvisioning(Level world, BlockPos pos, Direction side, ItemStack stack) {
		if (!provisionRequests.containsKey(world.dimension()))
			provisionRequests.put(world.dimension(), new ArrayList<ProvisionRequest>());
		ArrayList<ProvisionRequest> list = provisionRequests.get(world.dimension());
		ProvisionRequest pr = new ProvisionRequest(pos, side, stack.copy());
		if (!list.contains(pr)) {
			list.add(pr);
		}
		if (list.size()>LISTLIMIT) list.remove(0);
	}

	/**
	 *
	 * @param world
	 * @param entity
	 * @param stack the stack requested. Can accept wildcard values.
	 */
	public static void requestProvisioning(Level world, Entity entity, ItemStack stack) {
		if (!provisionRequests.containsKey(world.dimension()))
			provisionRequests.put(world.dimension(), new ArrayList<ProvisionRequest>());
		ArrayList<ProvisionRequest> list = provisionRequests.get(world.dimension());
		ProvisionRequest pr = new ProvisionRequest(entity, stack.copy());
		if (!list.contains(pr)) {
			list.add(pr);
		}
		if (list.size()>LISTLIMIT) list.remove(0);
	}

	/**
	 *
	 * @param world
	 * @param pos
	 * @param side
	 * @param stack the stack requested. Can accept wildcard values.
	 * @param ui a unique number to make the request slightly more unique in case you want to add multiple similar requests
	 */
	public static void requestProvisioning(Level world, BlockPos pos, Direction side, ItemStack stack, int ui) {
		if (!provisionRequests.containsKey(world.dimension()))
			provisionRequests.put(world.dimension(), new ArrayList<ProvisionRequest>());
		ArrayList<ProvisionRequest> list = provisionRequests.get(world.dimension());
		ProvisionRequest pr = new ProvisionRequest(pos, side, stack.copy());
		pr.setUI(ui);
		if (!list.contains(pr)) {
			list.add(pr);
		}
		if (list.size()>LISTLIMIT) list.remove(0);
	}

	/**
	 *
	 * @param world
	 * @param entity
	 * @param stack the stack requested. Can accept wildcard values.
	 * @param ui a unique number to make the request slightly more unique in case you want to add multiple similar requests
	 */
	public static void requestProvisioning(Level world, Entity entity, ItemStack stack, int ui) {
		if (!provisionRequests.containsKey(world.dimension()))
			provisionRequests.put(world.dimension(), new ArrayList<ProvisionRequest>());
		ArrayList<ProvisionRequest> list = provisionRequests.get(world.dimension());
		ProvisionRequest pr = new ProvisionRequest(entity, stack.copy());
		pr.setUI(ui);
		if (!list.contains(pr)) {
			list.add(pr);
		}
		if (list.size()>LISTLIMIT) list.remove(0);
	}

	/**
	 * This method is used to get a single blockpos from within a designated seal area.
	 * This method is best used if you want to increment through the blocks in the area.
	 * @param seal
	 * @param count a value used to derive a specific pos
	 * @return
	 */
	public static BlockPos getPosInArea(ISealEntity seal, int count) {
		int xx = 1 + (seal.getArea().getX()-1) * (seal.getSealPos().face.getStepX()==0?2:1);
		int yy = 1 + (seal.getArea().getY()-1) * (seal.getSealPos().face.getStepY()==0?2:1);
		int zz = 1 + (seal.getArea().getZ()-1) * (seal.getSealPos().face.getStepZ()==0?2:1);

		int qx = seal.getSealPos().face.getStepX()!=0?seal.getSealPos().face.getStepX():1;
		int qy = seal.getSealPos().face.getStepY()!=0?seal.getSealPos().face.getStepY():1;
		int qz = seal.getSealPos().face.getStepZ()!=0?seal.getSealPos().face.getStepZ():1;

		int y = qy*((count/zz)/xx)%yy + seal.getSealPos().face.getStepY();
		int x = qx*(count/zz)%xx + seal.getSealPos().face.getStepX();
		int z = qz*count%zz + seal.getSealPos().face.getStepZ();

		BlockPos p = seal.getSealPos().pos.offset(
				x - (seal.getSealPos().face.getStepX()==0?xx/2:0),
				y - (seal.getSealPos().face.getStepY()==0?yy/2:0),
				z - (seal.getSealPos().face.getStepZ()==0?zz/2:0));

		return p;
	}


	/**
	 * Returns the designated seal area as a AxisAlignedBB
	 * @param seal
	 * @return
	 */
	public static AABB getBoundsForArea(ISealEntity seal) {
		return new AABB(
				seal.getSealPos().pos.getX(), seal.getSealPos().pos.getY(), seal.getSealPos().pos.getZ(),
				seal.getSealPos().pos.getX()+1, seal.getSealPos().pos.getY()+1, seal.getSealPos().pos.getZ()+1)
				.move(
					seal.getSealPos().face.getStepX(),
					seal.getSealPos().face.getStepY(),
					seal.getSealPos().face.getStepZ())
				.expandTowards(
					seal.getSealPos().face.getStepX()!=0?(seal.getArea().getX()-1) * seal.getSealPos().face.getStepX():0,
					seal.getSealPos().face.getStepY()!=0?(seal.getArea().getY()-1) * seal.getSealPos().face.getStepY():0,
					seal.getSealPos().face.getStepZ()!=0?(seal.getArea().getZ()-1) * seal.getSealPos().face.getStepZ():0)
				.inflate(
					seal.getSealPos().face.getStepX()==0?seal.getArea().getX()-1:0,
					seal.getSealPos().face.getStepY()==0?seal.getArea().getY()-1:0,
					seal.getSealPos().face.getStepZ()==0?seal.getArea().getZ()-1:0 );
	}



}
