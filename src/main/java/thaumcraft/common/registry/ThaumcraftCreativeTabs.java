package thaumcraft.common.registry;

import java.util.Set;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.golems.parts.GolemArm;
import thaumcraft.api.golems.parts.GolemHead;
import thaumcraft.api.golems.parts.GolemMaterial;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.items.ThaumcraftDataComponents;
import thaumcraft.common.golems.GolemProperties;
import thaumcraft.common.items.consumables.ItemPhial;

/**
 * The mod's single creative tab - the port's replacement for the original's {@code CreativeTabThaumcraft}
 * (icon: Goggles of Revealing, same as the original). Added 2026-09-25 after the first real playtest found the
 * port had no tab at all (everything had to be {@code /give}n).
 *
 * <p>The original populated its tab through per-item {@code getSubItems} overrides; modern items have no
 * metadata subtypes, so this class lists every registered Thaumcraft item once and then adds the variants
 * those overrides used to generate: one stack per aspect for crystal essences and phials (plus an empty
 * phial), and the original's 4 golem-placer presets. Items the original deliberately kept out of its tab
 * ({@code setCreativeTab(null)}: hole, barrier, the golem builder, thaumatorium, infernal furnace, the
 * placeholder blocks) or that are only test scaffolding ({@code test_} prefix) are skipped.
 *
 * <p>The Cheater's Thaumonomicon is listed only when {@code allowCheatSheet} is on, as in the original. Still not
 * The elemental tools and the Primal Crusher are listed pre-infused, as the original's getSubItems did.
 */
public final class ThaumcraftCreativeTabs {

	public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, Thaumcraft.MODID);

	/** Original tab-less blocks ({@code setCreativeTab(null)}) - their block items stay obtainable by command. */
	private static final Set<String> HIDDEN = Set.of("hole", "barrier", "golem_builder", "thaumatorium", "infernal_furnace");

	public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN = TABS.register("thaumcraft",
			() -> CreativeModeTab.builder()
					.title(Component.translatable("itemGroup.thaumcraft"))
					.icon(() -> new ItemStack(ItemsTC.goggles.get()))
					.displayItems((parameters, output) -> {
						for (var holder : ItemsTC.ITEMS.getEntries()) {
							Item item = holder.get();
							if (isHidden(holder.getId())) continue;
							if (item == ItemsTC.thaumonomiconCheat.get() && !thaumcraft.common.config.ThaumcraftConfig.ALLOW_CHEAT_SHEET.get()) continue;
							if (item instanceof ItemGenericEssentiaContainer container) {
								container.createAspectVariants().forEach(output::accept);
							} else if (item instanceof ItemPhial) {
								output.accept(new ItemStack(item));
								for (Aspect aspect : Aspect.aspects.values()) {
									output.accept(ItemPhial.makeFilledPhial(aspect));
								}
							} else if (item == ItemsTC.golemPlacer.get()) {
								golemPlacerPresets().forEach(output::accept);
							} else if (preInfused(item) != null) {
								// as the original's getSubItems: only the pre-infused copy, as its infusion recipe makes it
								output.accept(preInfused(item));
							} else {
								output.accept(item);
							}
						}
					})
					.build());

	private ThaumcraftCreativeTabs() {
	}

	/** The original's tab copies (and what their infusion recipes produce). The hoe has none. */
	private static final java.util.Map<Item, java.util.Map<String, Integer>> PRE_INFUSED = new java.util.IdentityHashMap<>();

	private static ItemStack preInfused(Item item) {
		if (PRE_INFUSED.isEmpty()) fillPreInfused();
		java.util.Map<String, Integer> levels = PRE_INFUSED.get(item);
		if (levels == null) return null;
		ItemStack stack = new ItemStack(item);
		stack.set(ThaumcraftDataComponents.INFUSION_ENCHANTMENTS.get(), levels);
		return stack;
	}

	private static void fillPreInfused() {
		PRE_INFUSED.put(ItemsTC.elementalAxe.get(), java.util.Map.of("BURROWING", 1, "COLLECTOR", 1));
		PRE_INFUSED.put(ItemsTC.elementalPick.get(), java.util.Map.of("REFINING", 1, "SOUNDING", 2));
		PRE_INFUSED.put(ItemsTC.elementalSword.get(), java.util.Map.of("ARCING", 2));
		PRE_INFUSED.put(ItemsTC.elementalShovel.get(), java.util.Map.of("DESTRUCTIVE", 1));
		PRE_INFUSED.put(ItemsTC.primalCrusher.get(), java.util.Map.of("DESTRUCTIVE", 1, "REFINING", 1));
	}

	private static boolean isHidden(ResourceLocation id) {
		String path = id.getPath();
		return HIDDEN.contains(path) || path.startsWith("placeholder_") || path.startsWith("test_");
	}

	/** The original's 4 creative-tab golem placers: a bare (all-zero properties) one, then three preset
	 * builds - basic head/arms, a second material with basic head and the next arms, and a fourth material
	 * with the third arms. */
	private static java.util.List<ItemStack> golemPlacerPresets() {
		java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
		stacks.add(placer(0L));

		GolemProperties props = new GolemProperties();
		props.setHead(GolemHead.getHeads()[1]);
		props.setArms(GolemArm.getArms()[1]);
		stacks.add(placer(props.toLong()));

		props = new GolemProperties();
		props.setMaterial(GolemMaterial.getMaterials()[1]);
		props.setHead(GolemHead.getHeads()[1]);
		props.setArms(GolemArm.getArms()[2]);
		stacks.add(placer(props.toLong()));

		props = new GolemProperties();
		props.setMaterial(GolemMaterial.getMaterials()[4]);
		props.setHead(GolemHead.getHeads()[1]);
		props.setArms(GolemArm.getArms()[3]);
		stacks.add(placer(props.toLong()));
		return stacks;
	}

	private static ItemStack placer(long props) {
		ItemStack stack = new ItemStack(ItemsTC.golemPlacer.get());
		stack.set(ThaumcraftDataComponents.GOLEM_PLACER_DATA.get(), new ThaumcraftDataComponents.GolemPlacerData(props, 0));
		return stack;
	}
}
