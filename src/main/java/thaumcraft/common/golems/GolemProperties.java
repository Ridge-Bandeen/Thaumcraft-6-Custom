package thaumcraft.common.golems;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.golems.EnumGolemTrait;
import thaumcraft.api.golems.IGolemProperties;
import thaumcraft.api.golems.parts.GolemAddon;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemLeg;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.api.golems.parts.PartModel;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code GolemProperties} - the concrete part-registry-backed
 * {@link IGolemProperties} implementation. Direct translation: {@code data} packs material/head/arm/leg/
 * addon registry ids plus a rank byte into one {@code long} (unchanged shape, still 6 real bytes not the
 * full 8, matching the original's own layout), and {@link #getTraits} unions every equipped part's traits
 * with the original's "opposite cancels out" rule (e.g. a DEFT part later offset by a CLUMSY one removes
 * DEFT rather than stacking both).
 *
 * {@link #setByteInLong}/{@link #getByteInLong} are a small local stand-in for
 * {@code ThaumcraftApiHelper}'s own versions - same "don't port the whole still-1.12.2 helper class for a
 * couple of pure-Java bit-twiddling methods" precedent {@code ToolEvents#makeCrystal} already established.
 *
 * Real, documented simplifications versus the original's own static part registration below:
 * <ul>
 * <li>Every part uses the base {@link PartModel} directly rather than the original's per-part
 * {@code PartModelClaws}/{@code PartModelBreakers}/{@code PartModelDarts}/{@code PartModelWheel}/
 * {@code PartModelHauler} subclasses (client-only rendering hooks with nothing to call them yet - no golem
 * renderer exists in this port). The "DARTS" arm's real {@code IArmFunction}
 * ({@link thaumcraft.common.golems.parts.GolemArmDart}) is wired in - real ranged combat, not cosmetic. The
 * roller and flyer legs have their own per-tick functions too ({@link thaumcraft.common.golems.parts.GolemLegWheels},
 * {@link thaumcraft.common.golems.parts.GolemLegLevitator}: wheel dust and spin, flyer motes), as the original.</li>
 * <li>The FLYER leg's real cost item is {@link BlocksTC#levitator} again, real since the CodeChicken-
 * precision restoration arc (its own earlier {@code Items.FEATHER} placeholder was written when that block
 * wasn't real yet).</li>
 * <li>{@code Blocks.HARDENED_CLAY}/{@code Blocks.WOOL}/{@code Blocks.CHEST} become their modern
 * equivalents ({@code Blocks.TERRACOTTA}/{@code Blocks.WHITE_WOOL}/{@code Items.CHEST}) - renames, not
 * simplifications.</li>
 * </ul>
 */
public class GolemProperties implements IGolemProperties {

	private long data;
	private Set<EnumGolemTrait> traitCache;

	public GolemProperties() {
		data = 0L;
		traitCache = null;
	}

	@Override
	public Set<EnumGolemTrait> getTraits() {
		if (traitCache == null) {
			traitCache = new HashSet<>();
			for (EnumGolemTrait trait : getMaterial().traits) addTraitSmart(trait);
			for (EnumGolemTrait trait : getHead().traits) addTraitSmart(trait);
			for (EnumGolemTrait trait : getArms().traits) addTraitSmart(trait);
			for (EnumGolemTrait trait : getLegs().traits) addTraitSmart(trait);
			for (EnumGolemTrait trait : getAddon().traits) addTraitSmart(trait);
		}
		return traitCache;
	}

	private void addTraitSmart(EnumGolemTrait trait) {
		if (trait.opposite != null && traitCache.contains(trait.opposite)) {
			traitCache.remove(trait.opposite);
		} else {
			traitCache.add(trait);
		}
	}

	@Override
	public boolean hasTrait(EnumGolemTrait trait) {
		return getTraits().contains(trait);
	}

	@Override
	public void setMaterial(GolemMaterial mat) {
		data = setByteInLong(data, mat.id, 0);
		traitCache = null;
	}

	@Override
	public GolemMaterial getMaterial() {
		return GolemMaterial.getMaterials()[getByteInLong(data, 0)];
	}

	@Override
	public void setHead(GolemHead mat) {
		data = setByteInLong(data, mat.id, 1);
		traitCache = null;
	}

	@Override
	public GolemHead getHead() {
		return GolemHead.getHeads()[getByteInLong(data, 1)];
	}

	@Override
	public void setArms(GolemArm mat) {
		data = setByteInLong(data, mat.id, 2);
		traitCache = null;
	}

	@Override
	public GolemArm getArms() {
		return GolemArm.getArms()[getByteInLong(data, 2)];
	}

	@Override
	public void setLegs(GolemLeg mat) {
		data = setByteInLong(data, mat.id, 3);
		traitCache = null;
	}

	@Override
	public GolemLeg getLegs() {
		return GolemLeg.getLegs()[getByteInLong(data, 3)];
	}

	@Override
	public void setAddon(GolemAddon mat) {
		data = setByteInLong(data, mat.id, 4);
		traitCache = null;
	}

	@Override
	public GolemAddon getAddon() {
		return GolemAddon.getAddons()[getByteInLong(data, 4)];
	}

	@Override
	public void setRank(int rank) {
		data = setByteInLong(data, (byte) rank, 5);
	}

	@Override
	public int getRank() {
		return getByteInLong(data, 5);
	}

	public static IGolemProperties fromLong(long d) {
		GolemProperties out = new GolemProperties();
		out.data = d;
		return out;
	}

	@Override
	public long toLong() {
		return data;
	}

	@Override
	public ItemStack[] generateComponents() {
		ArrayList<ItemStack> comps = new ArrayList<>();
		ItemStack base = getMaterial().componentBase;
		ItemStack mech = getMaterial().componentMechanism;
		addToList(comps, base, 2);
		addToList(comps, mech, 1);
		addToListFromComps(comps, getArms().components, getMaterial());
		addToListFromComps(comps, getLegs().components, getMaterial());
		addToListFromComps(comps, getHead().components, getMaterial());
		addToListFromComps(comps, getAddon().components, getMaterial());
		return comps.toArray(new ItemStack[0]);
	}

	private static void addToListFromComps(ArrayList<ItemStack> comps, Object[] objs, GolemMaterial mat) {
		for (Object o : objs) {
			if (o instanceof ItemStack stack) {
				addToList(comps, stack, 1);
			} else if (o instanceof String s) {
				if (s.equalsIgnoreCase("base")) {
					addToList(comps, mat.componentBase, 1);
				} else if (s.equalsIgnoreCase("mech")) {
					addToList(comps, mat.componentMechanism, 1);
				}
			}
		}
	}

	private static void addToList(ArrayList<ItemStack> comps, ItemStack newItem, int mult) {
		for (ItemStack stack : comps) {
			if (ItemStack.isSameItemSameComponents(stack, newItem)) {
				stack.grow(newItem.getCount() * mult);
				return;
			}
		}
		ItemStack stack2 = newItem.copy();
		stack2.setCount(stack2.getCount() * mult);
		comps.add(stack2);
	}

	private static long setByteInLong(long data, byte b, int index) {
		ByteBuffer bb = ByteBuffer.allocate(8);
		bb.putLong(0, data);
		bb.put(index, b);
		return bb.getLong(0);
	}

	private static byte getByteInLong(long data, int index) {
		ByteBuffer bb = ByteBuffer.allocate(8);
		bb.putLong(0, data);
		return bb.get(index);
	}

	/** Registers every real part - {@code Thaumcraft#commonSetup} must call this, not a static initializer:
	 * every registration below dereferences a real {@code DeferredHolder} via {@code .get()}, which throws
	 * before that registry's own {@code RegisterEvent} has fired (the same registry-timing rule
	 * {@code ToolEvents#initSpecialMining} and {@code ThaumcraftObjectTags#init} already established).
	 *
	 * <p>Every texture {@code ResourceLocation} below originally had a literal {@code .png} suffix baked
	 * into its path - harmless while nothing consumed these fields, but a real bug once something does:
	 * Minecraft's own texture-location convention never includes the extension (the loader appends
	 * {@code .png} itself), so a real consumer would have looked for a double-extensioned, nonexistent
	 * file. Found and fixed when {@code RenderThaumcraftGolem} became the first real consumer of
	 * {@code GolemMaterial#texture}. */
	public static void init() {
		GolemMaterial.register(new GolemMaterial("WOOD", new String[] { "MATSTUDWOOD" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_wood"), 5059370, 6, 2, 1,
				new ItemStack(BlocksTC.plankGreatwood.get()), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.LIGHT }));
		GolemMaterial.register(new GolemMaterial("IRON", new String[] { "MATSTUDIRON" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_iron"), 16777215, 20, 8, 3,
				new ItemStack(ItemsTC.plateIron.get()), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.HEAVY, EnumGolemTrait.FIREPROOF, EnumGolemTrait.BLASTPROOF }));
		GolemMaterial.register(new GolemMaterial("CLAY", new String[] { "MATSTUDCLAY" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_clay"), 13071447, 10, 4, 2,
				new ItemStack(Blocks.TERRACOTTA), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.FIREPROOF }));
		GolemMaterial.register(new GolemMaterial("BRASS", new String[] { "MATSTUDBRASS" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_brass"), 15638812, 16, 6, 3,
				new ItemStack(ItemsTC.plateBrass.get()), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.LIGHT }));
		GolemMaterial.register(new GolemMaterial("THAUMIUM", new String[] { "MATSTUDTHAUMIUM" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_thaumium"), 5257074, 24, 10, 4,
				new ItemStack(ItemsTC.plateThaumium.get()), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.HEAVY, EnumGolemTrait.FIREPROOF, EnumGolemTrait.BLASTPROOF }));
		GolemMaterial.register(new GolemMaterial("VOID", new String[] { "MATSTUDVOID" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/mat_void"), 1445161, 20, 6, 4,
				new ItemStack(ItemsTC.plateVoid.get()), new ItemStack(ItemsTC.mechanismSimple.get()), new EnumGolemTrait[] { EnumGolemTrait.REPAIR }));

		GolemHead.register(new GolemHead("BASIC", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_basic.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_basic.obj"), null, PartModel.EnumAttachPoint.HEAD),
				new Object[] { new ItemStack(ItemsTC.mindClockwork.get()) }, new EnumGolemTrait[0]));
		GolemHead.register(new GolemHead("SMART", new String[] { "MINDBIOTHAUMIC" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smart.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_smart.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other"), PartModel.EnumAttachPoint.HEAD),
				new Object[] { new ItemStack(ItemsTC.mindBiothaumic.get()) }, new EnumGolemTrait[] { EnumGolemTrait.SMART, EnumGolemTrait.FRAGILE }));
		GolemHead.register(new GolemHead("SMART_ARMORED", new String[] { "MINDBIOTHAUMIC", "GOLEMCOMBATADV" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smartarmor.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_smart_armor.obj"), null, PartModel.EnumAttachPoint.HEAD),
				new Object[] { new ItemStack(ItemsTC.mindBiothaumic.get()), new ItemStack(ItemsTC.plateIron.get()), "base", new ItemStack(Blocks.WHITE_WOOL) }, new EnumGolemTrait[] { EnumGolemTrait.SMART }));
		GolemHead.register(new GolemHead("SCOUT", new String[] { "GOLEMVISION" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_scout.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_scout.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other"), PartModel.EnumAttachPoint.HEAD),
				new Object[] { new ItemStack(ItemsTC.mindClockwork.get()), new ItemStack(ItemsTC.moduleVision.get()) }, new EnumGolemTrait[] { EnumGolemTrait.SCOUT, EnumGolemTrait.FRAGILE }));
		GolemHead.register(new GolemHead("SMART_SCOUT", new String[] { "GOLEMVISION", "MINDBIOTHAUMIC" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/head_smartscout.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_head_scout_smart.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_head_other"), PartModel.EnumAttachPoint.HEAD),
				new Object[] { new ItemStack(ItemsTC.mindBiothaumic.get()), new ItemStack(ItemsTC.moduleVision.get()) }, new EnumGolemTrait[] { EnumGolemTrait.SCOUT, EnumGolemTrait.SMART, EnumGolemTrait.FRAGILE }));

		GolemArm.register(new GolemArm("BASIC", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_basic.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_basic.obj"), null, PartModel.EnumAttachPoint.ARMS), new Object[0], new EnumGolemTrait[0]));
		GolemArm.register(new GolemArm("FINE", new String[] { "MATSTUDBRASS" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_fine.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_fine.obj"), null, PartModel.EnumAttachPoint.ARMS),
				new Object[] { new ItemStack(ItemsTC.mechanismSimple.get()), "base" }, new EnumGolemTrait[] { EnumGolemTrait.DEFT, EnumGolemTrait.FRAGILE }));
		GolemArm.register(new GolemArm("CLAWS", new String[] { "GOLEMCOMBATADV" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_claws.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_claws.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_claws"), PartModel.EnumAttachPoint.ARMS),
				new Object[] { new ItemStack(ItemsTC.moduleAggression.get()), new ItemStack(Items.SHEARS, 2), "base" }, new EnumGolemTrait[] { EnumGolemTrait.FIGHTER, EnumGolemTrait.CLUMSY, EnumGolemTrait.BRUTAL }));
		GolemArm.register(new GolemArm("BREAKERS", new String[] { "GOLEMBREAKER" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_breakers.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_breakers.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_breakers"), PartModel.EnumAttachPoint.ARMS),
				new Object[] { new ItemStack(Items.DIAMOND, 2), "base", new ItemStack(Blocks.PISTON, 2) }, new EnumGolemTrait[] { EnumGolemTrait.BREAKER, EnumGolemTrait.CLUMSY, EnumGolemTrait.BRUTAL }));
		GolemArm.register(new GolemArm("DARTS", new String[] { "GOLEMCOMBATADV" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/arms_darts.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_arms_darter.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_arms_darter"), PartModel.EnumAttachPoint.ARMS),
				new Object[] { new ItemStack(ItemsTC.moduleAggression.get()), new ItemStack(Blocks.DISPENSER, 2), new ItemStack(Items.ARROW, 32), "mech" },
				new thaumcraft.common.golems.parts.GolemArmDart(),
				new EnumGolemTrait[] { EnumGolemTrait.FIGHTER, EnumGolemTrait.CLUMSY, EnumGolemTrait.RANGED, EnumGolemTrait.FRAGILE }));

		GolemLeg.register(new GolemLeg("WALKER", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_walker.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_walker.obj"), null, PartModel.EnumAttachPoint.LEGS), new Object[] { "base", "mech" }, new EnumGolemTrait[0]));
		GolemLeg.register(new GolemLeg("ROLLER", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_roller.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_wheel.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_legs_wheel"), PartModel.EnumAttachPoint.BODY),
				new Object[] { new ItemStack(Items.BOWL, 2), new ItemStack(Items.LEATHER), "mech" }, new thaumcraft.common.golems.parts.GolemLegWheels(), new EnumGolemTrait[] { EnumGolemTrait.WHEELED }));
		GolemLeg.register(new GolemLeg("CLIMBER", new String[] { "GOLEMCLIMBER" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_climber.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_climber.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/base_metal"), PartModel.EnumAttachPoint.LEGS),
				new Object[] { new ItemStack(Items.FLINT, 4), "base", "mech", "mech" }, new EnumGolemTrait[] { EnumGolemTrait.CLIMBER }));
		GolemLeg.register(new GolemLeg("FLYER", new String[] { "GOLEMFLYER" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/legs_flyer.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_legs_floater.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_legs_floater"), PartModel.EnumAttachPoint.BODY),
				new Object[] { new ItemStack(BlocksTC.levitator.get()), new ItemStack(ItemsTC.plateIron.get(), 4), new ItemStack(Items.SLIME_BALL), "mech" }, new thaumcraft.common.golems.parts.GolemLegLevitator(), new EnumGolemTrait[] { EnumGolemTrait.FLYER, EnumGolemTrait.FRAGILE }));

		GolemAddon.register(new GolemAddon("NONE", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/blocks/blank"), null, new Object[0], new EnumGolemTrait[0]));
		GolemAddon.register(new GolemAddon("ARMORED", new String[] { "GOLEMCOMBATADV" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_armored.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_armor.obj"), null, PartModel.EnumAttachPoint.BODY),
				new Object[] { "base", "base", "base", "base" }, new EnumGolemTrait[] { EnumGolemTrait.ARMORED, EnumGolemTrait.HEAVY }));
		GolemAddon.register(new GolemAddon("FIGHTER", new String[] { "SEALGUARD" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_fighter.png"), null,
				new Object[] { new ItemStack(ItemsTC.moduleAggression.get()), "mech" }, new EnumGolemTrait[] { EnumGolemTrait.FIGHTER }));
		GolemAddon.register(new GolemAddon("HAULER", new String[] { "MINDCLOCKWORK" }, ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/misc/golem/addon_hauler.png"),
				new PartModel(ResourceLocation.fromNamespaceAndPath("thaumcraft", "models/obj/golem_hauler.obj"), ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/golems/golem_hauler"), PartModel.EnumAttachPoint.BODY),
				new Object[] { new ItemStack(Items.LEATHER), new ItemStack(Items.CHEST) }, new EnumGolemTrait[] { EnumGolemTrait.HAULER }));
	}
}
