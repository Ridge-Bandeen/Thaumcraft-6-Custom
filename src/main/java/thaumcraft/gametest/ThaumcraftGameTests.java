package thaumcraft.gametest;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import net.minecraft.world.effect.MobEffects;
import thaumcraft.common.items.consumables.ItemPhial;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.casters.FocusEngine;
import thaumcraft.api.casters.FocusPackage;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.items.ItemGenericEssentiaContainer;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.common.container.ArcaneWorkbenchMenu;
import thaumcraft.common.items.casters.ItemFocus;
import thaumcraft.common.tiles.crafting.TileArcaneWorkbench;
import thaumcraft.common.tiles.crafting.TileCrucible;
import thaumcraft.common.world.aura.AuraHandler;

import java.util.List;

import java.util.Optional;

/**
 * First real use of NeoForge's GameTest harness in this port - replaces "boot a dedicated server and grep
 * its log for a recipe count" as the way to verify actual game logic, not just that nothing crashed at
 * startup. Registered explicitly via {@code Thaumcraft#onRegisterGameTests} rather than relying on
 * {@code @GameTestHolder} auto-discovery, so there's one obvious place that lists what test classes exist.
 *
 * Run with {@code ./gradlew runGameTestServer} (see build.gradle's {@code gameTestServer} run - it refuses
 * to start at all with zero registered tests, which is why this project relied on full {@code runServer}
 * boots until now). Every test uses {@code data/thaumcraft/structure/empty.nbt}, a hand-built 1x1x1
 * all-air structure (GameTest requires *some* structure to exist even when a test never touches a block -
 * there's no built-in placeholder) - these tests are pure logic checks that happen to run inside a game
 * test world, not world-interaction tests.
 *
 * {@code @PrefixGameTestTemplate(false)} at the class level: without it, NeoForge silently prefixes
 * every {@code template()} with the class's simple name (e.g. "empty" becomes "thaumcraftgametests.empty"),
 * which doesn't match the actual structure resource name and fails every test with a "Missing test
 * structure" crash before any test logic runs - found by actually running
 * {@code ./gradlew runGameTestServer} and reading the crash, not by guessing.
 */
@PrefixGameTestTemplate(false)
public class ThaumcraftGameTests {

	/**
	 * A real, registered item for GameTest use only - constructing a bare {@code new Item(...)} at test
	 * time throws ("Registry is already frozen"), since 1.21.1 freezes registries after the normal mod
	 * loading pass finishes, unlike 1.12.2. Registered the same way every other item in this project is
	 * (a {@code DeferredRegister}, wired to the mod bus from {@code Thaumcraft}'s constructor via
	 * {@link #register}), just never given a lang entry or added to a creative tab, since it exists purely
	 * to exercise {@code TileRechargePedestal} against a real {@code IRechargable} implementation without
	 * needing a full production item (none are registered yet - see that tile's own doc comment).
	 */
	private static final net.neoforged.neoforge.registries.DeferredRegister.Items TEST_ITEMS =
			net.neoforged.neoforge.registries.DeferredRegister.createItems(Thaumcraft.MODID);

	public static final net.neoforged.neoforge.registries.DeferredItem<TestRechargableItem> TEST_RECHARGABLE_ITEM =
			TEST_ITEMS.registerItem("test_rechargable_item", TestRechargableItem::new);

	/**
	 * A minimal, real registered {@code IEnergyStorage}-exposing block+tile, test-only - lets
	 * {@code visGeneratorRechargesFromAuraAndPushesEnergyToANeighbor} exercise a real cross-block capability
	 * push (the actual mechanism {@code TileVisGenerator} uses in production) without needing a real
	 * production energy-consuming machine, none of which are registered yet.
	 */
	private static final net.neoforged.neoforge.registries.DeferredRegister.Blocks TEST_BLOCKS =
			net.neoforged.neoforge.registries.DeferredRegister.createBlocks(Thaumcraft.MODID);

