package thaumcraft.api.aspects;

import java.util.ArrayList;
import java.util.Random;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.ThaumcraftApi;

/**
 * Replaces the old (1.12.2) {@code AspectHelper}. Almost entirely self-contained aspect arithmetic - no
 * registry dependency - except {@link #getObjectAspects}/{@link #generateTags}/{@link #getEntityAspects},
 * which (same as the original) delegate to {@code ThaumcraftApi.internalMethods}. See
 * {@link thaumcraft.common.lib.crafting.ThaumcraftObjectTags}/{@code ThaumcraftEntityTags}'s own doc
 * comments for the real item-to-aspect/entity-to-aspect lookups this port now backs those with.
 */
public class AspectHelper {

	private AspectHelper() {
	}

	public static AspectList cullTags(AspectList temp) {
		return cullTags(temp, 7);
	}

	public static AspectList cullTags(AspectList temp, int cap) {
		AspectList temp2 = new AspectList();
		for (Aspect tag : temp.getAspects()) {
			if (tag != null) temp2.add(tag, temp.getAmount(tag));
		}
		while (temp2 != null && temp2.size() > cap) {
			Aspect lowest = null;
			float low = Short.MAX_VALUE;
			for (Aspect tag : temp2.getAspects()) {
				if (tag == null) continue;
				float ta = temp2.getAmount(tag);
				if (tag.isPrimal()) {
					ta *= 0.9f;
				} else {
					Aspect[] components = tag.getComponents();
					if (!components[0].isPrimal()) {
						ta *= 1.1f;
						if (!components[0].getComponents()[0].isPrimal()) ta *= 1.05f;
						if (!components[0].getComponents()[1].isPrimal()) ta *= 1.05f;
					}
					if (!components[1].isPrimal()) {
						ta *= 1.1f;
						if (!components[1].getComponents()[0].isPrimal()) ta *= 1.05f;
						if (!components[1].getComponents()[1].isPrimal()) ta *= 1.05f;
					}
				}
				if (ta < low) {
					low = ta;
					lowest = tag;
				}
			}
			temp2.remove(lowest);
		}
		return temp2;
	}

	public static AspectList getObjectAspects(ItemStack is) {
		return ThaumcraftApi.internalMethods.getObjectAspects(is);
	}

	public static AspectList generateTags(ItemStack is) {
		return ThaumcraftApi.internalMethods.generateTags(is);
	}

	/**
	 * A player's aspects were never a real lookup even in the original - just 4 Man plus 3 random aspects at
	 * 15 each, reseeded per player name (so the same player always "scans" the same, but different players
	 * differ) rather than actually representing anything about them. Kept exactly as-is; every other entity
	 * goes through {@code ThaumcraftApi.internalMethods} to {@code ThaumcraftEntityTags}'s real registry.
	 */
	public static AspectList getEntityAspects(Entity entity) {
		if (entity instanceof Player player) {
			AspectList tags = new AspectList().add(Aspect.MAN, 4);
			Random rand = new Random(player.getGameProfile().getName().hashCode());
			Aspect[] all = Aspect.aspects.values().toArray(new Aspect[0]);
			tags.add(all[rand.nextInt(all.length)], 15);
			tags.add(all[rand.nextInt(all.length)], 15);
			tags.add(all[rand.nextInt(all.length)], 15);
			return tags;
		}
		return ThaumcraftApi.internalMethods.getEntityAspects(entity);
	}

	public static Aspect getCombinationResult(Aspect aspect1, Aspect aspect2) {
		for (Aspect aspect : Aspect.aspects.values()) {
			Aspect[] components = aspect.getComponents();
			if (components != null
					&& ((components[0] == aspect1 && components[1] == aspect2)
					|| (components[0] == aspect2 && components[1] == aspect1))) {
				return aspect;
			}
		}
		return null;
	}

	public static Aspect getRandomPrimal(Random rand, Aspect aspect) {
		ArrayList<Aspect> list = new ArrayList<>();
		if (aspect != null) {
			AspectList temp = new AspectList();
			temp.add(aspect, 1);
			AspectList temp2 = reduceToPrimals(temp);
			for (Aspect a : temp2.getAspects()) {
				for (int b = 0; b < temp2.getAmount(a); b++) {
					list.add(a);
				}
			}
		}
		return list.isEmpty() ? null : list.get(rand.nextInt(list.size()));
	}

	public static AspectList reduceToPrimals(AspectList in) {
		AspectList out = new AspectList();
		for (Aspect aspect : in.getAspects()) {
			if (aspect == null) continue;
			if (aspect.isPrimal()) {
				out.add(aspect, in.getAmount(aspect));
			} else {
				AspectList temp = new AspectList();
				temp.add(aspect.getComponents()[0], in.getAmount(aspect));
				temp.add(aspect.getComponents()[1], in.getAmount(aspect));
				AspectList temp2 = reduceToPrimals(temp);
				for (Aspect a : temp2.getAspects()) {
					out.add(a, temp2.getAmount(a));
				}
			}
		}
		return out;
	}

	public static AspectList getPrimalAspects(AspectList in) {
		AspectList t = new AspectList();
		t.add(Aspect.AIR, in.getAmount(Aspect.AIR));
		t.add(Aspect.FIRE, in.getAmount(Aspect.FIRE));
		t.add(Aspect.WATER, in.getAmount(Aspect.WATER));
		t.add(Aspect.EARTH, in.getAmount(Aspect.EARTH));
		t.add(Aspect.ORDER, in.getAmount(Aspect.ORDER));
		t.add(Aspect.ENTROPY, in.getAmount(Aspect.ENTROPY));
		return t;
	}

	public static AspectList getAuraAspects(AspectList in) {
		AspectList t = getPrimalAspects(in);
		t.add(Aspect.FLUX, in.getAmount(Aspect.FLUX));
		return t;
	}
}
