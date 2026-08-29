package thaumcraft.api.casters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;



/**
 * This class serves a similar function to IInteractWithCaster in that it allows casters to interact
 * with object in the world. In this case it is most useful for adding interaction with non-mod
 * blocks where you can't control what happens in their code.
 *
 * @author azanor
 *
 */
public class CasterTriggerRegistry {

	private static HashMap<String,LinkedHashMap<BlockState,List<Trigger>>> triggers = new HashMap<String,LinkedHashMap<BlockState,List<Trigger>>>();
	private static String DEFAULT = "default";

	private static class Trigger {
		ICasterTriggerManager manager;
		int event;
		public Trigger(ICasterTriggerManager manager, int event) {
			super();
			this.manager = manager;
			this.event = event;
		}
	}

	/**
	 * Registers an action to perform when a caster right clicks on a specific block.
	 * A manager class needs to be created that implements ICasterTriggerManager.
	 * @param manager
	 * @param event a logical number that you can use to differentiate different events or actions
	 * @param blockState
	 * @param meta send -1 as a wildcard value for all possible meta values
	 * @param modid a unique identifier. It is best to register your own triggers using your mod id to avoid conflicts with mods that register triggers for the same block
	 */
	public static void registerWandBlockTrigger(ICasterTriggerManager manager, int event, BlockState state, String modid) {
		if (!triggers.containsKey(modid)) {
			triggers.put(modid, new LinkedHashMap<BlockState,List<Trigger>>());
		}
		LinkedHashMap<BlockState,List<Trigger>> temp = triggers.get(modid);
		List<Trigger> ts = temp.get(state);
		if (ts==null) ts = new ArrayList<Trigger>();
		ts.add(new Trigger(manager,event));
		temp.put(state,ts);
		triggers.put(modid, temp);
	}

	/**
	 * for legacy support
	 */
	public static void registerCasterBlockTrigger(ICasterTriggerManager manager, int event, BlockState state) {
		registerWandBlockTrigger(manager, event, state, DEFAULT);
	}

	/**
	 * Checks all trigger registries if one exists for the given block and meta
	 * @param blockState
	 * @param meta
	 * @return
	 */
	public static boolean hasTrigger(BlockState state) {
		for (String modid:triggers.keySet()) {
			LinkedHashMap<BlockState,List<Trigger>> temp = triggers.get(modid);
			if (temp.containsKey(state)) return true;
		}
		return false;
	}

	/**
	 * modid sensitive version
	 */
	public static boolean hasTrigger(BlockState state, String modid) {
		if (!triggers.containsKey(modid)) return false;
		LinkedHashMap<BlockState,List<Trigger>> temp = triggers.get(modid);
		if (temp.containsKey(state)) return true;
		return false;
	}


	/**
	 * This is called by the onItemUseFirst function in casters.
	 * Parameters and return value functions like you would expect for that function.
	 * @param world
	 * @param casterStack
	 * @param player
	 * @param x
	 * @param y
	 * @param z
	 * @param side
	 * @param blockState
	 * @param meta
	 * @return
	 */
	public static boolean performTrigger(Level world, ItemStack casterStack, Player player,
			BlockPos pos, Direction side, BlockState state) {
		for (String modid:triggers.keySet()) {
			LinkedHashMap<BlockState,List<Trigger>> temp = triggers.get(modid);
			List<Trigger> l = temp.get(state);
			if (l==null || l.size()==0) continue;
			for (Trigger trig:l) {
				boolean result = trig.manager.performTrigger(world, casterStack, player, pos, side, trig.event);
				if (result) return true;
			}
		}
		return false;
	}

	/**
	 * modid sensitive version
	 */
	public static boolean performTrigger(Level world, ItemStack casterStack, Player player,
			BlockPos pos, Direction side, BlockState state, String modid) {
		if (!triggers.containsKey(modid)) return false;
		LinkedHashMap<BlockState,List<Trigger>> temp = triggers.get(modid);
		List<Trigger> l = temp.get(state);
		if (l==null || l.size()==0) return false;
		for (Trigger trig:l) {
			boolean result = trig.manager.performTrigger(world, casterStack, player, pos, side, trig.event);
			if (result) return true;
		}
		return false;
	}

}