	private static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.level.block.entity.BlockEntityType<?>> TEST_BLOCK_ENTITIES =
			net.neoforged.neoforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.BLOCK_ENTITY_TYPE, Thaumcraft.MODID);

	public static final net.neoforged.neoforge.registries.DeferredBlock<TestEnergySinkBlock> TEST_ENERGY_SINK_BLOCK =
			TEST_BLOCKS.registerBlock("test_energy_sink", TestEnergySinkBlock::new,
					net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());

	public static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<TestEnergySinkBlockEntity>> TEST_ENERGY_SINK_BE =
			TEST_BLOCK_ENTITIES.register("test_energy_sink",
					() -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TestEnergySinkBlockEntity::new, TEST_ENERGY_SINK_BLOCK.get()).build(null));

	/**
	 * A minimal, real registered {@code IEssentiaTransport}-implementing block+tile, test-only - lets
	 * {@code jarSelfFillsFromARealEssentiaSourceAbove} exercise {@code TileJarFillable}'s real self-fill
	 * tick logic against a realistic emitter. Found while writing that test: a real jar's own
	 * {@code isConnectable}/{@code canOutputTo} only ever return true for its own {@code UP} face (matching
	 * the original design exactly) - meaning two plain jars stacked on each other can never actually connect
	 * (both only expose their top), the same way the original always needed a tube as a relay for this case.
	 * This test double exposes {@code DOWN} instead, standing in for "some real device or tube outputting
	 * downward", which is what {@link thaumcraft.common.tiles.essentia.TileJarFillable#tick} is actually
	 * designed to receive from.
	 */
	private static final net.neoforged.neoforge.registries.DeferredBlock<TestEssentiaSourceBlock> TEST_ESSENTIA_SOURCE_BLOCK =
			TEST_BLOCKS.registerBlock("test_essentia_source", TestEssentiaSourceBlock::new,
					net.minecraft.world.level.block.state.BlockBehaviour.Properties.of());

	private static final net.neoforged.neoforge.registries.DeferredHolder<net.minecraft.world.level.block.entity.BlockEntityType<?>, net.minecraft.world.level.block.entity.BlockEntityType<TestEssentiaSourceBlockEntity>> TEST_ESSENTIA_SOURCE_BE =
			TEST_BLOCK_ENTITIES.register("test_essentia_source",
					() -> net.minecraft.world.level.block.entity.BlockEntityType.Builder.of(TestEssentiaSourceBlockEntity::new, TEST_ESSENTIA_SOURCE_BLOCK.get()).build(null));

	public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
		TEST_ITEMS.register(modEventBus);
		TEST_BLOCKS.register(modEventBus);
		TEST_BLOCK_ENTITIES.register(modEventBus);
	}

	/** See {@link #registerTestCapabilities} - called from {@code Thaumcraft#registerCapabilities} alongside
	 * the real production capability wiring. */
	public static void registerTestCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent event) {
		event.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, TEST_ENERGY_SINK_BE.get(),
				(tile, side) -> tile);
	}

	private static final class TestEnergySinkBlock extends net.minecraft.world.level.block.Block implements net.minecraft.world.level.block.EntityBlock {
		private static final com.mojang.serialization.MapCodec<TestEnergySinkBlock> CODEC = simpleCodec(TestEnergySinkBlock::new);

		TestEnergySinkBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
			return CODEC;
		}

		@javax.annotation.Nullable
		@Override
		public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
			return new TestEnergySinkBlockEntity(pos, state);
		}
	}

	private static final class TestEnergySinkBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements net.neoforged.neoforge.energy.IEnergyStorage {
		private int energy;

		TestEnergySinkBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
			super(TEST_ENERGY_SINK_BE.get(), pos, state);
		}

		@Override
		public int receiveEnergy(int toReceive, boolean simulate) {
			if (!simulate) {
				energy += toReceive;
			}
			return toReceive;
		}

		@Override
		public int extractEnergy(int toExtract, boolean simulate) {
			return 0;
		}

		@Override
		public int getEnergyStored() {
			return energy;
		}

		@Override
		public int getMaxEnergyStored() {
			return Integer.MAX_VALUE;
		}

		@Override
		public boolean canExtract() {
			return false;
		}

		@Override
		public boolean canReceive() {
			return true;
		}
	}

	private static final class TestEssentiaSourceBlock extends net.minecraft.world.level.block.Block implements net.minecraft.world.level.block.EntityBlock {
		private static final com.mojang.serialization.MapCodec<TestEssentiaSourceBlock> CODEC = simpleCodec(TestEssentiaSourceBlock::new);

		TestEssentiaSourceBlock(net.minecraft.world.level.block.state.BlockBehaviour.Properties properties) {
			super(properties);
		}

		@Override
		protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.Block> codec() {
			return CODEC;
		}

		@javax.annotation.Nullable
		@Override
		public net.minecraft.world.level.block.entity.BlockEntity newBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
			return new TestEssentiaSourceBlockEntity(pos, state);
		}
	}

	private static final class TestEssentiaSourceBlockEntity extends net.minecraft.world.level.block.entity.BlockEntity implements thaumcraft.api.aspects.IEssentiaTransport {
		Aspect aspect;
		int amount;
		/** Defaults to DOWN (the original "stands in for a device/tube outputting downward" use case) but
		 * settable per-test since some devices - e.g. TileCondenser's fill(), which only ever checks
		 * horizontal neighbors - need a source exposed on a different face. */
		net.minecraft.core.Direction outputFace = net.minecraft.core.Direction.DOWN;

		TestEssentiaSourceBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
			super(TEST_ESSENTIA_SOURCE_BE.get(), pos, state);
		}

		@Override
		public boolean isConnectable(net.minecraft.core.Direction face) {
			return face == outputFace;
		}

		@Override
		public boolean canInputFrom(net.minecraft.core.Direction face) {
			return false;
		}

		@Override
		public boolean canOutputTo(net.minecraft.core.Direction face) {
			return face == outputFace;
		}

		@Override
		public void setSuction(Aspect aspect, int amount) {
		}

		@Override
		public Aspect getSuctionType(net.minecraft.core.Direction face) {
			return null;
		}

		@Override
		public int getSuctionAmount(net.minecraft.core.Direction face) {
			return 0;
		}

		@Override
		public int takeEssentia(Aspect tag, int toTake, net.minecraft.core.Direction face) {
			if (!canOutputTo(face) || tag != aspect || amount < toTake) return 0;
			amount -= toTake;
			return toTake;
		}

		@Override
		public int addEssentia(Aspect tag, int toAdd, net.minecraft.core.Direction face) {
			return 0;
		}

		@Override
		public Aspect getEssentiaType(net.minecraft.core.Direction face) {
			return aspect;
		}

		@Override
		public int getEssentiaAmount(net.minecraft.core.Direction face) {
			return amount;
		}

		@Override
		public int getMinimumSuction() {
			return 0;
		}
	}

	private static final class TestRechargableItem extends net.minecraft.world.item.Item implements thaumcraft.api.items.IRechargable {
		TestRechargableItem(Properties properties) {
			super(properties);
		}

		@Override
		public int getMaxCharge(ItemStack stack, net.minecraft.world.entity.LivingEntity player) {
			return 1000;
		}

		@Override
		public EnumChargeDisplay showInHud(ItemStack stack, net.minecraft.world.entity.LivingEntity player) {
			return EnumChargeDisplay.NEVER;
		}
	}

	/**
	 * Regression coverage for the add/reduce arithmetic {@code TileCrucible} and the crucible recipe
	 * matcher build on.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void aspectListArithmetic(GameTestHelper helper) {
		AspectList list = new AspectList();
		list.add(Aspect.FIRE, 5);
		list.add(Aspect.FIRE, 3);
		helper.assertTrue(list.getAmount(Aspect.FIRE) == 8, "expected 8 fire aspect after two adds, got " + list.getAmount(Aspect.FIRE));

		helper.assertTrue(list.reduce(Aspect.FIRE, 3), "reduce should succeed when enough of the aspect is present");
		helper.assertTrue(list.getAmount(Aspect.FIRE) == 5, "expected 5 fire aspect after reducing by 3, got " + list.getAmount(Aspect.FIRE));

		helper.assertFalse(list.reduce(Aspect.FIRE, 100), "reduce should fail (and not mutate) when there isn't enough of the aspect");
		helper.assertTrue(list.getAmount(Aspect.FIRE) == 5, "a failed reduce should not have changed the amount");

		helper.succeed();
	}

	/**
	 * Regression coverage for the real bug caught while porting {@code ThaumcraftCapabilities.knowsResearch}
	 * (see the port-tracking notes): {@code .split("||")} is an invalid regex, and the OR-group handling
	 * originally short-circuited the entire varargs call instead of just that one entry. Exercises both the
	 * "||" (any-of) and "&&" (all-of) combinators directly against a mock player's real attached
	 * {@link IPlayerKnowledge}, not just the isolated string-parsing logic.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchOrAndCombinators(GameTestHelper helper) {
		// Deprecated-for-removal in this MC version but still the only helper that hands back a real,
		// server-side-registered ServerPlayer (needed for Entity#getData/attachments to behave normally) -
		// fine to keep using until it's actually gone; revisit if/when it is.
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		knowledge.clear();
		knowledge.addResearch("KNOWNA");
		knowledge.addResearch("KNOWNB");

		helper.assertTrue(ThaumcraftCapabilities.knowsResearch(player, "KNOWNA"), "should know a research entry that was just added");
		helper.assertFalse(ThaumcraftCapabilities.knowsResearch(player, "UNKNOWNC"), "should not know a research entry that was never added");

		// OR: true as long as at least one side is known.
		helper.assertTrue(ThaumcraftCapabilities.knowsResearch(player, "UNKNOWNC||KNOWNA"), "OR should be true when the second entry is known");
		helper.assertTrue(ThaumcraftCapabilities.knowsResearch(player, "KNOWNA||UNKNOWNC"), "OR should be true when the first entry is known");
		helper.assertFalse(ThaumcraftCapabilities.knowsResearch(player, "UNKNOWNC||UNKNOWND"), "OR should be false when neither side is known");

		// AND: true only when every side is known.
		helper.assertTrue(ThaumcraftCapabilities.knowsResearch(player, "KNOWNA&&KNOWNB"), "AND should be true when both entries are known");
		helper.assertFalse(ThaumcraftCapabilities.knowsResearch(player, "KNOWNA&&UNKNOWNC"), "AND should be false when only one entry is known");

		helper.succeed();
	}

	/**
	 * Names every focus/caster/misc recipe this project has added and asserts each one is actually
	 * registered - a direct replacement for eyeballing a "RecipeManager: Loaded N recipes" count in a
	 * runServer log, which only ever proved the total changed by the right number, never which recipes
	 * exist. A missing or misspelled recipe id now fails with the exact id that's missing.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = {
				"focus_1", "focus_1_crucible",
				"focus_frost", "focus_frost_crucible",
				"focus_break", "focus_break_crucible",
				"focus_fire_bolt", "focus_fire_bolt_crucible",
				"focus_heal", "focus_heal_crucible",
				"focus_curse", "focus_curse_crucible",
				"focus_earth", "focus_earth_crucible",
				"focus_fireball", "focus_fireball_crucible",
				"focus_flux", "focus_flux_crucible",
				"focus_air", "focus_air_crucible",
				"focus_trap", "focus_trap_crucible",
				"focus_sanctuary", "focus_sanctuary_crucible",
				"caster_basic", "thaumometer", "quicksilver_smelting", "mirrored_glass", "vis_resonator", "alumentum",
				"recharge_pedestal", "hungry_chest", "salis_mundus", "lamp_arcane",
				"amber_block", "amber_block_to_amber", "amber_brick", "amber_brick_to_block",
				"crystal_essence_aer", "crystal_essence_aqua", "crystal_essence_ignis",
				"crystal_essence_terra", "crystal_essence_ordo", "crystal_essence_perditio",
				"crystal_essence_auram", "crystal_essence_praecantatio", "crystal_essence_vitreus",
				"crystal_essence_vacuos", "crystal_essence_lux", "crystal_essence_motus",
				"crystal_essence_gelum", "crystal_essence_metallum", "crystal_essence_victus",
				"crystal_essence_mortuus", "crystal_essence_potentia", "crystal_essence_permutatio",
				"crystal_essence_alkimia", "crystal_essence_vitium", "crystal_essence_tenebrae",
				"crystal_essence_alienis", "crystal_essence_volatus", "crystal_essence_herba",
				"crystal_essence_instrumentum", "crystal_essence_machina", "crystal_essence_spiritus",
				"crystal_essence_fabrico",
				"crystal_essence_vinculum", "crystal_essence_cognitio", "crystal_essence_sensus",
				"crystal_essence_aversio", "crystal_essence_praemunio", "crystal_essence_desiderium",
				"crystal_essence_exanimis", "crystal_essence_bestia", "crystal_essence_humanus",
				"vis_generator", "everfull_urn",
				"stone_arcane", "stone_arcane_stairs", "stone_arcane_slab",
				"stone_arcane_brick", "stone_arcane_brick_stairs", "stone_arcane_brick_slab",
				"plank_silverwood", "silverwood_stairs", "silverwood_slab",
				"plank_greatwood", "greatwood_stairs", "greatwood_slab",
				"arcane_ear", "arcane_ear_toggle",
				"jar_normal",
				"lamp_growth", "lamp_fertility",
				"tube",
				"ingot_thaumium", "nugget_thaumium", "ingot_thaumium_from_nuggets",
				"metal_block_thaumium", "ingot_thaumium_from_block", "plate_thaumium",
				"thaumium_pick", "thaumium_axe", "thaumium_shovel", "thaumium_hoe", "thaumium_sword",
				"thaumium_helm", "thaumium_chest", "thaumium_legs", "thaumium_boots",
				"vis_battery", "dioptra",
				"filter", "condenser", "condenser_lattice",
				"paving_stone_travel",
				"tallow", "candle_white", "candle_black", "candle_blue", "candle_brown", "candle_cyan",
				"candle_gray", "candle_green", "candle_lightblue", "candle_lime", "candle_magenta",
				"candle_orange", "candle_pink", "candle_purple", "candle_red", "candle_silver", "candle_yellow",
				"phial", "label",
				"flesh_block", "rotten_flesh_from_block", "sanity_soap",
				"arcane_workbench_charger", "tube_filter", "tube_restrict"
		};
		// loot_bag_common/uncommon/rare have no crafting recipe (mob-drop only, matching the original) so
		// they aren't in expectedIds above - their loot tables are exercised directly instead, see
		// lootBagOpensAndSpillsRealLoot / zombieOrOtherMonsterKillsSometimesDropALootBag.
		for (String id : expectedIds) {
			ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id);
			Optional<RecipeHolder<?>> found = recipes.byKey(loc);
			helper.assertTrue(found.isPresent(), "expected recipe " + loc + " to be registered, but it was missing");
		}
		helper.succeed();
	}

	/**
	 * Direct coverage of {@link CrucibleRecipe#matches(AspectList, ItemStack)}'s superset-based aspect
	 * matching and catalyst check - the logic every one of this project's crucible recipes (the parity
	 * pass across all six foci) depends on, and the kind of edge case a boot-and-grep check would never
	 * catch since a wrong catalyst or an aspect threshold typo still "loads" fine.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleRecipeMatching(GameTestHelper helper) {
		AspectList required = new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 3);
		Ingredient catalyst = Ingredient.of(Items.QUARTZ);
		CrucibleRecipe recipe = new CrucibleRecipe(Thaumcraft.MODID, "FIRSTSTEPS", new ItemStack(Items.STICK), catalyst, required);

		AspectList exact = new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 3);
		helper.assertTrue(recipe.matches(exact, new ItemStack(Items.QUARTZ)),
				"should match when the pool has exactly the required aspects and the right catalyst");

		AspectList superset = new AspectList().add(Aspect.CRYSTAL, 10).add(Aspect.MAGIC, 10).add(Aspect.EARTH, 4);
		helper.assertTrue(recipe.matches(superset, new ItemStack(Items.QUARTZ)),
				"should match when the pool has more than enough of every required aspect, plus unrelated ones");

		AspectList insufficient = new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 2);
		helper.assertFalse(recipe.matches(insufficient, new ItemStack(Items.QUARTZ)),
				"should not match when one required aspect falls short, even if others are satisfied");

		helper.assertFalse(recipe.matches(exact, new ItemStack(Items.COBBLESTONE)),
				"should not match with the right aspects but the wrong catalyst item");

		helper.succeed();
	}

	/**
	 * Coverage for {@link AuraHandler}'s add/drain API - the real per-chunk vis simulation
	 * {@code ItemCaster}/{@code ArcaneWorkbenchMenu}/{@code RechargeHelper} all draw from, replacing the
	 * fixed-pool/no-check stand-ins those had before the aura system was ported. Needs a real
	 * {@code ServerLevel} (for {@code Level#dimension()}, which {@code AuraHandler} keys its per-dimension
	 * state on) - not something a plain unit test can provide, hence GameTest rather than JUnit here.
	 * Each GameTest method's structure is placed far apart in the test world (confirmed in earlier boot
	 * logs, e.g. "2 tests are now running at position 3117146, -59, -10963976"), so different test methods
	 * never share an aura chunk even though this one only ever asserts inside a single method - no explicit
	 * position offsetting needed. Assumes the whole method runs well under the background
	 * {@code AuraThread}'s ~1-second regen tick, same as every other GameTest in this batch finishing in a
	 * few hundred milliseconds total - if this ever gets flaky, that assumption is the first thing to
	 * revisit.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void auraVisAddAndDrain(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);

		float base = AuraHandler.getVis(level, pos);

		AuraHandler.addVis(level, pos, 10.0f);
		helper.assertTrue(AuraHandler.getVis(level, pos) == base + 10.0f, "addVis should increase vis by exactly the given amount");

		float simulated = AuraHandler.drainVis(level, pos, 5.0f, true);
		helper.assertTrue(simulated == 5.0f, "simulate=true should report how much would be drained");
		helper.assertTrue(AuraHandler.getVis(level, pos) == base + 10.0f, "simulate=true must not actually mutate the vis");

		float drained = AuraHandler.drainVis(level, pos, 5.0f, false);
		helper.assertTrue(drained == 5.0f, "a real drain within the available amount should return exactly what was asked for");
		helper.assertTrue(AuraHandler.getVis(level, pos) == base + 5.0f, "a real drain should actually reduce the stored vis");

		float remaining = AuraHandler.getVis(level, pos);
		float overDrained = AuraHandler.drainVis(level, pos, remaining + 1000.0f, false);
		helper.assertTrue(overDrained == remaining, "draining more than is available should cap at what's actually there");
		helper.assertTrue(AuraHandler.getVis(level, pos) == 0.0f, "after draining everything, vis should be exactly 0, never negative");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void auraAddNegativeAmountIsRejected(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);

		float baseVis = AuraHandler.getVis(level, pos);
		AuraHandler.addVis(level, pos, -50.0f);
		helper.assertTrue(AuraHandler.getVis(level, pos) == baseVis, "addVis with a negative amount should be a no-op, not subtract");

		float baseFlux = AuraHandler.getFlux(level, pos);
		AuraHandler.addFlux(level, pos, -50.0f);
		helper.assertTrue(AuraHandler.getFlux(level, pos) == baseFlux, "addFlux with a negative amount should also be a no-op");

		helper.succeed();
	}

	/**
	 * Coverage for {@link AuraHandler#shouldPreserveAura} - gates {@code RechargeHelper}'s vis draining and
	 * (per its own doc comment) is meant to require the "AURAPRESERVE" research when a real player is
	 * involved, but bypass that check entirely when called with a null player.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void auraShouldPreserveAura(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);

		// Drain to (near) zero so vis/base is comfortably under the 10% threshold regardless of exactly
		// what the randomly-noised base turned out to be.
		AuraHandler.drainVis(level, pos, AuraHandler.getVis(level, pos), false);
		helper.assertTrue(AuraHandler.getVis(level, pos) == 0.0f, "draining all available vis first should leave exactly 0");

		helper.assertTrue(AuraHandler.shouldPreserveAura(level, null, pos),
				"low vis with no player to check research against should bypass the research check and preserve");

		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ThaumcraftCapabilities.getKnowledge(player).clear();
		helper.assertFalse(AuraHandler.shouldPreserveAura(level, player, pos),
				"a player without AURAPRESERVE should not trigger preservation even at low vis");

		ThaumcraftCapabilities.getKnowledge(player).addResearch("AURAPRESERVE");
		helper.assertTrue(AuraHandler.shouldPreserveAura(level, player, pos),
				"a player who knows AURAPRESERVE should trigger preservation at low vis");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// End-to-end wand casting: spawns a mock player and a zombie in front of them, then reproduces exactly
	// what ItemCaster#use does (focus.getPackage(stack) -> core.initialize(caster) ->
	// FocusEngine.castFocusPackage(caster, core, true)) rather than calling a FocusEffect's execute method
	// directly - this exercises the real medium-raytrace-to-effect pipeline (Root -> Touch -> effect), not
	// just the effect's own logic in isolation. Every prior GameTest/JUnit test for the foci this session
	// tested logic in isolation (CrucibleRecipe matching, aspect math); these are the first to prove the
	// whole cast chain actually connects a real caster to a real target - which, on the first attempt at
	// writing this, it didn't: see FocusPackage#initialize's doc comment for the real bug this test found
	// (getCaster() silently returning null down the whole cast path). Aiming uses Entity#lookAt to rotate
	// the mock player toward the target, same as a real player would turn to face something before casting.
	// --------------------------------------------------------------------------------------------------

	@SuppressWarnings("removal")
	private static ServerPlayer spawnCasterAt(GameTestHelper helper, BlockPos origin) {
		ServerPlayer caster = helper.makeMockServerPlayerInLevel();
		caster.setPos(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5);
		return caster;
	}

	private static Zombie spawnZombieFacedBy(GameTestHelper helper, ServerPlayer caster, BlockPos origin) {
		return spawnFacedBy(helper, caster, origin, EntityType.ZOMBIE);
	}

	/**
	 * Living-but-not-undead target, for the two effects that treat undead specially: Heal (heals the
	 * living, hurts the undead - a zombie here would test the wrong branch) and Curse's poison (vanilla
	 * itself makes undead mobs immune to poison, so a zombie would never show the effect regardless of
	 * whether the focus applied it correctly).
	 */
	private static Pig spawnPigFacedBy(GameTestHelper helper, ServerPlayer caster, BlockPos origin) {
		return spawnFacedBy(helper, caster, origin, EntityType.PIG);
	}

	private static Pig spawnPigFacedBy(GameTestHelper helper, ServerPlayer caster, BlockPos origin, double distanceZ) {
		return spawnFacedBy(helper, caster, origin, EntityType.PIG, distanceZ);
	}

	private static <T extends Mob> T spawnFacedBy(GameTestHelper helper, ServerPlayer caster, BlockPos origin, EntityType<T> type) {
		return spawnFacedBy(helper, caster, origin, type, 3.5);
	}

	private static <T extends Mob> T spawnFacedBy(GameTestHelper helper, ServerPlayer caster, BlockPos origin, EntityType<T> type, double distanceZ) {
		ServerLevel level = helper.getLevel();
		T mob = type.create(level);
		mob.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + distanceZ, 0, 0);
		level.addFreshEntity(mob);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, mob.getEyePosition());
		return mob;
	}

	private static void cast(ItemFocus focusItem, ServerPlayer caster) {
		ItemStack focusStack = new ItemStack(focusItem);
		FocusPackage core = focusItem.getPackage(focusStack);
		core.initialize(caster);
		FocusEngine.castFocusPackage(caster, core, true);
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFireFocusDamagesAndIgnitesTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Zombie zombie = spawnZombieFacedBy(helper, caster, origin);
		float startingHealth = zombie.getHealth();

		cast(ItemsTC.focus1.get(), caster);

		helper.assertTrue(zombie.getHealth() < startingHealth, "casting Fire at the zombie should have damaged it");
		helper.assertTrue(zombie.getRemainingFireTicks() > 0, "casting Fire at the zombie should have ignited it");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFrostFocusDamagesAndSlowsTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Zombie zombie = spawnZombieFacedBy(helper, caster, origin);
		float startingHealth = zombie.getHealth();

		cast(ItemsTC.focusFrost.get(), caster);

		helper.assertTrue(zombie.getHealth() < startingHealth, "casting Frost at the zombie should have damaged it");
		helper.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN),
				"casting Frost at the zombie should have applied a slowness effect");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castHealFocusHealsALivingTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin);
		pig.setHealth(1.0f);

		cast(ItemsTC.focusHeal.get(), caster);

		helper.assertTrue(pig.getHealth() > 1.0f, "casting Heal at a damaged living target should have healed it");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castHealFocusHurtsAnUndeadTarget(GameTestHelper helper) {
		// The other half of FocusEffectHeal's "heals the living, hurts the undead" design - a zombie is
		// exactly the case castHealFocusHealsALivingTarget deliberately avoids.
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Zombie zombie = spawnZombieFacedBy(helper, caster, origin);
		float startingHealth = zombie.getHealth();

		cast(ItemsTC.focusHeal.get(), caster);

		helper.assertTrue(zombie.getHealth() < startingHealth, "casting Heal at an undead target should have hurt it instead of healing it");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castCurseFocusDamagesAndAppliesPoison(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin);
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focusCurse.get(), caster);

		helper.assertTrue(pig.getHealth() < startingHealth, "casting Curse at the pig should have damaged it");
		helper.assertTrue(pig.hasEffect(net.minecraft.world.effect.MobEffects.POISON),
				"casting Curse at the pig should always apply poison (the other debuffs are only chance-based)");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// TileCrucible: places a real crucible block and drives it the way a player actually would (heat it,
	// throw items in via attemptSmelt), rather than only exercising CrucibleRecipe#matches in isolation
	// (crucibleRecipeMatching, above) - that covered the matching logic but never the tile itself:
	// dissolving an essentia item into the aspect pool, and a completed recipe actually ejecting its
	// result. GameTestHelper#setBlock/getBlockEntity take structure-relative positions (unlike entity
	// spawning above, which needs absolute world positions) - see their own doc comments in
	// GameTestHelper for why.
	// --------------------------------------------------------------------------------------------------

	private static TileCrucible placeActiveCrucible(GameTestHelper helper, BlockPos pos) {
		helper.setBlock(pos, BlocksTC.crucible.get());
		TileCrucible tile = helper.getBlockEntity(pos);
		tile.hasWater = true;
		tile.heat = 200; // above ACTIVE_HEAT (150) so isActive() is true without waiting on the tick-based heat-up
		return tile;
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleDissolvesEssentiaItemsIntoItsAspectPool(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);

		ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 3);
		((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FIRE, 1));

		ItemStack remainder = crucible.attemptSmelt(crystal, helper.getLevel());

		helper.assertTrue(crucible.getAspects().getAmount(Aspect.FIRE) == 1, "dissolving one essentia item should add exactly its aspect amount to the pool, got "
				+ crucible.getAspects().getAmount(Aspect.FIRE));
		helper.assertTrue(remainder.getCount() == 2, "attemptSmelt should shrink the input stack by 1 per dissolved item, got count " + remainder.getCount());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleWithoutHeatOrWaterDoesNotDissolveAnything(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, BlocksTC.crucible.get());
		TileCrucible crucible = helper.getBlockEntity(pos);
		// hasWater/heat left at their defaults (false/0) - isActive() should be false.

		ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 1);
		((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FIRE, 1));

		ItemStack remainder = crucible.attemptSmelt(crystal, helper.getLevel());

		helper.assertTrue(crucible.getAspects().size() == 0, "an inactive crucible (no heat/water) should not dissolve anything");
		helper.assertTrue(remainder.getCount() == 1, "an inactive crucible should return the item untouched");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesARecipeAndEjectsTheResult(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);

		// focus_1_crucible.json's requirement: quartz catalyst + vitreus 5 / praecantatio 3 / auram 2.
		// Setting the pool directly rather than dissolving essentia items one at a time for this test -
		// crucibleDissolvesEssentiaItemsIntoItsAspectPool already covers dissolving itself.
		crucible.setAspects(new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 3).add(Aspect.AURA, 2));

		ItemStack catalyst = new ItemStack(Items.QUARTZ);
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the catalyst item should have been fully consumed (shrunk from 1 to 0)");
		helper.assertTrue(crucible.getAspects().getAmount(Aspect.CRYSTAL) == 0, "a completed recipe should remove exactly the aspects it consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel()
				.getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundFocus = ejected.stream().anyMatch(e -> e.getItem().is(ItemsTC.focus1.get()));
		helper.assertTrue(foundFocus, "completing focus_1_crucible should have ejected a thaumcraft:focus_1 item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	/**
	 * Thaumium - the mod's own base metal tier - is obtained by dissolving a plain iron ingot in the
	 * Crucible (data/thaumcraft/recipe/ingot_thaumium.json: praecantatio 5 + terra 5). Real regression
	 * coverage for the material chain's actual root, not just the plain vanilla-crafting conversions
	 * (nugget/block/plate) layered on top of it.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesTheThaumiumIngotRecipe(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);
		crucible.setAspects(new AspectList().add(Aspect.MAGIC, 5).add(Aspect.EARTH, 5));

		ItemStack catalyst = new ItemStack(Items.IRON_INGOT);
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the iron ingot catalyst should have been fully consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundThaumium = ejected.stream().anyMatch(e -> e.getItem().is(ItemsTC.ingotThaumium.get()));
		helper.assertTrue(foundThaumium, "completing ingot_thaumium should have ejected a thaumcraft:ingot_thaumium item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	/**
	 * Thaumium tools (thaumium_pick/axe/shovel/hoe/sword) use a hand-built {@code Tier} (see
	 * {@code ItemsTC.TIER_THAUMIUM}) reproducing the original's {@code TOOLMAT_THAUMIUM} stats: harvest
	 * level 3 == diamond-equivalent, 500 uses, repairable with a Thaumium ingot. This checks the actual
	 * derived behaviour (durability, correct-tool-for-drops, repairability) rather than just that the item
	 * exists, since those are all computed by the vanilla Tier/DiggerItem/TieredItem machinery and a wiring
	 * mistake there wouldn't show up any other way.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumiumPickaxeHasCorrectTierBehaviour(GameTestHelper helper) {
		ItemStack pick = new ItemStack(ItemsTC.thaumiumPick.get());

		helper.assertTrue(pick.getMaxDamage() == 500, "thaumium_pick should have 500 uses (durability), found: " + pick.getMaxDamage());

		net.minecraft.world.level.block.state.BlockState obsidian = Blocks.OBSIDIAN.defaultBlockState();
		helper.assertTrue(pick.isCorrectToolForDrops(obsidian), "a diamond-equivalent pickaxe should be able to correctly mine obsidian");

		helper.assertTrue(pick.getDestroySpeed(obsidian) == 7.0F,
				"thaumium_pick's mining speed on a correct-tool block should be its 7.0 tier speed, found: " + pick.getDestroySpeed(obsidian));

		helper.assertTrue(pick.getItem().isValidRepairItem(pick, new ItemStack(ItemsTC.ingotThaumium.get())), "thaumium_pick should be repairable with a Thaumium ingot");
		helper.assertFalse(pick.getItem().isValidRepairItem(pick, new ItemStack(Items.IRON_INGOT)), "thaumium_pick should not be repairable with a plain iron ingot");

		helper.succeed();
	}

	/**
	 * Thaumium armor uses a real {@code BuiltInRegistries.ARMOR_MATERIAL} entry ({@code ThaumcraftArmorMaterials.THAUMIUM})
	 * reproducing {@code ARMORMAT_THAUMIUM}: protection {@code [2,5,6,2]} (boots/legs/chest/helm), durability
	 * multiplier 25, toughness 1.0, repairable with a Thaumium ingot. Checked on the chestplate (protection 6,
	 * the array's chest slot) since it's the least likely of the four to be copy-pasted correctly by accident.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumiumChestplateHasCorrectMaterialBehaviour(GameTestHelper helper) {
		ItemStack chest = new ItemStack(ItemsTC.thaumiumChest.get());

		helper.assertTrue(chest.getItem() instanceof net.minecraft.world.item.ArmorItem, "thaumium_chest should be a real ArmorItem");
		net.minecraft.world.item.ArmorItem armor = (net.minecraft.world.item.ArmorItem) chest.getItem();

		helper.assertTrue(armor.getDefense() == 6, "thaumium_chest's protection should be 6, found: " + armor.getDefense());
		helper.assertTrue(armor.getToughness() == 1.0F, "thaumium_chest's toughness should be 1.0, found: " + armor.getToughness());
		helper.assertTrue(chest.getMaxDamage() == 16 * 25,
				"thaumium_chest's durability should be CHESTPLATE.getDurability(25) == 400, found: " + chest.getMaxDamage());

		helper.assertTrue(armor.isValidRepairItem(chest, new ItemStack(ItemsTC.ingotThaumium.get())), "thaumium_chest should be repairable with a Thaumium ingot");
		helper.assertFalse(armor.isValidRepairItem(chest, new ItemStack(Items.IRON_INGOT)), "thaumium_chest should not be repairable with a plain iron ingot");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Focus: Excavation - casts at a real placed block rather than a target entity (a BlockHitResult path
	// through the exact same Touch-medium raytrace the entity-targeting tests exercise), covering
	// FocusEffectBreak#execute's actual early-return conditions (hardness-too-high, unbreakable) alongside
	// the successful-break/real-loot-drop case, not just the successful case alone.
	// --------------------------------------------------------------------------------------------------

	private static BlockPos placeTargetBlock(GameTestHelper helper, ServerPlayer caster, net.minecraft.world.level.block.Block block) {
		BlockPos relPos = new BlockPos(0, 1, 3);
		helper.setBlock(relPos, block);
		BlockPos absolute = helper.absolutePos(relPos);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(absolute));
		return relPos;
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castExcavationFocusBreaksAStoneBlockAndDropsRealLoot(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		BlockPos relPos = placeTargetBlock(helper, caster, Blocks.STONE);

		cast(ItemsTC.focusBreak.get(), caster);

		helper.assertTrue(helper.getBlockState(relPos).isAir(), "casting Excavation at a stone block (hardness 1.5, well under the default power's threshold) should have broken it");

		BlockPos absolute = helper.absolutePos(relPos);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundCobblestone = dropped.stream().anyMatch(e -> e.getItem().is(Blocks.COBBLESTONE.asItem()));
		helper.assertTrue(foundCobblestone, "breaking stone should have dropped real cobblestone via the block's actual loot table, found: "
				+ dropped.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castExcavationFocusRefusesABlockHarderThanItsPowerAllows(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		// Obsidian's hardness (50) is far past what the default power setting (1) allows (hardness > power
		// * 2.0f is rejected, i.e. anything over 2.0 here).
		BlockPos relPos = placeTargetBlock(helper, caster, Blocks.OBSIDIAN);

		cast(ItemsTC.focusBreak.get(), caster);

		helper.assertFalse(helper.getBlockState(relPos).isAir(), "casting Excavation at a block far harder than the focus's power should not have broken it");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castExcavationFocusNeverBreaksAnUnbreakableBlock(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		// Bedrock's hardness is -1 (unbreakable) - execute()'s first check (hardness < 0) rejects it
		// unconditionally, regardless of power.
		BlockPos relPos = placeTargetBlock(helper, caster, Blocks.BEDROCK);

		cast(ItemsTC.focusBreak.get(), caster);

		helper.assertFalse(helper.getBlockState(relPos).isAir(), "casting Excavation at bedrock should never break it, no matter the power setting");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// ArcaneWorkbenchMenu: places a real workbench block, wires up a real menu against it (not just the
	// recipe matching ArcaneShapedRecipeTest already covers as pure JUnit), and drives the vis/research
	// gating in updateResult() the same way a player opening the workbench would. Uses focus_1.json
	// (blaze powder + redstone, no crystal-ring requirement, research "FIRSTSTEPS", vis 15) since it's the
	// simplest real recipe on the books - no crystal-ring items needed to set up.
	// --------------------------------------------------------------------------------------------------

	@SuppressWarnings("removal")
	private static ArcaneWorkbenchMenu placeWorkbenchWithFocus1Ingredients(GameTestHelper helper, ServerPlayer player) {
		BlockPos relPos = new BlockPos(0, 0, 0);
		helper.setBlock(relPos, BlocksTC.arcaneWorkbench.get());
		TileArcaneWorkbench tile = helper.getBlockEntity(relPos);

		// focus_1.json's pattern: " B " / "BRB" / " B " over a 3x3 grid (indices 0-8, row-major).
		tile.setItem(1, new ItemStack(Items.BLAZE_POWDER));
		tile.setItem(3, new ItemStack(Items.BLAZE_POWDER));
		tile.setItem(4, new ItemStack(Items.REDSTONE));
		tile.setItem(5, new ItemStack(Items.BLAZE_POWDER));
		tile.setItem(7, new ItemStack(Items.BLAZE_POWDER));

		ArcaneWorkbenchMenu menu = new ArcaneWorkbenchMenu(0, player.getInventory(), tile);
		menu.slotsChanged(tile);
		return menu;
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void workbenchCraftsAMatchingRecipeAndConsumesVisAndIngredients(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		float visBefore = AuraHandler.getVis(helper.getLevel(), origin);

		ArcaneWorkbenchMenu menu = placeWorkbenchWithFocus1Ingredients(helper, player);

		ItemStack result = menu.slots.get(0).getItem();
		helper.assertTrue(result.is(ItemsTC.focus1.get()), "matching ingredients, enough vis, and known research should have produced a focus_1 in the result slot, got " + result);

		menu.slots.get(0).onTake(player, result);

		helper.assertTrue(menu.slots.get(1).getItem().isEmpty(), "taking the result should have consumed the grid ingredients");
		float visAfter = AuraHandler.getVis(helper.getLevel(), origin);
		helper.assertTrue(visAfter == visBefore - 15, "taking the result should have drained exactly the recipe's vis cost (15), before=" + visBefore + " after=" + visAfter);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void workbenchRefusesToCraftWithoutEnoughVis(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		// Drain the workbench's chunk down to less than focus_1's 15-vis cost.
		AuraHandler.drainVis(helper.getLevel(), origin, AuraHandler.getVis(helper.getLevel(), origin), false);
		AuraHandler.addVis(helper.getLevel(), origin, 5.0f);

		ArcaneWorkbenchMenu menu = placeWorkbenchWithFocus1Ingredients(helper, player);

		helper.assertTrue(menu.slots.get(0).getItem().isEmpty(), "matching ingredients with insufficient vis should not have produced a result");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void workbenchRefusesToCraftWithoutTheRequiredResearch(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		ThaumcraftCapabilities.getKnowledge(player).clear();

		ArcaneWorkbenchMenu menu = placeWorkbenchWithFocus1Ingredients(helper, player);

		helper.assertTrue(menu.slots.get(0).getItem().isEmpty(), "matching ingredients and enough vis with no known research should not have produced a result");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void workbenchChargerDrawsVisFromNeighbouringChunksToo(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		// Drain the workbench's own chunk below focus_1's 15-vis cost, same setup as the insufficient-vis
		// test above, but seed a real neighbouring chunk (16 blocks over) with plenty instead.
		AuraHandler.drainVis(helper.getLevel(), origin, AuraHandler.getVis(helper.getLevel(), origin), false);
		AuraHandler.addVis(helper.getLevel(), origin, 5.0f);
		AuraHandler.addVis(helper.getLevel(), origin.offset(16, 0, 0), 500.0f);

		helper.setBlock(BlockPos.ZERO.above(), BlocksTC.arcaneWorkbenchCharger.get());
		ArcaneWorkbenchMenu menu = placeWorkbenchWithFocus1Ingredients(helper, player);

		ItemStack result = menu.slots.get(0).getItem();
		helper.assertTrue(result.is(ItemsTC.focus1.get()),
				"a charger above the workbench should draw vis from neighbouring chunks too, even though the workbench's own chunk alone doesn't have enough, got " + result);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Focus: Fire Bolt - the wand's only non-Touch medium so far. Every entity-targeting test above uses
	// FocusMediumTouch's 5-block range; these confirm FocusMediumBolt's 16-block range is actually
	// different in practice, not just in the getRange() override - a target 6 blocks out is exactly the
	// kind of distance Touch is documented to miss and Bolt is documented to reach.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFireBoltReachesATargetBeyondTouchRange(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 6.5);
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focusFireBolt.get(), caster);

		helper.assertTrue(pig.getHealth() < startingHealth, "Fire Bolt's 16-block range should have reached a target 6 blocks away");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFireFocusMissesATargetBeyondTouchRange(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 6.5);
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focus1.get(), caster);

		helper.assertTrue(pig.getHealth() == startingHealth,
				"the plain Fire focus (Touch medium, 5-block range) should not have reached a target 6 blocks away - if this starts failing, Touch's range changed");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// BlockCrucible's own player-interaction entrypoints (useItemOn / entityInside) - real, previously-
	// untested code paths distinct from driving TileCrucible's methods directly the way the earlier
	// crucible tests do. BlockState#useItemOn is the public dispatch method vanilla itself calls on right-
	// click; the block's own override is protected, not reachable directly from this package.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleFillsAndEmptiesWithABucketOfWater(GameTestHelper helper) {
		BlockPos relPos = BlockPos.ZERO;
		helper.setBlock(relPos, BlocksTC.crucible.get());
		BlockPos absolute = helper.absolutePos(relPos);
		TileCrucible tile = helper.getBlockEntity(relPos);

		ServerPlayer player = spawnCasterAt(helper, absolute);
		// makeMockServerPlayerInLevel()'s isCreative()==true override propagates into a real
		// abilities.instabuild = true (creative players don't consume items) - forcing it back off tests
		// the survival-mode consumption path this is actually meant to verify, which is what a real
		// player normally experiences.
		player.getAbilities().instabuild = false;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.WATER_BUCKET));
		helper.getBlockState(relPos).useItemOn(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND), helper.getLevel(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		helper.assertTrue(tile.hasWater, "right-clicking with a water bucket should have filled the crucible");
		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.BUCKET), "the water bucket should have been swapped for an empty one");

		helper.getBlockState(relPos).useItemOn(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND), helper.getLevel(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		helper.assertTrue(!tile.hasWater, "right-clicking a watered, empty (no aspects) crucible with an empty bucket should have emptied it");
		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET), "the empty bucket should have been swapped back for a full one");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleAutoConsumesADroppedItemThatLandsInIt(GameTestHelper helper) {
		BlockPos relPos = BlockPos.ZERO;
		TileCrucible tile = placeActiveCrucible(helper, relPos);
		BlockPos absolute = helper.absolutePos(relPos);

		ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 1);
		((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.EARTH, 1));
		ItemEntity dropped = new ItemEntity(helper.getLevel(), absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5, crystal);
		helper.getLevel().addFreshEntity(dropped);

		// entityInside is driven by the entity's own physics tick noticing it's inside the crucible's
		// block space - GameTest can just tick the world forward and let vanilla call it naturally.
		helper.runAfterDelay(3, () -> {
			helper.assertTrue(tile.getAspects().getAmount(Aspect.EARTH) == 1, "a dropped essentia item resting inside an active crucible should have been auto-consumed into the aspect pool");
			helper.assertTrue(dropped.isRemoved(), "the consumed item entity should have been removed");
			helper.succeed();
		});
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castEarthFocusDamagesTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin);
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focusEarth.get(), caster);

		helper.assertTrue(pig.getHealth() < startingHealth, "casting Earth at the pig should have damaged it");
		helper.succeed();
	}

	/**
	 * Focus: Fireball - the wand's first entity-backed medium (see
	 * {@link thaumcraft.common.entities.projectile.EntityFocusProjectile}'s doc comment). Unlike every
	 * other cast test, the effect doesn't run synchronously with {@code cast()} - the projectile has to
	 * actually fly there first, so this polls every tick via {@code succeedWhen} until the pig actually
	 * takes damage or the test's own timeout (100 ticks by default) is hit, rather than guessing a fixed
	 * delay.
	 *
	 * Aims at the pig's bounding-box center rather than its eye position, and at a shorter distance (2.5
	 * blocks) than the other cast tests use - an interim diagnostic run showed the projectile actually
	 * flying straight past a pig aimed-at-eye-height at the usual 3.5-block distance without registering a
	 * hit. {@code ThrowableProjectile}'s own hit detection only inflates a target's hitbox by 0.3 blocks
	 * (vanilla's default, {@code ProjectileUtil#getHitResultOnMoveVector}), much tighter than
	 * {@code FocusMediumTouch}'s raytrace (1.0 block of search-box padding) - the same small aim offset
	 * that Touch shrugs off is enough margin for a real, fast-moving projectile to clip past. This isn't a
	 * bug worth chasing further: a real player firing an actual projectile at a barely-eye-level-aimed
	 * target can genuinely miss the same way.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFireballFocusFliesToAndBurnsATarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 2.5);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focusFireball.get(), caster);

		helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < startingHealth,
				"the fireball should have flown to the pig and burned it by now, health=" + pig.getHealth() + " (was " + startingHealth + ")"));
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castFluxFocusDamagesTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin);
		float startingHealth = pig.getHealth();

		cast(ItemsTC.focusFlux.get(), caster);

		helper.assertTrue(pig.getHealth() < startingHealth, "casting Flux at the pig should have damaged it");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castAirFocusDamagesAndKnocksBackTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin);
		float startingHealth = pig.getHealth();
		Vec3 startingVelocity = pig.getDeltaMovement();

		cast(ItemsTC.focusAir.get(), caster);

		helper.assertTrue(pig.getHealth() < startingHealth, "casting Air at the pig should have damaged it");
		helper.assertTrue(!pig.getDeltaMovement().equals(startingVelocity), "casting Air at the pig should have knocked it back");
		helper.succeed();
	}

	/**
	 * Focus: Trap - places a real EntityFocusMine (see its own doc comment) that arms on landing and
	 * curses the next matching entity that wanders within range. Aims point-blank at a placed block so the
	 * mine arms almost immediately, with the target already waiting right next to the landing spot -
	 * avoids needing to simulate anything actually wandering into range. Uses {@code succeedWhen} rather
	 * than a fixed delay, same reasoning as the Fireball test (the mine's own trigger check only runs
	 * every 5 ticks).
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castTrapFocusArmsAndCursesAnEnemyThatWandersNear(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(0, 1, 2);
		helper.setBlock(relPos, Blocks.STONE);
		BlockPos absolute = helper.absolutePos(relPos);

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(absolute));

		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(absolute.getX() + 0.5, absolute.getY() + 1.0, absolute.getZ() + 0.5, 0, 0);
		helper.getLevel().addFreshEntity(zombie);
		float startingHealth = zombie.getHealth();

		cast(ItemsTC.focusTrap.get(), caster);

		helper.succeedWhen(() -> helper.assertTrue(zombie.getHealth() < startingHealth,
				"the trap should have armed on the block and cursed the zombie waiting next to it, health=" + zombie.getHealth()));
	}

	/**
	 * Focus: Sanctuary - the last of the five mediums, {@link thaumcraft.common.entities.projectile.EntityFocusCloud},
	 * a stationary lingering AoE rather than a moving/self-destructing carrier. Casts it directly onto a
	 * damaged pig (Root#setupFromCaster already aims/spawns at the caster's own feet-adjacent position by
	 * default with the default radius (1) comfortably covering a pig standing right next to the caster) and
	 * polls via succeedWhen since the cloud only re-triggers every 5 ticks.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void castSanctuaryFocusHealsANearbyDamagedTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 0.8);
		pig.setHealth(1.0f);

		cast(ItemsTC.focusSanctuary.get(), caster);

		helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() > 1.0f,
				"the sanctuary cloud should have healed the nearby damaged pig by now, health=" + pig.getHealth()));
	}

	// --------------------------------------------------------------------------------------------------
	// ThaumcraftInternalMethodHandler - the real IInternalMethodHandler installed over ThaumcraftApi's
	// DummyInternalMethodHandler default, finally letting thaumcraft.api.aura.AuraHelper (and the
	// knowledge/warp methods) do something instead of being permanent stubs. Thaumcraft's constructor
	// installs it during normal mod loading, which GameTest goes through the same as any other boot - no
	// special setup needed here, just confirming it's actually the real one and not still the dummy.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void auraHelperDelegatesToTheRealAuraHandler(GameTestHelper helper) {
		Level level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);

		float base = thaumcraft.api.aura.AuraHelper.getVis(level, pos);
		thaumcraft.api.aura.AuraHelper.addVis(level, pos, 10.0f);
		helper.assertTrue(thaumcraft.api.aura.AuraHelper.getVis(level, pos) == base + 10.0f,
				"AuraHelper.addVis should have reached the real AuraHandler, not silently no-opped like the old stub");

		float drained = thaumcraft.api.aura.AuraHelper.drainVis(level, pos, 5.0f, false);
		helper.assertTrue(drained == 5.0f, "AuraHelper.drainVis should return what it actually drained, not 0 like the old stub");
		helper.assertTrue(thaumcraft.api.aura.AuraHelper.getVis(level, pos) == base + 5.0f, "the drain should have actually reduced the stored vis");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void internalMethodHandlerProgressesResearchAndGrantsWarp(GameTestHelper helper) {
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		knowledge.clear();

		boolean progressed = thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(player, "TESTRESEARCH");
		helper.assertTrue(progressed, "progressResearch should report success");
		helper.assertTrue(knowledge.getResearchStage("TESTRESEARCH") == 1, "a never-known research should progress to stage 1, got " + knowledge.getResearchStage("TESTRESEARCH"));

		thaumcraft.api.ThaumcraftApi.internalMethods.progressResearch(player, "TESTRESEARCH");
		helper.assertTrue(knowledge.getResearchStage("TESTRESEARCH") == 2, "progressing an already-known stage-1 research should advance it to stage 2, got " + knowledge.getResearchStage("TESTRESEARCH"));

		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		int before = thaumcraft.api.ThaumcraftApi.internalMethods.getActualWarp(player);
		thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(player, 3, IPlayerWarp.EnumWarpType.NORMAL);
		helper.assertTrue(warp.get(IPlayerWarp.EnumWarpType.NORMAL) == 3, "addWarpToPlayer should have added directly to the player's real IPlayerWarp");
		helper.assertTrue(thaumcraft.api.ThaumcraftApi.internalMethods.getActualWarp(player) == before + 3, "getActualWarp should reflect the newly-added warp");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Amber ore + Curio - a genuinely new consumer of the just-unlocked addKnowledge/addWarpToPlayer path,
	// plus the first real content in thaumcraft.api.research.ResearchCategories (previously a working but
	// entirely empty registry - see ThaumcraftResearchCategories' own doc comment).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchCategoriesAreRegistered(GameTestHelper helper) {
		String[] expectedKeys = { "BASICS", "AUROMANCY", "ALCHEMY", "ARTIFICE", "INFUSION", "GOLEMANCY", "ELDRITCH" };
		for (String key : expectedKeys) {
			ResearchCategory cat = ResearchCategories.getResearchCategory(key);
			helper.assertTrue(cat != null, "research category '" + key + "' should be registered");
			helper.assertTrue(cat.key.equals(key), "registered category should report its own key back");
		}
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void curioGrantsKnowledgeAndIsConsumed(GameTestHelper helper) {
		@SuppressWarnings("removal")
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;
		ThaumcraftCapabilities.getKnowledge(player).clear();

		ItemStack curio = new ItemStack(ItemsTC.curio.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, curio);

		int totalBefore = totalKnowledge(player);
		ItemsTC.curio.get().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);
		int totalAfter = totalKnowledge(player);

		helper.assertTrue(totalAfter > totalBefore, "using a curio should have granted some Observation/Theory knowledge, total went from " + totalBefore + " to " + totalAfter);
		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty(), "the single-count curio should have been consumed on use in survival mode");
		helper.succeed();
	}

	/**
	 * Sums raw accumulated knowledge points, not {@code getKnowledge}'s progression-scaled "levels" view
	 * (which divides by {@code EnumKnowledgeType.getProgression()}, 16/32 - a single curio use only grants
	 * a fraction of one level's worth of raw points, so that view would floor to 0 even on a real grant).
	 */
	private static int totalKnowledge(ServerPlayer player) {
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		int total = 0;
		for (ResearchCategory cat : ResearchCategories.researchCategories.values()) {
			total += knowledge.getKnowledgeRaw(IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, cat);
			total += knowledge.getKnowledgeRaw(IPlayerKnowledge.EnumKnowledgeType.THEORY, cat);
		}
		return total;
	}

	// --------------------------------------------------------------------------------------------------
	// Loot table coverage - found while adding Silverwood that amber_block/amber_brick and the whole
	// stone_arcane family had never gotten a loot table at all (they compiled and passed every prior test,
	// but would have dropped nothing at all when broken - a real, silent survival gap, same class of bug as
	// the earlier-session "crystal essence has no recipe" one). These break a real placed block via
	// Block#dropResources (the same real-loot mechanism FocusEffectBreak uses) and assert an actual drop
	// appears, closing the gap for real rather than just asserting the loot table JSON parses.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void amberBlockOnlyDropsWithSilkTouch(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		BlockPos absolute = helper.absolutePos(relPos);
		helper.setBlock(relPos, BlocksTC.amberBlock.get());
		net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(absolute);

		// Without silk touch: matches vanilla glass's own convention (glass-material blocks only drop with
		// silk touch) - breaking it plain should yield no drops at all.
		net.minecraft.world.level.block.Block.dropResources(state, helper.getLevel(), absolute, null, null, ItemStack.EMPTY);
		List<ItemEntity> droppedPlain = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		helper.assertTrue(droppedPlain.isEmpty(), "amber_block should not drop anything without silk touch, found: "
				+ droppedPlain.stream().map(e -> e.getItem().toString()).toList());

		ItemStack silkTouchTool = new ItemStack(Items.NETHERITE_PICKAXE);
		silkTouchTool.enchant(helper.getLevel().registryAccess()
				.lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SILK_TOUCH), 1);
		net.minecraft.world.level.block.Block.dropResources(state, helper.getLevel(), absolute, null, null, silkTouchTool);
		List<ItemEntity> droppedSilk = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundAmberBlock = droppedSilk.stream().anyMatch(e -> e.getItem().is(ItemsTC.amberBlock.get().asItem()));
		helper.assertTrue(foundAmberBlock, "amber_block should drop itself with silk touch, found: "
				+ droppedSilk.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	/**
	 * Real bug found while adding Alumentum (unrelated chunk) and diagnosing an unrelated boot warning:
	 * ore_amber's loot table nested a weighted {@code minecraft:item} pair (curio weight 1, amber weight
	 * 14) inside a {@code minecraft:alternatives} entry - but {@code alternatives} picks the first child
	 * whose conditions pass, it does not do weighted-random selection (that's a plain pool entries list's
	 * job). Since neither child had a condition, the first one (curio) always matched and amber was
	 * genuinely unreachable - the vanilla loot table validator even flags this at datapack-reload time
	 * ("Unreachable entry!"), which is how this got noticed. Ore Amber had dropped a Curio on literally
	 * every single break since the day it was added, never real Amber from this pool. Fixed by giving curio
	 * its own {@code random_chance} condition and letting amber be the unconditional (guaranteed) fallback
	 * - correct {@code alternatives} usage. This test breaks the ore many times and asserts both outcomes
	 * actually occur, which the old (broken) loot table would have failed by never producing amber.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void oreAmberDropsBothCurioAndAmberAcrossManyBreaks(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		BlockPos absolute = helper.absolutePos(relPos);
		boolean foundCurio = false;
		boolean foundAmber = false;
		for (int i = 0; i < 200 && !(foundCurio && foundAmber); i++) {
			helper.setBlock(relPos, BlocksTC.oreAmber.get());
			net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(absolute);
			net.minecraft.world.level.block.Block.dropResources(state, helper.getLevel(), absolute, null, null, ItemStack.EMPTY);
			List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
			for (ItemEntity e : dropped) {
				if (e.getItem().is(ItemsTC.curio.get())) foundCurio = true;
				if (e.getItem().is(ItemsTC.amber.get())) foundAmber = true;
				e.discard();
			}
		}
		helper.assertTrue(foundAmber, "ore_amber should drop real Amber most of the time across 200 breaks - the fixed bug made this never happen");
		helper.assertTrue(foundCurio, "ore_amber should still drop a Curio sometimes (~6.6% of breaks) across 200 breaks");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void stoneArcaneFamilyDropsRealLoot(GameTestHelper helper) {
		assertSelfDrops(helper, new BlockPos(1, 1, 1), BlocksTC.stoneArcane.get(), ItemsTC.stoneArcane.get());
		assertSelfDrops(helper, new BlockPos(3, 1, 1), BlocksTC.stoneArcaneBrick.get(), ItemsTC.stoneArcaneBrick.get());
		assertSelfDrops(helper, new BlockPos(5, 1, 1), BlocksTC.logSilverwood.get(), ItemsTC.logSilverwood.get());
		assertSelfDrops(helper, new BlockPos(1, 1, 3), BlocksTC.plankSilverwood.get(), ItemsTC.plankSilverwood.get());
		assertSelfDrops(helper, new BlockPos(3, 1, 3), BlocksTC.silverwoodStairs.get(), ItemsTC.silverwoodStairs.get());
		assertSelfDrops(helper, new BlockPos(5, 1, 3), BlocksTC.logGreatwood.get(), ItemsTC.logGreatwood.get());
		assertSelfDrops(helper, new BlockPos(1, 1, 5), BlocksTC.plankGreatwood.get(), ItemsTC.plankGreatwood.get());
		assertSelfDrops(helper, new BlockPos(3, 1, 5), BlocksTC.greatwoodStairs.get(), ItemsTC.greatwoodStairs.get());
		helper.succeed();
	}

	private static void assertSelfDrops(GameTestHelper helper, BlockPos relPos, net.minecraft.world.level.block.Block block, net.minecraft.world.item.Item expectedItem) {
		BlockPos absolute = helper.absolutePos(relPos);
		helper.setBlock(relPos, block);
		net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(absolute);
		net.minecraft.world.level.block.Block.dropResources(state, helper.getLevel(), absolute, null, null, ItemStack.EMPTY);
		List<ItemEntity> dropped = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean found = dropped.stream().anyMatch(e -> e.getItem().is(expectedItem));
		helper.assertTrue(found, block + " should drop " + expectedItem + " when broken, found: "
				+ dropped.stream().map(e -> e.getItem().toString()).toList());
	}

	// --------------------------------------------------------------------------------------------------
	// Silverwood tree - a real SaplingBlock/TreeGrower now, resolving against the new
	// data/thaumcraft/worldgen/configured_feature/silverwood_tree.json at grow time (vanilla's own
	// minecraft:tree feature, not a reproduction of the original's hand-rolled shape - see BlocksTC's own
	// doc comment). Advances a stage-1 sapling directly via SaplingBlock#advanceTree (the same method both
	// bonemeal and the natural random-tick growth call) rather than waiting out real random ticks. Uses a
	// new, taller "tall" structure (data/thaumcraft/structure/tall.nbt, 12x32x12 all-air) instead of the
	// shared "empty" one (8x5x8) - confirmed by direct diagnosis that GameTest's own containment barrier
	// sits just above "empty"'s bounds, which silently made TreeGrower#growTree's internal placement checks
	// fail (the sapling reverted to its own stage-1 state, no exception) well before the tree feature's own
	// logic was ever really exercised. Not a production bug - the same "real projectile/growth-feature needs
	// more room than the tiny shared structure gives it" lesson as the Fireball focus's aim-precision note.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "tall")
	public static void silverwoodSaplingGrowsIntoARealTree(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(4, 1, 4);
		BlockPos absolute = helper.absolutePos(relPos);
		ServerLevel level = helper.getLevel();

		// The tree feature needs real soil beneath it to grow into, same as any real sapling planted in
		// the world - the shared "empty" structure is entirely air, so this test provides its own ground.
		level.setBlock(helper.absolutePos(relPos.below()), Blocks.DIRT.defaultBlockState(), 3);
		level.setBlock(absolute, BlocksTC.saplingSilverwood.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.SaplingBlock.STAGE, 1), 3);
		net.minecraft.world.level.block.state.BlockState saplingState = level.getBlockState(absolute);
		((net.minecraft.world.level.block.SaplingBlock) BlocksTC.saplingSilverwood.get())
				.advanceTree(level, absolute, saplingState, level.getRandom());

		AABB searchVolume = new AABB(absolute).inflate(10.0).expandTowards(0, 25.0, 0);
		boolean foundLog = false;
		boolean foundLeaves = false;
		for (BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
				net.minecraft.core.BlockPos.containing(searchVolume.minX, searchVolume.minY, searchVolume.minZ),
				net.minecraft.core.BlockPos.containing(searchVolume.maxX, searchVolume.maxY, searchVolume.maxZ))) {
			if (level.getBlockState(p).is(BlocksTC.leavesSilverwood.get())) {
				foundLeaves = true;
			}
			if (level.getBlockState(p).is(BlocksTC.logSilverwood.get())) {
				foundLog = true;
				break;
			}
		}
		helper.assertTrue(foundLog, "advancing a stage-1 silverwood sapling should have grown a real tree with log_silverwood blocks somewhere nearby (leaves found: " + foundLeaves + ")");
		helper.succeed();
	}

	/** Same shape as silverwoodSaplingGrowsIntoARealTree above - Greatwood's own configured_feature just
	 * uses taller/wider fancy_trunk_placer/fancy_foliage_placer parameters for a visually bigger tree,
	 * deliberately dropping the original's 2x2-sapling "giant" variant (see BlocksTC's own doc comment on
	 * logGreatwood for why - vanilla's giant-sapling logic is hardcoded to specific vanilla saplings, not a
	 * generic hook a modded one can opt into). */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "tall")
	public static void greatwoodSaplingGrowsIntoARealTree(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(4, 1, 4);
		BlockPos absolute = helper.absolutePos(relPos);
		ServerLevel level = helper.getLevel();

		level.setBlock(helper.absolutePos(relPos.below()), Blocks.DIRT.defaultBlockState(), 3);
		level.setBlock(absolute, BlocksTC.saplingGreatwood.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.SaplingBlock.STAGE, 1), 3);
		net.minecraft.world.level.block.state.BlockState saplingState = level.getBlockState(absolute);
		((net.minecraft.world.level.block.SaplingBlock) BlocksTC.saplingGreatwood.get())
				.advanceTree(level, absolute, saplingState, level.getRandom());

		AABB searchVolume = new AABB(absolute).inflate(10.0).expandTowards(0, 25.0, 0);
		boolean foundLog = false;
		boolean foundLeaves = false;
		for (BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
				net.minecraft.core.BlockPos.containing(searchVolume.minX, searchVolume.minY, searchVolume.minZ),
				net.minecraft.core.BlockPos.containing(searchVolume.maxX, searchVolume.maxY, searchVolume.maxZ))) {
			if (level.getBlockState(p).is(BlocksTC.leavesGreatwood.get())) {
				foundLeaves = true;
			}
			if (level.getBlockState(p).is(BlocksTC.logGreatwood.get())) {
				foundLog = true;
				break;
			}
		}
		helper.assertTrue(foundLog, "advancing a stage-1 greatwood sapling should have grown a real tree with log_greatwood blocks somewhere nearby (leaves found: " + foundLeaves + ")");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Alumentum - a real throwable fire bomb (thaumcraft.common.items.consumables.ItemAlumentum +
	// thaumcraft.common.entities.projectile.EntityAlumentum), not a wand focus. Right-clicking throws it
	// like a real vanilla snowball/egg; same succeedWhen pattern as the Fireball focus test since the
	// projectile needs real flight time before its explosion can land.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void throwingAlumentumExplodesAndDamagesANearbyTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 2.5);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
		float startingHealth = pig.getHealth();

		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.alumentum.get()));
		ItemsTC.alumentum.get().use(helper.getLevel(), caster, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.succeedWhen(() -> helper.assertTrue(pig.getHealth() < startingHealth,
				"the thrown alumentum should have exploded near the pig and damaged it by now, health=" + pig.getHealth() + " (was " + startingHealth + ")"));
	}

	// --------------------------------------------------------------------------------------------------
	// Recharge Pedestal - the first device built since the "shared inventory-tile base" question was
	// scoped and repeatedly deferred (see thaumcraft.common.tiles.devices.TileRechargePedestal's own doc
	// comment for why it's purpose-built rather than a ported generic base). No real production
	// IRechargable item is registered yet (ItemGrappleGun etc. still need their own unported dependencies),
	// so this exercises the pedestal against TEST_RECHARGABLE_ITEM (see this class's own field doc comment
	// for why that has to be a real registered item, not a bare `new Item(...)`).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void rechargePedestalAcceptsRechargesAndEjectsAnItem(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		BlockPos absolute = helper.absolutePos(relPos);
		helper.setBlock(relPos, BlocksTC.rechargePedestal.get());
		thaumcraft.common.tiles.devices.TileRechargePedestal tile = helper.getBlockEntity(relPos);

		AuraHandler.addVis(helper.getLevel(), absolute, 500.0f);

		ItemStack rechargable = new ItemStack(TEST_RECHARGABLE_ITEM.get());
		helper.assertTrue(tile.accepts(rechargable), "the pedestal should accept an IRechargable item");
		helper.assertFalse(tile.accepts(new ItemStack(Items.STICK)), "the pedestal should reject a non-IRechargable item");

		tile.setItem(rechargable);
		float visBefore = AuraHandler.getVis(helper.getLevel(), absolute);

		thaumcraft.common.tiles.devices.TileRechargePedestal.tick(helper.getLevel(), absolute, helper.getLevel().getBlockState(absolute), tile);

		int chargeAfter = thaumcraft.api.items.RechargeHelper.getCharge(tile.getItem());
		helper.assertTrue(chargeAfter > 0, "ticking the pedestal should have recharged the held item from the local aura, charge=" + chargeAfter);
		helper.assertTrue(AuraHandler.getVis(helper.getLevel(), absolute) < visBefore, "recharging should have drained real vis from the local aura");

		ItemStack ejected = tile.getItem();
		tile.setItem(ItemStack.EMPTY);
		helper.assertTrue(ejected.getItem() == TEST_RECHARGABLE_ITEM.get(), "the ejected item should be the same recharged stack");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Hungry Chest - a real vanilla ChestBlock subclass (see BlockHungryChest's own doc comment). No new
	// tile class was needed at all, so these tests focus on the two things this port actually adds:
	// auto-eating dropped items on collision, and never linking into a double chest (even with itself).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void hungryChestAutoConsumesADroppedItem(GameTestHelper helper) {
		BlockPos relPos = BlockPos.ZERO;
		helper.setBlock(relPos, BlocksTC.hungryChest.get());
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(relPos);
		BlockPos absolute = helper.absolutePos(relPos);

		ItemEntity dropped = new ItemEntity(helper.getLevel(), absolute.getX() + 0.5, absolute.getY() + 0.5, absolute.getZ() + 0.5, new ItemStack(Items.STICK, 5));
		helper.getLevel().addFreshEntity(dropped);

		// entityInside is driven by the entity's own physics tick, same as the crucible's own auto-consume
		// test - let vanilla call it naturally rather than invoking it directly. Uses succeedWhen (not a
		// fixed-delay guess) since a freshly spawned entity's first physics tick - and so its first
		// entityInside call - doesn't necessarily land within a couple of ticks of being added; confirmed
		// by an interim diagnostic showing entityInside firing correctly, just later than a 3-tick guess
		// allowed for - the same lesson as the Fireball focus test earlier this session.
		helper.succeedWhen(() -> {
			boolean foundSticks = false;
			for (int i = 0; i < chest.getContainerSize(); i++) {
				if (chest.getItem(i).is(Items.STICK)) {
					foundSticks = true;
				}
			}
			helper.assertTrue(foundSticks, "a dropped item resting on a hungry chest should have been auto-consumed into it by now");
			helper.assertTrue(dropped.isRemoved(), "the fully-consumed item entity should have been removed");
		});
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void hungryChestsNeverLinkIntoADoubleChest(GameTestHelper helper) {
		BlockPos posA = new BlockPos(2, 1, 2);
		BlockPos posB = new BlockPos(3, 1, 2);
		helper.setBlock(posA, BlocksTC.hungryChest.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH));
		helper.setBlock(posB, BlocksTC.hungryChest.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.ChestBlock.FACING, net.minecraft.core.Direction.NORTH));

		// Neighbor-change notification (as if a player had just placed the second chest next to the first).
		helper.getLevel().neighborChanged(helper.absolutePos(posA), BlocksTC.hungryChest.get(), helper.absolutePos(posB));

		net.minecraft.world.level.block.state.properties.ChestType typeA =
				helper.getBlockState(posA).getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
		net.minecraft.world.level.block.state.properties.ChestType typeB =
				helper.getBlockState(posB).getValue(net.minecraft.world.level.block.ChestBlock.TYPE);
		helper.assertTrue(typeA == net.minecraft.world.level.block.state.properties.ChestType.SINGLE, "two adjacent hungry chests should never link - chest A was " + typeA);
		helper.assertTrue(typeB == net.minecraft.world.level.block.state.properties.ChestType.SINGLE, "two adjacent hungry chests should never link - chest B was " + typeB);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Salis Mundus (thaumcraft.common.items.resources.ItemMagicDust) - closes a loop flagged much earlier
	// this session: the Arcane Workbench was originally built as a directly-registered block "with the
	// dust-conversion + research-gate explicitly flagged as a TODO to restore once research exists". It
	// does now (ThaumcraftCapabilities), so this exercises that real dust-trigger conversion path -
	// FIRSTSTEPS@1 is in every player's default knowledge, so a fresh mock player can use it immediately.
	// --------------------------------------------------------------------------------------------------

	private static net.minecraft.world.InteractionResult useSalisMundusOn(GameTestHelper helper, ServerPlayer player, BlockPos relPos) {
		BlockPos absolute = helper.absolutePos(relPos);
		ItemStack dust = new ItemStack(ItemsTC.salisMundus.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, dust);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(
				player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		return ItemsTC.salisMundus.get().useOn(context);
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void salisMundusConvertsACraftingTableIntoAnArcaneWorkbench(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		helper.setBlock(relPos, Blocks.CRAFTING_TABLE);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;

		useSalisMundusOn(helper, player, relPos);

		helper.assertTrue(helper.getBlockState(relPos).is(BlocksTC.arcaneWorkbench.get()), "the crafting table should have become an arcane workbench");
		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty(), "the single-count dust should have been consumed in survival mode");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void salisMundusConvertsACauldronIntoACrucible(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		helper.setBlock(relPos, Blocks.CAULDRON);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		useSalisMundusOn(helper, player, relPos);

		helper.assertTrue(helper.getBlockState(relPos).is(BlocksTC.crucible.get()), "the cauldron should have become a crucible");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void salisMundusDoesNothingToAnUnrelatedBlock(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		helper.setBlock(relPos, Blocks.DIRT);
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		player.getAbilities().instabuild = false;

		useSalisMundusOn(helper, player, relPos);

		helper.assertTrue(helper.getBlockState(relPos).is(Blocks.DIRT), "an unrelated block should be untouched");
		helper.assertFalse(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty(), "the dust should not have been consumed when no trigger matched");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Arcane Lamp (BlockLampArcane/TileLampArcane) - scatters real vanilla light blocks around itself. The
	// tile's own tick() only fires on real-world-time multiples of 5, so this lets GameTest advance real
	// ticks naturally (succeedWhen) rather than fighting that gate with direct calls.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneLampScattersLightBlocksNearby(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(4, 2, 4);
		helper.setBlock(relPos, BlocksTC.lampArcane.get());
		BlockPos absolute = helper.absolutePos(relPos);
		thaumcraft.common.tiles.devices.TileLampArcane tile = helper.getBlockEntity(relPos);

		// Calls scatterAttempt directly, many times, rather than waiting on real ticks - most of a ±15
		// scatter radius falls outside the shared "empty" structure's tiny bounds, into GameTest's own
		// containment barrier (solid, so isEmptyBlock rejects it every time), the same class of issue the
		// Silverwood tree test hit earlier this session. Enough direct attempts makes the odds of at least
		// one landing inside the structure's own open interior overwhelming, without needing a bigger
		// dedicated structure just for this one test. 300 was observed to be flaky in practice (failed
		// roughly 1 run in 2) - 3000 drives the failure probability low enough to be effectively zero while
		// still running in a few milliseconds (this is a tight direct-call loop, not real ticks).
		for (int i = 0; i < 3000; i++) {
			tile.scatterAttempt(helper.getLevel(), absolute);
		}

		AABB searchVolume = new AABB(absolute).inflate(15.0);
		boolean foundLight = false;
		for (BlockPos p : net.minecraft.core.BlockPos.betweenClosed(
				net.minecraft.core.BlockPos.containing(searchVolume.minX, searchVolume.minY, searchVolume.minZ),
				net.minecraft.core.BlockPos.containing(searchVolume.maxX, searchVolume.maxY, searchVolume.maxZ))) {
			if (helper.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.LIGHT)) {
				foundLight = true;
				break;
			}
		}
		helper.assertTrue(foundLight, "an enabled arcane lamp should have scattered at least one light block nearby after 3000 attempts");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneLampIsLitUnlessRedstonePowered(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(4, 2, 4);
		helper.setBlock(relPos, BlocksTC.lampArcane.get());

		helper.assertTrue(helper.getBlockState(relPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"a freshly placed, unpowered arcane lamp should be enabled");

		helper.setBlock(relPos.below(), Blocks.REDSTONE_BLOCK);
		helper.assertTrue(!helper.getBlockState(relPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"a powered arcane lamp should turn off");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Vis Generator - one of the few devices this session where the original's capability (IEnergyStorage)
	// survived into NeoForge 1.21.1 almost unchanged, so it ports over close to mechanically. See
	// TileVisGenerator's own doc comment for the "push-only" design. Uses AuraHandler.addVis + calling
	// TileVisGenerator.tick(...) directly (same pattern as rechargePedestalAcceptsRechargesAndEjectsAnItem)
	// rather than waiting out real aura-generation/tick timing.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void visGeneratorRechargesFromAuraAndPushesEnergyToANeighbor(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(2, 1, 2);
		BlockPos sinkPos = genPos.above();
		BlockPos genAbs = helper.absolutePos(genPos);

		helper.setBlock(genPos, BlocksTC.visGenerator.get());
		helper.setBlock(sinkPos, TEST_ENERGY_SINK_BLOCK.get());

		// Force facing towards the sink directly rather than relying on the placement-time neighbor scan
		// (BlockVisGenerator#getStateForPlacement) - helper.setBlock doesn't go through a real
		// BlockPlaceContext, and this test targets the tile's recharge/push tick logic specifically.
		net.minecraft.world.level.block.state.BlockState genState = helper.getBlockState(genPos)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP);
		helper.setBlock(genPos, genState);

		AuraHandler.addVis(helper.getLevel(), genAbs, 500.0f);

		thaumcraft.common.tiles.devices.TileVisGenerator tile = helper.getBlockEntity(genPos);
		TestEnergySinkBlockEntity sink = helper.getBlockEntity(sinkPos);
		helper.assertTrue(sink.getEnergyStored() == 0, "the sink shouldn't have any energy before the generator ticks");

		thaumcraft.common.tiles.devices.TileVisGenerator.tick(helper.getLevel(), genAbs, genState, tile);

		helper.assertTrue(sink.getEnergyStored() > 0,
				"the sink adjacent to an enabled vis generator's output face should have received energy after one tick, got " + sink.getEnergyStored());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void visGeneratorDoesNothingWhileDisabledByRedstone(GameTestHelper helper) {
		BlockPos genPos = new BlockPos(2, 1, 2);
		BlockPos genAbs = helper.absolutePos(genPos);
		helper.setBlock(genPos, BlocksTC.visGenerator.get());

		net.minecraft.world.level.block.state.BlockState disabled = helper.getBlockState(genPos)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, false);
		helper.setBlock(genPos, disabled);

		AuraHandler.addVis(helper.getLevel(), genAbs, 500.0f);
		thaumcraft.common.tiles.devices.TileVisGenerator tile = helper.getBlockEntity(genPos);

		thaumcraft.common.tiles.devices.TileVisGenerator.tick(helper.getLevel(), genAbs, disabled, tile);

		helper.assertTrue(tile.getEnergyStored() == 0, "a disabled vis generator shouldn't recharge from the aura, got " + tile.getEnergyStored());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Vis Battery - unlike every device above, has no tile entity at all: all state lives directly in the
	// CHARGE blockstate property. Calls BlockVisBattery.tickVisBattery(...) directly rather than waiting out
	// the real random-tick-then-scheduled-tick chain, same "test the real entry point" precedent as the Vis
	// Generator tests just above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void visBatteryChargesFromASurplusAuraWhenUnpowered(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.visBattery.get());
		AuraHandler.addVis(helper.getLevel(), absolute, 500.0f);

		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		helper.assertTrue(state.getValue(thaumcraft.common.blocks.devices.BlockVisBattery.CHARGE) == 0, "a freshly placed vis battery should start at charge 0");

		thaumcraft.common.blocks.devices.BlockVisBattery.tickVisBattery((net.minecraft.server.level.ServerLevel) helper.getLevel(), absolute, state, helper.getLevel().getRandom());

		int charge = helper.getBlockState(pos).getValue(thaumcraft.common.blocks.devices.BlockVisBattery.CHARGE);
		helper.assertTrue(charge == 1, "with plenty of surplus aura and no redstone signal, one tick should raise charge to 1, found: " + charge);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void visBatteryReleasesAChargeIntoTheAuraWhenRedstonePowered(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.visBattery.get().defaultBlockState().setValue(thaumcraft.common.blocks.devices.BlockVisBattery.CHARGE, 5));
		AuraHandler.drainVis(helper.getLevel(), absolute, AuraHandler.getVis(helper.getLevel(), absolute), false);
		float visBefore = AuraHandler.getVis(helper.getLevel(), absolute);

		helper.setBlock(pos.below(), Blocks.REDSTONE_BLOCK);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		helper.assertTrue(helper.getLevel().hasNeighborSignal(absolute), "the battery should see the redstone block's signal");

		thaumcraft.common.blocks.devices.BlockVisBattery.tickVisBattery((net.minecraft.server.level.ServerLevel) helper.getLevel(), absolute, state, helper.getLevel().getRandom());

		int charge = helper.getBlockState(pos).getValue(thaumcraft.common.blocks.devices.BlockVisBattery.CHARGE);
		helper.assertTrue(charge == 4, "a powered battery should discharge one charge per tick, found: " + charge);
		helper.assertTrue(AuraHandler.getVis(helper.getLevel(), absolute) > visBefore, "discharging should have raised the local aura's vis");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Dioptra - an aura radar with no visual rendering ported (see TileDioptra's own doc comment). Calls
	// TileDioptra.tick(...) directly 20 times to cross its "every 20 ticks" scan gate rather than waiting
	// out real tick timing, same precedent as the Vis Battery/Vis Generator tests above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void dioptraScansTheLocalAuraIntoItsGridAndComparatorOutput(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.dioptra.get());
		AuraHandler.addVis(helper.getLevel(), absolute, 500.0f);

		thaumcraft.common.tiles.devices.TileDioptra tile = helper.getBlockEntity(pos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		helper.assertTrue(state.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"a freshly placed dioptra should default to vis mode (enabled)");

		for (int i = 0; i < 20; i++) {
			thaumcraft.common.tiles.devices.TileDioptra.tick(helper.getLevel(), absolute, state, tile);
		}

		helper.assertTrue(tile.gridAmount[84] > 0, "the center cell (this dioptra's own chunk) should reflect the 500 vis just added, found: " + tile.gridAmount[84]);

		int signal = helper.getBlockState(pos).getAnalogOutputSignal(helper.getLevel(), absolute);
		helper.assertTrue(signal > 0, "a dioptra sitting on a real vis reading should produce a nonzero comparator signal, found: " + signal);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void dioptraRightClickTogglesBetweenVisAndFluxMode(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.DIRT);
		helper.setBlock(pos, BlocksTC.dioptra.get());
		BlockPos absolute = helper.absolutePos(pos);

		ServerPlayer player = spawnCasterAt(helper, absolute);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);

		helper.assertTrue(!helper.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"right-clicking an enabled dioptra should switch it to flux mode (disabled)");

		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
		helper.assertTrue(helper.getBlockState(pos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"right-clicking again should switch it back to vis mode (enabled)");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Flux Condenser - converts real world flux + stored essentia into stored Flux essentia, paced by a
	// connected BlockCondenserLattice structure above it. Calls TileCondenser.tick(...) directly, same
	// "test the real entry point" precedent as every ticking device above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void condenserLatticeStructureGivesTheCondenserARealLatticeCount(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.condenser.get());
		helper.setBlock(pos.above(), BlocksTC.condenserLattice.get());
		helper.setBlock(pos.above(2), BlocksTC.condenserLattice.get());
		helper.setBlock(pos.above(3), BlocksTC.condenserLattice.get());

		thaumcraft.common.tiles.devices.TileCondenser tile = helper.getBlockEntity(pos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		thaumcraft.common.tiles.devices.TileCondenser.tick(helper.getLevel(), absolute, state, tile);

		helper.assertTrue(tile.getLatticeCount() > 0.0f, "a condenser with a connected lattice above it should have a positive lattice count, found: " + tile.getLatticeCount());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void condenserConvertsEssentiaAndWorldFluxIntoStoredFluxEssentia(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.condenser.get());
		helper.setBlock(pos.above(), BlocksTC.condenserLattice.get());
		helper.setBlock(pos.above(2), BlocksTC.condenserLattice.get());

		// TileCondenser.addEssentia (the IEssentiaTransport interface method) is a faithfully-ported
		// original quirk that computes how much *could* be added but never actually stores it - the real
		// condenser only ever gains essentia through its own fill() pulling from a real neighbor, so that's
		// what this test exercises too, via the same test-only IEssentiaTransport source used for the Jar/
		// Tube tests (reconfigured to output on its SOUTH face, since fill() only checks horizontal sides).
		helper.setBlock(pos.north(), TEST_ESSENTIA_SOURCE_BLOCK.get());
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(pos.north());
		source.aspect = Aspect.AIR;
		source.amount = 50;
		source.outputFace = net.minecraft.core.Direction.SOUTH;

		thaumcraft.common.tiles.devices.TileCondenser tile = helper.getBlockEntity(pos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		AuraHandler.addFlux(helper.getLevel(), absolute, 50.0f);
		float worldFluxBefore = AuraHandler.getFlux(helper.getLevel(), absolute);

		for (int i = 0; i < 700; i++) {
			thaumcraft.common.tiles.devices.TileCondenser.tick(helper.getLevel(), absolute, state, tile);
			if (tile.getFluxStored() > 0) break;
		}

		helper.assertTrue(tile.getFluxStored() > 0, "the condenser should have converted at least 1 point of Flux essentia by now, found: " + tile.getFluxStored());
		helper.assertTrue(tile.getEssentia() >= 0 && source.amount < 50, "the condenser should have pulled essentia from the source, source now has: " + source.amount);
		helper.assertTrue(AuraHandler.getFlux(helper.getLevel(), absolute) < worldFluxBefore, "converting should have drained real world flux");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pavingStoneTravelGrantsSpeedAndJumpBoostOnStep(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.pavingStoneTravel.get());

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		helper.assertFalse(player.hasEffect(MobEffects.MOVEMENT_SPEED), "the player shouldn't have Speed before stepping on the paving stone");

		BlocksTC.pavingStoneTravel.get().stepOn(helper.getLevel(), absolute, helper.getBlockState(pos), player);

		helper.assertTrue(player.hasEffect(MobEffects.MOVEMENT_SPEED), "stepping on the paving stone should grant Speed");
		helper.assertTrue(player.hasEffect(MobEffects.JUMP), "stepping on the paving stone should grant Jump Boost");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Candles - one small block per DyeColor, all sharing BlockCandle. The color-handler tint itself is
	// client-only and not GameTest-observable; these check the functional side instead (light level,
	// falling off when unsupported).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void candleEmitsFullLightAndCarriesItsOwnDye(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, BlocksTC.candles.get(net.minecraft.world.item.DyeColor.RED).get());
		BlockPos absolute = helper.absolutePos(pos);

		int light = helper.getBlockState(pos).getLightEmission(helper.getLevel(), absolute);
		helper.assertTrue(light == 15, "a lit candle should emit full light, found: " + light);

		net.minecraft.world.level.block.Block candleBlock = helper.getBlockState(pos).getBlock();
		helper.assertTrue(candleBlock instanceof thaumcraft.common.blocks.basic.BlockCandle red && red.dye == net.minecraft.world.item.DyeColor.RED,
				"the red candle block should carry DyeColor.RED");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void candleFallsOffWhenItsSupportIsRemoved(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		helper.setBlock(pos, BlocksTC.candles.get(net.minecraft.world.item.DyeColor.WHITE).get());

		helper.assertTrue(!helper.getBlockState(pos).isAir(), "the candle should be placed before its support is removed");
		helper.setBlock(pos.below(), Blocks.AIR);

		helper.assertTrue(helper.getBlockState(pos).isAir(), "removing a candle's support should make it fall off (turn to air)");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Phial - fills/empties at a Warded Jar via onItemUseFirst (a NeoForge IItemExtension hook, called
	// before the jar's own right-click handling). Calls it directly with a real UseOnContext rather than
	// simulating a full player interaction event.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void phialFillsFromAndEmptiesIntoAWardedJar(GameTestHelper helper) {
		BlockPos jarPos = BlockPos.ZERO;
		BlockPos jarAbs = helper.absolutePos(jarPos);
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(jarPos);
		jar.setAspects(new AspectList().add(Aspect.AIR, 50));

		ServerPlayer player = spawnCasterAt(helper, jarAbs.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(jarAbs), net.minecraft.core.Direction.UP, jarAbs, false);

		ItemStack emptyPhial = new ItemStack(ItemsTC.phial.get());
		player.setItemInHand(hand, emptyPhial);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().onItemUseFirst(player.getItemInHand(hand), context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.CONSUME, "filling from a jar with enough essentia should succeed, got: " + result);
		helper.assertTrue(jar.getAspects().getAmount(Aspect.AIR) == 40, "the jar should have given up 10 essentia, found: " + jar.getAspects().getAmount(Aspect.AIR));

		boolean foundFilled = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			ItemStack stack = player.getInventory().getItem(i);
			if (stack.getItem() == ItemsTC.phial.get() && stack.has(thaumcraft.api.items.ThaumcraftDataComponents.ASPECTS.get())) {
				foundFilled = true;
				break;
			}
		}
		helper.assertTrue(foundFilled, "the player should now hold a filled phial");

		// Empty the filled phial back into the jar.
		ItemStack filledPhial = ItemPhial.makeFilledPhial(Aspect.AIR);
		player.setItemInHand(hand, filledPhial);
		context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		result = player.getItemInHand(hand).getItem().onItemUseFirst(player.getItemInHand(hand), context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.CONSUME, "emptying a filled phial into an accepting jar should succeed, got: " + result);
		helper.assertTrue(jar.getAspects().getAmount(Aspect.AIR) == 50, "the jar should be back to 50 essentia, found: " + jar.getAspects().getAmount(Aspect.AIR));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crystalEssenceShowsTheAspectItHoldsInItsDisplayName(GameTestHelper helper) {
		ItemStack unaspected = new ItemStack(ItemsTC.crystalEssence.get());
		String plainName = unaspected.getHoverName().getString();

		ItemStack fire = new ItemStack(ItemsTC.crystalEssence.get());
		ItemsTC.crystalEssence.get().setAspects(fire, new AspectList().add(Aspect.FIRE, 1));
		String fireName = fire.getHoverName().getString();

		helper.assertTrue(!fireName.equals(plainName), "a crystal essence holding an aspect should show a different name than an unaspected one, both were: " + fireName);
		helper.assertTrue(fireName.contains("Fire"), "a Fire crystal essence's display name should mention Fire, found: " + fireName);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Label - locks a Warded Jar to one aspect via ILabelable, raising its suction from 32 to 64 (see
	// TileJarFillable's own doc comment for why that's what makes its strict suction comparison meaningful).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void aspectedLabelLocksAnEmptyJarAndRaisesItsSuction(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.jarNormal.get());
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(pos);

		helper.assertTrue(jar.getSuctionAmount(net.minecraft.core.Direction.UP) == 32, "an unlabelled jar should have 32 suction, found: " + jar.getSuctionAmount(net.minecraft.core.Direction.UP));

		ItemStack fireLabel = new ItemStack(ItemsTC.label.get());
		ItemsTC.label.get().setAspects(fireLabel, new AspectList().add(Aspect.FIRE, 1));

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, fireLabel);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().onItemUseFirst(player.getItemInHand(hand), context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.CONSUME, "labelling an empty, unlabelled jar should consume the label, got: " + result);
		helper.assertTrue(jar.getAspectFilter() == Aspect.FIRE, "the jar should now be filtered to Fire, found: " + jar.getAspectFilter());
		helper.assertTrue(jar.getSuctionAmount(net.minecraft.core.Direction.UP) == 64, "a labelled jar should have raised suction of 64, found: " + jar.getSuctionAmount(net.minecraft.core.Direction.UP));

		// A second label should fail - a jar can only ever be labelled once.
		ItemStack waterLabel = new ItemStack(ItemsTC.label.get());
		ItemsTC.label.get().setAspects(waterLabel, new AspectList().add(Aspect.WATER, 1));
		player.setItemInHand(hand, waterLabel);
		context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		result = player.getItemInHand(hand).getItem().onItemUseFirst(player.getItemInHand(hand), context);
		helper.assertTrue(jar.getAspectFilter() == Aspect.FIRE, "a second label should not be able to override an already-labelled jar's filter, found: " + jar.getAspectFilter());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void blankLabelCannotLockAnEmptyJar(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.jarNormal.get());
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(pos);

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.label.get()));
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		player.getItemInHand(hand).getItem().onItemUseFirst(player.getItemInHand(hand), context);

		helper.assertTrue(jar.getAspectFilter() == null, "a blank label on an empty jar (nothing to lock onto) should not set a filter, found: " + jar.getAspectFilter());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Zombie Brain - a random vanilla Zombie kill has a chance to drop one (Thaumcraft#onLivingDrops).
	// Calls Thaumcraft.rollZombieBrainDrop directly across many attempts (a real, statistically-rare kill
	// isn't practical to simulate here) rather than depending on real combat, same "test the real entry
	// point" precedent as every probabilistic mechanic this session.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void zombieKillsSometimesDropABrain(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 1, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
		helper.getLevel().addFreshEntity(zombie);

		java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
		boolean foundDrop = false;
		for (int i = 0; i < 200 && !foundDrop; i++) {
			thaumcraft.Thaumcraft.rollZombieBrainDrop(zombie, drops);
			foundDrop = drops.stream().anyMatch(e -> e.getItem().getItem() == ItemsTC.brain.get());
		}

		helper.assertTrue(foundDrop, "a zombie brain should have dropped at least once in 200 rolls at a 15% chance each");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sanitySoapClearsAllWarpWhenUsedUp(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(player, 5, IPlayerWarp.EnumWarpType.NORMAL);
		thaumcraft.api.ThaumcraftApi.internalMethods.addWarpToPlayer(player, 4, IPlayerWarp.EnumWarpType.TEMPORARY);
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		helper.assertTrue(warp.get(IPlayerWarp.EnumWarpType.NORMAL) == 5 && warp.get(IPlayerWarp.EnumWarpType.TEMPORARY) == 4,
				"warp should be seeded before cleansing, found normal=" + warp.get(IPlayerWarp.EnumWarpType.NORMAL) + " temporary=" + warp.get(IPlayerWarp.EnumWarpType.TEMPORARY));

		thaumcraft.common.items.consumables.ItemSanitySoap.cleanse(player);

		helper.assertTrue(warp.get(IPlayerWarp.EnumWarpType.NORMAL) == 4, "cleansing should remove exactly 1 point of Normal warp, found: " + warp.get(IPlayerWarp.EnumWarpType.NORMAL));
		helper.assertTrue(warp.get(IPlayerWarp.EnumWarpType.TEMPORARY) == 0, "cleansing should clear all Temporary warp, found: " + warp.get(IPlayerWarp.EnumWarpType.TEMPORARY));
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Loot Bag - rolls a real vanilla-format loot table (LootContextParamSets.GIFT) rather than the
	// original's hand-written weighted item generator. A recently-hit vanilla hostile mob has a chance to
	// drop one, tiered by a simple weighted pick - see Thaumcraft#rollLootBagDrop's own doc comment for why
	// the original's champion-gated condition and looting-level scaling are both simplified away.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void lootBagOpensAndSpillsRealLoot(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack bag = new ItemStack(ItemsTC.lootBagCommon.get());
		player.setItemInHand(hand, bag);

		ItemsTC.lootBagCommon.get().use(helper.getLevel(), player, hand);

		List<ItemEntity> spilled = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(origin).inflate(3.0));
		helper.assertTrue(!spilled.isEmpty(), "opening a common loot bag should have spilled at least one real item");
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the bag itself should have been consumed");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void monsterKillsSometimesDropALootBag(GameTestHelper helper) {
		net.minecraft.world.entity.monster.Monster monster = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
		BlockPos absolute = helper.absolutePos(BlockPos.ZERO);
		monster.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5, 0, 0);
		helper.getLevel().addFreshEntity(monster);

		List<ItemEntity> drops = new java.util.ArrayList<>();
		boolean foundDrop = false;
		for (int i = 0; i < 300 && !foundDrop; i++) {
			thaumcraft.Thaumcraft.rollLootBagDrop(monster, drops);
			foundDrop = drops.stream().anyMatch(e -> e.getItem().getItem() instanceof thaumcraft.common.items.curios.ItemLootBag);
		}

		helper.assertTrue(foundDrop, "a loot bag should have dropped at least once in 300 rolls at an 8% chance each");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Everfull Urn (TileWaterJug) - see TileWaterJug's own doc comment for what's simplified (the
	// original's 5x5x3 neighbor auto-fill scanning is dropped; this keeps the core "infinite water source"
	// identity). Bucket/bottle interaction uses the real BlockState#useItemOn public dispatch, same pattern
	// as crucibleFillsAndEmptiesWithABucketOfWater above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void everfullUrnFillsABucketAndABottleFromStoredWater(GameTestHelper helper) {
		BlockPos relPos = BlockPos.ZERO;
		helper.setBlock(relPos, BlocksTC.everfullUrn.get());
		BlockPos absolute = helper.absolutePos(relPos);
		thaumcraft.common.tiles.devices.TileWaterJug tile = helper.getBlockEntity(relPos);
		tile.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
				net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);

		ServerPlayer player = spawnCasterAt(helper, absolute);
		player.getAbilities().instabuild = false;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);

		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.BUCKET));
		helper.getBlockState(relPos).useItemOn(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND), helper.getLevel(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).is(Items.WATER_BUCKET),
				"an empty bucket right-clicked on a full urn should come back full of water");
		helper.assertTrue(tile.tank.getFluidAmount() == 0, "filling a bucket should have drained the urn's tank, got " + tile.tank.getFluidAmount());

		tile.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 1000),
				net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.GLASS_BOTTLE));
		helper.getBlockState(relPos).useItemOn(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND), helper.getLevel(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		ItemStack held = player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(held.is(Items.POTION), "an empty glass bottle right-clicked on the urn should come back as a water potion, got " + held);
		helper.assertTrue(tile.tank.getFluidAmount() == 667, "bottling should have drained exactly 333 from the urn's tank, got " + tile.tank.getFluidAmount());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void everfullUrnRegeneratesFromAura(GameTestHelper helper) {
		BlockPos relPos = BlockPos.ZERO;
		helper.setBlock(relPos, BlocksTC.everfullUrn.get());
		BlockPos absolute = helper.absolutePos(relPos);
		thaumcraft.common.tiles.devices.TileWaterJug tile = helper.getBlockEntity(relPos);

		AuraHandler.addVis(helper.getLevel(), absolute, 500.0f);
		helper.assertTrue(tile.tank.getFluidAmount() == 0, "a freshly placed urn should start empty");

		for (int i = 0; i < 20; i++) {
			thaumcraft.common.tiles.devices.TileWaterJug.tick(helper.getLevel(), absolute, helper.getBlockState(relPos), tile);
		}

		helper.assertTrue(tile.tank.getFluidAmount() > 0, "ticking an empty urn with vis available should have regenerated some water, got " + tile.tank.getFluidAmount());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Arcane Ear - see TileArcaneEar's own doc comment for the cross-dimension note-listening mechanic.
	// Uses TileArcaneEar.recordNoteEvent(...) directly rather than triggering a real vanilla note block via
	// Level#blockEvent: an earlier attempt at exactly that (with extensive debug logging to track it down)
	// found that repeated synchronous Level#blockEvent calls within a GameTest only reliably reach
	// NoteBlockEvent.Play's listeners on their FIRST call - the block state at that position, its instrument
	// and note values, were all confirmed correct on every call, but the event bus post itself silently
	// stopped reaching Thaumcraft's subscriber on subsequent calls within the same test (a real oddity of
	// this specific harness/event-bus combination, not reproducible via any logic bug in this device's own
	// code). recordNoteEvent is itself real production code (the exact method Thaumcraft's subscriber calls
	// once NoteBlockEvent.Play does reach it) - these tests exercise the actual interesting logic
	// (TileArcaneEar.tick's instrument/note/distance matching, the toggle-vs-pulse branch) directly rather
	// than depending on a real note block's event round trip working reliably inside GameTest. A freshly
	// placed ear rests on dirt - unlike stone (which vanilla explicitly tunes to BASEDRUM, ordinal 1,
	// confirmed by reading Blocks.java after this test's first attempt failed on exactly that wrong
	// assumption), dirt has no explicit instrument override and so falls back to vanilla's real default,
	// HARP/ordinal 0 - matching the ear's own note field, which starts at 0, with zero extra setup needed.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneEarPulsesRedstoneWhenAMatchingNoteBlockPlaysNearby(GameTestHelper helper) {
		BlockPos earPos = new BlockPos(2, 2, 2);
		helper.setBlock(earPos.below(), Blocks.DIRT);
		helper.setBlock(earPos, BlocksTC.arcaneEar.get());
		BlockPos earAbs = helper.absolutePos(earPos);
		BlockPos noteAbs = earAbs.offset(3, 0, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar tile = helper.getBlockEntity(earPos);

		// NOTE_EVENTS is shared per-dimension across every concurrently-running GameTest instance (all
		// share the overworld) - clearing right before recording isolates this check from any other test's
		// own events, so a pass here proves this test's own event actually caused it, not stray bleed.
		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);

		helper.assertTrue(helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"an arcane ear should enable after a matching note+instrument plays nearby");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneEarIgnoresANonMatchingNote(GameTestHelper helper) {
		BlockPos earPos = new BlockPos(2, 2, 2);
		helper.setBlock(earPos.below(), Blocks.DIRT);
		helper.setBlock(earPos, BlocksTC.arcaneEar.get());
		BlockPos earAbs = helper.absolutePos(earPos);
		BlockPos noteAbs = earAbs.offset(3, 0, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar tile = helper.getBlockEntity(earPos);

		// NOTE_EVENTS is shared per-dimension across every concurrently-running GameTest instance (all
		// share the overworld) - deliberately using an instrument+note combo (SNARE/17) that none of this
		// file's other Arcane Ear tests ever record (they all use HARP/0 or HARP/1), so a nearby *other*
		// test's own legitimate matching event can never bleed into this negative assertion. The first
		// attempt at this test used HARP/5 and still failed even after switching to SNARE/17 here, because a
		// *different*, concurrently-running test's own legitimate HARP/0 event (recorded for its own ear,
		// landing within this ear's 64-block radius purely from GameTest's structure grid spacing) was the
		// one actually matching, not this test's own SNARE/17 entry. Clearing the dimension's list
		// immediately before recording removes that cross-test contamination for the one synchronous window
		// this test cares about, without touching any other test's own already-completed assertions.
		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.SNARE, 17);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);

		helper.assertTrue(!helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"an arcane ear should stay disabled when the only note nearby plays a different pitch");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneEarRightClickCyclesWhichPitchItListensFor(GameTestHelper helper) {
		BlockPos earPos = new BlockPos(2, 2, 2);
		helper.setBlock(earPos.below(), Blocks.DIRT);
		helper.setBlock(earPos, BlocksTC.arcaneEar.get());
		BlockPos earAbs = helper.absolutePos(earPos);

		ServerPlayer player = spawnCasterAt(helper, earAbs);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(earAbs), net.minecraft.core.Direction.UP, earAbs, false);
		helper.getBlockState(earPos).useWithoutItem(helper.getLevel(), player, hit);

		BlockPos noteAbs = earAbs.offset(3, 0, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar tile = helper.getBlockEntity(earPos);

		// A note at the ear's original pitch (0) should no longer match after the right-click cycled it to 1.
		// clearNoteEvents isolates each check from other concurrently-running tests' own events sharing this
		// dimension's list - see arcaneEarPulsesRedstoneWhenAMatchingNoteBlockPlaysNearby's own comment.
		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);
		helper.assertTrue(!helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"after cycling its pitch to 1, the ear should no longer react to its old pitch (0)");

		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP, 1);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);
		helper.assertTrue(helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"after one right-click cycling its pitch to 1, the ear should react to pitch 1");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneEarToggleTogglesInsteadOfPulsing(GameTestHelper helper) {
		BlockPos earPos = new BlockPos(2, 2, 2);
		helper.setBlock(earPos.below(), Blocks.DIRT);
		helper.setBlock(earPos, BlocksTC.arcaneEarToggle.get());
		BlockPos earAbs = helper.absolutePos(earPos);
		BlockPos noteAbs = earAbs.offset(3, 0, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar tile = helper.getBlockEntity(earPos);

		// clearNoteEvents isolates each check from other concurrently-running tests' own events sharing this
		// dimension's list - see arcaneEarPulsesRedstoneWhenAMatchingNoteBlockPlaysNearby's own comment.
		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);
		helper.assertTrue(helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"the toggle variant should switch on after the first matching note");

		thaumcraft.common.tiles.devices.TileArcaneEar.clearNoteEvents(helper.getLevel());
		thaumcraft.common.tiles.devices.TileArcaneEar.recordNoteEvent(helper.getLevel(), noteAbs,
				net.minecraft.world.level.block.state.properties.NoteBlockInstrument.HARP, 0);
		thaumcraft.common.tiles.devices.TileArcaneEar.tick(helper.getLevel(), earAbs, helper.getBlockState(earPos), tile);
		helper.assertTrue(!helper.getBlockState(earPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"the toggle variant should switch back off after a second matching note, not stay on/pulse like the plain variant");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneEarDropsWhenItsSupportIsRemoved(GameTestHelper helper) {
		BlockPos earPos = new BlockPos(2, 2, 2);
		helper.setBlock(earPos.below(), Blocks.DIRT);
		helper.setBlock(earPos, BlocksTC.arcaneEar.get());
		helper.assertTrue(helper.getBlockState(earPos).is(BlocksTC.arcaneEar.get()), "sanity check: the ear should be placed");

		helper.setBlock(earPos.below(), Blocks.AIR);

		helper.assertTrue(helper.getBlockState(earPos).isAir(), "removing the block an arcane ear rests against should make the ear itself pop off");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Warded Jar (TileJarFillable) - the first real IAspectSource/IEssentiaTransport device this port has
	// built. See TileJarFillable's own doc comment for why no physical tube network was needed.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void jarFillsFromAnEssentiaContainerItemRightClick(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, BlocksTC.jarNormal.get());
		BlockPos absolute = helper.absolutePos(pos);
		thaumcraft.common.tiles.essentia.TileJarFillable tile = helper.getBlockEntity(pos);

		ItemStack crystal = new ItemStack(ItemsTC.crystalEssence.get(), 1);
		((ItemGenericEssentiaContainer) crystal.getItem()).setAspects(crystal, new AspectList().add(Aspect.FIRE, 1));

		ServerPlayer player = spawnCasterAt(helper, absolute);
		player.getAbilities().instabuild = false;
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, crystal);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		helper.getBlockState(pos).useItemOn(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND), helper.getLevel(), player,
				net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		helper.assertTrue(tile.getAspects().getAmount(Aspect.FIRE) == 1,
				"right-clicking the jar with a Crystal Essence carrying 1 fire should add exactly 1 fire to the jar, got " + tile.getAspects().getAmount(Aspect.FIRE));
		helper.assertTrue(player.getItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND).isEmpty(),
				"the crystal essence should have been consumed");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void jarEmptiesIntoTheAuraOnSneakRightClick(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, BlocksTC.jarNormal.get());
		BlockPos absolute = helper.absolutePos(pos);
		thaumcraft.common.tiles.essentia.TileJarFillable tile = helper.getBlockEntity(pos);
		tile.setAspects(new AspectList().add(Aspect.WATER, 50));

		float visBefore = AuraHandler.getVis(helper.getLevel(), absolute);

		ServerPlayer player = spawnCasterAt(helper, absolute);
		player.setShiftKeyDown(true);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		helper.getBlockState(pos).useItemOn(ItemStack.EMPTY, helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);

		helper.assertTrue(tile.getAspects().size() == 0, "emptying the jar should leave it with no aspects, got " + tile.getAspects());
		helper.assertTrue(AuraHandler.getVis(helper.getLevel(), absolute) > visBefore,
				"emptying a jar into the aura should have raised the local vis, was " + visBefore + " now " + AuraHandler.getVis(helper.getLevel(), absolute));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void jarSelfFillsFromARealEssentiaSourceAbove(GameTestHelper helper) {
		BlockPos bottomPos = new BlockPos(2, 1, 2);
		BlockPos sourcePos = bottomPos.above();
		helper.setBlock(bottomPos, BlocksTC.jarNormal.get());
		helper.setBlock(sourcePos, TEST_ESSENTIA_SOURCE_BLOCK.get());
		BlockPos bottomAbs = helper.absolutePos(bottomPos);

		thaumcraft.common.tiles.essentia.TileJarFillable bottom = helper.getBlockEntity(bottomPos);
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(sourcePos);
		source.aspect = Aspect.EARTH;
		source.amount = 40;

		for (int i = 0; i < 40; i++) {
			thaumcraft.common.tiles.essentia.TileJarFillable.tick(helper.getLevel(), bottomAbs, helper.getBlockState(bottomPos), bottom);
		}

		helper.assertTrue(bottom.getAspects().getAmount(Aspect.EARTH) > 0,
				"a jar should pull essentia from a real IEssentiaTransport tile placed directly above it, got " + bottom.getAspects());
		helper.assertTrue(source.amount < 40,
				"the source above should have been drained by the same amount the jar below gained, still at " + source.amount);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Growth Lamp (TileLampGrowth) - see its own doc comment for the real BonemealableBlock-based growth
	// mechanic and the charge/essentia-draw shell it shares in spirit with the original.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "tall")
	public static void growthLampDrawsChargeAndAdvancesARealCropNearby(GameTestHelper helper) {
		BlockPos lampPos = new BlockPos(4, 1, 4);
		BlockPos sourcePos = lampPos.above();
		BlockPos farmlandPos = lampPos.offset(0, 2, 0);
		BlockPos cropPos = lampPos.offset(0, 3, 0);

		helper.setBlock(sourcePos, TEST_ESSENTIA_SOURCE_BLOCK.get());
		helper.setBlock(lampPos, BlocksTC.lampGrowth.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
		helper.setBlock(farmlandPos, net.minecraft.world.level.block.Blocks.FARMLAND);
		helper.setBlock(cropPos, net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()
				.setValue(net.minecraft.world.level.block.CropBlock.AGE, 0));

		BlockPos lampAbs = helper.absolutePos(lampPos);
		BlockPos cropAbs = helper.absolutePos(cropPos);
		thaumcraft.common.tiles.devices.TileLampGrowth lamp = helper.getBlockEntity(lampPos);
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(sourcePos);
		source.aspect = Aspect.PLANT;
		source.amount = 999;

		for (int i = 0; i < 500; i++) {
			thaumcraft.common.tiles.devices.TileLampGrowth.tick(helper.getLevel(), lampAbs, helper.getBlockState(lampPos), lamp);
		}

		helper.assertTrue(lamp.getCharges() >= 0, "the lamp should have drawn essentia and be tracking real charge, got " + lamp.getCharges());
		helper.assertTrue(source.amount < 999, "the essentia source above should have been drawn from, still at " + source.amount);
		int age = helper.getLevel().getBlockState(cropAbs).getValue(net.minecraft.world.level.block.CropBlock.AGE);
		helper.assertTrue(age > 0, "a real wheat crop within the lamp's search radius should have been advanced via BonemealableBlock, age is still " + age);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void growthLampDropsWhenItsSupportIsRemoved(GameTestHelper helper) {
		BlockPos lampPos = new BlockPos(2, 2, 2);
		helper.setBlock(lampPos.above(), Blocks.STONE);
		helper.setBlock(lampPos, BlocksTC.lampGrowth.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.UP));
		helper.assertTrue(helper.getBlockState(lampPos).is(BlocksTC.lampGrowth.get()), "sanity check: the lamp should be placed");

		helper.setBlock(lampPos.above(), Blocks.AIR);

		helper.assertTrue(helper.getBlockState(lampPos).isAir(), "removing the block a growth lamp draws essentia from should make the lamp itself pop off");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Fertility Lamp (TileLampFertility) - same charge/essentia shell as the Growth Lamp, see its own doc
	// comment for the breeding mechanic.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fertilityLampBreedsTwoAdjacentAdultAnimals(GameTestHelper helper) {
		BlockPos lampPos = new BlockPos(2, 2, 2);
		helper.setBlock(lampPos.below(), Blocks.DIRT);
		helper.setBlock(lampPos, BlocksTC.lampFertility.get());
		BlockPos lampAbs = helper.absolutePos(lampPos);
		thaumcraft.common.tiles.devices.TileLampFertility lamp = helper.getBlockEntity(lampPos);
		lamp.setChargesForTest(10);

		ServerLevel level = helper.getLevel();
		Pig pig1 = EntityType.PIG.create(level);
		Pig pig2 = EntityType.PIG.create(level);
		pig1.moveTo(lampAbs.getX() + 0.5, lampAbs.getY() + 1.0, lampAbs.getZ() + 0.5, 0, 0);
		pig2.moveTo(lampAbs.getX() + 1.5, lampAbs.getY() + 1.0, lampAbs.getZ() + 0.5, 0, 0);
		level.addFreshEntity(pig1);
		level.addFreshEntity(pig2);

		lamp.tryBreedAnimalsForTest(level, lampAbs);

		helper.assertTrue(pig1.isInLove() && pig2.isInLove(), "the fertility lamp should have put both adjacent adult pigs in love");
		helper.assertTrue(lamp.getCharges() == 5, "breeding should have cost 5 charges, got " + lamp.getCharges());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fertilityLampDropsWhenItsSupportIsRemoved(GameTestHelper helper) {
		BlockPos lampPos = new BlockPos(2, 2, 2);
		helper.setBlock(lampPos.below(), Blocks.DIRT);
		helper.setBlock(lampPos, BlocksTC.lampFertility.get());
		helper.assertTrue(helper.getBlockState(lampPos).is(BlocksTC.lampFertility.get()), "sanity check: the lamp should be placed");

		helper.setBlock(lampPos.below(), Blocks.AIR);

		helper.assertTrue(helper.getBlockState(lampPos).isAir(), "removing the block a fertility lamp draws essentia from should make the lamp itself pop off");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Essentia Tube (TileTube) - see its own doc comment for the relay mechanic and what's simplified (no
	// per-face wand toggling). This is the actual point of the tube: extending IEssentiaTransport's reach
	// beyond direct adjacency, which the Warded Jar/Growth Lamp/Fertility Lamp chunks proved works but only
	// ever tested one hop at a time.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tubeRelaysEssentiaFromASourceToAJarBelow(GameTestHelper helper) {
		BlockPos sourcePos = new BlockPos(4, 3, 4);
		BlockPos tubePos = sourcePos.below();
		BlockPos jarPos = tubePos.below();

		helper.setBlock(sourcePos, TEST_ESSENTIA_SOURCE_BLOCK.get());
		helper.setBlock(tubePos, BlocksTC.tube.get());
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());

		BlockPos tubeAbs = helper.absolutePos(tubePos);
		BlockPos jarAbs = helper.absolutePos(jarPos);

		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(sourcePos);
		source.aspect = Aspect.EARTH;
		source.amount = 999;

		thaumcraft.common.tiles.essentia.TileTube tube = helper.getBlockEntity(tubePos);
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(jarPos);

		for (int i = 0; i < 200; i++) {
			thaumcraft.common.tiles.essentia.TileTube.tick(helper.getLevel(), tubeAbs, helper.getBlockState(tubePos), tube);
			thaumcraft.common.tiles.essentia.TileJarFillable.tick(helper.getLevel(), jarAbs, helper.getBlockState(jarPos), jar);
		}

		helper.assertTrue(jar.getAspects().getAmount(Aspect.EARTH) > 0,
				"essentia should have relayed from the source, through the tube, into the jar below it, got " + jar.getAspects());
		helper.assertTrue(source.amount < 999, "the original source should have been drawn down, still at " + source.amount);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tubeConnectionStateReflectsRealNeighbors(GameTestHelper helper) {
		BlockPos tubePos = new BlockPos(2, 2, 2);
		BlockPos jarPos = tubePos.below();

		helper.setBlock(tubePos, BlocksTC.tube.get());
		helper.assertTrue(!helper.getBlockState(tubePos).getValue(net.minecraft.world.level.block.PipeBlock.DOWN),
				"a freshly placed tube with nothing below it should not show a connection downward");

		helper.setBlock(jarPos, BlocksTC.jarNormal.get());

		helper.assertTrue(helper.getBlockState(tubePos).getValue(net.minecraft.world.level.block.PipeBlock.DOWN),
				"placing a real IEssentiaTransport tile (a jar) below the tube should update its DOWN connection property");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Tube Filter/Restrict - both extend TileTube via a handful of protected hooks rather than duplicating
	// its suction/relay loop (see TileTube's own doc comment). Tube One-way isn't ported - it needs a
	// wand-configured "facing" direction this simplified tube deliberately doesn't have.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tubeFilterCanBeSetAndClearedByRightClicking(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.tubeFilter.get());
		BlockPos absolute = helper.absolutePos(pos);
		thaumcraft.common.tiles.essentia.TileTubeFilter tile = helper.getBlockEntity(pos);

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);

		ItemStack phial = ItemPhial.makeFilledPhial(Aspect.AIR);
		player.setItemInHand(hand, phial);
		helper.getBlockState(pos).useItemOn(phial, helper.getLevel(), player, hand, hit);
		helper.assertTrue(tile.getAspectFilter() == Aspect.AIR, "right-clicking with a filled phial should set the filter to its aspect, found: " + tile.getAspectFilter());

		player.setItemInHand(hand, ItemStack.EMPTY);
		player.setShiftKeyDown(true);
		helper.getBlockState(pos).useItemOn(ItemStack.EMPTY, helper.getLevel(), player, hand, hit);
		helper.assertTrue(tile.getAspectFilter() == null, "sneak+empty-hand right-clicking should clear the filter, found: " + tile.getAspectFilter());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tubeFilterOnlyRelaysItsSetAspect(GameTestHelper helper) {
		BlockPos sourcePos = new BlockPos(4, 3, 4);
		BlockPos tubePos = sourcePos.below();
		BlockPos jarPos = tubePos.below();

		helper.setBlock(sourcePos, TEST_ESSENTIA_SOURCE_BLOCK.get());
		helper.setBlock(tubePos, BlocksTC.tubeFilter.get());
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		BlockPos tubeAbs = helper.absolutePos(tubePos);
		BlockPos jarAbs = helper.absolutePos(jarPos);

		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(sourcePos);
		source.aspect = Aspect.EARTH;
		source.amount = 999;

		thaumcraft.common.tiles.essentia.TileTubeFilter tube = helper.getBlockEntity(tubePos);
		tube.setAspectFilter(Aspect.WATER);
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(jarPos);

		for (int i = 0; i < 200; i++) {
			thaumcraft.common.tiles.essentia.TileTube.tick(helper.getLevel(), tubeAbs, helper.getBlockState(tubePos), tube);
			thaumcraft.common.tiles.essentia.TileJarFillable.tick(helper.getLevel(), jarAbs, helper.getBlockState(jarPos), jar);
		}
		helper.assertTrue(jar.getAspects().size() == 0, "a tube filtered to Water should never relay a Water-demanding source's Earth essentia, jar got " + jar.getAspects());

		tube.setAspectFilter(Aspect.EARTH);
		for (int i = 0; i < 200; i++) {
			thaumcraft.common.tiles.essentia.TileTube.tick(helper.getLevel(), tubeAbs, helper.getBlockState(tubePos), tube);
			thaumcraft.common.tiles.essentia.TileJarFillable.tick(helper.getLevel(), jarAbs, helper.getBlockState(jarPos), jar);
		}
		helper.assertTrue(jar.getAspects().getAmount(Aspect.EARTH) > 0, "once filtered to Earth, the tube should relay the source's real Earth essentia, jar got " + jar.getAspects());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tubeRestrictHalvesItsOwnSuction(GameTestHelper helper) {
		// A jar only ever connects on its own UP face (see TileJarFillable's own doc comment - the same
		// reason two plain jars can never stack-connect), so the jar must sit below the tube, not above,
		// for the tube to see its suction demand at all.
		BlockPos tubePos = new BlockPos(4, 3, 4);
		BlockPos jarPos = tubePos.below();
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		helper.setBlock(tubePos, BlocksTC.tubeRestrict.get());
		BlockPos tubeAbs = helper.absolutePos(tubePos);

		thaumcraft.common.tiles.essentia.TileTubeRestrict tube = helper.getBlockEntity(tubePos);
		// tick() only recomputes suction every other call (count % 2 == 0) - call it twice to guarantee
		// that lands, same as every other multi-tick test in this file.
		thaumcraft.common.tiles.essentia.TileTube.tick(helper.getLevel(), tubeAbs, helper.getBlockState(tubePos), tube);
		thaumcraft.common.tiles.essentia.TileTube.tick(helper.getLevel(), tubeAbs, helper.getBlockState(tubePos), tube);

		// A plain jar's own suction is 32 (see TileJarFillable); a restrict tube should report half that.
		helper.assertTrue(tube.getSuctionAmount(net.minecraft.core.Direction.DOWN) == 16,
				"a restrict tube facing a plain jar's 32 suction should report half (16), found: " + tube.getSuctionAmount(net.minecraft.core.Direction.DOWN));
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Infusion Altar (Runic Matrix + Arcane Pedestal) - see TileInfusionMatrix's own doc comment for what's
	// simplified this pass. No wand/caster item is ported yet, so activation goes through
	// TileInfusionMatrix#activate directly rather than a real right-click item-use round-trip.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pedestalStoresAndReturnsAnItemOnRightClick(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.pedestal.get());
		BlockPos absolute = helper.absolutePos(pos);
		thaumcraft.common.tiles.crafting.TilePedestal tile = helper.getBlockEntity(pos);

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);

		ItemStack held = new ItemStack(Items.IRON_INGOT);
		player.setItemInHand(hand, held);
		helper.getBlockState(pos).useItemOn(held, helper.getLevel(), player, hand, hit);
		helper.assertTrue(tile.getItem().is(Items.IRON_INGOT), "right-clicking with an item should place one on the pedestal, found: " + tile.getItem());
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the placed item should have been taken from the player's hand");

		helper.getBlockState(pos).useWithoutItem(helper.getLevel(), player, hit);
		helper.assertTrue(tile.getItem().isEmpty(), "empty-hand right-clicking a full pedestal should take the item back out, found: " + tile.getItem());
		helper.assertTrue(player.getInventory().contains(new ItemStack(Items.IRON_INGOT)), "the returned item should have gone back into the player's inventory");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixWontActivateWithoutACentralPedestal(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);

		ServerPlayer player = spawnCasterAt(helper, matrixAbs);
		boolean activated = matrix.activate(helper.getLevel(), player);

		helper.assertTrue(!activated && !matrix.active, "an infusion matrix with no pedestal two blocks below it should refuse to activate");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixCraftsADemoInfusionRecipeEndToEnd(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		BlockPos centralPos = matrixPos.below(2);
		BlockPos componentPos = centralPos.east(2);
		BlockPos jarOrdoPos = matrixPos.north(2);
		BlockPos jarIgnisPos = matrixPos.south(2);

		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(centralPos, BlocksTC.pedestal.get());
		helper.setBlock(componentPos, BlocksTC.pedestal.get());
		helper.setBlock(jarOrdoPos, BlocksTC.jarNormal.get());
		helper.setBlock(jarIgnisPos, BlocksTC.jarNormal.get());

		thaumcraft.common.tiles.crafting.TilePedestal central = helper.getBlockEntity(centralPos);
		central.setItem(new ItemStack(Items.GLOWSTONE_DUST));
		thaumcraft.common.tiles.crafting.TilePedestal component = helper.getBlockEntity(componentPos);
		component.setItem(new ItemStack(Items.REDSTONE));

		thaumcraft.common.tiles.essentia.TileJarFillable jarOrdo = helper.getBlockEntity(jarOrdoPos);
		jarOrdo.addToContainer(Aspect.ORDER, 10);
		thaumcraft.common.tiles.essentia.TileJarFillable jarIgnis = helper.getBlockEntity(jarIgnisPos);
		jarIgnis.addToContainer(Aspect.FIRE, 10);

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		ServerPlayer player = spawnCasterAt(helper, matrixAbs);

		helper.assertTrue(matrix.activate(helper.getLevel(), player), "activation should succeed with a central pedestal in place");
		helper.assertTrue(matrix.active, "the matrix should be active after the first activation");
		helper.assertTrue(matrix.activate(helper.getLevel(), player), "the second activation (with a valid recipe on the pedestals) should start crafting");
		helper.assertTrue(matrix.crafting, "the matrix should be mid-craft after a successful craftingStart");

		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		for (int i = 0; i < 400 && matrix.crafting; i++) {
			thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(helper.getLevel(), matrixAbs, matrixState, matrix);
		}

		helper.assertTrue(!matrix.crafting, "the craft should have completed (or definitively failed) within 400 ticks");
		helper.assertTrue(central.getItem().is(Items.GLOWSTONE), "the central pedestal should hold the demo recipe's result (glowstone), found: " + central.getItem());
		helper.assertTrue(component.getItem().isEmpty(), "the component pedestal's redstone should have been consumed by the craft");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Infusion Stabilizer - a pure drop-in for TileInfusionMatrix#getSurroundings, which already checks
	// any block generically via instanceof IInfusionStabiliser(Ext). No block entity, no changes needed
	// to the matrix itself - see BlockStabilizer's own doc comment.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixPicksUpASymmetricalPairOfStabilizersAsARealBonus(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(matrixPos.offset(3, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(-3, -2, 0), BlocksTC.stabilizer.get());

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		// checkSurroundings starts true, so the very first tick() runs the scan.
		thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(helper.getLevel(), matrixAbs, matrixState, matrix);

		helper.assertTrue(matrix.stabilityReplenish > 0,
				"a matched symmetrical pair of stabilizers should be a net positive to stabilityReplenish, got " + matrix.stabilityReplenish);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixPenalizesALoneUnmatchedStabilizer(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(matrixPos.offset(3, -2, 0), BlocksTC.stabilizer.get());
		// Deliberately no mirrored partner at offset(-3, -2, 0) - the scan should see the mismatch (air on
		// the other side) and penalize instead of reward, same as an unbalanced pedestal pairing.

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(helper.getLevel(), matrixAbs, matrixState, matrix);

		helper.assertTrue(matrix.stabilityReplenish < 0,
				"a lone, unmatched stabilizer should penalize stabilityReplenish, got " + matrix.stabilityReplenish);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void casterBasicActivatesAnInfusionMatrixThroughOnItemUseFirst(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(matrixPos.below(2), BlocksTC.pedestal.get());
		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);

		ServerPlayer player = spawnCasterAt(helper, matrixAbs);
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack wand = new ItemStack(ItemsTC.casterBasic.get());
		player.setItemInHand(hand, wand);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(matrixAbs), net.minecraft.core.Direction.UP, matrixAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, wand, hit);

		net.minecraft.world.InteractionResult result = wand.getItem().onItemUseFirst(wand, context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.CONSUME, "a caster right-clicking a valid infusion matrix should consume the interaction, got " + result);
		helper.assertTrue(matrix.active, "the matrix should have activated through the caster's onItemUseFirst dispatch");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixPicksUpASymmetricalPairOfInlaysAsARealBonus(GameTestHelper helper) {
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		// BlockInlay needs a sturdy block below it to survive (canSurvive), unlike the Stabilizer.
		helper.setBlock(matrixPos.offset(3, -3, 0), Blocks.STONE);
		helper.setBlock(matrixPos.offset(-3, -3, 0), Blocks.STONE);
		helper.setBlock(matrixPos.offset(3, -2, 0), BlocksTC.inlay.get());
		helper.setBlock(matrixPos.offset(-3, -2, 0), BlocksTC.inlay.get());

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(helper.getLevel(), matrixAbs, matrixState, matrix);

		helper.assertTrue(matrix.stabilityReplenish > 0,
				"a matched symmetrical pair of inlays should be a net positive to stabilityReplenish, got " + matrix.stabilityReplenish);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Infusion Enchantments - see EnumInfusionEnchantment/InfusionEnchantmentRecipe/ToolEvents' own doc
	// comments for what's ported this pass (LAMPLIGHT, DESTRUCTIVE) and what's deferred (the other 6 of the
	// 8 reachable, tool-restricted entries - ARCING/SOUNDING/COLLECTOR/ESSENCE/REFINING/BURROWING).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionEnchantLamplightRecipeMatchesAndGrantsTheEnchantment(GameTestHelper helper) {
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				pickaxe,
				java.util.List.of(
						new ItemStack(ItemsTC.crystalEssence.get()),
						new ItemStack(Items.GLOWSTONE_DUST),
						new ItemStack(Items.TORCH)));

		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, helper.getLevel());
		helper.assertTrue(match.isPresent(), "the infusion_enchant_lamplight recipe should match a plain pickaxe with its real components");

		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(new BlockPos(0, 2, 0)));
		ItemStack result = match.get().value().getRecipeOutput(player, pickaxe, input.components());
		helper.assertTrue(!result.isEmpty(), "a fresh iron pickaxe should be a valid LAMPLIGHT infusion target");
		helper.assertTrue(thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.getInfusionEnchantmentLevel(result, thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.LAMPLIGHT) == 1,
				"crafting the recipe should raise LAMPLIGHT from unenchanted to level 1, found level "
						+ thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.getInfusionEnchantmentLevel(result, thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.LAMPLIGHT));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionEnchantDestructiveRecipeRefusesANonToolCentralItem(GameTestHelper helper) {
		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				new ItemStack(Items.LEATHER_BOOTS),
				java.util.List.of(
						new ItemStack(ItemsTC.crystalEssence.get()),
						new ItemStack(ItemsTC.crystalEssence.get()),
						new ItemStack(Items.TNT)));

		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, helper.getLevel());
		helper.assertTrue(match.isEmpty(), "DESTRUCTIVE is axe/pickaxe/shovel-only, so a pair of boots should never match, found: " + match);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsLamplightPlacesARealLightSourceInADarkMinedSpot(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
			helper.setBlock(pos.relative(dir), Blocks.STONE);
		}
		helper.setBlock(pos, Blocks.AIR);
		BlockPos absPos = helper.absolutePos(pos);

		ServerPlayer player = spawnCasterAt(helper, absPos);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.LAMPLIGHT, 1);

		// The lighting engine processes the new stone enclosure's shadow lazily and asynchronously (a real
		// dedicated server runs it off the main thread - calling it directly from here isn't safe, per an
		// earlier draft of this test crashing with "Ran automatically on a different thread!"). Re-firing
		// the (idempotent - it only ever places the light block once brightness actually drops) event every
		// tick via succeedWhen sidesteps needing to guess how long that settling takes.
		ServerLevel level = helper.getLevel();
		helper.succeedWhen(() -> {
			net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
					level, absPos, level.getBlockState(absPos), null, new java.util.ArrayList<>(), player, pickaxe);
			thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);

			helper.assertTrue(level.getBlockState(absPos).is(Blocks.LIGHT),
					"LAMPLIGHT should place a real light source in a dark, fully air-enclosed mined spot (brightness "
							+ level.getMaxLocalRawBrightness(absPos) + "), found: " + level.getBlockState(absPos));
		});
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsDestructiveChainBreaksTheMinedPlanesEightNeighbours(GameTestHelper helper) {
		BlockPos center = new BlockPos(3, 2, 3);
		helper.setBlock(center, Blocks.AIR);
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				// No face was left-clicked in this test, so ToolEvents falls back to Direction.UP - an
				// UP face's plane is the XZ plane at the same Y, matching this layout.
				helper.setBlock(center.offset(a, 0, b), Blocks.STONE);
			}
		}
		BlockPos absCenter = helper.absolutePos(center);

		ServerPlayer player = spawnCasterAt(helper, absCenter.above(3));
		// Mock players default to creative (instabuild = true), which skips item damage entirely - force
		// survival so the durability assertion below is meaningful, same idiom used throughout this file.
		player.getAbilities().instabuild = false;
		ItemStack pickaxe = new ItemStack(Items.DIAMOND_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.DESTRUCTIVE, 1);

		net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
				helper.getLevel(), absCenter, helper.getLevel().getBlockState(absCenter), null, new java.util.ArrayList<>(), player, pickaxe);
		thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);

		int broken = 0;
		for (int a = -1; a <= 1; a++) {
			for (int b = -1; b <= 1; b++) {
				if (a == 0 && b == 0) continue;
				if (helper.getLevel().getBlockState(absCenter.offset(a, 0, b)).isAir()) broken++;
			}
		}
		helper.assertTrue(broken == 8, "DESTRUCTIVE should chain-break all 8 neighbours in the mined plane, broke " + broken + "/8");
		helper.assertTrue(pickaxe.getDamageValue() > 0, "the tool should take durability damage for each neighbour it chain-breaks, found damage " + pickaxe.getDamageValue());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsCollectorRedirectsHarvestDropsAndHomesOnTheHarvester(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(pos, Blocks.AIR);
		BlockPos absPos = helper.absolutePos(pos);
		ServerLevel level = helper.getLevel();

		ServerPlayer harvester = spawnCasterAt(helper, absPos.offset(5, 0, 5));
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.COLLECTOR, 1);

		ItemEntity rawDrop = new ItemEntity(level, absPos.getX() + 0.5, absPos.getY() + 0.5, absPos.getZ() + 0.5, new ItemStack(Items.COBBLESTONE));
		List<ItemEntity> drops = new java.util.ArrayList<>(java.util.List.of(rawDrop));

		net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
				level, absPos, level.getBlockState(absPos), null, drops, harvester, pickaxe);
		thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);

		helper.assertTrue(drops.size() == 1 && drops.get(0) instanceof thaumcraft.common.entities.EntityFollowingItem,
				"COLLECTOR should replace the raw harvest drop with a homing EntityFollowingItem, found: " + drops.get(0));

		ItemEntity homing = drops.get(0);
		level.addFreshEntity(homing);
		double startDistance = homing.position().distanceTo(harvester.position());
		for (int i = 0; i < 10; i++) {
			homing.tick();
		}
		double endDistance = homing.position().distanceTo(harvester.position());
		helper.assertTrue(endDistance < startDistance,
				"the homing drop should have moved closer to the harvester over 10 ticks, was " + startDistance + ", now " + endDistance);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsCollectorRedirectsKillDropsToTheKiller(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		ServerPlayer killer = spawnCasterAt(helper, origin);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(sword,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.COLLECTOR, 1);
		killer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);

		Pig pig = spawnPigFacedBy(helper, killer, origin);
		ItemEntity rawDrop = new ItemEntity(level, pig.getX(), pig.getY(), pig.getZ(), new ItemStack(Items.PORKCHOP));
		List<ItemEntity> drops = new java.util.ArrayList<>(java.util.List.of(rawDrop));

		net.minecraft.world.damagesource.DamageSource source = level.damageSources().playerAttack(killer);
		net.neoforged.neoforge.event.entity.living.LivingDropsEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(pig, source, drops, true);
		thaumcraft.common.lib.events.ToolEvents.onLivingDrops(event);

		helper.assertTrue(drops.size() == 1 && drops.get(0) instanceof thaumcraft.common.entities.EntityFollowingItem,
				"COLLECTOR should redirect kill drops to a homing EntityFollowingItem too, found: " + drops.get(0));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void aspectHelperReturnsRealEntityAspectsForARegisteredMobAndAPlayer(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		AspectList zombieAspects = thaumcraft.api.aspects.AspectHelper.getEntityAspects(zombie);
		helper.assertTrue(zombieAspects != null && zombieAspects.getAmount(Aspect.UNDEAD) == 20,
				"a Zombie should report its real registered UNDEAD aspect, found: " + zombieAspects);

		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		AspectList playerAspects = thaumcraft.api.aspects.AspectHelper.getEntityAspects(player);
		// >= not == : the 3 random aspects rolled on top of the base Man are drawn from the whole aspect
		// pool, which can itself land on Man again (AspectList#add accumulates, not overwrites).
		helper.assertTrue(playerAspects != null && playerAspects.getAmount(Aspect.MAN) >= 4,
				"a player should always report at least the base 4 Man aspect, found: " + playerAspects);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsEssenceDropsCrystalsFromTheVictimsAspects(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		ServerPlayer killer = spawnCasterAt(helper, origin);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(sword,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.ESSENCE, 2);
		killer.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);

		Pig pig = spawnPigFacedBy(helper, killer, origin);
		List<ItemEntity> drops = new java.util.ArrayList<>();
		net.minecraft.world.damagesource.DamageSource source = level.damageSources().playerAttack(killer);
		net.neoforged.neoforge.event.entity.living.LivingDropsEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(pig, source, drops, true);
		thaumcraft.common.lib.events.ToolEvents.onLivingDrops(event);

		// A Pig has 3 distinct registered aspects (BEAST/EARTH/DESIRE) - level 2 should draw exactly 2 of them.
		helper.assertTrue(drops.size() == 2, "ESSENCE level 2 should drop exactly 2 crystals from a pig's 3-aspect pool, found " + drops.size());
		for (ItemEntity drop : drops) {
			ItemStack crystal = drop.getItem();
			AspectList crystalAspects = ((thaumcraft.api.items.ItemGenericEssentiaContainer) crystal.getItem()).getAspects(crystal);
			helper.assertTrue(crystalAspects != null && crystalAspects.size() == 1,
					"each ESSENCE drop should be a single-aspect crystal, found: " + crystalAspects);
		}
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsRefiningEventuallyReplacesARawIronDropWithClusterIron(GameTestHelper helper) {
		BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		ServerPlayer harvester = spawnCasterAt(helper, absPos);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.REFINING, 4);

		// REFINING level 4 is a 62.5% chance per the (1 + level) * 0.125f formula - not deterministic, so
		// this rolls repeatedly (a fresh drop list each time) until it's seen at least once, same idea as
		// this file's other chance-based assertions elsewhere. 300 tries at 62.5% makes a false failure
		// astronomically unlikely (~10^-125).
		boolean sawCluster = false;
		for (int i = 0; i < 300 && !sawCluster; i++) {
			List<ItemEntity> drops = new java.util.ArrayList<>();
			drops.add(new ItemEntity(level, absPos.getX() + 0.5, absPos.getY() + 0.5, absPos.getZ() + 0.5, new ItemStack(Items.RAW_IRON)));
			net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
					level, absPos, level.getBlockState(absPos), null, drops, harvester, pickaxe);
			thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);
			if (drops.get(0).getItem().is(ItemsTC.clusterIron.get())) {
				sawCluster = true;
			}
		}
		helper.assertTrue(sawCluster, "REFINING level 4 should have replaced a raw iron drop with cluster_iron at least once in 300 tries");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsRefiningNeverTouchesAnUnrelatedDrop(GameTestHelper helper) {
		BlockPos absPos = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		ServerPlayer harvester = spawnCasterAt(helper, absPos);
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.REFINING, 4);

		List<ItemEntity> drops = new java.util.ArrayList<>();
		drops.add(new ItemEntity(level, absPos.getX() + 0.5, absPos.getY() + 0.5, absPos.getZ() + 0.5, new ItemStack(Items.COBBLESTONE)));
		net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
				level, absPos, level.getBlockState(absPos), null, drops, harvester, pickaxe);
		thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);

		helper.assertTrue(drops.get(0).getItem().is(Items.COBBLESTONE),
				"REFINING should never touch a drop that isn't in its special-mining table, found: " + drops.get(0).getItem());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsArcingCleavesANearbySecondaryTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();
		ServerPlayer attacker = spawnCasterAt(helper, origin);
		ItemStack sword = new ItemStack(Items.IRON_SWORD);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(sword,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.ARCING, 1);
		attacker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);

		Pig primary = EntityType.PIG.create(level);
		primary.moveTo(origin.getX() + 1.0, origin.getY(), origin.getZ() + 0.5);
		level.addFreshEntity(primary);
		Pig secondary = EntityType.PIG.create(level);
		secondary.moveTo(origin.getX() + 1.5, origin.getY(), origin.getZ() + 0.5);
		level.addFreshEntity(secondary);
		float secondaryStartingHealth = secondary.getHealth();

		net.neoforged.neoforge.event.entity.player.AttackEntityEvent event =
				new net.neoforged.neoforge.event.entity.player.AttackEntityEvent(attacker, primary);
		thaumcraft.common.lib.events.ToolEvents.onAttackEntity(event);

		helper.assertTrue(secondary.getHealth() < secondaryStartingHealth,
				"ARCING should cleave a secondary target standing next to the primary one, health was "
						+ secondaryStartingHealth + " now " + secondary.getHealth());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsSoundingDamagesTheToolOnASneakRightClick(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(pos, Blocks.STONE);
		BlockPos absPos = helper.absolutePos(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());
		// isCrouching() reads the entity's actual pose, not the input-intent flag setShiftKeyDown sets (that
		// only gets translated into a pose change during a real tick's aiStep, which nothing here drives) -
		// force the pose directly so ToolEvents' isCrouching() check actually sees it.
		player.setShiftKeyDown(true);
		player.setPose(net.minecraft.world.entity.Pose.CROUCHING);
		// Mock players default to creative (instabuild = true), which skips item damage entirely - same
		// idiom used throughout this file (e.g. the DESTRUCTIVE test above).
		player.getAbilities().instabuild = false;
		ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.SOUNDING, 1);
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, pickaxe);

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absPos), net.minecraft.core.Direction.UP, absPos, false);
		net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock event =
				new net.neoforged.neoforge.event.entity.player.PlayerInteractEvent.RightClickBlock(player, hand, absPos, hit);
		thaumcraft.common.lib.events.ToolEvents.onRightClickBlock(event);

		helper.assertTrue(player.getItemInHand(hand).getDamageValue() == 5,
				"SOUNDING should damage the tool by 5 on a sneak right-click, found damage " + player.getItemInHand(hand).getDamageValue());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void toolEventsBurrowingAutoMinesTheFurthestConnectedLog(GameTestHelper helper) {
		BlockPos base = new BlockPos(3, 2, 3);
		for (int i = 0; i < 5; i++) {
			helper.setBlock(base.offset(0, 0, i), Blocks.OAK_LOG);
		}
		// The mined block is already gone by the time BlockDropsEvent fires - mirror that here too.
		helper.setBlock(base, Blocks.AIR);
		BlockPos absMined = helper.absolutePos(base);
		ServerLevel level = helper.getLevel();

		ServerPlayer harvester = spawnCasterAt(helper, absMined);
		ItemStack axe = new ItemStack(Items.IRON_AXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(axe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.BURROWING, 1);

		net.neoforged.neoforge.event.level.BlockDropsEvent event = new net.neoforged.neoforge.event.level.BlockDropsEvent(
				level, absMined, Blocks.OAK_LOG.defaultBlockState(), null, new java.util.ArrayList<>(), harvester, axe);
		thaumcraft.common.lib.events.ToolEvents.onBlockDrops(event);

		BlockPos absFarthest = helper.absolutePos(base.offset(0, 0, 4));
		helper.assertTrue(level.getBlockState(absFarthest).isAir(),
				"BURROWING should auto-mine the furthest log connected to the one just mined, found: " + level.getBlockState(absFarthest));
		for (int i = 1; i <= 3; i++) {
			BlockPos mid = helper.absolutePos(base.offset(0, 0, i));
			helper.assertTrue(!level.getBlockState(mid).isAir(),
					"the logs between the mined block and the vein's tip should still be standing, log " + i + " found: " + level.getBlockState(mid));
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Essentia Resonator - a pure diagnostic tool, same onItemUseFirst shape as the caster wiring above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void resonatorReportsOnARealEssentiaTransportTile(GameTestHelper helper) {
		BlockPos jarPos = new BlockPos(2, 2, 2);
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		BlockPos jarAbs = helper.absolutePos(jarPos);
		thaumcraft.common.tiles.essentia.TileJarFillable jar = helper.getBlockEntity(jarPos);
		jar.addToContainer(Aspect.EARTH, 10);

		ServerPlayer player = spawnCasterAt(helper, jarAbs.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack resonator = new ItemStack(ItemsTC.resonator.get());
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(jarAbs), net.minecraft.core.Direction.UP, jarAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, resonator, hit);

		net.minecraft.world.InteractionResult result = resonator.getItem().onItemUseFirst(resonator, context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "the resonator should succeed against a real IEssentiaTransport tile, got " + result);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void resonatorFailsOnANonTransportBlock(GameTestHelper helper) {
		BlockPos stonePos = new BlockPos(2, 2, 2);
		helper.setBlock(stonePos, Blocks.STONE);
		BlockPos stoneAbs = helper.absolutePos(stonePos);

		ServerPlayer player = spawnCasterAt(helper, stoneAbs.above());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack resonator = new ItemStack(ItemsTC.resonator.get());
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(stoneAbs), net.minecraft.core.Direction.UP, stoneAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, resonator, hit);

		net.minecraft.world.InteractionResult result = resonator.getItem().onItemUseFirst(resonator, context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.FAIL, "the resonator should fail against a block with no IEssentiaTransport tile, got " + result);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// ThaumcraftObjectTags - the real item-to-aspect database backing AspectHelper#getObjectAspects, first
	// consumed by (an eventual) Smelter/Alembic. Exercised through the real public entry point,
	// AspectHelper.getObjectAspects, not the internal class directly.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void objectTagsReturnsRealAspectsForATaggedVanillaItem(GameTestHelper helper) {
		AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(Items.IRON_INGOT));
		helper.assertTrue(tags != null && tags.getAmount(Aspect.METAL) > 0, "an iron ingot should carry a real Metal aspect, got " + tags);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void objectTagsAppliesArmorDefenseBonus(GameTestHelper helper) {
		AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(Items.DIAMOND_CHESTPLATE));
		helper.assertTrue(tags != null && tags.getAmount(Aspect.PROTECT) > 0, "a diamond chestplate should carry a Protect bonus from its armor defense, got " + tags);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void objectTagsReturnsEmptyForAnUntaggedItem(GameTestHelper helper) {
		AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(Items.DIRT));
		helper.assertTrue(tags != null && tags.size() == 0, "an item with no explicit tag or bonus-tag match should come back empty, got " + tags);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void objectTagsPrefersAFilledEssentiaContainersRealContents(GameTestHelper helper) {
		ItemStack phial = ItemPhial.makeFilledPhial(Aspect.FIRE);
		AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(phial);
		helper.assertTrue(tags != null && tags.getAmount(Aspect.FIRE) > 0, "a filled phial's object tags should reflect its real held aspect, got " + tags);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Smelter + Alembic - the whole point of this session's ThaumcraftObjectTags chunk. The smelter smelts
	// an item's real object aspects into stored essentia, then feeds it one unit at a time to an alembic
	// stacked above it via TileAlembic#processAlembics.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void smelterSmeltsAnItemIntoEssentiaAndFeedsAnAlembicAbove(GameTestHelper helper) {
		BlockPos smelterPos = new BlockPos(2, 2, 2);
		BlockPos alembicPos = smelterPos.above();
		helper.setBlock(smelterPos, BlocksTC.smelter.get());
		helper.setBlock(alembicPos, BlocksTC.alembic.get());
		BlockPos smelterAbs = helper.absolutePos(smelterPos);

		thaumcraft.common.tiles.essentia.TileSmelter smelter = helper.getBlockEntity(smelterPos);
		smelter.setItem(0, new ItemStack(Items.IRON_INGOT));
		smelter.setItem(1, new ItemStack(Items.COAL));
		thaumcraft.common.tiles.essentia.TileAlembic alembic = helper.getBlockEntity(alembicPos);

		net.minecraft.world.level.block.state.BlockState smelterState = helper.getBlockState(smelterPos);
		for (int i = 0; i < 400; i++) {
			thaumcraft.common.tiles.essentia.TileSmelter.tick(helper.getLevel(), smelterAbs, smelterState, smelter);
		}

		helper.assertTrue(smelter.getItem(0).isEmpty(), "the input item should have been consumed once smelted, found " + smelter.getItem(0));
		helper.assertTrue(alembic.getEssentiaAmount(net.minecraft.core.Direction.UP) > 0,
				"the smelted iron ingot's Metal essentia should have made its way into the alembic above, found " + alembic.getEssentiaAmount(net.minecraft.core.Direction.UP));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void smelterRefusesAnItemWithNoRealAspects(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.smelter.get());
		thaumcraft.common.tiles.essentia.TileSmelter smelter = helper.getBlockEntity(pos);

		helper.assertTrue(!smelter.canPlaceItem(0, new ItemStack(Items.DIRT)), "an untagged item like dirt shouldn't be acceptable smelter input");
		helper.assertTrue(smelter.canPlaceItem(0, new ItemStack(Items.IRON_INGOT)), "a real object-tagged item like an iron ingot should be acceptable smelter input");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void alembicCanBeLabelledToOneAspectAndCleared(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.alembic.get());
		thaumcraft.common.tiles.essentia.TileAlembic tile = helper.getBlockEntity(pos);

		helper.assertTrue(tile.applyLabel(Aspect.FIRE), "labelling an empty alembic should succeed");
		helper.assertTrue(tile.getAspectFilter() == Aspect.FIRE, "the alembic should now be filtered to Fire, found " + tile.getAspectFilter());
		tile.clearLabel();
		helper.assertTrue(tile.getAspectFilter() == null, "clearing the label should remove the filter, found " + tile.getAspectFilter());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Void Jar - a small, purely additive extension of TileJarFillable (a new protected isFull() hook,
	// same "safe retrofit" shape as TileTube's own Filter/Restrict hooks). Never stops demanding essentia,
	// discards overflow past capacity instead of rejecting it.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidJarNeverStopsDemandingSuctionEvenWhenFull(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.jarVoid.get());
		thaumcraft.common.tiles.essentia.TileJarFillableVoid jar = helper.getBlockEntity(pos);
		jar.addToContainer(Aspect.EARTH, 250);

		helper.assertTrue(jar.getSuctionAmount(net.minecraft.core.Direction.UP) > 0,
				"a void jar should keep demanding suction even once full, got " + jar.getSuctionAmount(net.minecraft.core.Direction.UP));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidJarDiscardsOverflowInsteadOfRejectingIt(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.jarVoid.get());
		thaumcraft.common.tiles.essentia.TileJarFillableVoid jar = helper.getBlockEntity(pos);
		jar.addToContainer(Aspect.EARTH, 250);

		int leftover = jar.addToContainer(Aspect.EARTH, 50);

		helper.assertTrue(leftover == 0, "a void jar should silently discard overflow instead of returning it as leftover, got " + leftover);
		helper.assertTrue(jar.getEssentiaAmount(net.minecraft.core.Direction.UP) == 250,
				"amount should stay clamped at capacity, got " + jar.getEssentiaAmount(net.minecraft.core.Direction.UP));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidJarStillRejectsAMismatchedAspect(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.jarVoid.get());
		thaumcraft.common.tiles.essentia.TileJarFillableVoid jar = helper.getBlockEntity(pos);
		jar.addToContainer(Aspect.EARTH, 10);

		int leftover = jar.addToContainer(Aspect.WATER, 5);

		helper.assertTrue(leftover == 5, "a void jar holding Earth should still reject a mismatched Water addition, got leftover " + leftover);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Boots of the Traveller - the first real IRechargable item (previously only exercised via a synthetic
	// test stand-in against TileRechargePedestal). Tests call the real Item#inventoryTick entry point
	// directly, same "test the real entry point directly" shape used throughout this session.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void travellerBootsBoostSpeedWhenChargedAndMovingForward(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack boots = new ItemStack(ItemsTC.travellerBoots.get());
		thaumcraft.api.items.RechargeHelper.rechargeItemBlindly(boots, player, 240);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
		player.zza = 1.0f;

		boots.getItem().inventoryTick(boots, helper.getLevel(), player, 0, false);

		helper.assertTrue(player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
				"worn, charged traveller boots moving forward should grant a Speed boost");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void travellerBootsDoNothingWithoutCharge(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack boots = new ItemStack(ItemsTC.travellerBoots.get());
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, boots);
		player.zza = 1.0f;

		boots.getItem().inventoryTick(boots, helper.getLevel(), player, 0, false);

		helper.assertTrue(!player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED),
				"uncharged traveller boots shouldn't grant a Speed boost");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void travellerBootsRecipeMatchesItsRealComponents(GameTestHelper helper) {
		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				new ItemStack(Items.LEATHER_BOOTS),
				java.util.List.of(
						new ItemStack(ItemsTC.crystalEssence.get()),
						new ItemStack(ItemsTC.crystalEssence.get()),
						new ItemStack(ItemsTC.fabric.get()),
						new ItemStack(ItemsTC.fabric.get()),
						new ItemStack(Items.FEATHER),
						new ItemStack(Items.COD)));

		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, helper.getLevel());

		helper.assertTrue(match.isPresent(), "the traveller_boots infusion recipe should match its own real component list");
		helper.assertTrue(match.get().value().getResultItem(helper.getLevel().registryAccess()).is(ItemsTC.travellerBoots.get()),
				"the matched recipe should produce the traveller boots");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Robes - the first real IVisDiscountGear consumer. ItemCaster#getConsumptionModifier now checks worn
	// armor for it for real (that interface was already ported early this session with zero implementers).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void wornRobesDiscountWandVisCost(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(ItemsTC.clothChest.get()));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.LEGS, new ItemStack(ItemsTC.clothLegs.get()));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(ItemsTC.clothBoots.get()));

		ItemStack wand = new ItemStack(ItemsTC.casterBasic.get());
		float modifier = ((thaumcraft.common.items.casters.ItemCaster) wand.getItem()).getConsumptionModifier(wand, player, false);

		// 3 (chest) + 3 (legs) + 2 (boots) = 8% total discount -> modifier 0.92
		helper.assertTrue(Math.abs(modifier - 0.92f) < 0.001f, "wearing the full robe set should discount vis cost by 8%, got modifier " + modifier);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void noArmorMeansNoVisDiscount(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack wand = new ItemStack(ItemsTC.casterBasic.get());

		float modifier = ((thaumcraft.common.items.casters.ItemCaster) wand.getItem()).getConsumptionModifier(wand, player, false);

		helper.assertTrue(modifier == 1.0f, "no armor worn should mean no vis discount, got modifier " + modifier);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Cinderpearl - a plain BushBlock, its own real vanilla recipe (Blaze Powder), and a real
	// ThaumcraftObjectTags entry (the essentia-smeltery path).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cinderpearlCanSurviveOnSandButNotOnStone(GameTestHelper helper) {
		BlockPos sandPos = new BlockPos(2, 2, 2);
		BlockPos cinderpearlOnSand = sandPos.above();
		helper.setBlock(sandPos, Blocks.SAND);
		helper.setBlock(cinderpearlOnSand, BlocksTC.cinderpearl.get());
		helper.assertTrue(helper.getBlockState(cinderpearlOnSand).canSurvive(helper.getLevel(), helper.absolutePos(cinderpearlOnSand)),
				"cinderpearl should be able to survive on sand");

		BlockPos stonePos = new BlockPos(4, 2, 4);
		BlockPos cinderpearlOnStone = stonePos.above();
		helper.setBlock(stonePos, Blocks.STONE);
		helper.setBlock(cinderpearlOnStone, BlocksTC.cinderpearl.get());
		helper.assertTrue(!helper.getBlockState(cinderpearlOnStone).canSurvive(helper.getLevel(), helper.absolutePos(cinderpearlOnStone)),
				"cinderpearl shouldn't be able to survive on stone");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cinderpearlHasRealObjectTagsForTheSmeltery(GameTestHelper helper) {
		AspectList tags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(ItemsTC.cinderpearl.get()));
		helper.assertTrue(tags != null && tags.getAmount(Aspect.FIRE) > 0, "cinderpearl should carry real Fire essentia tags for the Smeltery, got " + tags);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cinderpearlCraftsIntoBlazePowder(GameTestHelper helper) {
		var craftingInput = net.minecraft.world.item.crafting.CraftingInput.of(1, 1, java.util.List.of(new ItemStack(ItemsTC.cinderpearl.get())));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING, craftingInput, helper.getLevel());

		helper.assertTrue(match.isPresent() && match.get().value().getResultItem(helper.getLevel().registryAccess()).is(Items.BLAZE_POWDER),
				"a single cinderpearl should craft into blaze powder");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Native Iron Cluster - an ore-purification alt-path through the already-shipped Crucible (Metal +
	// Order essentia dissolves an iron ore into a cluster), which then smelts into 2 iron ingots instead
	// of the usual 1 via a plain vanilla furnace recipe.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesAClusterIronRecipe(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);
		crucible.setAspects(new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5));

		ItemStack catalyst = new ItemStack(Items.IRON_ORE);
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the iron ore catalyst should have been fully consumed");
		helper.assertTrue(crucible.getAspects().getAmount(Aspect.METAL) == 0, "a completed recipe should remove exactly the aspects it consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundCluster = ejected.stream().anyMatch(e -> e.getItem().is(ItemsTC.clusterIron.get()));
		helper.assertTrue(foundCluster, "completing cluster_iron should have ejected a thaumcraft:cluster_iron item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void clusterIronSmeltsIntoTwoIronIngots(GameTestHelper helper) {
		var smeltingInput = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ItemsTC.clusterIron.get()));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, smeltingInput, helper.getLevel());

		helper.assertTrue(match.isPresent(), "the cluster_iron smelting recipe should exist");
		ItemStack result = match.get().value().getResultItem(helper.getLevel().registryAccess());
		helper.assertTrue(result.is(Items.IRON_INGOT) && result.getCount() == 2, "a native iron cluster should smelt into 2 iron ingots, got " + result);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesAClusterCinnabarRecipe(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);
		crucible.setAspects(new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5));

		ItemStack catalyst = new ItemStack(BlocksTC.oreCinnabar.get());
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the cinnabar ore catalyst should have been fully consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundCluster = ejected.stream().anyMatch(e -> e.getItem().is(ItemsTC.clusterCinnabar.get()));
		helper.assertTrue(foundCluster, "completing cluster_cinnabar should have ejected a thaumcraft:cluster_cinnabar item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void clusterCinnabarSmeltsIntoTwoQuicksilver(GameTestHelper helper) {
		var smeltingInput = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ItemsTC.clusterCinnabar.get()));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, smeltingInput, helper.getLevel());

		helper.assertTrue(match.isPresent(), "the cluster_cinnabar smelting recipe should exist");
		ItemStack result = match.get().value().getResultItem(helper.getLevel().registryAccess());
		helper.assertTrue(result.is(ItemsTC.quicksilver.get()) && result.getCount() == 2, "a native cinnabar cluster should smelt into 2 quicksilver, got " + result);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesAClusterGoldRecipe(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);
		crucible.setAspects(new AspectList().add(Aspect.METAL, 5).add(Aspect.ORDER, 5));

		ItemStack catalyst = new ItemStack(Items.GOLD_ORE);
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the gold ore catalyst should have been fully consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundCluster = ejected.stream().anyMatch(e -> e.getItem().is(ItemsTC.clusterGold.get()));
		helper.assertTrue(foundCluster, "completing cluster_gold should have ejected a thaumcraft:cluster_gold item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void clusterGoldSmeltsIntoTwoGoldIngots(GameTestHelper helper) {
		var smeltingInput = new net.minecraft.world.item.crafting.SingleRecipeInput(new ItemStack(ItemsTC.clusterGold.get()));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(net.minecraft.world.item.crafting.RecipeType.SMELTING, smeltingInput, helper.getLevel());

		helper.assertTrue(match.isPresent(), "the cluster_gold smelting recipe should exist");
		ItemStack result = match.get().value().getResultItem(helper.getLevel().registryAccess());
		helper.assertTrue(result.is(Items.GOLD_INGOT) && result.getCount() == 2, "a native gold cluster should smelt into 2 gold ingots, got " + result);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Bottle of Taint - a thrown debuff bomb, same ThrowableItemProjectile shape as Alumentum (same
	// succeedWhen-after-real-flight-time pattern as throwingAlumentumExplodesAndDamagesANearbyTarget
	// above). First real consumer of MobEffectFluxTaint - ported early this session, never registered or
	// applied by anything until now.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void throwingBottleTaintAppliesFluxTaintToANearbyTarget(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, caster, origin, 2.5);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());

		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.bottleTaint.get()));
		ItemsTC.bottleTaint.get().use(helper.getLevel(), caster, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.succeedWhen(() -> helper.assertTrue(pig.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT),
				"the thrown bottle of taint should have applied Flux Taint to the nearby pig by now"));
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void throwingBottleTaintSkipsUndeadTargets(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Zombie zombie = spawnZombieFacedBy(helper, caster, origin);
		caster.lookAt(EntityAnchorArgument.Anchor.EYES, zombie.getBoundingBox().getCenter());

		caster.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(ItemsTC.bottleTaint.get()));
		ItemsTC.bottleTaint.get().use(helper.getLevel(), caster, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.runAfterDelay(40, () -> {
			helper.assertTrue(!zombie.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT),
					"an undead target should never receive Flux Taint from a thrown bottle");
			helper.succeed();
		});
	}

	// --------------------------------------------------------------------------------------------------
	// MobEffectInfectiousVisExhaust - another Flux Rift prerequisite (see EntityWisp's own doc comment for
	// the running prerequisite list). Registered together with its already-ported-but-previously-unused
	// sibling MobEffectVisExhaust, its real amplifier-0 hand-off target.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infectiousVisExhaustSpreadsToANearbyEntityAndDecaysToPlainVisExhaust(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerLevel level = helper.getLevel();
		ServerPlayer caster = spawnCasterAt(helper, origin);
		Pig carrier = spawnPigFacedBy(helper, caster, origin, 2.5);
		carrier.addEffect(new net.minecraft.world.effect.MobEffectInstance(
				thaumcraft.common.registry.ThaumcraftMobEffects.INFECTIOUS_VIS_EXHAUST, 6000, 0, false, true));

		Pig nearby = EntityType.PIG.create(level);
		nearby.setPos(carrier.getX() + 1.0, carrier.getY(), carrier.getZ());
		level.addFreshEntity(nearby);

		helper.succeedWhen(() -> helper.assertTrue(nearby.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.VIS_EXHAUST),
				"an amplifier-0 infectious carrier should eventually hand a nearby entity plain vis_exhaust"));
	}

	// --------------------------------------------------------------------------------------------------
	// ScanningManager + ItemThaumometer - a headless (no book UI) slice of the research system. Real
	// consumer: TileInfusionMatrix#craftingStart already checks a recipe's research key against player
	// knowledge, so granting BOOTSTRAVELLER for real closes a genuine existing gap (that recipe was
	// otherwise unreachable outside creative mode).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void scanningTheRunicMatrixGrantsBootsTravellerResearch(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.infusionMatrix.get());
		BlockPos absolute = helper.absolutePos(pos);

		ServerPlayer player = spawnCasterAt(helper, absolute.above());
		helper.assertTrue(!thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("BOOTSTRAVELLER"),
				"the player shouldn't know BOOTSTRAVELLER yet");

		ItemStack thaumometer = new ItemStack(ItemsTC.thaumometer.get());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absolute), net.minecraft.core.Direction.UP, absolute, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, thaumometer, hit);

		thaumometer.getItem().onItemUseFirst(thaumometer, context);

		helper.assertTrue(thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("BOOTSTRAVELLER"),
				"scanning the runic matrix with a thaumometer should have granted BOOTSTRAVELLER");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void scanningARegisteredEntityGrantsItsResearch(GameTestHelper helper) {
		thaumcraft.api.research.ScanningManager.addScannableThing(new thaumcraft.api.research.IScanThing() {
			@Override
			public boolean checkThing(net.minecraft.world.entity.player.Player player, Object obj) {
				return obj instanceof Pig;
			}

			@Override
			public String getResearchKey(net.minecraft.world.entity.player.Player player, Object obj) {
				return "TEST_PIG_SCAN";
			}
		});

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Pig pig = spawnPigFacedBy(helper, player, origin, 2.0);
		helper.assertTrue(!thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("TEST_PIG_SCAN"),
				"the player shouldn't know TEST_PIG_SCAN yet");

		ItemStack thaumometer = new ItemStack(ItemsTC.thaumometer.get());
		thaumometer.getItem().interactLivingEntity(thaumometer, player, pig, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.assertTrue(thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("TEST_PIG_SCAN"),
				"scanning the pig should have granted its registered research key");
		helper.succeed();
	}

	/** Real (not ad-hoc) scan targets - {@code ScanAspect} (auto-registered once per real aspect straight
	 * from {@code Aspect}'s own constructor) and {@code ScanEntity} (two real examples registered in
	 * {@code Thaumcraft#commonSetup}). {@code ScanningManager#scanTheThing} matches ALL registered things,
	 * not just the first - so scanning a real Zombie should grant its entity-type key ("!ZOMBIE") AND every
	 * aspect key it's really tagged with in ThaumcraftEntityTags (UNDEAD/MAN/EARTH -> "!exanimis"/"!ordo... */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void scanningARealZombieGrantsItsEntityAndAspectResearch(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);

		IPlayerKnowledge knowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player);
		helper.assertTrue(!knowledge.isResearchKnown("!ZOMBIE") && !knowledge.isResearchKnown("!" + thaumcraft.api.aspects.Aspect.UNDEAD.getTag()),
				"the player shouldn't know either research key yet");

		ItemStack thaumometer = new ItemStack(ItemsTC.thaumometer.get());
		thaumometer.getItem().interactLivingEntity(thaumometer, player, zombie, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.assertTrue(knowledge.isResearchKnown("!ZOMBIE"),
				"scanning a real Zombie should grant the real registered ScanEntity(\"!ZOMBIE\") research key");
		helper.assertTrue(knowledge.isResearchKnown("!" + thaumcraft.api.aspects.Aspect.UNDEAD.getTag()),
				"scanning a real Zombie should also grant the real ScanAspect research key for its real UNDEAD aspect (from ThaumcraftEntityTags)");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Arcane Bellows - see TileBellows' own doc comment for the furnace-speedup mechanic and the access
	// transformer it needed (src/main/resources/META-INF/accesstransformer.cfg).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void bellowsNudgesAnAdjacentCookingFurnaceForward(GameTestHelper helper) {
		BlockPos bellowsPos = new BlockPos(3, 2, 3);
		BlockPos furnacePos = bellowsPos.south();
		helper.setBlock(furnacePos, Blocks.FURNACE);
		helper.setBlock(bellowsPos, thaumcraft.api.blocks.BlocksTC.bellows.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, true));

		ServerLevel level = helper.getLevel();
		BlockPos absBellows = helper.absolutePos(bellowsPos);

		net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
				(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
		furnace.cookingTotalTime = 200;
		furnace.cookingProgress = 50;

		thaumcraft.common.tiles.devices.TileBellows tile = helper.getBlockEntity(bellowsPos);
		net.minecraft.world.level.block.state.BlockState bellowsState = helper.getBlockState(bellowsPos);
		// The tile only acts every 2nd tick - two direct calls to trigger one real nudge, same isolation-test
		// idiom this file uses throughout rather than waiting on a real tick loop.
		thaumcraft.common.tiles.devices.TileBellows.tick(level, absBellows, bellowsState, tile);
		thaumcraft.common.tiles.devices.TileBellows.tick(level, absBellows, bellowsState, tile);

		helper.assertTrue(furnace.cookingProgress == 51,
				"the Bellows should nudge an adjacent cooking furnace's progress forward by 1 every 2 ticks, found " + furnace.cookingProgress);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void bellowsLeavesAFinishedOrIdleFurnaceAlone(GameTestHelper helper) {
		BlockPos bellowsPos = new BlockPos(3, 2, 3);
		BlockPos furnacePos = bellowsPos.south();
		helper.setBlock(furnacePos, Blocks.FURNACE);
		helper.setBlock(bellowsPos, thaumcraft.api.blocks.BlocksTC.bellows.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, true));

		ServerLevel level = helper.getLevel();
		BlockPos absBellows = helper.absolutePos(bellowsPos);

		net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
				(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(furnacePos);
		furnace.cookingTotalTime = 200;
		furnace.cookingProgress = 0; // idle - nothing cooking yet

		thaumcraft.common.tiles.devices.TileBellows tile = helper.getBlockEntity(bellowsPos);
		net.minecraft.world.level.block.state.BlockState bellowsState = helper.getBlockState(bellowsPos);
		thaumcraft.common.tiles.devices.TileBellows.tick(level, absBellows, bellowsState, tile);
		thaumcraft.common.tiles.devices.TileBellows.tick(level, absBellows, bellowsState, tile);

		helper.assertTrue(furnace.cookingProgress == 0,
				"the Bellows shouldn't touch an idle (not actually cooking) furnace, found progress " + furnace.cookingProgress);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Pech Wand - see ItemPechWand's own doc comment for the loosened research gate and the real
	// (Pech-trade) acquisition gap that isn't addressed yet.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechWandGrantsFocusResearchAndConsumesItself(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getAbilities().instabuild = false;
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack wand = new ItemStack(ItemsTC.pechWand.get());
		player.setItemInHand(hand, wand);

		IPlayerKnowledge knowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player);
		helper.assertTrue(!knowledge.isResearchKnown("FOCUSPECH"), "the player shouldn't know FOCUSPECH yet");

		wand.getItem().use(helper.getLevel(), player, hand);

		helper.assertTrue(knowledge.isResearchKnown("FOCUSPECH"), "using the Pech Wand should grant FOCUSPECH");
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "a single Pech Wand should be fully consumed after one use");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Arcane Grappler - see ItemGrappleGun/EntityGrapple's own doc comments for the recipe simplification
	// (the original's real recipe needs brass and an unported "simple mechanism" item) and the dropped
	// floating-tick-count anti-cheat reset.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void grappleGunFiresAGrappleAndConsumesCharge(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack gun = new ItemStack(ItemsTC.grappleGun.get());
		gun.set(thaumcraft.api.items.ThaumcraftDataComponents.CHARGE.get(), 50);
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, gun);

		gun.getItem().use(helper.getLevel(), player, hand);

		helper.assertTrue(thaumcraft.api.items.RechargeHelper.getCharge(gun) == 49,
				"firing the grapple gun should consume 1 charge, found " + thaumcraft.api.items.RechargeHelper.getCharge(gun));
		List<thaumcraft.common.entities.projectile.EntityGrapple> grapples = helper.getLevel().getEntitiesOfClass(
				thaumcraft.common.entities.projectile.EntityGrapple.class, player.getBoundingBox().inflate(16));
		helper.assertTrue(grapples.size() == 1, "firing should spawn exactly one real EntityGrapple, found " + grapples.size());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void grappleGunPullsThePlayerOnceItLands(GameTestHelper helper) {
		BlockPos wallPos = new BlockPos(3, 5, 6);
		helper.setBlock(wallPos, Blocks.STONE);
		BlockPos absWall = helper.absolutePos(wallPos);

		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(new BlockPos(3, 5, 3)));
		player.setDeltaMovement(Vec3.ZERO);

		thaumcraft.common.entities.projectile.EntityGrapple grapple =
				new thaumcraft.common.entities.projectile.EntityGrapple(player, level);
		grapple.setPos(absWall.getX() + 0.5, absWall.getY() + 0.5, absWall.getZ() - 1.5);
		grapple.setDeltaMovement(0, 0, 1.0);
		level.addFreshEntity(grapple);

		for (int i = 0; i < 10 && !thaumcraft.common.entities.projectile.EntityGrapple.hasActive(player); i++) {
			grapple.tick();
		}
		helper.assertTrue(thaumcraft.common.entities.projectile.EntityGrapple.hasActive(player),
				"flying a grapple into a wall should land it and mark it active for the thrower");

		double startY = player.getDeltaMovement().y;
		grapple.tick();
		helper.assertTrue(player.getDeltaMovement().y > startY,
				"once landed, the grapple should start pulling the player toward it, y-velocity was "
						+ startY + " now " + player.getDeltaMovement().y);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Node Transducer (Mirror) - see BlockMirror/BlockMirrorItem/TileMirror's own doc comments for what's
	// deferred (TileMirrorEssentia, ItemHandMirror) and the link-preservation-on-break cut.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void mirrorItemChargesALinkFromAnUnlinkedMirror(GameTestHelper helper) {
		BlockPos mirrorPos = new BlockPos(3, 2, 3);
		helper.setBlock(mirrorPos, thaumcraft.api.blocks.BlocksTC.mirror.get());
		BlockPos absMirror = helper.absolutePos(mirrorPos);

		ServerPlayer player = spawnCasterAt(helper, absMirror.above());
		ItemStack mirrorItem = new ItemStack(ItemsTC.mirror.get());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, mirrorItem);

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absMirror), net.minecraft.core.Direction.UP, absMirror, false);
		net.minecraft.world.item.context.UseOnContext context =
				new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, mirrorItem, hit);
		mirrorItem.getItem().useOn(context);

		boolean found = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).has(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get())) {
				found = true;
			}
		}
		helper.assertTrue(found, "right-clicking an unlinked mirror with a mirror item should charge a copy with a real MIRROR_LINK component");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void linkedMirrorsTransportAnItemToTheOtherSide(GameTestHelper helper) {
		BlockPos posA = new BlockPos(3, 2, 3);
		BlockPos posB = new BlockPos(3, 2, 10);
		helper.setBlock(posA, thaumcraft.api.blocks.BlocksTC.mirror.get());
		helper.setBlock(posB, thaumcraft.api.blocks.BlocksTC.mirror.get());
		BlockPos absA = helper.absolutePos(posA);
		BlockPos absB = helper.absolutePos(posB);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.tiles.devices.TileMirror tileA = helper.getBlockEntity(posA);
		thaumcraft.common.tiles.devices.TileMirror tileB = helper.getBlockEntity(posB);
		tileA.applyLink(new thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink(level.dimension(), absB));

		helper.assertTrue(tileA.isLinkValid() && tileB.isLinkValid(), "applying a link should establish a real mutual pairing on both tiles");

		ItemEntity dropped = new ItemEntity(level, absA.getX() + 0.5, absA.getY() + 0.5, absA.getZ() + 0.5, new ItemStack(Items.STICK, 3));
		boolean accepted = tileA.transport(dropped);
		helper.assertTrue(accepted && dropped.isRemoved(), "touching a linked mirror should transport the item and remove the original entity");

		net.minecraft.world.level.block.state.BlockState stateB = helper.getBlockState(posB);
		for (int i = 0; i < 5; i++) {
			thaumcraft.common.tiles.devices.TileMirror.tick(level, absB, stateB, tileB);
		}

		List<ItemEntity> nearB = level.getEntitiesOfClass(ItemEntity.class, new AABB(absB).inflate(2));
		helper.assertTrue(!nearB.isEmpty(), "the transported item should eventually eject out of the linked mirror on the other side");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Flux Rift prerequisites, chunk 1: EntityFluxRift's own collapse-drop items - see
	// ItemPrimordialPearl's own doc comment for what's ported vs. deliberately cut.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void primordialPearlNameAndModelTierFollowItsDamageValue(GameTestHelper helper) {
		ItemStack pearl = new ItemStack(thaumcraft.api.items.ItemsTC.primordialPearl.get());
		helper.assertTrue(pearl.getDamageValue() == 0, "a freshly created primordial pearl should start undamaged");
		helper.assertTrue(thaumcraft.common.items.curios.ItemPrimordialPearl.typeProperty(pearl) == 0.0f
				&& pearl.getHoverName().getString().equals("Primordial Pearl"), "a fresh stack should be the 'pearl' tier");

		pearl.setDamageValue(4);
		helper.assertTrue(thaumcraft.common.items.curios.ItemPrimordialPearl.typeProperty(pearl) == 1.0f
				&& pearl.getHoverName().getString().equals("Primordial Nodule"), "damage 4 should read as the 'nodule' tier");

		pearl.setDamageValue(7);
		helper.assertTrue(thaumcraft.common.items.curios.ItemPrimordialPearl.typeProperty(pearl) == 2.0f
				&& pearl.getHoverName().getString().equals("Primordial Mote"), "damage 7 should read as the 'mote' tier");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void primordialPearlAndVoidSeedHaveRealAspectsAndNames(GameTestHelper helper) {
		AspectList pearlAspects = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(thaumcraft.api.items.ItemsTC.primordialPearl.get()));
		helper.assertTrue(pearlAspects != null && pearlAspects.size() > 0, "the primordial pearl should carry real registered aspects for scanning/crafting cost");

		ItemStack voidSeed = new ItemStack(thaumcraft.api.items.ItemsTC.voidSeed.get());
		helper.assertTrue(voidSeed.getHoverName().getString().equals("Void Seed"), "void_seed should resolve its real lang name, found '" + voidSeed.getHoverName().getString() + "'");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void wispLocksInARealAspectTypeAndDropsAMatchingCrystalOnDeath(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(3, 2, 3));
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.EntityWisp wisp =
				new thaumcraft.common.entities.monster.EntityWisp(thaumcraft.common.registry.ThaumcraftEntities.WISP.get(), level);
		wisp.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		level.addFreshEntity(wisp);

		for (int i = 0; i < 5 && Aspect.getAspect(wisp.getWispType()) == null; i++) {
			wisp.tick();
		}
		Aspect type = Aspect.getAspect(wisp.getWispType());
		helper.assertTrue(type != null, "a wisp should lock in a real aspect type within a few ticks, found '" + wisp.getWispType() + "'");

		wisp.hurt(level.damageSources().generic(), 9999.0f);

		boolean foundMatchingCrystal = false;
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
			ItemStack stack = drop.getItem();
			if (stack.getItem() == ItemsTC.crystalEssence.get()) {
				AspectList aspects = ((ItemGenericEssentiaContainer) stack.getItem()).getAspects(stack);
				if (aspects != null && aspects.getAmount(type) > 0) {
					foundMatchingCrystal = true;
				}
			}
		}
		helper.assertTrue(foundMatchingCrystal, "killing a wisp should drop an essentia crystal matching its locked-in aspect type");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// EntityFluxRift - the entity every other Flux Rift prerequisite this session was building toward.
	// createRift's own real-world placement search (random offset + heightmap) isn't exercised directly
	// here - too randomness/terrain-dependent for a small GameTest structure to reliably assert on - these
	// instead construct a rift directly and drive its own real lifecycle logic (shape/tick/collapse).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fluxRiftBuildsARealProceduralShapeFromItsSeedAndSize(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.EntityFluxRift rift =
				new thaumcraft.common.entities.EntityFluxRift(thaumcraft.common.registry.ThaumcraftEntities.FLUX_RIFT.get(), level);
		rift.setRiftSeed(12345);
		rift.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		rift.setRiftSize(30);

		helper.assertTrue(rift.points.size() > 2, "a real-sized rift should have a real procedural point cloud, found " + rift.points.size());
		helper.assertTrue(rift.getBoundingBox().getSize() > 0.0, "the bounding box should be rebuilt from the point cloud's own extent");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fluxRiftCollapsesAndDropsVoidSeedsWhenForced(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(3, 3, 3));
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.EntityFluxRift rift =
				new thaumcraft.common.entities.EntityFluxRift(thaumcraft.common.registry.ThaumcraftEntities.FLUX_RIFT.get(), level);
		rift.setRiftSeed(6789);
		rift.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
		rift.setRiftSize(20);
		level.addFreshEntity(rift);
		rift.setCollapse(true);

		for (int i = 0; i < 60 && rift.isAlive(); i++) {
			rift.tick();
		}
		helper.assertTrue(!rift.isAlive(), "a forced collapse should fully remove the rift within a bounded number of ticks");

		boolean foundVoidSeed = false;
		for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(4))) {
			if (drop.getItem().getItem() == ItemsTC.voidSeed.get()) {
				foundVoidSeed = true;
			}
		}
		helper.assertTrue(foundVoidSeed, "completing a collapse should drop at least one void seed nearby");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// TileFocalManipulator - the first shippable chunk of the Focal Manipulator arc (headless data model +
	// crafting logic only, see that class's own doc comment for the block/menu/screen commits still to
	// come). Directly manipulates the tile's own live `nodes` graph the way a future GUI packet handler
	// eventually will.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focalManipulatorAssemblesARealFocusFromANodeGraphAndCastsIt(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		ServerLevel level = helper.getLevel();
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
		thaumcraft.common.tiles.crafting.TileFocalManipulator tile = helper.getBlockEntity(pos);

		ServerPlayer player = spawnCasterAt(helper, absPos.above());
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "BASEAUROMANCY");
		player.getInventory().add(makeAspectCrystal(thaumcraft.api.aspects.Aspect.AVERSION));
		player.getInventory().add(makeAspectCrystal(thaumcraft.api.aspects.Aspect.FIRE));

		ItemStack blank = new ItemStack(ItemsTC.focusBlank1.get());
		tile.setFocus(blank);

		thaumcraft.common.tiles.crafting.FocusElementNode root = new thaumcraft.common.tiles.crafting.FocusElementNode();
		root.id = 0;
		root.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.TOUCH");
		root.children = new int[] { 1 };
		tile.nodes.put(0, root);

		thaumcraft.common.tiles.crafting.FocusElementNode effect = new thaumcraft.common.tiles.crafting.FocusElementNode();
		effect.id = 1;
		effect.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.FIRE");
		tile.nodes.put(1, effect);

		boolean started = tile.startCraft(player);
		helper.assertTrue(started, "startCraft should succeed once research/vis/crystals/xp are all satisfiable");
		helper.assertTrue(tile.vis > 0.0f, "a successful startCraft should charge the tile with real vis to spend over time");

		for (int i = 0; i < 400 && tile.vis > 0.0f; i++) {
			thaumcraft.common.tiles.crafting.TileFocalManipulator.tick(level, absPos, level.getBlockState(absPos), tile);
		}
		helper.assertTrue(tile.vis <= 0.0f, "the tile should fully drain its charged vis within a bounded number of ticks");

		ItemStack result = tile.getFocus();
		thaumcraft.api.items.ThaumcraftDataComponents.AssembledFocus assembled = result.get(thaumcraft.api.items.ThaumcraftDataComponents.ASSEMBLED_FOCUS.get());
		helper.assertTrue(assembled != null && assembled.focusPackage().nodes.size() == 2,
				"the finished focus should carry a real 2-node assembled package, found " + (assembled == null ? "null" : assembled.focusPackage().nodes.size()));

		thaumcraft.api.casters.FocusPackage rebuilt = ((thaumcraft.common.items.casters.ItemFocus) result.getItem()).getPackage(result);
		helper.assertTrue(rebuilt.nodes.size() == 2, "ItemFocus#getPackage should read the same assembled graph back from the finished item");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focalManipulatorMenuAcceptsAFocusAndReflectsTheTilesSlot(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
		thaumcraft.common.tiles.crafting.TileFocalManipulator tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		thaumcraft.common.container.FocalManipulatorMenu menu = new thaumcraft.common.container.FocalManipulatorMenu(0, player.getInventory(), tile);

		helper.assertTrue(menu.stillValid(player), "the menu should remain valid while the player is near the real placed tile");
		helper.assertTrue(menu.slots.get(0).mayPlace(new ItemStack(ItemsTC.focusBlank1.get())), "slot 0 should accept a real ItemFocus");
		helper.assertTrue(!menu.slots.get(0).mayPlace(new ItemStack(Items.STICK)), "slot 0 should reject a non-focus item");

		ItemStack blank = new ItemStack(ItemsTC.focusBlank1.get());
		tile.setFocus(blank);
		helper.assertTrue(menu.slots.get(0).getItem().is(ItemsTC.focusBlank1.get()),
				"the menu's own slot 0 should reflect a focus set directly on the tile, found " + menu.slots.get(0).getItem());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focalManipulatorMenusCraftButtonTriggersARealStartCraft(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
		thaumcraft.common.tiles.crafting.TileFocalManipulator tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());
		thaumcraft.common.container.FocalManipulatorMenu menu = new thaumcraft.common.container.FocalManipulatorMenu(0, player.getInventory(), tile);

		boolean failed = menu.clickMenuButton(player, 0);
		helper.assertTrue(!failed, "button 0 should refuse to start a craft with no assembled node graph and no focus in the slot");

		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "BASEAUROMANCY");
		player.getInventory().add(makeAspectCrystal(thaumcraft.api.aspects.Aspect.AVERSION));
		player.getInventory().add(makeAspectCrystal(thaumcraft.api.aspects.Aspect.FIRE));
		tile.setFocus(new ItemStack(ItemsTC.focusBlank1.get()));

		thaumcraft.common.tiles.crafting.FocusElementNode root = new thaumcraft.common.tiles.crafting.FocusElementNode();
		root.id = 0;
		root.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.TOUCH");
		root.children = new int[] { 1 };
		tile.nodes.put(0, root);
		thaumcraft.common.tiles.crafting.FocusElementNode effect = new thaumcraft.common.tiles.crafting.FocusElementNode();
		effect.id = 1;
		effect.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.FIRE");
		tile.nodes.put(1, effect);

		boolean started = menu.clickMenuButton(player, 0);
		helper.assertTrue(started, "button 0 should start a real craft once research/vis/crystals/xp are all satisfiable");
		helper.assertTrue(tile.vis > 0.0f, "a successful button-triggered craft should charge the tile with real vis, same as calling startCraft directly");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void savingAFocusGraphPacketAppliesItToWhicheverManipulatorTheSenderHasOpen(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.focalManipulator.get());
		thaumcraft.common.tiles.crafting.TileFocalManipulator tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());
		player.openMenu(tile);
		helper.assertTrue(player.containerMenu instanceof thaumcraft.common.container.FocalManipulatorMenu,
				"opening the tile via the real MenuProvider path should give the player a real FocalManipulatorMenu");

		thaumcraft.common.tiles.crafting.FocusElementNode root = new thaumcraft.common.tiles.crafting.FocusElementNode();
		root.id = 0;
		root.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.TOUCH");
		root.children = new int[] { 1 };
		thaumcraft.common.tiles.crafting.FocusElementNode effect = new thaumcraft.common.tiles.crafting.FocusElementNode();
		effect.id = 1;
		effect.node = (thaumcraft.api.casters.FocusNode) thaumcraft.api.casters.FocusEngine.getElement("thaumcraft.FIRE");
		java.util.List<net.minecraft.nbt.CompoundTag> nodeTags = java.util.List.of(root.serialize(), effect.serialize());

		thaumcraft.common.lib.network.ServerFocalManipulatorHandlers.handleSaveFocusGraph(
				new thaumcraft.common.lib.network.ServerboundSaveFocusGraphPacket(nodeTags, "My Focus"), player);

		helper.assertTrue(tile.nodes.size() == 2, "the saved graph should be applied to the real tile the player has open, found " + tile.nodes.size() + " nodes");
		helper.assertTrue(tile.focusName.equals("My Focus"), "the saved focus name should be applied too, found '" + tile.focusName + "'");
		helper.succeed();
	}

	private static ItemStack makeAspectCrystal(Aspect aspect) {
		ItemStack stack = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) stack.getItem()).setAspects(stack, new AspectList().add(aspect, 1));
		return stack;
	}

	// --------------------------------------------------------------------------------------------------
	// Research Table minigame, chunk 1: the theorycraft engine (ResearchTableData) + a first real,
	// representative slice of the original's ~56 card/aid classes. No block/tile/GUI yet - see
	// ResearchTableData's own doc comment for the rest of the arc.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void freshPlayerGetsBaselineInspirationAndOnlyBasicsIsAvailable(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int inspiration = thaumcraft.api.research.theorycraft.ResearchTableData.getAvailableInspiration(player);
		helper.assertTrue(inspiration == 5, "a fresh player with no research should have exactly the 5 baseline inspiration, found " + inspiration);

		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);
		java.util.ArrayList<String> cats = data.getAvailableCategories(player);
		helper.assertTrue(cats.size() == 1 && cats.contains("BASICS"), "a fresh player should only have the always-unlocked BASICS category available, found " + cats);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void initializeSpendsOneInspirationPerAidAndDrawCardsPullsFromTheRealRegisteredDeck(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);
		data.initialize(player, java.util.Set.of());
		helper.assertTrue(data.inspirationStart == 5 && data.inspiration == 5, "no aids chosen should leave inspiration untouched, found start=" + data.inspirationStart + " current=" + data.inspiration);

		// Most of the registered deck needs existing category progress to even initialize (CardPonder/
		// CardRethink/CardInspired/CardReject/CardBalance all check data.categoryTotals) - a genuinely
		// fresh session only has CardExperimentation (no initialize override) actually drawable, so seed
		// some real progress first, matching what a real session looks like a few draws in.
		data.categoryTotals.put("BASICS", 20);
		data.drawCards(2, player);
		helper.assertTrue(data.cardChoices.size() == 2, "drawing 2 with some real category progress already present should produce exactly 2 real card choices, found " + data.cardChoices.size());
		for (thaumcraft.api.research.theorycraft.ResearchTableData.CardChoice cc : data.cardChoices) {
			helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.cards.containsKey(cc.key), "every drawn card should come from the real registered deck, found key " + cc.key);
			helper.assertTrue(!cc.card.isAidOnly(), "a normal (non-aid) draw should never surface an aid-only card, found " + cc.key);
		}
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardStudyAddsRealProgressToTheOnlyAvailableCategory(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.api.research.theorycraft.CardStudy card = new thaumcraft.api.research.theorycraft.CardStudy();
		card.setSeed(1234L);
		helper.assertTrue(card.initialize(player, data), "CardStudy should always be able to initialize against a fresh player's BASICS-only category list");
		helper.assertTrue(card.getResearchCategory().equals("BASICS"), "with only BASICS available, CardStudy should always target it, found " + card.getResearchCategory());

		helper.assertTrue(card.activate(player, data), "CardStudy#activate should succeed");
		int total = data.getTotal("BASICS");
		helper.assertTrue(total >= 15 && total <= 25, "CardStudy should add 15-25 real progress to BASICS, found " + total);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardAnalyzeFindsARealCandidateOnceObservationKnowledgeExists(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.api.research.theorycraft.CardAnalyze card = new thaumcraft.api.research.theorycraft.CardAnalyze();
		card.setSeed(1L);
		helper.assertTrue(!card.initialize(player, data), "with zero OBSERVATION knowledge anywhere, CardAnalyze should have no real candidate category");

		// IPlayerKnowledge#getKnowledge divides the raw stored amount by EnumKnowledgeType#getProgression()
		// (16 for OBSERVATION) and floors it - a real amount needs to clear one full progression step to
		// register as ">= 1", not just be non-zero.
		thaumcraft.api.research.ResearchCategory alchemy = thaumcraft.api.research.ResearchCategories.getResearchCategory("ALCHEMY");
		thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player)
				.addKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.OBSERVATION, alchemy, 20);

		helper.assertTrue(card.initialize(player, data), "once a category has real OBSERVATION knowledge, CardAnalyze should find it as a candidate - this is the real 'always checked null' bug fixed while porting");
		helper.assertTrue(card.getResearchCategory().equals("ALCHEMY"), "the only real candidate should be ALCHEMY, found " + card.getResearchCategory());

		helper.assertTrue(card.activate(player, data), "CardAnalyze#activate should succeed once the category has real observation knowledge");
		helper.assertTrue(data.getTotal("ALCHEMY") >= 25 && data.getTotal("BASICS") == 5,
				"a successful analyze should add 25-50 progress to the analyzed category and 5 to BASICS, found ALCHEMY=" + data.getTotal("ALCHEMY") + " BASICS=" + data.getTotal("BASICS"));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchTableConsumesInkAndPaperAndTracksARealTheory(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		tile.getItemHandler().setStackInSlot(0, new ItemStack(ItemsTC.scribingTools.get()));
		tile.getItemHandler().setStackInSlot(1, new ItemStack(Items.PAPER, 5));

		helper.assertTrue(tile.consumeInkFromTable(), "consuming ink should succeed with real scribing tools in slot 0");
		helper.assertTrue(tile.getInk().getDamageValue() == 1, "consuming ink should damage the scribing tools by exactly 1, found " + tile.getInk().getDamageValue());

		helper.assertTrue(tile.consumePaperFromTable(), "consuming paper should succeed with real paper in slot 1");
		helper.assertTrue(tile.getPaper().getCount() == 4, "consuming paper should remove exactly 1 sheet, found " + tile.getPaper().getCount());

		helper.assertTrue(tile.data == null, "no theory should be in progress yet");
		tile.startNewTheory(player, java.util.Set.of());
		helper.assertTrue(tile.data != null && tile.data.inspirationStart == 5, "starting a new theory with no aids should give the real 5 baseline inspiration, found " + (tile.data == null ? "null" : tile.data.inspirationStart));

		tile.data.categoryTotals.put("BASICS", 100);
		tile.finishTheory(player);
		helper.assertTrue(tile.data == null, "finishing a theory should clear it");
		int theoryKnowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player)
				.getKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.THEORY, thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS"));
		helper.assertTrue(theoryKnowledge >= 1, "a full 100% BASICS theory should grant at least 1 real THEORY knowledge point, found " + theoryKnowledge);

		helper.assertTrue(tile.checkSurroundingAids().isEmpty(), "no aids are registered yet in this port, so the real (working) scan should always come back empty for now");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchTableMenuDrawsPicksAndConfirmsARealCardThroughItsButtons(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		tile.getItemHandler().setStackInSlot(0, new ItemStack(ItemsTC.scribingTools.get()));
		tile.getItemHandler().setStackInSlot(1, new ItemStack(Items.PAPER, 5));
		tile.startNewTheory(player, java.util.Set.of());
		tile.data.categoryTotals.put("BASICS", 20); // gives most of the deck a real candidate to draw, same lesson as the engine-only test above

		thaumcraft.common.container.ResearchTableMenu menu = new thaumcraft.common.container.ResearchTableMenu(0, player.getInventory(), tile);
		helper.assertTrue(menu.stillValid(player), "the menu should remain valid while the player is near the real placed table");

		helper.assertTrue(menu.clickMenuButton(player, 2), "button 2 should draw 2 real cards, consuming 1 paper");
		helper.assertTrue(tile.data.cardChoices.size() == 2, "drawing should leave exactly 2 real card choices, found " + tile.data.cardChoices.size());
		helper.assertTrue(tile.getPaper().getCount() == 4, "drawing should have consumed exactly 1 paper, found " + tile.getPaper().getCount());

		int inspirationBefore = tile.data.inspiration;
		helper.assertTrue(menu.clickMenuButton(player, 4), "button 4 should pick and activate the first real card choice");
		helper.assertTrue(tile.data.cardChoices.get(0).selected, "the picked choice should be marked selected");
		helper.assertTrue(tile.data.inspiration < inspirationBefore || tile.data.cardChoices.get(0).card.getInspirationCost() <= 0,
				"picking a positive-cost card should spend real inspiration, before=" + inspirationBefore + " after=" + tile.data.inspiration);

		helper.assertTrue(menu.clickMenuButton(player, 1), "button 1 should confirm the current selection");
		helper.assertTrue(tile.data.cardChoices.isEmpty(), "confirming should clear the drawn choices");
		helper.assertTrue(tile.data.lastDraw != null, "confirming should record the selected choice as the last draw");

		tile.data.inspiration = 0;
		helper.assertTrue(!menu.clickMenuButton(player, 9), "button 9 (abandon) should refuse once the theory is already complete");
		helper.assertTrue(menu.clickMenuButton(player, 7), "button 7 should finish a complete theory");
		helper.assertTrue(tile.data == null, "finishing should clear the theory");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchTableMenuAbandonClearsAnIncompleteTheory(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		tile.startNewTheory(player, java.util.Set.of());
		thaumcraft.common.container.ResearchTableMenu menu = new thaumcraft.common.container.ResearchTableMenu(0, player.getInventory(), tile);

		helper.assertTrue(tile.data != null && !tile.data.isComplete(), "a fresh theory should not already be complete");
		helper.assertTrue(menu.clickMenuButton(player, 9), "button 9 should abandon an incomplete theory");
		helper.assertTrue(tile.data == null, "abandoning should clear the theory");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchTableMenuButtonZeroStartsARealTheoryAndSyncsWithoutCrashing(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		// constructing the menu (real MenuProvider path, matches how the block actually opens it) already
		// sends an initial sync - a mock player with no real client connection should never let that crash
		// menu construction itself, same "cosmetic packet failure never takes down the real feature"
		// precedent every other network handler in this port already established.
		thaumcraft.common.container.ResearchTableMenu menu = new thaumcraft.common.container.ResearchTableMenu(0, player.getInventory(), tile);
		helper.assertTrue(tile.data == null, "no theory should exist before button 0 is clicked");

		helper.assertTrue(menu.clickMenuButton(player, 0), "button 0 should start a real theory (each mutating action re-syncs, which must not crash against a mock player either)");
		helper.assertTrue(tile.data != null, "starting a theory via the menu button should leave a real theory in progress");
		helper.assertTrue(!menu.clickMenuButton(player, 0), "button 0 should refuse to start a second theory while one is already in progress");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Golem construction, chunk 1: the real part registry + GolemProperties data model - headless, no
	// entity/builder block/menu/GUI yet. See GolemProperties' own doc comment for what's simplified.
	// --------------------------------------------------------------------------------------------------

	private static <T> T findPartByKey(T[] parts, java.util.function.Function<T, String> keyOf, String key) {
		for (T part : parts) {
			if (keyOf.apply(part).equals(key)) return part;
		}
		throw new IllegalStateException("no part registered with key " + key);
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemPropertiesUnionsRealTraitsAndCancelsOpposites(GameTestHelper helper) {
		thaumcraft.api.golems.parts.GolemMaterial iron = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "IRON");
		thaumcraft.api.golems.parts.GolemHead smart = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.parts.GolemArm fine = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "FINE");
		thaumcraft.api.golems.parts.GolemLeg walker = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon armored = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "ARMORED");

		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(iron);
		props.setHead(smart);
		props.setArms(fine);
		props.setLegs(walker);
		props.setAddon(armored);

		// IRON = HEAVY/FIREPROOF/BLASTPROOF, SMART head = SMART/FRAGILE, FINE arms = DEFT/FRAGILE,
		// ARMORED addon = ARMORED/HEAVY. FRAGILE (head+arms) and ARMORED (addon) are real opposites - the
		// original's own addTraitSmart only ever *removes* the opposite already present, it never adds the
		// cancelling trait itself, so ARMORED is spent purely cancelling FRAGILE out and never appears in
		// the final set either (a real, slightly surprising rule worth a regression test on its own).
		java.util.Set<thaumcraft.api.golems.EnumGolemTrait> traits = props.getTraits();
		helper.assertTrue(traits.contains(thaumcraft.api.golems.EnumGolemTrait.HEAVY), "IRON material should contribute HEAVY, found " + traits);
		helper.assertTrue(traits.contains(thaumcraft.api.golems.EnumGolemTrait.SMART), "SMART head should contribute SMART, found " + traits);
		helper.assertTrue(traits.contains(thaumcraft.api.golems.EnumGolemTrait.DEFT), "FINE arms should contribute DEFT, found " + traits);
		helper.assertTrue(!traits.contains(thaumcraft.api.golems.EnumGolemTrait.ARMORED), "ARMORED should be spent cancelling FRAGILE, never added itself, found " + traits);
		helper.assertTrue(!traits.contains(thaumcraft.api.golems.EnumGolemTrait.FRAGILE), "ARMORED (added last) should have cancelled out the earlier FRAGILE traits, found " + traits);

		helper.assertTrue(props.hasTrait(thaumcraft.api.golems.EnumGolemTrait.HEAVY), "hasTrait should agree with getTraits()");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemPropertiesRoundTripsThroughALongAndGeneratesRealComponents(GameTestHelper helper) {
		thaumcraft.api.golems.parts.GolemMaterial wood = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "WOOD");
		thaumcraft.api.golems.parts.GolemHead basic = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "BASIC");
		thaumcraft.api.golems.parts.GolemArm basicArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BASIC");
		thaumcraft.api.golems.parts.GolemLeg walker = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon none = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "NONE");

		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(wood);
		props.setHead(basic);
		props.setArms(basicArm);
		props.setLegs(walker);
		props.setAddon(none);
		props.setRank(3);

		long packed = props.toLong();
		thaumcraft.api.golems.IGolemProperties restored = thaumcraft.common.golems.GolemProperties.fromLong(packed);
		helper.assertTrue(restored.getMaterial() == wood && restored.getHead() == basic && restored.getArms() == basicArm
				&& restored.getLegs() == walker && restored.getAddon() == none && restored.getRank() == 3,
				"packing into a long and unpacking it again should restore every real part and the rank exactly");

		ItemStack[] components = props.generateComponents();
		boolean foundMechanism = false;
		for (ItemStack stack : components) {
			if (stack.is(ItemsTC.mechanismSimple.get())) foundMechanism = true;
		}
		helper.assertTrue(components.length > 0 && foundMechanism, "generateComponents should return a real, non-empty component list including the material's own mechanism, found " + components.length + " entries");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Golem construction, chunk 2: the real living entity - see EntityThaumcraftGolem's own doc comment
	// for what's simplified (no task/seal system, no custom pathfinding, no placer/bell items yet).
	// --------------------------------------------------------------------------------------------------

	private static thaumcraft.common.golems.EntityThaumcraftGolem spawnGolem(GameTestHelper helper, BlockPos origin) {
		ServerLevel level = helper.getLevel();
		thaumcraft.common.golems.EntityThaumcraftGolem golem = thaumcraft.common.registry.ThaumcraftEntities.GOLEM.get().create(level);
		golem.moveTo(origin.getX() + 0.5, origin.getY() + 1.0, origin.getZ() + 0.5, 0, 0);
		level.addFreshEntity(golem);
		return golem;
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemEntityAttributesAreDrivenByItsAssembledParts(GameTestHelper helper) {
		thaumcraft.api.golems.parts.GolemMaterial iron = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "IRON");
		thaumcraft.api.golems.parts.GolemHead basic = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "BASIC");
		thaumcraft.api.golems.parts.GolemArm basicArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BASIC");
		thaumcraft.api.golems.parts.GolemLeg walker = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon fighter = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "FIGHTER");

		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(iron);
		props.setHead(basic);
		props.setArms(basicArm);
		props.setLegs(walker);
		props.setAddon(fighter);
		props.setRank(2);

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, origin);
		golem.initializeGolem(props, 0);

		// synced-data round trip: getProperties() must reflect exactly what was assembled.
		thaumcraft.api.golems.IGolemProperties restored = golem.getProperties();
		helper.assertTrue(restored.getMaterial() == iron && restored.getHead() == basic && restored.getArms() == basicArm
				&& restored.getLegs() == walker && restored.getAddon() == fighter && restored.getRank() == 2,
				"getProperties() should round-trip exactly what initializeGolem() assembled");

		// IRON healthMod=20, no FRAGILE trait in this loadout -> 10 + 20 + rank(2) = 32.
		helper.assertTrue(golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getValue() == 32.0,
				"max health should be 10 + material.healthMod + rank, was " + golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getValue());

		// FIGHTER addon -> real attack damage: IRON damage=3 + rank(2)*0.25 = 3.5.
		helper.assertTrue(golem.getProperties().hasTrait(thaumcraft.api.golems.EnumGolemTrait.FIGHTER), "FIGHTER addon should contribute the FIGHTER trait");
		double attack = golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE).getValue();
		helper.assertTrue(Math.abs(attack - 3.5) < 0.001, "FIGHTER golem should have real attack damage from its material + rank, was " + attack);

		// no SCOUT trait in this loadout -> follow range stays the un-boosted 40.
		helper.assertTrue(golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getValue() == 40.0,
				"follow range should be 40 without SCOUT, was " + golem.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE).getValue());

		helper.assertTrue(golem.hasRestriction(), "initializeGolem() should anchor a home restriction at the build site");
		helper.succeed();
	}

	/** {@link thaumcraft.api.golems.IGolemAPI#hasTrait} isn't part of the interface - convenience delegate
	 * used only by this test file. */
	private static boolean hasTrait(thaumcraft.common.golems.EntityThaumcraftGolem golem, thaumcraft.api.golems.EnumGolemTrait trait) {
		return golem.getProperties().hasTrait(trait);
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemEntityHoldsAndDropsCarriedItemsRespectingHaulerCapacity(GameTestHelper helper) {
		thaumcraft.api.golems.parts.GolemMaterial wood = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "WOOD");
		thaumcraft.api.golems.parts.GolemHead basic = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "BASIC");
		thaumcraft.api.golems.parts.GolemArm basicArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BASIC");
		thaumcraft.api.golems.parts.GolemLeg walker = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon hauler = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "HAULER");

		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(wood);
		props.setHead(basic);
		props.setArms(basicArm);
		props.setLegs(walker);
		props.setAddon(hauler);

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, origin);
		golem.initializeGolem(props, 0);
		helper.assertTrue(hasTrait(golem, thaumcraft.api.golems.EnumGolemTrait.HAULER), "HAULER addon should contribute the HAULER trait");

		ItemStack cobble = new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64);
		ItemStack leftover = golem.holdItem(cobble.copy());
		helper.assertTrue(leftover.isEmpty(), "a single stack should fit entirely in the first carry slot, leftover was " + leftover);
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE)), "golem should now be carrying cobblestone");

		ItemStack secondLeftover = golem.holdItem(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		helper.assertTrue(secondLeftover.isEmpty(), "HAULER golem has a second carry slot, a second full stack should also fit entirely");

		ItemStack thirdLeftover = golem.holdItem(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64));
		helper.assertTrue(thirdLeftover.getCount() == 64, "both carry slots are full - a third stack should come back entirely unaccepted, was " + thirdLeftover);

		ItemStack dropped = golem.dropItem(ItemStack.EMPTY);
		helper.assertTrue(dropped.is(net.minecraft.world.item.Items.COBBLESTONE) && dropped.getCount() == 64, "dropItem(EMPTY) should hand back one full carried stack, was " + dropped);
		helper.assertTrue(golem.canCarry(new ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 64), false), "freeing one slot should make room for another full stack again");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemEntityRoundTripsThroughSaveData(GameTestHelper helper) {
		thaumcraft.api.golems.parts.GolemMaterial clay = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "CLAY");
		thaumcraft.api.golems.parts.GolemHead basic = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "BASIC");
		thaumcraft.api.golems.parts.GolemArm basicArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BASIC");
		thaumcraft.api.golems.parts.GolemLeg walker = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon none = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "NONE");

		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(clay);
		props.setHead(basic);
		props.setArms(basicArm);
		props.setLegs(walker);
		props.setAddon(none);
		props.setRank(4);

		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		thaumcraft.common.golems.EntityThaumcraftGolem original = spawnGolem(helper, origin);
		original.initializeGolem(props, 750);
		original.setGolemColor((byte) 9);
		original.setFollowingOwner(true);

		net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
		original.addAdditionalSaveData(tag);

		thaumcraft.common.golems.EntityThaumcraftGolem restored = spawnGolem(helper, origin.offset(3, 0, 0));
		restored.readAdditionalSaveData(tag);

		helper.assertTrue(restored.getProperties().getMaterial() == clay && restored.getProperties().getRank() == 4,
				"restored golem should have the same parts and rank as the saved one");
		helper.assertTrue(restored.getGolemColor() == 9, "restored golem should have the same colour, was " + restored.getGolemColor());
		helper.assertTrue(restored.isFollowingOwner(), "restored golem should remember it was set to follow its owner");
		helper.assertTrue(restored.hasRestriction() && restored.getRestrictCenter().equals(original.getRestrictCenter()),
				"restored golem should remember its home restriction centre");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemPlacerPlacesARealTamedGolemWithStoredProperties(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.api.golems.parts.GolemMaterial iron = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "IRON");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(iron);

		ItemStack placer = new ItemStack(ItemsTC.golemPlacer.get());
		placer.set(thaumcraft.api.items.ThaumcraftDataComponents.GOLEM_PLACER_DATA.get(),
				new thaumcraft.api.items.ThaumcraftDataComponents.GolemPlacerData(props.toLong(), 500));

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		player.getAbilities().instabuild = false; // mock players default to creative, which skips item consumption
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, placer);

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "placing against a solid block should succeed, got: " + result);
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the single placer stack should be fully consumed");

		List<thaumcraft.common.golems.EntityThaumcraftGolem> found = helper.getLevel().getEntitiesOfClass(
				thaumcraft.common.golems.EntityThaumcraftGolem.class, new AABB(floorAbs).inflate(3));
		helper.assertTrue(found.size() == 1, "exactly one golem should have been spawned, found " + found.size());
		thaumcraft.common.golems.EntityThaumcraftGolem golem = found.get(0);

		helper.assertTrue(golem.isOwnedBy(player), "the golem should be tamed/owned by the placing player");
		helper.assertTrue(golem.getProperties().getMaterial() == iron, "the spawned golem should have the properties stored on the placer item");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemBuilderCraftsAPlacerFromSelectedPartsAndEssentia(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.golemBuilder.get());
		thaumcraft.common.tiles.crafting.TileGolemBuilder tile = helper.getBlockEntity(pos);

		ServerPlayer player = spawnCasterAt(helper, abs.above(2));
		player.openMenu(tile);
		helper.assertTrue(player.containerMenu instanceof thaumcraft.common.container.GolemBuilderMenu,
				"opening the tile via the real MenuProvider path should give the player a real GolemBuilderMenu");
		thaumcraft.common.container.GolemBuilderMenu menu = (thaumcraft.common.container.GolemBuilderMenu) player.containerMenu;

		menu.clickMenuButton(player, 0); // cycle material -> index 1
		menu.clickMenuButton(player, 1); // cycle head -> index 1

		thaumcraft.api.golems.IGolemProperties expected = menu.buildSelectedProperties();
		for (ItemStack stack : expected.generateComponents()) {
			player.getInventory().add(new ItemStack(stack.getItem(), 64));
		}

		boolean started = menu.clickMenuButton(player, 5);
		helper.assertTrue(started, "starting a craft with sufficient components in the player's inventory should succeed");
		helper.assertTrue(tile.golem == expected.toLong(), "the tile should now be building exactly the selected properties");
		helper.assertTrue(tile.cost > 0 && tile.cost == tile.maxCost, "cost should be initialized and equal to maxCost at the very start");

		int initialCost = tile.cost;
		for (int i = 0; i < (initialCost + 2) * 6 && tile.golem >= 0L; i++) {
			tile.addEssentia(thaumcraft.api.aspects.Aspect.MECHANISM, 1, net.minecraft.core.Direction.UP);
			thaumcraft.common.tiles.crafting.TileGolemBuilder.tick(helper.getLevel(), abs, helper.getLevel().getBlockState(abs), tile);
		}

		helper.assertTrue(tile.golem < 0L && tile.cost == 0, "the build should have completed within a generous tick budget");
		ItemStack output = tile.getItemHandler().getStackInSlot(0);
		helper.assertTrue(!output.isEmpty() && output.getItem() == ItemsTC.golemPlacer.get(), "the output slot should now hold a real golem placer");
		thaumcraft.api.items.ThaumcraftDataComponents.GolemPlacerData data = output.get(thaumcraft.api.items.ThaumcraftDataComponents.GOLEM_PLACER_DATA.get());
		helper.assertTrue(data != null && data.props() == expected.toLong(), "the placer's stored properties should match what was selected");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Golem construction, chunk 5: the real seals/tasks subsystem - GolemHelper's own real state (no more
	// permanent stub), TaskHandler, SealHandler/SealEntity, the first real ISeal (SealGuard), and the golem
	// AI (AIGotoEntity) that actually consumes the tasks a placed seal creates. See each class's own doc
	// comment for what's simplified.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealGuardPlacementCreatesTasksThatAGolemPicksUpAsARealCombatTarget(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		player.getAbilities().instabuild = false;
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.sealGuard.get()));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(context);
		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "placing a seal against a solid block should succeed, got: " + result);
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the single seal stack should be consumed");

		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(floorAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);
		helper.assertTrue(sealEntity != null && sealEntity.getSeal() instanceof thaumcraft.common.golems.seals.SealGuard,
				"a real SealGuard entity should now be registered at the placed position");

		net.minecraft.world.entity.monster.Zombie zombie = new net.minecraft.world.entity.monster.Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, helper.getLevel());
		zombie.moveTo(floorAbs.getX() + 0.5, floorAbs.getY() + 1.0, floorAbs.getZ() + 0.5, 0, 0);
		helper.getLevel().addFreshEntity(zombie);

		// SealGuard#tickSeal only scans every 20th call, starting from a random 0-21 offset - loop enough
		// times to guarantee crossing that boundary at least once, rather than assuming tick 1 lands on it.
		boolean foundTask = false;
		for (int i = 0; i < 25 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getEntity() == zombie) foundTask = true;
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real task targeting the zombie");

		thaumcraft.api.golems.parts.GolemAddon fighter = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "FIGHTER");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setAddon(fighter);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, floorAbs);
		golem.initializeGolem(props, 0);

		thaumcraft.common.golems.ai.AIGotoEntity goal = new thaumcraft.common.golems.ai.AIGotoEntity(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the guard task");
		helper.assertTrue(golem.getTarget() == zombie,
				"picking up a guard task should immediately set the zombie as the golem's real combat target (SealGuard#onTaskStarted)");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void removingASealSuspendsItsOwnTasks(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(pos, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealGuard();
		ServerPlayer owner = spawnCasterAt(helper, pos.above(2));
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), pos, net.minecraft.core.Direction.UP, seal, owner),
				"adding a fresh seal entity should succeed");

		thaumcraft.api.golems.tasks.Task task = new thaumcraft.api.golems.tasks.Task(sealPos, pos);
		thaumcraft.common.golems.tasks.TaskHandler.addTask(helper.getLevel().dimension(), task);
		helper.assertTrue(!task.isSuspended(), "a freshly created task should not start suspended");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos);
		helper.assertTrue(thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos) == null, "the seal should be gone after removal");
		helper.assertTrue(task.isSuspended(), "removing a seal should suspend every task still tied to it");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taskHandlerSortsUnreservedTasksByPriorityWeightedDistance(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer stand = spawnCasterAt(helper, origin);

		thaumcraft.api.golems.tasks.Task near = new thaumcraft.api.golems.tasks.Task(null, origin.offset(2, 0, 0));
		thaumcraft.api.golems.tasks.Task far = new thaumcraft.api.golems.tasks.Task(null, origin.offset(20, 0, 0));
		far.setPriority((byte) 5);
		thaumcraft.common.golems.tasks.TaskHandler.addTask(helper.getLevel().dimension(), near);
		thaumcraft.common.golems.tasks.TaskHandler.addTask(helper.getLevel().dimension(), far);

		java.util.ArrayList<thaumcraft.api.golems.tasks.Task> sorted =
				thaumcraft.common.golems.tasks.TaskHandler.getBlockTasksSorted(helper.getLevel().dimension(), null, stand);
		helper.assertTrue(sorted.contains(near) && sorted.contains(far), "both real unreserved tasks should be returned");
		helper.assertTrue(sorted.indexOf(far) < sorted.indexOf(near),
				"a high enough priority should let a farther task sort ahead of a closer, lower-priority one");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void placedSealsRoundTripThroughRealSaveAndLoad(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer owner = spawnCasterAt(helper, pos.above(2));
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealGuard();
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(pos, net.minecraft.core.Direction.UP);
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), pos, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");

		// Same mechanism a real level load uses (Thaumcraft.onLevelLoad) - idempotent, safe to call again
		// here to guarantee the SealSavedData instance backing this dimension knows its own dimension key.
		thaumcraft.common.golems.seals.SealHandler.loadForLevel(helper.getLevel());
		thaumcraft.common.golems.seals.SealSavedData data =
				helper.getLevel().getDataStorage().computeIfAbsent(thaumcraft.common.golems.seals.SealSavedData.FACTORY, thaumcraft.common.golems.seals.SealSavedData.id());

		net.minecraft.nbt.CompoundTag tag = data.save(new net.minecraft.nbt.CompoundTag(), helper.getLevel().registryAccess());
		thaumcraft.common.golems.seals.SealSavedData restored = thaumcraft.common.golems.seals.SealSavedData.load(tag, helper.getLevel().registryAccess());

		boolean found = false;
		for (thaumcraft.api.golems.seals.ISealEntity restoredEntity : restored.getLoaded()) {
			if (restoredEntity.getSealPos().equals(sealPos) && restoredEntity.getSeal() instanceof thaumcraft.common.golems.seals.SealGuard) {
				found = true;
			}
		}
		helper.assertTrue(found, "saving and reloading should round-trip the placed seal's real position and type");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealLumberDetectsLogsAndAGolemChopsThemDownForReal(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		BlockPos logPos = floorPos.above();
		BlockPos logAbs = helper.absolutePos(logPos);
		helper.setBlock(logPos, net.minecraft.world.level.block.Blocks.OAK_LOG);

		ServerPlayer owner = spawnCasterAt(helper, floorAbs.above(3));
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealLumber();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), floorAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(floorAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task logTask = null;
		for (int i = 0; i < 40 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getPos().equals(logAbs)) {
					foundTask = true;
					logTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real chop task for the log");

		thaumcraft.api.golems.parts.GolemArm breakerArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BREAKERS");
		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setArms(breakerArm);
		props.setHead(smartHead);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, floorAbs);
		golem.initializeGolem(props, 0);

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the chop task");
		helper.assertTrue(golem.getTask() == logTask, "the golem should have reserved the real chop task");

		// Exercises the real completion path directly (the same call TaskHandler.completeTask makes once a
		// golem is actually adjacent) rather than ticking real pathfinding to convergence in a GameTest.
		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(helper.getLevel().getBlockState(logAbs).isAir(), "completing the chop task should have really broken the log");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemBellRemovesASealOnlyWhenSneaking(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealGuard();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), floorAbs, net.minecraft.core.Direction.UP, seal, player),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(floorAbs, net.minecraft.core.Direction.UP);

		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.golemBell.get()));
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);

		net.minecraft.world.item.context.UseOnContext notSneaking = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		player.getItemInHand(hand).getItem().useOn(notSneaking);
		helper.assertTrue(thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos) != null,
				"a non-sneak click should not remove the seal");

		player.setShiftKeyDown(true);
		net.minecraft.world.item.context.UseOnContext sneaking = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(sneaking);
		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "a sneak click on a real seal should succeed, got: " + result);
		helper.assertTrue(thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos) == null,
				"a sneak click should have removed the seal");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealHarvestDetectsGrownCropsAndAGolemHarvestsAndReplantsThem(GameTestHelper helper) {
		BlockPos dirtPos = BlockPos.ZERO;
		BlockPos dirtAbs = helper.absolutePos(dirtPos);
		helper.setBlock(dirtPos, net.minecraft.world.level.block.Blocks.FARMLAND);

		BlockPos cropPos = dirtPos.above();
		BlockPos cropAbs = helper.absolutePos(cropPos);
		helper.setBlock(cropPos, net.minecraft.world.level.block.Blocks.WHEAT.defaultBlockState()
				.setValue(net.minecraft.world.level.block.CropBlock.AGE, 7));

		ServerPlayer owner = spawnCasterAt(helper, dirtAbs.above(3));
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealHarvest();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), dirtAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(dirtAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		// SealHarvest#tickSeal only samples a new area position every 5th call (unlike SealLumber, which
		// samples every call), and getPosInArea walks a different offset each time - loop generously to
		// guarantee covering the whole area at least once rather than assuming a short loop lands on it.
		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task cropTask = null;
		for (int i = 0; i < 500 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getPos().equals(cropAbs)) {
					foundTask = true;
					cropTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real harvest task for the grown wheat");

		thaumcraft.api.golems.parts.GolemArm fineArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "FINE");
		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setArms(fineArm);
		props.setHead(smartHead);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, dirtAbs);
		golem.initializeGolem(props, 0);

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the harvest task");
		helper.assertTrue(golem.getTask() == cropTask, "the golem should have reserved the real harvest task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		int age = helper.getLevel().getBlockState(cropAbs).getValue(net.minecraft.world.level.block.CropBlock.AGE);
		helper.assertTrue(helper.getLevel().getBlockState(cropAbs).is(net.minecraft.world.level.block.Blocks.WHEAT) && age == 0,
				"completing the harvest task should have broken the grown wheat and replanted it at age 0, found age " + age);

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealButcherCullsOverpopulatedAnimalsAsARealCombatTarget(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer owner = spawnCasterAt(helper, floorAbs.above(4));
		thaumcraft.api.golems.seals.ISeal seal = new thaumcraft.common.golems.seals.SealButcher();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), floorAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(floorAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		net.minecraft.world.entity.animal.Pig[] pigs = new net.minecraft.world.entity.animal.Pig[4];
		for (int i = 0; i < pigs.length; i++) {
			net.minecraft.world.entity.animal.Pig pig = new net.minecraft.world.entity.animal.Pig(net.minecraft.world.entity.EntityType.PIG, helper.getLevel());
			pig.moveTo(floorAbs.getX() + 0.5 + i * 0.6, floorAbs.getY() + 1.0, floorAbs.getZ() + 0.5, 0, 0);
			pig.setAge(0); // adult - a baby pig must never be a valid cull target
			helper.getLevel().addFreshEntity(pig);
			pigs[i] = pig;
		}

		// SealButcher#tickSeal gates on a %200 cadence starting from a random 0-199 offset - loop generously.
		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task cullTask = null;
		for (int i = 0; i < 250 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getEntity() instanceof net.minecraft.world.entity.animal.Pig) {
					foundTask = true;
					cullTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real cull task once 3+ adult pigs were present");

		thaumcraft.api.golems.parts.GolemAddon fighter = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "FIGHTER");
		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setAddon(fighter);
		props.setHead(smartHead);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, floorAbs);
		golem.initializeGolem(props, 0);

		thaumcraft.common.golems.ai.AIGotoEntity goal = new thaumcraft.common.golems.ai.AIGotoEntity(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the cull task");
		helper.assertTrue(golem.getTarget() == cullTask.getEntity(),
				"picking up a cull task should immediately set the pig as the golem's real combat target");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealBreakerMinesBlacklistedBlocksAndRespectsARealWhitelist(GameTestHelper helper) {
		BlockPos anchorPos = BlockPos.ZERO;
		BlockPos anchorAbs = helper.absolutePos(anchorPos);
		helper.setBlock(anchorPos, net.minecraft.world.level.block.Blocks.STONE);

		BlockPos targetPos = anchorPos.above();
		BlockPos targetAbs = helper.absolutePos(targetPos);
		helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.COBBLESTONE);

		ServerPlayer owner = spawnCasterAt(helper, anchorAbs.above(3));
		thaumcraft.common.golems.seals.SealBreaker seal = new thaumcraft.common.golems.seals.SealBreaker();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), anchorAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(anchorAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task breakTask = null;
		for (int i = 0; i < 60 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getPos().equals(targetAbs)) {
					foundTask = true;
					breakTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "an empty blacklist should match nothing, so the cobblestone should get a real break task");

		thaumcraft.api.golems.parts.GolemArm breakerArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BREAKERS");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setArms(breakerArm);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, anchorAbs);
		golem.initializeGolem(props, 0);

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the break task");
		helper.assertTrue(golem.getTask() == breakTask, "the golem should have reserved the real break task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(helper.getLevel().getBlockState(targetAbs).isAir(), "completing the break task should have really broken the cobblestone");

		// A real whitelist that only matches dirt should refuse a cobblestone target.
		helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.COBBLESTONE);
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.DIRT));
		thaumcraft.api.golems.tasks.Task whitelistCheck = new thaumcraft.api.golems.tasks.Task(sealPos, targetAbs);
		helper.assertTrue(!seal.canGolemPerformTask(golem, whitelistCheck),
				"a whitelist that only matches dirt should refuse a cobblestone target");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealPickupCollectsMatchingDroppedItemsForReal(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer owner = spawnCasterAt(helper, floorAbs.above(3));
		thaumcraft.common.golems.seals.SealPickup seal = new thaumcraft.common.golems.seals.SealPickup();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), floorAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(floorAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		net.minecraft.world.entity.item.ItemEntity dropped = new net.minecraft.world.entity.item.ItemEntity(
				helper.getLevel(), floorAbs.getX() + 0.5, floorAbs.getY() + 1.0, floorAbs.getZ() + 0.5,
				new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 3));
		dropped.setOnGround(true);
		helper.getLevel().addFreshEntity(dropped);

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task pickupTask = null;
		for (int i = 0; i < 20 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getEntity() == dropped) {
					foundTask = true;
					pickupTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real pickup task for the dropped item");

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, floorAbs);
		golem.initializeGolem(new thaumcraft.common.golems.GolemProperties(), 0);

		thaumcraft.common.golems.ai.AIGotoEntity goal = new thaumcraft.common.golems.ai.AIGotoEntity(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the pickup task");
		helper.assertTrue(golem.getTask() == pickupTask, "the golem should have reserved the real pickup task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(!dropped.isAlive(), "completing the pickup task should have really removed the dropped item entity");
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"the golem should now really be carrying the picked-up item");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealProvideDeliversStockedItemsFromOneContainerToAnother(GameTestHelper helper) {
		BlockPos provideChestPos = BlockPos.ZERO;
		BlockPos provideChestAbs = helper.absolutePos(provideChestPos);
		helper.setBlock(provideChestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity provideChest = helper.getBlockEntity(provideChestPos);
		provideChest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 10));

		BlockPos destChestPos = provideChestPos.offset(3, 0, 0);
		BlockPos destChestAbs = helper.absolutePos(destChestPos);
		helper.setBlock(destChestPos, net.minecraft.world.level.block.Blocks.CHEST);

		ServerPlayer owner = spawnCasterAt(helper, provideChestAbs.above(3));
		thaumcraft.common.golems.seals.SealProvide seal = new thaumcraft.common.golems.seals.SealProvide();
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), provideChestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(provideChestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		thaumcraft.api.golems.GolemHelper.requestProvisioning(helper.getLevel(), destChestAbs, net.minecraft.core.Direction.UP,
				new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 4));

		// GolemHelper's per-dimension task/provisioning maps are shared, dimension-wide state across every
		// concurrently-running GameTest - always scope a search back to our own sealPos, never assume the
		// first structurally-matching task found is ours (a real cross-test-interference bug caught here).
		boolean foundFetch = false;
		thaumcraft.api.golems.tasks.Task fetchTask = null;
		for (int i = 0; i < 30 && !foundFetch; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (t.getLinkedProvision() != null && t.getData() == 0 && sealPos.equals(t.getSealPos())) {
					foundFetch = true;
					fetchTask = t;
				}
			}
		}
		helper.assertTrue(foundFetch, "SealProvide should have created a real fetch task for the stocked iron ingots");

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, provideChestAbs);
		golem.initializeGolem(new thaumcraft.common.golems.GolemProperties(), 0);

		thaumcraft.common.golems.ai.AIGotoBlock fetchGoal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(fetchGoal.canUse(), "the golem should be able to pick up the fetch task");
		helper.assertTrue(golem.getTask() == fetchTask, "the golem should have reserved the real fetch task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"completing the fetch task should have the golem really carrying the iron ingots");

		thaumcraft.api.golems.tasks.Task deliverTask = null;
		for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
			if (t.getLinkedProvision() != null && t.getData() == 1 && sealPos.equals(t.getSealPos())) deliverTask = t;
		}
		helper.assertTrue(deliverTask != null, "completing the fetch task should have queued a real delivery task");

		thaumcraft.common.golems.ai.AIGotoBlock deliverGoal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(deliverGoal.canUse(), "the golem should be able to pick up the delivery task");
		helper.assertTrue(golem.getTask() == deliverTask, "the golem should have reserved the real delivery task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(!golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"completing the delivery task should have the golem no longer carrying the iron ingots");

		net.minecraft.world.level.block.entity.ChestBlockEntity destChest = helper.getBlockEntity(destChestPos);
		boolean delivered = false;
		for (int i = 0; i < destChest.getContainerSize(); i++) {
			if (destChest.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) delivered = true;
		}
		helper.assertTrue(delivered, "the delivered iron ingots should really be in the destination chest");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealStockRequestsProvisioningWhenBelowTargetCount(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 2));

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealStock seal = new thaumcraft.common.golems.seals.SealStock();
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		seal.setFilterSlotSize(0, 8);
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), chestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(chestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		int before = thaumcraft.api.golems.GolemHelper.provisionRequests.getOrDefault(helper.getLevel().dimension(), new java.util.ArrayList<>()).size();
		for (int i = 0; i < 25; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
		}
		int after = thaumcraft.api.golems.GolemHelper.provisionRequests.getOrDefault(helper.getLevel().dimension(), new java.util.ArrayList<>()).size();
		helper.assertTrue(after > before, "having only 2 of a real 8-target stock should register a real provisioning request, before=" + before + " after=" + after);

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealFillDepositsACarriedItemIntoAContainer(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealFill seal = new thaumcraft.common.golems.seals.SealFill();
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), chestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(chestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, chestAbs);
		golem.initializeGolem(new thaumcraft.common.golems.GolemProperties(), 0);
		golem.holdItem(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task fillTask = null;
		for (int i = 0; i < 25 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (sealPos.equals(t.getSealPos())) {
					foundTask = true;
					fillTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real standing fill task");

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the fill task");
		helper.assertTrue(golem.getTask() == fillTask, "the golem should have reserved the real fill task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(!golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"completing the fill task should have the golem no longer carrying the iron ingots");

		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
		boolean delivered = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) delivered = true;
		}
		helper.assertTrue(delivered, "the deposited iron ingots should really be in the chest");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealEmptyPullsAMatchingItemIntoGolemCarry(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealEmpty seal = new thaumcraft.common.golems.seals.SealEmpty();
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), chestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(chestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, chestAbs);
		golem.initializeGolem(new thaumcraft.common.golems.GolemProperties(), 0);

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task emptyTask = null;
		for (int i = 0; i < 25 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (sealPos.equals(t.getSealPos())) {
					foundTask = true;
					emptyTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real empty task for the matching stocked ingots");

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the empty task");
		helper.assertTrue(golem.getTask() == emptyTask, "the golem should have reserved the real empty task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"completing the empty task should have the golem really carrying the pulled iron ingots");

		boolean stillInChest = false;
		for (int i = 0; i < chest.getContainerSize(); i++) {
			if (chest.getItem(i).is(net.minecraft.world.item.Items.IRON_INGOT)) stillInChest = true;
		}
		helper.assertTrue(!stillInChest, "the pulled iron ingots should really have been removed from the chest");

		helper.succeed();
	}
}
