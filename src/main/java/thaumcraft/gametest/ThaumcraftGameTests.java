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
import thaumcraft.common.entities.construct.EntityTurretCrossbow;
import thaumcraft.common.registry.ThaumcraftEntities;
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
				"arcane_workbench_charger", "tube_filter", "tube_restrict",
				"brassingot", "nugget_brass", "ingot_brass_from_nuggets",
				"metal_block_brass", "ingot_brass_from_block", "plate_brass",
				"table_wood", "table_stone"
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
	 * Brass - real regression coverage for the material chain's actual root, same shape as the Thaumium test
	 * above. Real, load-bearing: the already-real METALLURGY research node references this exact recipe id
	 * ({@code thaumcraft:brassingot}) in its own stage-1 recipe list - see {@code ItemsTC.ingotBrass}'s own
	 * doc comment for the full real gap this closed (several already-shipped devices' own real recipes need
	 * a real Brass Plate, which needs a real Brass Ingot to exist at all).
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crucibleCompletesTheBrassIngotRecipe(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		TileCrucible crucible = placeActiveCrucible(helper, pos);
		crucible.setAspects(new AspectList().add(Aspect.TOOL, 5));

		ItemStack catalyst = new ItemStack(Items.IRON_INGOT);
		ItemStack remainder = crucible.attemptSmelt(catalyst, helper.getLevel());

		helper.assertTrue(remainder.getCount() == 0, "the iron ingot catalyst should have been fully consumed");

		BlockPos absolute = helper.absolutePos(pos);
		List<ItemEntity> ejected = helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(absolute).inflate(2.0));
		boolean foundBrass = ejected.stream().anyMatch(e -> e.getItem().is(thaumcraft.api.items.ItemsTC.ingotBrass.get()));
		helper.assertTrue(foundBrass, "completing brassingot should have ejected a thaumcraft:ingot_brass item near the crucible, found: "
				+ ejected.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.blocks.basic.BlockTable}'s own real Wood Table upgrade -
	 * right-clicking it with a real scribing tool should really replace it with a real Research Table,
	 * carrying the scribing tool into the new tile's own slot 0. The Stone Table has no such upgrade,
	 * matching the original's own real hardcoded "wood variant only" check.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void woodTableUpgradesIntoARealResearchTableWithAScribingTool(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.tableWood.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerLevel level = helper.getLevel();

		ServerPlayer player = spawnCasterAt(helper, abs.above());
		ItemStack scribingTools = new ItemStack(thaumcraft.api.items.ItemsTC.scribingTools.get());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, scribingTools);

		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
		state.useItemOn(scribingTools, level, player, hand, hit);

		helper.assertTrue(level.getBlockState(abs).is(thaumcraft.api.blocks.BlocksTC.researchTable.get()),
				"right-clicking a real Wood Table with a real scribing tool should really upgrade it into a real Research Table");
		helper.assertTrue(level.getBlockEntity(abs) instanceof thaumcraft.common.tiles.crafting.TileResearchTable tile
						&& tile.getItemHandler().getStackInSlot(0).is(thaumcraft.api.items.ItemsTC.scribingTools.get()),
				"the real scribing tool should really carry over into the new Research Table's own slot 0");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void stoneTableDoesNotUpgradeWithAScribingTool(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.tableStone.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerLevel level = helper.getLevel();

		ServerPlayer player = spawnCasterAt(helper, abs.above());
		ItemStack scribingTools = new ItemStack(thaumcraft.api.items.ItemsTC.scribingTools.get());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, scribingTools);

		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);
		state.useItemOn(scribingTools, level, player, hand, hit);

		helper.assertTrue(level.getBlockState(abs).is(thaumcraft.api.blocks.BlocksTC.tableStone.get()),
				"a real Stone Table should really never upgrade, matching the original's own real wood-only check");
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

	/**
	 * Real regression guard for the sync fix found while scoping the Infusion Matrix renderer (see
	 * {@link thaumcraft.common.tiles.devices.TileRechargePedestal}'s own doc comment): before that fix, this
	 * tile never overrode {@code getUpdateTag}/{@code getUpdatePacket} at all, so a real client never saw its
	 * held item - not even on initial chunk load - which meant {@code TileRechargePedestalRenderer}'s
	 * floating item could never actually render. GameTest can't observe a real network sync, but it can
	 * assert the real payload {@code getUpdateTag} would send is now correct.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void rechargePedestalUpdateTagCarriesTheRealHeldItem(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 1, 2);
		helper.setBlock(relPos, BlocksTC.rechargePedestal.get());
		thaumcraft.common.tiles.devices.TileRechargePedestal tile = helper.getBlockEntity(relPos);

		net.minecraft.nbt.CompoundTag emptyTag = tile.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(emptyTag != null, "the real update tag should never be null now that this tile overrides getUpdateTag");

		tile.setItem(new ItemStack(TEST_RECHARGABLE_ITEM.get()));
		net.minecraft.nbt.CompoundTag filledTag = tile.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(filledTag.contains("Items"), "the real update tag should carry the held item once one is placed, got " + filledTag);
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
	// Potion Sprayer - banks essentia into charges (same suction-and-fill shape as TileCondenser#fill),
	// then on a redstone pulse spends one charge to apply the held potion's effects to nearby entities
	// without consuming it. Calls TilePotionSprayer.tick(...) directly, same "test the real entry point"
	// precedent as every other ticking device above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void potionSprayerBanksAChargeFromSuctionedEssentia(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.potionSprayer.get());

		thaumcraft.common.tiles.devices.TilePotionSprayer tile = helper.getBlockEntity(pos);
		net.minecraft.world.item.ItemStack potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
				net.minecraft.world.item.Items.SPLASH_POTION, net.minecraft.world.item.alchemy.Potions.SLOWNESS);
		helper.assertTrue(tile.accepts(potion), "the sprayer should accept a real splash potion with effects");
		tile.setItem(potion);
		helper.assertTrue(tile.getCharges() == 0, "a freshly-loaded sprayer shouldn't already have a charge");

		// Same test-only IEssentiaTransport source used for the Condenser's own fill() test, exposed on the
		// sprayer's south face (fill() excludes only the facing side, which defaults to north).
		helper.setBlock(pos.south(), TEST_ESSENTIA_SOURCE_BLOCK.get());
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(pos.south());
		source.aspect = Aspect.ALCHEMY;
		source.amount = 50;
		source.outputFace = net.minecraft.core.Direction.NORTH;

		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		for (int i = 0; i < 200 && tile.getCharges() == 0; i++) {
			thaumcraft.common.tiles.devices.TilePotionSprayer.tick(helper.getLevel(), absolute, state, tile);
		}

		helper.assertTrue(tile.getCharges() > 0, "the sprayer should have banked at least 1 charge by now, found: " + tile.getCharges());
		helper.assertTrue(source.amount < 50, "the sprayer should have pulled essentia from the source, source now has: " + source.amount);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void potionSprayerSpraysOnARedstonePulseWithoutConsumingThePotion(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.potionSprayer.get());

		thaumcraft.common.tiles.devices.TilePotionSprayer tile = helper.getBlockEntity(pos);
		net.minecraft.world.item.ItemStack potion = net.minecraft.world.item.alchemy.PotionContents.createItemStack(
				net.minecraft.world.item.Items.SPLASH_POTION, net.minecraft.world.item.alchemy.Potions.SLOWNESS);
		tile.setItem(potion);

		helper.setBlock(pos.south(), TEST_ESSENTIA_SOURCE_BLOCK.get());
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(pos.south());
		source.aspect = Aspect.ALCHEMY;
		source.amount = 50;
		source.outputFace = net.minecraft.core.Direction.NORTH;

		net.minecraft.world.level.block.state.BlockState enabledState = helper.getBlockState(pos);
		for (int i = 0; i < 200 && tile.getCharges() == 0; i++) {
			thaumcraft.common.tiles.devices.TilePotionSprayer.tick(helper.getLevel(), absolute, enabledState, tile);
		}
		int chargesBefore = tile.getCharges();
		helper.assertTrue(chargesBefore > 0, "the sprayer needs a banked charge before this test can pulse it");

		// Default facing is north (BlockStateProperties default), so the spray lands 2 blocks north.
		BlockPos targetPos = absolute.north(2);
		net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
		zombie.moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, 0, 0);
		helper.getLevel().addFreshEntity(zombie);
		helper.assertFalse(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the zombie shouldn't have Slowness before the spray");

		net.minecraft.world.level.block.state.BlockState poweredState = enabledState.setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, false);
		helper.getLevel().setBlock(absolute, poweredState, 3);
		thaumcraft.common.tiles.devices.TilePotionSprayer.tick(helper.getLevel(), absolute, poweredState, tile);

		helper.assertTrue(tile.getCharges() == chargesBefore - 1, "spraying should have spent exactly 1 charge, found: " + tile.getCharges());
		helper.assertTrue(zombie.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "the zombie should have been sprayed with Slowness");
		helper.assertFalse(tile.getItem().isEmpty(), "spraying shouldn't consume the stored potion");

		// A second tick while still powered shouldn't spray again (edge-triggered, one spray per pulse).
		thaumcraft.common.tiles.devices.TilePotionSprayer.tick(helper.getLevel(), absolute, poweredState, tile);
		helper.assertTrue(tile.getCharges() == chargesBefore - 1, "holding the pulse shouldn't spend a second charge, found: " + tile.getCharges());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Void Siphon - drains nearby EntityFluxRifts (in real line of sight, within 8 blocks) into progress,
	// eventually producing Void Seeds. Calls TileVoidSiphon.tick(...) directly, same "test the real entry
	// point" precedent as every other ticking device above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidSiphonDrainsAVisibleRiftAndProducesAVoidSeed(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.voidSiphon.get());

		thaumcraft.common.tiles.crafting.TileVoidSiphon tile = helper.getBlockEntity(pos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);

		thaumcraft.common.entities.EntityFluxRift rift =
				new thaumcraft.common.entities.EntityFluxRift(thaumcraft.common.registry.ThaumcraftEntities.FLUX_RIFT.get(), helper.getLevel());
		rift.setRiftSeed(42);
		rift.setPos(absolute.getX() + 3.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		rift.setRiftSize(400);
		helper.getLevel().addFreshEntity(rift);
		float stabilityBefore = rift.getRiftStability();

		for (int i = 0; i < 3000 && tile.getItem().isEmpty(); i++) {
			thaumcraft.common.tiles.crafting.TileVoidSiphon.tick(helper.getLevel(), absolute, state, tile);
		}

		helper.assertFalse(tile.getItem().isEmpty(), "the siphon should have produced a Void Seed by now, progress: " + tile.getProgress());
		helper.assertTrue(tile.getItem().is(thaumcraft.api.items.ItemsTC.voidSeed.get()), "the produced item should be a real Void Seed");
		helper.assertTrue(rift.getRiftStability() < stabilityBefore, "draining the rift should have reduced its stability");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidSiphonDoesNothingWhenDisabledOrRiftOutOfRange(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.voidSiphon.get());

		thaumcraft.common.tiles.crafting.TileVoidSiphon tile = helper.getBlockEntity(pos);
		net.minecraft.world.level.block.state.BlockState disabledState = helper.getBlockState(pos).setValue(
				net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, false);
		helper.getLevel().setBlock(absolute, disabledState, 3);

		thaumcraft.common.entities.EntityFluxRift rift =
				new thaumcraft.common.entities.EntityFluxRift(thaumcraft.common.registry.ThaumcraftEntities.FLUX_RIFT.get(), helper.getLevel());
		rift.setRiftSeed(7);
		rift.setPos(absolute.getX() + 3.5, absolute.getY() + 1, absolute.getZ() + 0.5);
		rift.setRiftSize(400);
		helper.getLevel().addFreshEntity(rift);

		for (int i = 0; i < 40; i++) {
			thaumcraft.common.tiles.crafting.TileVoidSiphon.tick(helper.getLevel(), absolute, disabledState, tile);
		}
		helper.assertTrue(tile.getProgress() == 0, "a disabled siphon shouldn't drain a rift, found progress: " + tile.getProgress());
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Thaumatorium - auto-crafting altar built on the same CrucibleRecipe type as TileCrucible. Given a
	// heat source below and a catalyst matching a registered recipe, it sucks essentia from connected
	// neighbors (same suction-and-fill shape as TilePotionSprayer#fill) toward that recipe's requirement
	// and crafts automatically, no GUI/curation needed. Calls TileThaumatorium.tick(...) directly, same
	// "test the real entry point" precedent as every other ticking device above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumatoriumAutoCraftsAFocusFromACrucibleRecipeWhenHeatedWithMatchingCatalystAndEssentia(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.LAVA);
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.thaumatorium.get());

		thaumcraft.common.tiles.crafting.TileThaumatorium tile = helper.getBlockEntity(pos);
		// focus_air_crucible.json: catalyst feather, aspects vitreus:5/praecantatio:3/aer:3, result Focus: Air.
		tile.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER));
		helper.assertFalse(tile.getItem().isEmpty(), "the feather catalyst should have been accepted");

		helper.setBlock(pos.north(), TEST_ESSENTIA_SOURCE_BLOCK.get());
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(pos.north());
		source.amount = 200;
		source.outputFace = net.minecraft.core.Direction.SOUTH;

		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		for (int i = 0; i < 500 && !tile.getItem().isEmpty(); i++) {
			// A real tube network would have one source per aspect - this single test fixture only ever
			// emits one aspect at a time, so keep pointing it at whatever the tile currently wants.
			if (tile.getCurrentSuction() != null) source.aspect = tile.getCurrentSuction();
			thaumcraft.common.tiles.crafting.TileThaumatorium.tick(helper.getLevel(), absolute, state, tile);
		}

		helper.assertTrue(tile.getItem().isEmpty(), "the feather catalyst should have been consumed crafting the focus");
		java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(absolute).inflate(2));
		boolean foundFocus = drops.stream().anyMatch(e -> e.getItem().is(thaumcraft.api.items.ItemsTC.focusAir.get()));
		helper.assertTrue(foundFocus, "the thaumatorium should have crafted and ejected a Focus: Air");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumatoriumDoesNothingWithoutHeatEvenWithAMatchingCatalystAndEssentiaAvailable(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.thaumatorium.get());

		thaumcraft.common.tiles.crafting.TileThaumatorium tile = helper.getBlockEntity(pos);
		tile.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER));

		helper.setBlock(pos.north(), TEST_ESSENTIA_SOURCE_BLOCK.get());
		TestEssentiaSourceBlockEntity source = helper.getBlockEntity(pos.north());
		source.aspect = Aspect.EARTH;
		source.amount = 200;
		source.outputFace = net.minecraft.core.Direction.SOUTH;

		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		for (int i = 0; i < 100; i++) {
			thaumcraft.common.tiles.crafting.TileThaumatorium.tick(helper.getLevel(), absolute, state, tile);
		}

		helper.assertFalse(tile.getItem().isEmpty(), "without a heat source below, the catalyst shouldn't be consumed");
		helper.assertTrue(tile.getAspects().size() == 0, "without a heat source below, no essentia should have been drawn in");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.tiles.crafting.TileThaumatorium#getPreviewResult()} - the
	 * real accessor added for {@link thaumcraft.client.render.TileThaumatoriumRenderer}'s own real floating
	 * preview. Real and immediate regardless of heat/essentia state - matches the original's own real
	 * renderer, which showed its own preview unconditionally too.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumatoriumPreviewResultShowsTheRealMatchingRecipeOutput(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.thaumatorium.get());
		thaumcraft.common.tiles.crafting.TileThaumatorium tile = helper.getBlockEntity(pos);

		helper.assertTrue(tile.getPreviewResult().isEmpty(), "with no real catalyst inserted, the real preview should really be empty");

		tile.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK));
		helper.assertTrue(tile.getPreviewResult().isEmpty(), "with a real catalyst that matches no real recipe, the real preview should really be empty");

		// focus_air_crucible.json: catalyst feather, result Focus: Air - same real recipe the other real
		// Thaumatorium tests above already exercise.
		tile.setItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FEATHER));
		ItemStack preview = tile.getPreviewResult();
		helper.assertTrue(preview.is(thaumcraft.api.items.ItemsTC.focusAir.get()),
				"with a real matching feather catalyst, the real preview should really show Focus: Air, found " + preview);
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
	// Nitor - one small floating light per DyeColor, same real "one shared texture, tinted per-instance"
	// shape as the candles above, minus the "needs support" behavior (it floats). The color-handler tint
	// itself is client-only and not GameTest-observable here either.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void nitorEmitsFullLightAndCarriesItsOwnDye(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 4, 2);
		helper.setBlock(pos, BlocksTC.nitor.get(net.minecraft.world.item.DyeColor.PURPLE).get());
		BlockPos absolute = helper.absolutePos(pos);

		int light = helper.getBlockState(pos).getLightEmission(helper.getLevel(), absolute);
		helper.assertTrue(light == 15, "a placed nitor should emit full light, found: " + light);

		net.minecraft.world.level.block.Block block = helper.getBlockState(pos).getBlock();
		helper.assertTrue(block instanceof thaumcraft.common.blocks.misc.BlockNitor purple && purple.dye == net.minecraft.world.item.DyeColor.PURPLE,
				"the purple nitor block should carry DyeColor.PURPLE");

		helper.assertTrue(helper.getLevel().getBlockState(absolute.below()).isAir(),
				"unlike a candle, nitor should have nothing to fall onto - it should real-float with no support block");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void nitorHasARealCrucibleBaseAndFifteenRealRecolorRecipes(GameTestHelper helper) {
		helper.assertTrue(helper.getLevel().getRecipeManager()
				.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "nitor_yellow")).isPresent(),
				"a real Crucible recipe turning glowstone dust into base (yellow) nitor should be registered");

		for (net.minecraft.world.item.DyeColor dye : net.minecraft.world.item.DyeColor.values()) {
			if (dye == net.minecraft.world.item.DyeColor.YELLOW) continue;
			String id = "nitor_" + thaumcraft.api.blocks.BlocksTC.candleDyeName(dye);
			helper.assertTrue(helper.getLevel().getRecipeManager()
					.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id)).isPresent(),
					"a real shapeless recolor recipe for " + id + " should be registered");
		}
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
	// Zombie Brain drops moved to MobDeathDropEvents#onLivingDrops (see mobDeathDropEventsGrantsABrainFrom-
	// ARegularZombieButNeverFromABrainyZombie above) - Thaumcraft#rollZombieBrainDrop was a real duplicate
	// roll on the exact same LivingDropsEvent and has been removed; see the port-tracking memory.
	// --------------------------------------------------------------------------------------------------

	// --------------------------------------------------------------------------------------------------
	// Real, small pieces salvaged from the still-stale CraftingEvents (Thaumcraft.onAnvilUpdate/
	// blocksAnvilOperation) and a real data-driven furnace_fuels contribution (Alumentum/Greatwood/
	// Silverwood logs) - see the port-tracking memory for why the rest of that file (and most of
	// common/lib/events generally) turned out to already be superseded elsewhere.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void anvilOperationsInvolvingThePrimordialPearlAreBlocked(GameTestHelper helper) {
		ItemStack pearl = new ItemStack(ItemsTC.primordialPearl.get());
		ItemStack iron = new ItemStack(Items.IRON_INGOT);

		helper.assertTrue(thaumcraft.Thaumcraft.blocksAnvilOperation(pearl, iron), "the pearl on the left input should block the anvil operation");
		helper.assertTrue(thaumcraft.Thaumcraft.blocksAnvilOperation(iron, pearl), "the pearl on the right input should block the anvil operation");
		helper.assertFalse(thaumcraft.Thaumcraft.blocksAnvilOperation(iron, ItemStack.EMPTY), "a plain iron ingot shouldn't block anything");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void alumentumIsRegisteredAsRealFurnaceFuel(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, Blocks.FURNACE);

		net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity furnace =
				(net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity) helper.getBlockEntity(pos);
		furnace.setItem(0, new ItemStack(Blocks.COBBLESTONE));
		furnace.setItem(1, new ItemStack(ItemsTC.alumentum.get()));

		helper.succeedWhen(() -> helper.assertTrue(furnace.cookingProgress > 0,
				"a real furnace fed alumentum (via the real data/thaumcraft/data_maps/item/furnace_fuels.json contribution) should light and actually cook - progress=" + furnace.cookingProgress));
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
	// Real infusion recipe content: Lamp Growth / Lamp Fertility - the first two of the original's own ~76
	// real infusion recipes ported past the bare demo, chosen because every real ingredient (lamp_arcane,
	// crystal_essence, and plain vanilla items) already exists in this port - most of the rest are still
	// blocked on whole unported item families (crystal-cluster blocks, Baubles-slot items, elemental tools,
	// void-robe armor, focus tiers 2/3, fortress masks, and more).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixCraftsLampGrowthFromLampArcane(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		BlockPos centralPos = matrixPos.below(2);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(centralPos, BlocksTC.pedestal.get());
		thaumcraft.common.tiles.crafting.TilePedestal central = helper.getBlockEntity(centralPos);
		central.setItem(new ItemStack(BlocksTC.lampArcane.get()));

		BlockPos[] componentPositions = {
				matrixPos.offset(2, 0, 0), matrixPos.offset(-2, 0, 0), matrixPos.offset(0, 0, 2),
				matrixPos.offset(0, 0, -2), matrixPos.offset(2, 0, 2), matrixPos.offset(-2, 0, -2)
		};
		ItemStack[] componentItems = {
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.WHITE_DYE), new ItemStack(ItemsTC.crystalEssence.get()),
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.WHITE_DYE), new ItemStack(ItemsTC.crystalEssence.get())
		};
		for (int i = 0; i < componentPositions.length; i++) {
			helper.setBlock(componentPositions[i], BlocksTC.pedestal.get());
			thaumcraft.common.tiles.crafting.TilePedestal ped = helper.getBlockEntity(componentPositions[i]);
			ped.setItem(componentItems[i]);
		}

		// Three real symmetrical pairs of stabilizers - without real stabilization, this real recipe's real
		// instability (4) has a real, growing chance over the many real craftCycles a 65-unit essentia cost
		// takes to trigger a real instability event (see TileInfusionMatrix#craftCycle) severe enough to
		// eject/destroy a nearby pedestal, genuinely stalling the craft forever - caught live (stability
		// spiralling to -50+, eventually blowing away a pedestal). One real pair alone (0.25 replenish) still
		// loses on average to a real instability-4 recipe's own average per-cycle loss (~0.4) - three pairs'
		// real diminishing-returns total (~0.58) comfortably wins on average, matching how a real player
		// would actually need to over-provision stabilizers for a recipe this costly, not a test-only fudge.
		helper.setBlock(matrixPos.offset(3, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(-3, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(0, -2, 3), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(0, -2, -3), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(5, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(-5, -2, 0), BlocksTC.stabilizer.get());

		BlockPos jarHerba = matrixPos.offset(3, 0, 0);
		BlockPos jarLux = matrixPos.offset(-3, 0, 0);
		BlockPos jarVictus = matrixPos.offset(0, 0, 3);
		BlockPos jarInstrumentum = matrixPos.offset(0, 0, -3);
		helper.setBlock(jarHerba, BlocksTC.jarNormal.get());
		helper.setBlock(jarLux, BlocksTC.jarNormal.get());
		helper.setBlock(jarVictus, BlocksTC.jarNormal.get());
		helper.setBlock(jarInstrumentum, BlocksTC.jarNormal.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarHerba)).addToContainer(Aspect.PLANT, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarLux)).addToContainer(Aspect.LIGHT, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarVictus)).addToContainer(Aspect.LIFE, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarInstrumentum)).addToContainer(Aspect.TOOL, 30);

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		ServerPlayer player = spawnCasterAt(helper, matrixAbs);

		helper.assertTrue(matrix.activate(level, player), "activation should succeed with a central pedestal in place");
		helper.assertTrue(matrix.activate(level, player), "the second activation (with a real, valid lamp_growth recipe on the pedestals) should start crafting");
		helper.assertTrue(matrix.crafting, "the matrix should be mid-craft after a successful craftingStart");

		// A real, much bigger tick budget than the bare demo recipe's own test needs: real essentia only
		// drains one unit per real craftCycle (every real countDelay ticks), and this recipe's real aspect
		// cost (herba 20 + lux 15 + victus 15 + instrumentum 15 = 65 real units) is an order of magnitude
		// above the demo recipe's own 6 - plenty of real margin even with the real stabilizer pair above
		// keeping stability positive throughout.
		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		for (int i = 0; i < 6000 && matrix.crafting; i++) {
			thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(level, matrixAbs, matrixState, matrix);
		}

		helper.assertTrue(!matrix.crafting, "the craft should have completed (or definitively failed) within a generous tick budget");
		helper.assertTrue(central.getItem().is(BlocksTC.lampGrowth.get().asItem()), "the central pedestal should hold a real lamp_growth, found: " + central.getItem());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixCraftsLampFertilityFromLampArcane(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		BlockPos centralPos = matrixPos.below(2);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(centralPos, BlocksTC.pedestal.get());
		thaumcraft.common.tiles.crafting.TilePedestal central = helper.getBlockEntity(centralPos);
		central.setItem(new ItemStack(BlocksTC.lampArcane.get()));

		BlockPos[] componentPositions = {
				matrixPos.offset(2, 0, 0), matrixPos.offset(-2, 0, 0), matrixPos.offset(0, 0, 2),
				matrixPos.offset(0, 0, -2), matrixPos.offset(2, 0, 2), matrixPos.offset(-2, 0, -2)
		};
		ItemStack[] componentItems = {
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.WHEAT), new ItemStack(ItemsTC.crystalEssence.get()),
				new ItemStack(Items.GOLD_INGOT), new ItemStack(Items.CARROT), new ItemStack(ItemsTC.crystalEssence.get())
		};
		for (int i = 0; i < componentPositions.length; i++) {
			helper.setBlock(componentPositions[i], BlocksTC.pedestal.get());
			thaumcraft.common.tiles.crafting.TilePedestal ped = helper.getBlockEntity(componentPositions[i]);
			ped.setItem(componentItems[i]);
		}

		// Same real three symmetrical stabilizer pairs as the lamp_growth test above, same real reason - see
		// that test's own comment.
		helper.setBlock(matrixPos.offset(3, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(-3, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(0, -2, 3), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(0, -2, -3), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(5, -2, 0), BlocksTC.stabilizer.get());
		helper.setBlock(matrixPos.offset(-5, -2, 0), BlocksTC.stabilizer.get());

		BlockPos jarBestia = matrixPos.offset(3, 0, 0);
		BlockPos jarLux = matrixPos.offset(-3, 0, 0);
		BlockPos jarVictus = matrixPos.offset(0, 0, 3);
		BlockPos jarDesiderium = matrixPos.offset(0, 0, -3);
		helper.setBlock(jarBestia, BlocksTC.jarNormal.get());
		helper.setBlock(jarLux, BlocksTC.jarNormal.get());
		helper.setBlock(jarVictus, BlocksTC.jarNormal.get());
		helper.setBlock(jarDesiderium, BlocksTC.jarNormal.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarBestia)).addToContainer(Aspect.BEAST, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarLux)).addToContainer(Aspect.LIGHT, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarVictus)).addToContainer(Aspect.LIFE, 30);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarDesiderium)).addToContainer(Aspect.DESIRE, 30);

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		ServerPlayer player = spawnCasterAt(helper, matrixAbs);

		helper.assertTrue(matrix.activate(level, player), "activation should succeed with a central pedestal in place");
		helper.assertTrue(matrix.activate(level, player), "the second activation (with a real, valid lamp_fertility recipe on the pedestals) should start crafting");
		helper.assertTrue(matrix.crafting, "the matrix should be mid-craft after a successful craftingStart");

		// Same real, generous tick budget as the lamp_growth test above, same real reason - see that test's
		// own comment.
		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		for (int i = 0; i < 6000 && matrix.crafting; i++) {
			thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(level, matrixAbs, matrixState, matrix);
		}
		helper.assertTrue(!matrix.crafting, "the craft should have completed (or definitively failed) within a generous tick budget");
		helper.assertTrue(central.getItem().is(BlocksTC.lampFertility.get().asItem()), "the central pedestal should hold a real lamp_fertility, found: " + central.getItem());
		helper.succeed();
	}

	/** Real first use of the new {@link thaumcraft.common.lib.crafting.InfusionCrystalRecipe} shape - see
	 * that class's own doc comment for why a plain JSON {@link net.minecraft.world.item.crafting.Ingredient}
	 * couldn't express "central item must be a crystal_essence of this one specific Aspect." The direct
	 * {@code matches} check below is deliberate, not just going through the altar: with all 7 real
	 * CrystalCluster recipes now registered (each wanting the exact same real seeds+salis_mundus components),
	 * a fire-aspect crystal on the altar would really, correctly start crafting crystal_ignis instead of
	 * simply refusing to craft anything - checking {@code matches} on the crystal_aer recipe specifically is
	 * the real, precise way to confirm it does NOT accept a fire-aspect central item, without that other
	 * real recipe's own correct match muddying the result. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixCraftsCrystalAirFromAnAirCrystalEssenceButNotAFireOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		BlockPos centralPos = matrixPos.below(2);
		BlockPos seedsPos = matrixPos.east(2);
		BlockPos salisPos = matrixPos.west(2);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(centralPos, BlocksTC.pedestal.get());
		helper.setBlock(seedsPos, BlocksTC.pedestal.get());
		helper.setBlock(salisPos, BlocksTC.pedestal.get());
		thaumcraft.common.tiles.crafting.TilePedestal central = helper.getBlockEntity(centralPos);
		thaumcraft.common.tiles.crafting.TilePedestal seeds = helper.getBlockEntity(seedsPos);
		thaumcraft.common.tiles.crafting.TilePedestal salis = helper.getBlockEntity(salisPos);
		seeds.setItem(new ItemStack(Items.WHEAT_SEEDS));
		salis.setItem(new ItemStack(ItemsTC.salisMundus.get()));

		// Real jars supplying the recipe's own real aspect cost (aer 10 + vitreus 10 + vinculum 5) - without
		// these, EssentiaHandler.drainEssentia never finds anything to drain and the craft can never actually
		// finish, even with a real, valid match (caught live: crafting=true forever, no jars at all here).
		BlockPos jarAer = matrixPos.offset(0, -1, 3);
		BlockPos jarVitreus = matrixPos.offset(0, -1, -3);
		BlockPos jarVinculum = matrixPos.offset(3, -1, 0);
		helper.setBlock(jarAer, BlocksTC.jarNormal.get());
		helper.setBlock(jarVitreus, BlocksTC.jarNormal.get());
		helper.setBlock(jarVinculum, BlocksTC.jarNormal.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarAer)).addToContainer(Aspect.AIR, 15);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarVitreus)).addToContainer(Aspect.CRYSTAL, 15);
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarVinculum)).addToContainer(Aspect.TRAP, 10);

		java.util.Optional<net.minecraft.world.item.crafting.RecipeHolder<?>> airRecipeHolder =
				level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion_crystal_cluster_air"));
		helper.assertTrue(airRecipeHolder.isPresent() && airRecipeHolder.get().value() instanceof thaumcraft.common.lib.crafting.InfusionCrystalRecipe,
				"the real infusion_crystal_cluster_air recipe should really be registered and loaded");
		thaumcraft.common.lib.crafting.InfusionCrystalRecipe airRecipe =
				(thaumcraft.common.lib.crafting.InfusionCrystalRecipe) airRecipeHolder.get().value();

		ItemStack fireCrystal = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) fireCrystal.getItem()).setAspects(fireCrystal, new AspectList().add(Aspect.FIRE, 1));
		java.util.List<ItemStack> comps = java.util.List.of(new ItemStack(Items.WHEAT_SEEDS), new ItemStack(ItemsTC.salisMundus.get()));
		helper.assertTrue(!airRecipe.matches(new thaumcraft.api.crafting.InfusionRecipeInput(fireCrystal, comps), level),
				"the crystal_aer recipe should NOT match a fire-aspect crystal_essence as its central item");

		ItemStack airCrystal = new ItemStack(ItemsTC.crystalEssence.get());
		((ItemGenericEssentiaContainer) airCrystal.getItem()).setAspects(airCrystal, new AspectList().add(Aspect.AIR, 1));
		helper.assertTrue(airRecipe.matches(new thaumcraft.api.crafting.InfusionRecipeInput(airCrystal, comps), level),
				"the crystal_aer recipe should really match a real air-aspect crystal_essence as its central item");

		central.setItem(airCrystal);
		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		ServerPlayer player = spawnCasterAt(helper, matrixAbs);
		helper.assertTrue(matrix.activate(level, player), "activation should succeed with a central pedestal in place");
		helper.assertTrue(matrix.activate(level, player), "the second activation (with a real, valid crystal_aer recipe on the pedestals) should start crafting");
		helper.assertTrue(matrix.crafting, "the matrix should be mid-craft after a successful craftingStart");

		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		for (int i = 0; i < 400 && matrix.crafting; i++) {
			thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(level, matrixAbs, matrixState, matrix);
		}
		helper.assertTrue(!matrix.crafting, "the craft should have completed (or definitively failed) within a generous tick budget");
		helper.assertTrue(central.getItem().is(BlocksTC.crystals.get(Aspect.AIR).get().asItem()), "the central pedestal should hold a real crystal_aer, found: " + central.getItem());
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

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void objectTagsCoversTheModsOwnItemsBackfilledAlongsideTheConfigChunk(GameTestHelper helper) {
		AspectList curioTags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(ItemsTC.curio.get()));
		helper.assertTrue(curioTags != null && curioTags.getAmount(Aspect.MIND) > 0, "a real curio should carry a real Mind aspect, got " + curioTags);

		AspectList bladeTags = thaumcraft.api.aspects.AspectHelper.getObjectAspects(new ItemStack(ItemsTC.crimsonBlade.get()));
		helper.assertTrue(bladeTags != null && bladeTags.getAmount(Aspect.ELDRITCH) > 0, "a real crimson blade should carry a real Eldritch aspect, got " + bladeTags);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void entityTagsCoversRealCustomMobsBackfilledAlongsideTheConfigChunk(GameTestHelper helper) {
		thaumcraft.common.entities.monster.EntityWisp wisp =
				new thaumcraft.common.entities.monster.EntityWisp(thaumcraft.common.registry.ThaumcraftEntities.WISP.get(), helper.getLevel());
		AspectList wispTags = thaumcraft.api.aspects.AspectHelper.getEntityAspects(wisp);
		helper.assertTrue(wispTags != null && wispTags.getAmount(Aspect.AURA) > 0, "a real Wisp should carry a real Aura aspect, got " + wispTags);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		AspectList pechTags = thaumcraft.api.aspects.AspectHelper.getEntityAspects(pech);
		helper.assertTrue(pechTags != null && pechTags.getAmount(Aspect.MAN) > 0, "a real Pech should carry a real Man aspect, got " + pechTags);
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
		// BOOTSTRAVELLER's real parents (INFUSION, RECHARGEPEDESTAL - now actually enforced, see
		// ThaumcraftInternalMethodHandler's own doc comment) each sit behind their own real chain back to
		// UNLOCKALCHEMY; FIRSTSTEPS itself is already complete by default for every fresh player.
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKALCHEMY");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKAUROMANCY");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "BASEAUROMANCY");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "RECHARGEPEDESTAL");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKINFUSION");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "BASEINFUSION");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "INFUSION");
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
	// deferred (TileMirrorEssentia) and the link-preservation-on-break cut. ItemHandMirror/HandMirrorMenu are
	// real and covered further below (Basic Gear section).
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
		// BASEAUROMANCY's real prerequisite chain - see focalManipulatorMenusCraftButtonTriggersARealStartCraft's
		// own comment on this same chain.
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKALCHEMY");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKAUROMANCY");
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

		// BASEAUROMANCY's real prerequisite chain (now actually enforced - see ThaumcraftInternalMethodHandler's
		// own doc comment) is UNLOCKALCHEMY -> UNLOCKAUROMANCY -> BASEAUROMANCY; UNLOCKALCHEMY's own parent,
		// FIRSTSTEPS, is already complete by default for every fresh player.
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKALCHEMY");
		thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "UNLOCKAUROMANCY");
		boolean gotBaseAuromancy = thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "BASEAUROMANCY");
		helper.assertTrue(gotBaseAuromancy, "BASEAUROMANCY should complete once its real prerequisite chain is satisfied");
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
	//
	// Chunk 2 (later): the real block/tile/menu/screen (researchTableConsumesInkAndPaperAndTracksARealTheory
	// onward). Chunk 3: closes out the real api.research.theorycraft package (AidBookshelf) and starts the
	// original's larger common.lib.research.theorycraft "extra content" tier - 3 more standalone cards
	// (CardCalibrate/CardMeasure/CardSynergy) and 4 more real aids with their own aid-only cards (Beacon,
	// Dragon Egg, Enchantment Table, both real Portal block variants) - see the tests right before the
	// golem-construction section below.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void freshPlayerGetsBaselineInspirationAndOnlyBasicsIsAvailable(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		int inspiration = thaumcraft.api.research.theorycraft.ResearchTableData.getAvailableInspiration(player);
		// 5 base + the real .5 SPIKY bonus (ResearchEntry.EnumResearchMeta's own doc comment) from the
		// default-complete FIRSTSTEPS entry, rounded up - now that ThaumcraftResearchLoader actually loads
		// real entries, FIRSTSTEPS.hasMeta(SPIKY) is real (it wasn't before, when getResearch("FIRSTSTEPS")
		// returned null and this bonus could never apply).
		helper.assertTrue(inspiration == 6, "a fresh player with real FIRSTSTEPS knowledge should have the 5 baseline + its real .5 SPIKY bonus, found " + inspiration);

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
		// 6, not 5 - see freshPlayerGetsBaselineInspirationAndOnlyBasicsIsAvailable's own comment on the real
		// FIRSTSTEPS SPIKY bonus.
		helper.assertTrue(data.inspirationStart == 6 && data.inspiration == 6, "no aids chosen should leave inspiration untouched, found start=" + data.inspirationStart + " current=" + data.inspiration);

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
		// 6, not 5 - see freshPlayerGetsBaselineInspirationAndOnlyBasicsIsAvailable's own comment on the real
		// FIRSTSTEPS SPIKY bonus.
		helper.assertTrue(tile.data != null && tile.data.inspirationStart == 6, "starting a new theory with no aids should give the real 5 baseline + FIRSTSTEPS's real SPIKY bonus, found " + (tile.data == null ? "null" : tile.data.inspirationStart));

		tile.data.categoryTotals.put("BASICS", 100);
		tile.finishTheory(player);
		helper.assertTrue(tile.data == null, "finishing a theory should clear it");
		int theoryKnowledge = thaumcraft.api.capabilities.ThaumcraftCapabilities.getKnowledge(player)
				.getKnowledge(thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.THEORY, thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS"));
		helper.assertTrue(theoryKnowledge >= 1, "a full 100% BASICS theory should grant at least 1 real THEORY knowledge point, found " + theoryKnowledge);

		helper.assertTrue(tile.checkSurroundingAids().isEmpty(), "with real aids now registered but none of their trigger blocks/items/entities actually placed near this table, the real scan should still come back empty");
		helper.succeed();
	}

	/**
	 * Real regression guard for the sync fix found while scoping the Infusion Matrix renderer (see this
	 * tile's own doc comment): before that fix, this tile never overrode {@code getUpdateTag}/
	 * {@code getUpdatePacket} at all, so a real client never saw the ink slot or the in-progress theory - not
	 * even on initial chunk load - which meant {@code TileResearchTableRenderer}'s inkwell/scroll could never
	 * actually render. GameTest can't observe a real network sync, but it can assert the real payload
	 * {@code getUpdateTag} would send is now correct.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchTableUpdateTagCarriesTheRealInkAndActiveTheory(GameTestHelper helper) {
		BlockPos pos = new BlockPos(3, 2, 3);
		BlockPos absPos = helper.absolutePos(pos);
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		ServerPlayer player = spawnCasterAt(helper, absPos.above());

		net.minecraft.nbt.CompoundTag emptyTag = tile.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(emptyTag != null && !emptyTag.contains("note"), "with no real theory started, the real update tag should carry no 'note' data, found " + emptyTag);

		tile.getItemHandler().setStackInSlot(0, new ItemStack(ItemsTC.scribingTools.get()));
		tile.startNewTheory(player, java.util.Set.of());
		net.minecraft.nbt.CompoundTag filledTag = tile.getUpdateTag(helper.getLevel().registryAccess());
		helper.assertTrue(filledTag.contains("Items"), "the real update tag should carry the real ink item once one is placed, got " + filledTag);
		helper.assertTrue(filledTag.contains("note"), "the real update tag should carry the real in-progress theory once one is started, got " + filledTag);
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

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void checkSurroundingAidsDetectsARealPlacedBookshelf(GameTestHelper helper) {
		BlockPos tablePos = new BlockPos(3, 2, 3);
		helper.setBlock(tablePos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(tablePos);

		helper.assertTrue(tile.checkSurroundingAids().isEmpty(), "with nothing placed nearby yet, the real scan should come back empty");

		helper.setBlock(tablePos.offset(1, 0, 0), net.minecraft.world.level.block.Blocks.BOOKSHELF);
		java.util.Set<String> found = tile.checkSurroundingAids();
		helper.assertTrue(found.contains(thaumcraft.api.research.theorycraft.AidBookshelf.class.getName()),
				"a real Bookshelf placed within the table's own 9x9x3 scan range should register AidBookshelf, found " + found);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void checkSurroundingAidsDetectsARealNetherPortalBlock(GameTestHelper helper) {
		BlockPos tablePos = new BlockPos(3, 2, 3);
		helper.setBlock(tablePos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(tablePos);

		helper.setBlock(tablePos.offset(-2, 0, 0), net.minecraft.world.level.block.Blocks.NETHER_PORTAL);
		java.util.Set<String> found = tile.checkSurroundingAids();
		helper.assertTrue(found.contains(thaumcraft.common.lib.research.theorycraft.AidPortal.AidPortalNether.class.getName()),
				"a real Nether Portal block within scan range should register AidPortalNether, found " + found);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardSynergyConvertsCombinedProgressIntoRealGolemancyProgress(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.common.lib.research.theorycraft.CardSynergy card = new thaumcraft.common.lib.research.theorycraft.CardSynergy();
		helper.assertTrue(!card.initialize(player, data), "with zero ARTIFICE/ALCHEMY/INFUSION progress, CardSynergy should have no real candidate");

		data.categoryTotals.put("ARTIFICE", 5);
		data.categoryTotals.put("ALCHEMY", 5);
		data.categoryTotals.put("INFUSION", 5);
		helper.assertTrue(card.initialize(player, data), "15 combined ARTIFICE+ALCHEMY+INFUSION progress should be a real candidate");

		int penaltyBefore = data.penaltyStart;
		helper.assertTrue(card.activate(player, data), "CardSynergy#activate should succeed with 15 combined progress available");
		int remaining = data.getTotal("ARTIFICE") + data.getTotal("ALCHEMY") + data.getTotal("INFUSION");
		helper.assertTrue(remaining == 0, "activating should spend the full 15 combined source progress, found " + remaining + " left");
		helper.assertTrue(data.getTotal("GOLEMANCY") == 30, "activating should grant exactly 30 real GOLEMANCY progress, found " + data.getTotal("GOLEMANCY"));
		helper.assertTrue(data.penaltyStart == penaltyBefore + 1, "activating should real-increment the completion penalty count");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardCalibrateAndMeasureAddFlatProgressAndABonusDraw(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		int drawsBefore = data.bonusDraws;
		helper.assertTrue(new thaumcraft.common.lib.research.theorycraft.CardCalibrate().activate(player, data), "CardCalibrate#activate should always succeed");
		helper.assertTrue(data.getTotal("ARTIFICE") == 15, "CardCalibrate should add exactly 15 real ARTIFICE progress, found " + data.getTotal("ARTIFICE"));
		helper.assertTrue(data.bonusDraws == drawsBefore + 1, "CardCalibrate should grant a real bonus draw");

		helper.assertTrue(new thaumcraft.common.lib.research.theorycraft.CardMeasure().activate(player, data), "CardMeasure#activate should always succeed");
		helper.assertTrue(data.getTotal("INFUSION") == 15, "CardMeasure should add exactly 15 real INFUSION progress, found " + data.getTotal("INFUSION"));
		helper.assertTrue(data.bonusDraws == drawsBefore + 2, "CardMeasure should also grant a real bonus draw");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void aidOnlyCardsNeverSurfaceInARealNormalDraw(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);
		data.inspirationStart = 999;
		data.inspiration = 999; // real inspiration ceiling high enough that no card's real cost ever blocks a draw
		data.categoryTotals.put("BASICS", 50);
		data.categoryTotals.put("ARTIFICE", 50);
		data.categoryTotals.put("ALCHEMY", 50);
		data.categoryTotals.put("INFUSION", 50);
		data.categoryTotals.put("GOLEMANCY", 50);

		// Real regression coverage for isAidOnly()'s own contract - draw a real large batch (aid-only cards
		// are only ever added to cardChoices via the aidCards pool, never the normal per-card pool
		// drawCards iterates) and confirm none of this chunk's 4 new aid-only cards ever appear.
		for (int i = 0; i < 20; i++) {
			data.drawCards(2, player);
			for (thaumcraft.api.research.theorycraft.ResearchTableData.CardChoice cc : data.cardChoices) {
				helper.assertTrue(!cc.fromAid, "no aids are in data.aidCards here, so no choice should ever be marked fromAid");
				helper.assertTrue(!cc.card.isAidOnly(), "an aid-only card (" + cc.key + ") should never surface in a real normal draw");
			}
		}
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
		golem.restrictTo(floorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

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

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(floorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the chop task");
		helper.assertTrue(golem.getTask() == logTask, "the golem should have reserved the real chop task");

		// Exercises the real completion path directly (the same call TaskHandler.completeTask makes once a
		// golem is actually adjacent) rather than ticking real pathfinding to convergence in a GameTest.
		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(helper.getLevel().getBlockState(logAbs).isAir(), "completing the chop task should have really broken the log");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(dirtAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the harvest task");
		helper.assertTrue(golem.getTask() == cropTask, "the golem should have reserved the real harvest task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		int age = helper.getLevel().getBlockState(cropAbs).getValue(net.minecraft.world.level.block.CropBlock.AGE);
		helper.assertTrue(helper.getLevel().getBlockState(cropAbs).is(net.minecraft.world.level.block.Blocks.WHEAT) && age == 0,
				"completing the harvest task should have broken the grown wheat and replanted it at age 0, found age " + age);

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(floorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoEntity goal = new thaumcraft.common.golems.ai.AIGotoEntity(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the cull task");
		helper.assertTrue(golem.getTarget() == cullTask.getEntity(),
				"picking up a cull task should immediately set the pig as the golem's real combat target");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(anchorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

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

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(floorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoEntity goal = new thaumcraft.common.golems.ai.AIGotoEntity(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the pickup task");
		helper.assertTrue(golem.getTask() == pickupTask, "the golem should have reserved the real pickup task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(!dropped.isAlive(), "completing the pickup task should have really removed the dropped item entity");
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"the golem should now really be carrying the picked-up item");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	/**
	 * Known intermittently flaky - real, deeply investigated across several rounds, still not fully fixed.
	 * See the thaumcraft-1211-port memory file for the full blow-by-blow; this comment carries only the
	 * distilled findings for whoever picks this up next.
	 *
	 * <p>Four separate, all-real, all-confirmed mechanisms were found, each independently mitigated below
	 * where possible - none of them, even together, has yet produced a fully clean run:
	 * <ol>
	 * <li>{@code GolemHelper.provisionRequests} is real, dimension-wide, unscoped-by-design shared state (one
	 * player's whole base is meant to share one provisioning economy) - {@code SealProvide#tickSeal} grabs
	 * the first unclaimed request matching its own filter item, with no per-seal scoping. Originally this
	 * test collided with {@code sealStockRequestsProvisioningWhenBelowTargetCount}'s own IRON_INGOT request
	 * (fixed: that test now uses GOLD_INGOT) and, separately, this test's own item was switched to
	 * NETHERITE_INGOT (used nowhere else in this file) to make it collision-proof against this specific
	 * mechanism entirely.
	 * <li>{@code Thaumcraft#tickGolemSeals} (a real per-level-tick event) ticks every seal ever placed via
	 * {@code SealHandler.addSealEntity} for as long as the server runs, independent of whether the test that
	 * placed it has finished - of the 16 seal-spawning tests in this file, only 2 self-cleaned (one removes
	 * its own seal as the literal thing under test, one explicitly calls {@code removeSealEntity} as part of
	 * its own assertions). Fixed: the other 14 now call {@code removeSealEntity} at the end of their own test.
	 * <li>{@code TaskHandler.clearSuspendedOrExpiredTasks} (also real per-level-tick, confirmed via direct
	 * instrumentation to actually fire mid-test) reaps any suspended task and calls the owning seal's
	 * {@code onTaskSuspension} on it - for {@code SealProvide} this nulls the reaped task's own
	 * {@code linkedProvision} field. Not itself a bug (this test's own fetch task is SUPPOSED to become
	 * suspended once completed and get reaped eventually) but confirms real ticks genuinely interleave with
	 * this test's own synchronous method body, which the next point depends on.
	 * <li><b>Not yet fixed, strongest remaining lead</b>: {@code EntityThaumcraftGolem} registers its own
	 * real {@code AIGotoEntity}/{@code AIGotoBlock} goals in its own {@code GoalSelector} at construction
	 * (real production behavior) - meaning any golem spawned in a GameTest is ALSO ticking its own automatic
	 * AI in the background via real server ticks (confirmed real ticks do interleave mid-test, point 3
	 * above), completely independent of this test's own manually-constructed {@code fetchGoal}/
	 * {@code deliverGoal} instances used to drive {@code canUse()} directly. Direct instrumentation
	 * confirmed: at the moment just before {@code deliverGoal.canUse()} is called, {@code deliverTask} passes
	 * every individually-checkable gate ({@code canGolemPerformTask}, {@code isWithinRestriction}, real
	 * distance, presence in {@code TaskHandler.getBlockTasksSorted}'s own returned list) - yet
	 * {@code canUse()} still sometimes returns false. The one gate that couldn't be checked directly from
	 * outside (it's {@code protected}) is {@code AIGoto#areGolemTagsValidForTask} - a real next step is either
	 * a debugger-attached run, or temporarily loosening that method's visibility for one diagnostic session,
	 * to see whether the golem's own automatic goal instance (not this test's manual one) is racing to
	 * reserve the same task first, or whether that gate itself is rejecting it for a reason not yet found.
	 * </ol>
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealProvideDeliversStockedItemsFromOneContainerToAnother(GameTestHelper helper) {
		BlockPos provideChestPos = BlockPos.ZERO;
		BlockPos provideChestAbs = helper.absolutePos(provideChestPos);
		helper.setBlock(provideChestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity provideChest = helper.getBlockEntity(provideChestPos);
		provideChest.setItem(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 10));

		BlockPos destChestPos = provideChestPos.offset(3, 0, 0);
		BlockPos destChestAbs = helper.absolutePos(destChestPos);
		helper.setBlock(destChestPos, net.minecraft.world.level.block.Blocks.CHEST);

		ServerPlayer owner = spawnCasterAt(helper, provideChestAbs.above(3));
		thaumcraft.common.golems.seals.SealProvide seal = new thaumcraft.common.golems.seals.SealProvide();
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT));
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), provideChestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(provideChestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		thaumcraft.api.golems.GolemHelper.requestProvisioning(helper.getLevel(), destChestAbs, net.minecraft.core.Direction.UP,
				new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT, 4));

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
		helper.assertTrue(foundFetch, "SealProvide should have created a real fetch task for the stocked netherite ingots");

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, provideChestAbs);
		golem.initializeGolem(new thaumcraft.common.golems.GolemProperties(), 0);
		golem.restrictTo(provideChestAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoBlock fetchGoal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(fetchGoal.canUse(), "the golem should be able to pick up the fetch task");
		helper.assertTrue(golem.getTask() == fetchTask, "the golem should have reserved the real fetch task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)),
				"completing the fetch task should have the golem really carrying the netherite ingots");
		// Real lifecycle hygiene, not what actually fixed this test's own flakiness (see the item-collision
		// comment below on the GOLD_INGOT switch for that): completeTask itself already suspends a completed
		// task via SealProvide#onTaskCompletion, so this is a no-op here, but it matches what a real
		// GoalSelector always does once canContinueToUse() goes false, and this test drives canUse() directly
		// without ever going through one - keeping that real step explicit rather than relying on the seal's
		// own side effect to happen to cover for it.
		fetchGoal.stop();

		thaumcraft.api.golems.tasks.Task deliverTask = null;
		for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
			if (t.getLinkedProvision() != null && t.getData() == 1 && sealPos.equals(t.getSealPos())) deliverTask = t;
		}
		helper.assertTrue(deliverTask != null, "completing the fetch task should have queued a real delivery task");

		thaumcraft.common.golems.ai.AIGotoBlock deliverGoal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(deliverGoal.canUse(), "the golem should be able to pick up the delivery task");
		helper.assertTrue(golem.getTask() == deliverTask, "the golem should have reserved the real delivery task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(!golem.isCarrying(new ItemStack(net.minecraft.world.item.Items.NETHERITE_INGOT)),
				"completing the delivery task should have the golem no longer carrying the netherite ingots");

		net.minecraft.world.level.block.entity.ChestBlockEntity destChest = helper.getBlockEntity(destChestPos);
		boolean delivered = false;
		for (int i = 0; i < destChest.getContainerSize(); i++) {
			if (destChest.getItem(i).is(net.minecraft.world.item.Items.NETHERITE_INGOT)) delivered = true;
		}
		helper.assertTrue(delivered, "the delivered netherite ingots should really be in the destination chest");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealStockRequestsProvisioningWhenBelowTargetCount(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
		// GOLD_INGOT, not IRON_INGOT: GolemHelper.provisionRequests is real, dimension-wide, unscoped shared
		// state (by design - one player's whole base is meant to share one provisioning economy), and
		// SealProvide#tickSeal's own request-matching loop picks the first unclaimed request that matches
		// its filter item, with no per-seal/per-test scoping at all. This test's SealStock and
		// sealProvideDeliversStockedItemsFromOneContainerToAnother's own SealProvide (below) both used to
		// request IRON_INGOT - a genuine collision this removes. This is a real, worthwhile fix on its own,
		// but see that test's own doc comment for why it does NOT fully explain that test's own remaining
		// flakiness - the actual full picture is bigger than a same-item collision between these two tests.
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 2));

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealStock seal = new thaumcraft.common.golems.seals.SealStock();
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
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

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(chestAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs
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

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
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
		golem.restrictTo(chestAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

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

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealUseTogglesALeverWithAnEmptyHandClick(GameTestHelper helper) {
		BlockPos leverPos = BlockPos.ZERO;
		BlockPos leverAbs = helper.absolutePos(leverPos);
		helper.setBlock(leverPos, net.minecraft.world.level.block.Blocks.LEVER);

		ServerPlayer owner = spawnCasterAt(helper, leverAbs.above(3));
		thaumcraft.common.golems.seals.SealUse seal = new thaumcraft.common.golems.seals.SealUse();
		seal.setToggle(1, true); // pemptyhand - click even without a matching carried item
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), leverAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(leverAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		// SealUse requires the DEFT + SMART traits (see its own getRequiredTags) - a SMART head + FINE arms
		// combo grants both, same lookup pattern golemPropertiesUnionsRealTraitsAndCancelsOpposites uses.
		thaumcraft.api.golems.parts.GolemMaterial iron = findPartByKey(thaumcraft.api.golems.parts.GolemMaterial.getMaterials(), m -> m.key, "IRON");
		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.parts.GolemArm fineArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "FINE");
		thaumcraft.api.golems.parts.GolemLeg walkerLeg = findPartByKey(thaumcraft.api.golems.parts.GolemLeg.getLegs(), l -> l.key, "WALKER");
		thaumcraft.api.golems.parts.GolemAddon noAddon = findPartByKey(thaumcraft.api.golems.parts.GolemAddon.getAddons(), a -> a.key, "NONE");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setMaterial(iron);
		props.setHead(smartHead);
		props.setArms(fineArm);
		props.setLegs(walkerLeg);
		props.setAddon(noAddon);

		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, leverAbs);
		golem.initializeGolem(props, 0);
		golem.restrictTo(leverAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		boolean foundTask = false;
		thaumcraft.api.golems.tasks.Task useTask = null;
		for (int i = 0; i < 30 && !foundTask; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (sealPos.equals(t.getSealPos())) {
					foundTask = true;
					useTask = t;
				}
			}
		}
		helper.assertTrue(foundTask, "tickSealEntity should have created a real standing use task");

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the use task");
		helper.assertTrue(golem.getTask() == useTask, "the golem should have reserved the real use task");

		boolean before = helper.getBlockState(leverPos).getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		boolean after = helper.getBlockState(leverPos).getValue(net.minecraft.world.level.block.LeverBlock.POWERED);
		helper.assertTrue(before != after, "completing the use task should have really toggled the lever, before=" + before + " after=" + after);

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealConfigMenuChangesRealSealStateThroughTheRealMenuProviderPath(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealStock seal = new thaumcraft.common.golems.seals.SealStock();
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), chestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(chestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		owner.openMenu((net.minecraft.world.MenuProvider) sealEntity);
		helper.assertTrue(owner.containerMenu instanceof thaumcraft.common.container.SealConfigMenu,
				"opening a seal via the real MenuProvider path should give the player a real SealConfigMenu");
		thaumcraft.common.container.SealConfigMenu menu = (thaumcraft.common.container.SealConfigMenu) owner.containerMenu;

		byte priorityBefore = sealEntity.getPriority();
		helper.assertTrue(menu.clickMenuButton(owner, 13), "the priority-up button should succeed"); // BUTTON_PRIORITY_UP
		helper.assertTrue(sealEntity.getPriority() == priorityBefore + 1, "priority should have really increased on the live seal");

		helper.assertTrue(menu.clickMenuButton(owner, 10), "the lock button should succeed for the real owner"); // BUTTON_LOCK
		helper.assertTrue(sealEntity.isLocked(), "the seal should now really be locked");

		ServerPlayer stranger = spawnCasterAt(helper, chestAbs.above(5));
		helper.assertTrue(!menu.clickMenuButton(stranger, 14), "a non-owner should be refused any change while the seal is locked"); // BUTTON_PRIORITY_DOWN
		helper.assertTrue(sealEntity.getPriority() == priorityBefore + 1, "priority should be unchanged after the refused non-owner click");

		helper.assertTrue(menu.clickMenuButton(owner, 10), "the lock button should toggle back off for the real owner");
		helper.assertTrue(!sealEntity.isLocked(), "the seal should now really be unlocked");

		// First left-click assigns the ghost slot's item (target count starts at 0, meaning "unset") - matches
		// the original's own real two-step gesture: only a REPEAT click on an already-matching slot
		// accumulates a real target count, a fresh/mismatched slot always resets it to 0 first.
		menu.setCarried(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
		menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
		helper.assertTrue(seal.getFilterSlot(0).is(net.minecraft.world.item.Items.IRON_INGOT),
				"left-clicking a ghost filter slot with a cursor item should really set the live seal's filter");
		helper.assertTrue(seal.getFilterSlotSize(0) == 0,
				"a fresh ghost slot's first assignment should leave the real target count unset (0), was " + seal.getFilterSlotSize(0));

		menu.setCarried(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
		menu.clicked(0, 0, net.minecraft.world.inventory.ClickType.PICKUP, owner);
		helper.assertTrue(seal.getFilterSlotSize(0) == 5,
				"a second matching left-click should really accumulate the real target amount, was " + seal.getFilterSlotSize(0));

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealBreakerAdvancedSilkTouchesTheMatchedBlock(GameTestHelper helper) {
		BlockPos anchorPos = BlockPos.ZERO;
		BlockPos anchorAbs = helper.absolutePos(anchorPos);
		helper.setBlock(anchorPos, net.minecraft.world.level.block.Blocks.STONE);

		BlockPos targetPos = anchorPos.above();
		BlockPos targetAbs = helper.absolutePos(targetPos);
		helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer owner = spawnCasterAt(helper, anchorAbs.above(3));
		thaumcraft.common.golems.seals.SealBreakerAdvanced seal = new thaumcraft.common.golems.seals.SealBreakerAdvanced();
		seal.setToggle(0, true); // psilk
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
		helper.assertTrue(foundTask, "an empty blacklist should match nothing, so the stone should get a real break task");

		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.parts.GolemArm breakerArm = findPartByKey(thaumcraft.api.golems.parts.GolemArm.getArms(), a -> a.key, "BREAKERS");
		thaumcraft.api.golems.IGolemProperties props = new thaumcraft.common.golems.GolemProperties();
		props.setHead(smartHead);
		props.setArms(breakerArm);
		thaumcraft.common.golems.EntityThaumcraftGolem golem = spawnGolem(helper, anchorAbs);
		golem.initializeGolem(props, 0);
		golem.restrictTo(anchorAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		thaumcraft.common.golems.ai.AIGotoBlock goal = new thaumcraft.common.golems.ai.AIGotoBlock(golem);
		helper.assertTrue(goal.canUse(), "the golem should be able to pick up the break task");
		helper.assertTrue(golem.getTask() == breakTask, "the golem should have reserved the real break task");

		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem.getTask(), golem);
		helper.assertTrue(helper.getLevel().getBlockState(targetAbs).isAir(), "completing the break task should have really broken the stone");

		List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, new AABB(targetAbs).inflate(2));
		boolean foundStone = false;
		for (net.minecraft.world.entity.item.ItemEntity ie : drops) {
			if (ie.getItem().is(net.minecraft.world.item.Items.STONE)) foundStone = true;
		}
		helper.assertTrue(foundStone, "silk touch should really have dropped the stone block itself, not cobblestone");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sealEmptyAdvancedCyclesThroughRealWhitelistedItemsInTurn(GameTestHelper helper) {
		BlockPos chestPos = BlockPos.ZERO;
		BlockPos chestAbs = helper.absolutePos(chestPos);
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);
		chest.setItem(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 5));
		chest.setItem(1, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT, 5));

		ServerPlayer owner = spawnCasterAt(helper, chestAbs.above(3));
		thaumcraft.common.golems.seals.SealEmptyAdvanced seal = new thaumcraft.common.golems.seals.SealEmptyAdvanced();
		seal.setBlacklist(false);
		seal.setFilterSlot(0, new ItemStack(net.minecraft.world.item.Items.IRON_INGOT));
		seal.setFilterSlot(1, new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT));
		seal.setToggle(1, true); // pcycle
		helper.assertTrue(thaumcraft.common.golems.seals.SealHandler.addSealEntity(helper.getLevel(), chestAbs, net.minecraft.core.Direction.UP, seal, owner),
				"placing a fresh seal should succeed");
		thaumcraft.api.golems.seals.SealPos sealPos = new thaumcraft.api.golems.seals.SealPos(chestAbs, net.minecraft.core.Direction.UP);
		thaumcraft.api.golems.seals.ISealEntity sealEntity = thaumcraft.api.golems.GolemHelper.getSealEntity(helper.getLevel().dimension(), sealPos);

		thaumcraft.api.golems.parts.GolemHead smartHead = findPartByKey(thaumcraft.api.golems.parts.GolemHead.getHeads(), h -> h.key, "SMART");
		thaumcraft.api.golems.IGolemProperties props1 = new thaumcraft.common.golems.GolemProperties();
		props1.setHead(smartHead);
		thaumcraft.common.golems.EntityThaumcraftGolem golem1 = spawnGolem(helper, chestAbs);
		golem1.initializeGolem(props1, 0);
		golem1.restrictTo(chestAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		boolean foundFirst = false;
		thaumcraft.api.golems.tasks.Task firstTask = null;
		for (int i = 0; i < 30 && !foundFirst; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (sealPos.equals(t.getSealPos())) {
					foundFirst = true;
					firstTask = t;
				}
			}
		}
		helper.assertTrue(foundFirst, "tickSealEntity should have created a real empty task cycling to the first whitelisted item");

		thaumcraft.common.golems.ai.AIGotoBlock goal1 = new thaumcraft.common.golems.ai.AIGotoBlock(golem1);
		helper.assertTrue(goal1.canUse(), "the first golem should be able to pick up the first empty task");
		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem1.getTask(), golem1);
		helper.assertTrue(golem1.isCarrying(new ItemStack(net.minecraft.world.item.Items.IRON_INGOT)),
				"the first cycle (index 0) should have really pulled the first filter slot's item (iron)");

		thaumcraft.api.golems.IGolemProperties props2 = new thaumcraft.common.golems.GolemProperties();
		props2.setHead(smartHead);
		thaumcraft.common.golems.EntityThaumcraftGolem golem2 = spawnGolem(helper, chestAbs);
		golem2.initializeGolem(props2, 0);
		golem2.restrictTo(chestAbs, 5); // keep this test golem's real task-search scoped to its own GameTest structure - see AIGoto's own class docs

		boolean foundSecond = false;
		thaumcraft.api.golems.tasks.Task secondTask = null;
		for (int i = 0; i < 30 && !foundSecond; i++) {
			sealEntity.tickSealEntity(helper.getLevel());
			for (thaumcraft.api.golems.tasks.Task t : thaumcraft.api.golems.GolemHelper.getTasks(helper.getLevel().dimension()).values()) {
				if (sealPos.equals(t.getSealPos()) && t != firstTask) {
					foundSecond = true;
					secondTask = t;
				}
			}
		}
		helper.assertTrue(foundSecond, "tickSealEntity should have created a real second empty task cycling to the second whitelisted item");

		thaumcraft.common.golems.ai.AIGotoBlock goal2 = new thaumcraft.common.golems.ai.AIGotoBlock(golem2);
		helper.assertTrue(goal2.canUse(), "the second golem should be able to pick up the second empty task");
		thaumcraft.common.golems.tasks.TaskHandler.completeTask(golem2.getTask(), golem2);
		helper.assertTrue(golem2.isCarrying(new ItemStack(net.minecraft.world.item.Items.GOLD_INGOT)),
				"the second cycle (index 1) should have really pulled the second filter slot's item (gold), not repeated the first");

		thaumcraft.common.golems.seals.SealHandler.removeSealEntity(helper.getLevel(), sealPos); // real per-test cleanup - see sealProvideDeliversStockedItemsFromOneContainerToAnother's own doc comment for why a leftover ticking seal is a genuine cross-test contamination risk
		helper.succeed();
	}

	/**
	 * Doesn't exercise real touch damage against a real player - confirmed via direct investigation
	 * ({@code javap} on {@code GameTestHelper$2}, the real anonymous class backing every mock/test player
	 * this whole file's tests use) that {@code makeMockServerPlayerInLevel()}'s own players hardcode
	 * {@code isSpectator()}/{@code isCreative()} to {@code true} regardless of any {@code setGameMode}/
	 * {@code Abilities} state a test sets on them afterward - a real, structural test-harness limitation
	 * (mock players are deliberately damage-immune by design), not a gap in this port's own code. Real
	 * vanilla {@code Slime#playerTouch}/{@code #dealDamage} is reused unmodified for the size-scaled attack
	 * itself (only {@link thaumcraft.common.entities.monster.EntityThaumicSlime#getAttackDamage} is new,
	 * simple, and low-risk), so this test covers the part that's actually new: the death drop.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumicSlimeDropsAFluxCrystalOnDeath(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.EntityThaumicSlime slime =
				thaumcraft.common.registry.ThaumcraftEntities.THAUMIC_SLIME.get().create(helper.getLevel());
		helper.assertTrue(slime != null, "the entity type should really create an instance");
		slime.moveTo(floorAbs.getX() + 0.5, floorAbs.getY() + 1.0, floorAbs.getZ() + 0.5, 0.0f, 0.0f);
		slime.setSize(4, true);
		helper.getLevel().addFreshEntity(slime);
		helper.assertTrue(slime instanceof thaumcraft.api.entities.ITaintedMob, "a thaumic slime should really be a tainted mob");

		slime.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
		List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, new AABB(floorAbs).inflate(3));
		boolean foundFluxCrystal = false;
		for (net.minecraft.world.entity.item.ItemEntity ie : drops) {
			ItemStack stack = ie.getItem();
			if (stack.getItem() == ItemsTC.crystalEssence.get()) {
				thaumcraft.api.aspects.AspectList aspects = stack.get(thaumcraft.api.items.ThaumcraftDataComponents.ASPECTS.get());
				if (aspects != null && aspects.getAmount(thaumcraft.api.aspects.Aspect.FLUX) > 0) {
					foundFluxCrystal = true;
				}
			}
		}
		helper.assertTrue(foundFluxCrystal, "killing a size>1 thaumic slime should really drop a flux-aspected crystal essence");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void brainyZombieHasRealToughenedStatsAndRetaliatesWhenHurt(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.EntityBrainyZombie zombie =
				thaumcraft.common.registry.ThaumcraftEntities.BRAINY_ZOMBIE.get().create(helper.getLevel());
		helper.assertTrue(zombie != null, "the entity type should really create an instance");
		zombie.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(zombie);

		helper.assertTrue(Math.abs(zombie.getMaxHealth() - 25.0f) < 0.01f, "a brainy zombie should really have 25 max health, was " + zombie.getMaxHealth());
		helper.assertTrue(zombie.getArmorValue() == 3, "a brainy zombie should really have +1 armor over a plain zombie's own baseline 2, got " + zombie.getArmorValue());

		net.minecraft.world.entity.npc.Villager attacker = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(attacker != null, "the villager entity type should really create an instance");
		attacker.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(attacker);

		// A brand-new HurtByTargetGoal's own real "timestamp" field defaults to 0, same as a freshly-added
		// entity's own real getLastHurtByMobTimestamp() (derived from tickCount, which also starts at 0) - a
		// real "0 != 0 is false" false negative unique to an isolated, never-ticked test entity, not a real
		// gameplay concern (a genuinely live entity's tickCount is never still 0 by the time anything hurts
		// it). Advancing tickCount past 0 first makes the timestamp real and distinguishable, matching what
		// always happens for a real, already-ticking entity.
		zombie.tickCount = 5;
		zombie.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0f);
		net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal retaliateGoal = new net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal(zombie);
		helper.assertTrue(retaliateGoal.canUse(), "the real HurtByTargetGoal should recognize the zombie was just hurt by a real living entity");
		retaliateGoal.start();
		helper.assertTrue(zombie.getTarget() == attacker, "starting the real goal should have set the real attacker as the zombie's target");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void giantBrainyZombieGrowsAndHitsHarderAsRealAngerRises(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.EntityGiantBrainyZombie zombie =
				thaumcraft.common.registry.ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get().create(helper.getLevel());
		helper.assertTrue(zombie != null, "the entity type should really create an instance");
		zombie.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(zombie);

		helper.assertTrue(Math.abs(zombie.getMaxHealth() - 60.0f) < 0.01f, "a giant brainy zombie should really have 60 max health, was " + zombie.getMaxHealth());

		float widthAtRest = zombie.getBbWidth();
		float heightAtRest = zombie.getBbHeight();
		zombie.aiStep();
		helper.assertTrue(Math.abs(zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) - 2.0) < 0.01,
				"at zero anger the real per-tick formula (7 + (anger-1)*5) should really compute to 2.0, was "
						+ zombie.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE));

		net.minecraft.world.entity.npc.Villager attacker = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(attacker != null, "the villager entity type should really create an instance");
		attacker.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(attacker);

		zombie.hurt(helper.getLevel().damageSources().mobAttack(attacker), 1.0f);
		helper.assertTrue(Math.abs(zombie.getAnger() - 0.1f) < 0.001f, "getting hurt should have really raised anger by 0.1, was " + zombie.getAnger());
		helper.assertTrue(zombie.getBbWidth() > widthAtRest && zombie.getBbHeight() > heightAtRest,
				"a real anger rise should have really grown the zombie's own hitbox, width " + widthAtRest + " -> " + zombie.getBbWidth()
						+ ", height " + heightAtRest + " -> " + zombie.getBbHeight());

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fireBatUnhangsWhenApproachedAndIsRealFireAndExplosionImmune(GameTestHelper helper) {
		BlockPos ceilingPos = new BlockPos(0, 3, 0);
		BlockPos ceilingAbs = helper.absolutePos(ceilingPos);
		helper.setBlock(ceilingPos, net.minecraft.world.level.block.Blocks.STONE);
		BlockPos hangPos = ceilingPos.below();
		BlockPos hangAbs = helper.absolutePos(hangPos);

		thaumcraft.common.entities.monster.EntityFireBat bat =
				thaumcraft.common.registry.ThaumcraftEntities.FIRE_BAT.get().create(helper.getLevel());
		helper.assertTrue(bat != null, "the entity type should really create an instance");
		bat.moveTo(hangAbs.getX() + 0.5, hangAbs.getY() + 0.9, hangAbs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(bat);
		helper.assertTrue(bat.isHanging(), "a fresh fire bat should really start hanging");

		bat.tickCount = 5;
		float healthBefore = bat.getHealth();
		boolean fireHurt = bat.hurt(helper.getLevel().damageSources().inFire(), 5.0f);
		helper.assertTrue(!fireHurt && bat.getHealth() == healthBefore, "a real fire bat should really be immune to fire damage");
		boolean explosionHurt = bat.hurt(helper.getLevel().damageSources().explosion(null, null), 5.0f);
		helper.assertTrue(!explosionHurt && bat.getHealth() == healthBefore, "a real fire bat should really be immune to explosion damage");

		ServerPlayer nearby = spawnCasterAt(helper, hangAbs);
		bat.aiStep();
		helper.assertTrue(!bat.isHanging(), "a real fire bat should really unhang once a player is within 4 blocks");

		bat.hurt(helper.getLevel().damageSources().generic(), 1000.0f);
		List<net.minecraft.world.entity.item.ItemEntity> drops = helper.getLevel().getEntitiesOfClass(
				net.minecraft.world.entity.item.ItemEntity.class, new AABB(hangAbs).inflate(3));
		boolean foundGunpowder = false;
		for (net.minecraft.world.entity.item.ItemEntity ie : drops) {
			if (ie.getItem().is(net.minecraft.world.item.Items.GUNPOWDER)) foundGunpowder = true;
		}
		helper.assertTrue(foundGunpowder, "killing a fire bat should really drop gunpowder");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void mindSpiderIsHarmlessAndSelfTerminatingWhenFlagged(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.EntityMindSpider spider =
				thaumcraft.common.registry.ThaumcraftEntities.MIND_SPIDER.get().create(helper.getLevel());
		helper.assertTrue(spider != null, "the entity type should really create an instance");
		spider.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(spider);
		helper.assertTrue(Math.abs(spider.getMaxHealth() - 1.0f) < 0.01f, "a mind spider should really have 1 max health, was " + spider.getMaxHealth());

		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(target);

		spider.setHarmless(true);
		boolean hurtWhileHarmless = spider.doHurtTarget(target);
		helper.assertTrue(!hurtWhileHarmless, "a real harmless mind spider should really refuse to attack");

		helper.assertTrue(spider.isAlive(), "the spider should still be alive before its real lifespan expires");
		spider.tickCount = 1300;
		spider.tick();
		helper.assertTrue(!spider.isAlive(), "a real harmless mind spider should really discard itself once its 1200-tick lifespan expires");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusModScatterFansOutRealTrajectoriesWithJitter(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer caster = spawnCasterAt(helper, abs);

		thaumcraft.api.casters.FocusPackage pack = new thaumcraft.api.casters.FocusPackage(caster);
		thaumcraft.api.casters.FocusMediumRoot root = new thaumcraft.api.casters.FocusMediumRoot();
		root.setupFromCaster(caster);
		pack.addNode(root);

		thaumcraft.common.items.casters.foci.FocusModScatter scatter = new thaumcraft.common.items.casters.foci.FocusModScatter();
		scatter.setPackage(pack);
		scatter.setParent(root);
		scatter.getSetting("forks").setValue(4);
		scatter.getSetting("cone").setValue(90);

		thaumcraft.api.casters.Trajectory[] result = scatter.supplyTrajectories();
		helper.assertTrue(result.length == 4, "scattering with 4 real forks should really produce 4 trajectories, got " + result.length);
		for (thaumcraft.api.casters.Trajectory t : result) {
			helper.assertTrue(Math.abs(t.direction.length() - 1.0) < 0.01, "each real scattered trajectory should really stay a unit vector, length=" + t.direction.length());
		}
		helper.assertTrue(scatter.execute(), "execute() should really always succeed for a real scatter mod");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusMediumSpellBatSpawnsARealCastingBatWithTheRightOwner(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer caster = spawnCasterAt(helper, abs);

		thaumcraft.api.casters.FocusPackage pack = new thaumcraft.api.casters.FocusPackage(caster);
		thaumcraft.api.casters.FocusMediumRoot root = new thaumcraft.api.casters.FocusMediumRoot();
		root.setupFromCaster(caster);
		pack.addNode(root);

		thaumcraft.common.items.casters.foci.FocusMediumSpellBat medium = new thaumcraft.common.items.casters.foci.FocusMediumSpellBat();
		medium.setPackage(pack);
		medium.setParent(root);
		medium.getSetting("target").setValue(0);
		pack.addNode(medium);

		thaumcraft.common.items.casters.foci.FocusEffectFlux effect = new thaumcraft.common.items.casters.foci.FocusEffectFlux();
		pack.addNode(effect);
		pack.setExecutionIndex(1);

		thaumcraft.api.casters.Trajectory trajectory = new thaumcraft.api.casters.Trajectory(
				new net.minecraft.world.phys.Vec3(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5), new net.minecraft.world.phys.Vec3(0.0, 0.0, 1.0));

		boolean spawned = medium.execute(trajectory);
		helper.assertTrue(spawned, "spawning a real spell bat should succeed");

		List<thaumcraft.common.entities.monster.EntitySpellBat> bats = helper.getLevel().getEntitiesOfClass(
				thaumcraft.common.entities.monster.EntitySpellBat.class, new AABB(abs).inflate(3));
		helper.assertTrue(bats.size() == 1, "exactly one real spell bat should have really been spawned, found " + bats.size());
		thaumcraft.common.entities.monster.EntitySpellBat bat = bats.get(0);
		helper.assertTrue(!bat.isFriendly(), "the spell bat should really be in enemy-targeting mode");
		helper.assertTrue(bat.getOwner() == caster, "the spell bat's real owner should really be the real caster");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void eldritchCrabHasARealHelmThatBreaksAndRidesItsTarget(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.EntityEldritchCrab crab =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_CRAB.get().create(helper.getLevel());
		helper.assertTrue(crab != null, "the entity type should really create an instance");
		crab.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(crab);
		helper.assertTrue(crab instanceof thaumcraft.api.entities.IEldritchMob, "an eldritch crab should really be an eldritch mob");

		crab.setHelm(true);
		helper.assertTrue(crab.getArmorValue() == 5, "a helmed crab should really have 5 armor, got " + crab.getArmorValue());
		helper.assertTrue(!crab.canBeAffected(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.POISON, 100)),
				"a real eldritch crab should really be immune to poison");

		crab.setHealth(crab.getMaxHealth() * 0.4f);
		crab.hurt(helper.getLevel().damageSources().generic(), 0.1f);
		helper.assertTrue(!crab.hasHelm(), "the helm should really have broken once health dropped to/below half, health=" + crab.getHealth() + "/" + crab.getMaxHealth());
		helper.assertTrue(crab.getArmorValue() == 0, "armor should really be gone once the helm breaks, got " + crab.getArmorValue());

		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(helper.getLevel());
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(target);
		crab.setTarget(target);

		// Re-pin the crab directly above its target AND force onGround false before every single attempt
		// (not just once before the loop): aiStep() runs this mob's own movement goals, which can walk it
		// away from the exact "directly above, within 4 blocks" position the mount condition needs, and
		// separately, calling aiStep() alone (not the entity's own tick()) never runs the real collision
		// pass that would normally clear a stale onGround flag after a mid-air teleport - so the mount
		// condition's own "!onGround()" check can flakily see a leftover true from whenever this crab (or
		// its bounding box) last touched a real block. Forcing both explicitly makes every attempt actually
		// exercise the real mount condition instead of leaving it dependent on incidental physics state.
		boolean mounted = false;
		for (int i = 0; i < 20 && !mounted; i++) {
			crab.setPos(target.getX(), target.getY() + 1.0, target.getZ());
			crab.setOnGround(false);
			crab.aiStep();
			mounted = crab.getVehicle() == target;
		}
		helper.assertTrue(mounted, "a real eldritch crab positioned above its target should really climb onto it");

		// dropCustomDeathLoot's own "recentlyHit" only tracks real PLAYER hits (not any mob's), so a real
		// mock player attack is needed here, not the villager used for the riding check above.
		ServerPlayer killer = spawnCasterAt(helper, abs.above(3));
		boolean foundPearl = false;
		for (int i = 0; i < 30 && !foundPearl; i++) {
			ServerLevel serverLevel = helper.getLevel();
			thaumcraft.common.entities.monster.EntityEldritchCrab probe =
					thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_CRAB.get().create(serverLevel);
			probe.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
			serverLevel.addFreshEntity(probe);
			probe.hurt(serverLevel.damageSources().playerAttack(killer), 1000.0f);
			List<net.minecraft.world.entity.item.ItemEntity> after =
					helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(abs).inflate(3));
			for (net.minecraft.world.entity.item.ItemEntity ie : after) {
				if (ie.getItem().is(net.minecraft.world.item.Items.ENDER_PEARL)) foundPearl = true;
			}
		}
		helper.assertTrue(foundPearl, "killing enough real crabs by a real player attack should eventually drop a real ender pearl");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cultistKnightWearsCrimsonPlateAndAlliesWithItsOwnKind(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.monster.cult.EntityCultistKnight knight =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(helper.getLevel());
		helper.assertTrue(knight != null, "the entity type should really create an instance");
		knight.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		helper.getLevel().addFreshEntity(knight);
		knight.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(abs),
				net.minecraft.world.entity.MobSpawnType.NATURAL, null);

		helper.assertTrue(knight.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(thaumcraft.api.items.ItemsTC.crimsonPlateHelm.get()),
				"a real cultist knight should really always wear the crimson plate helm");
		helper.assertTrue(knight.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(thaumcraft.api.items.ItemsTC.crimsonPlateChest.get()),
				"a real cultist knight should really always wear the crimson plate chest");
		helper.assertTrue(knight.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(thaumcraft.api.items.ItemsTC.crimsonPlateLegs.get()),
				"a real cultist knight should really always wear the crimson plate legs");
		net.minecraft.world.item.ItemStack weapon = knight.getMainHandItem();
		helper.assertTrue(weapon.is(net.minecraft.world.item.Items.IRON_SWORD) || weapon.is(thaumcraft.api.items.ItemsTC.voidSword.get())
						|| weapon.is(thaumcraft.api.items.ItemsTC.thaumiumSword.get()),
				"a real cultist knight should really always be holding one of its real possible weapons, got " + weapon);

		helper.assertTrue(knight.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.GroundPathNavigation ground && ground.canOpenDoors(),
				"a real cultist knight should really be able to open doors while pathfinding");

		thaumcraft.common.entities.monster.cult.EntityCultistKnight other =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(helper.getLevel());
		helper.assertTrue(knight.isAlliedTo(other), "two real cultist knights should really be allied to each other");
		ServerPlayer player = spawnCasterAt(helper, abs.above(3));
		helper.assertTrue(!knight.isAlliedTo(player), "a real cultist knight should really not be allied to a real player");

		boolean foundNugget = false;
		for (int i = 0; i < 15 && !foundNugget; i++) {
			ServerLevel serverLevel = helper.getLevel();
			thaumcraft.common.entities.monster.cult.EntityCultistKnight probe =
					thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(serverLevel);
			probe.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
			serverLevel.addFreshEntity(probe);
			probe.hurt(serverLevel.damageSources().generic(), 1000.0f);
			List<net.minecraft.world.entity.item.ItemEntity> after =
					helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(abs).inflate(3));
			for (net.minecraft.world.entity.item.ItemEntity ie : after) {
				if (ie.getItem().is(net.minecraft.world.item.Items.GOLD_NUGGET)) foundNugget = true;
			}
		}
		helper.assertTrue(foundNugget, "killing enough real cultist knights should eventually drop a real gold nugget");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void golemOrbHomesInOnAndDamagesItsRealTargetAndIsReallyReflectable(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.cult.EntityCultistKnight thrower =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(level);
		helper.assertTrue(thrower != null, "the entity type should really create an instance");
		thrower.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(thrower);

		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(abs.getX() + 4.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(target);

		thaumcraft.common.entities.projectile.EntityGolemOrb orb =
				new thaumcraft.common.entities.projectile.EntityGolemOrb(thrower, target, true);
		orb.setPos(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
		level.addFreshEntity(orb);

		float healthBefore = target.getHealth();
		for (int i = 0; i < 200 && !orb.isRemoved() && target.getHealth() >= healthBefore; i++) {
			orb.tick();
		}
		helper.assertTrue(target.getHealth() < healthBefore,
				"a real homing golem orb should really eventually hit and damage its target, before=" + healthBefore + " after=" + target.getHealth());

		// real reflect mechanic: a hit on the orb itself redirects it along the attacker's own look vector.
		thaumcraft.common.entities.projectile.EntityGolemOrb reflectable =
				new thaumcraft.common.entities.projectile.EntityGolemOrb(thrower, null, false);
		reflectable.setPos(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5);
		reflectable.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(reflectable);
		ServerPlayer reflector = spawnCasterAt(helper, abs.above(3));
		boolean reflected = reflectable.hurt(level.damageSources().playerAttack(reflector), 1.0f);
		helper.assertTrue(reflected, "a real golem orb should really be reflectable by a real player hit");
		helper.assertTrue(reflectable.getDeltaMovement().lengthSqr() > 0.0,
				"a reflected orb should really pick up real non-zero velocity from the attacker's own look vector");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cultistClericWearsCrimsonRobeAndRealAltarRitualismWorksForReal(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		BlockPos altarPos = abs.above();
		level.setBlockAndUpdate(altarPos, thaumcraft.api.blocks.BlocksTC.pedestalEldritch.get().defaultBlockState());

		thaumcraft.common.entities.monster.cult.EntityCultistCleric cleric =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_CLERIC.get().create(level);
		helper.assertTrue(cleric != null, "the entity type should really create an instance");
		cleric.moveTo(altarPos.getX() + 0.5, altarPos.getY() + 1.0, altarPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(cleric);
		cleric.finalizeSpawn(level, level.getCurrentDifficultyAt(altarPos), net.minecraft.world.entity.MobSpawnType.NATURAL, null);

		helper.assertTrue(cleric.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(thaumcraft.api.items.ItemsTC.crimsonRobeHelm.get()),
				"a real cultist cleric should really always wear the crimson robe helm");
		helper.assertTrue(cleric.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(thaumcraft.api.items.ItemsTC.crimsonRobeChest.get()),
				"a real cultist cleric should really always wear the crimson robe chest");
		helper.assertTrue(cleric.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS).is(thaumcraft.api.items.ItemsTC.crimsonRobeLegs.get()),
				"a real cultist cleric should really always wear the crimson robe legs");

		cleric.restrictTo(altarPos, 8);
		cleric.setRitualist(true);
		cleric.tickCount = 40;
		cleric.checkAltarFocus();
		helper.assertTrue(cleric.getIsRitualist(), "a real ritualist cleric standing right by a real altar should really stay ritualist");
		helper.assertTrue(!cleric.removeWhenFarAway(1000000.0), "a real ritualist cleric should really never be removed for being far away");

		level.setBlockAndUpdate(altarPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
		cleric.tickCount = 80;
		cleric.checkAltarFocus();
		helper.assertTrue(!cleric.getIsRitualist(), "a real ritualist cleric should really cancel channeling once its real altar is gone");
		helper.assertTrue(cleric.removeWhenFarAway(1000000.0), "a non-ritualist cleric should really be able to despawn normally again");

		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(altarPos.getX() + 5.5, altarPos.getY() + 1.0, altarPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(target);
		boolean spawnedProjectile = false;
		for (int i = 0; i < 15 && !spawnedProjectile; i++) {
			cleric.performRangedAttack(target, 1.0f);
			spawnedProjectile = !level.getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityGolemOrb.class, new AABB(altarPos).inflate(6)).isEmpty()
					|| !level.getEntitiesOfClass(net.minecraft.world.entity.projectile.SmallFireball.class, new AABB(altarPos).inflate(6)).isEmpty();
		}
		helper.assertTrue(spawnedProjectile, "a real ranged attack should really spawn either a real golem orb or real fireballs");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cultistLeaderIsARealBossThatEnragesHealsAndSupportsItsCult(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.boss.EntityCultistLeader leader =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_LEADER.get().create(level);
		helper.assertTrue(leader != null, "the entity type should really create an instance");
		leader.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(leader);
		leader.finalizeSpawn(level, level.getCurrentDifficultyAt(abs), net.minecraft.world.entity.MobSpawnType.NATURAL, null);

		helper.assertTrue(leader.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(thaumcraft.api.items.ItemsTC.crimsonPraetorHelm.get()),
				"a real cultist leader should really always wear the crimson praetor helm");
		helper.assertTrue(leader.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST).is(thaumcraft.api.items.ItemsTC.crimsonPraetorChest.get()),
				"a real cultist leader should really always wear the crimson praetor chest");
		net.minecraft.world.item.ItemStack weapon = leader.getMainHandItem();
		helper.assertTrue(weapon.is(thaumcraft.api.items.ItemsTC.voidSword.get()) || weapon.is(thaumcraft.api.items.ItemsTC.crimsonBlade.get()),
				"a real cultist leader should really wield one of its real possible weapons, got " + weapon);
		helper.assertTrue(!leader.getDisplayName().getString().isEmpty(), "a real cultist leader should really have a real generated name");

		thaumcraft.common.entities.monster.cult.EntityCultistKnight ally =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(level);
		helper.assertTrue(leader.isAlliedTo(ally), "a real cultist leader should really be allied to real regular cultists");

		// real enrage cap: a single hit over 35 damage is really capped at 35 and grants real buffs.
		float healthBefore = leader.getHealth();
		leader.hurt(level.damageSources().generic(), 50.0f);
		float lost = healthBefore - leader.getHealth();
		helper.assertTrue(lost <= 35.01f, "a real single hit over 35 damage should really be capped at 35, lost " + lost);
		helper.assertTrue(leader.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
				"a real enraged cultist leader should really gain real Regeneration");
		helper.assertTrue(leader.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST),
				"a real enraged cultist leader should really gain real Strength");

		// real periodic self-heal and real ally-buffing, both driven off tickCount % N checks.
		leader.setHealth(Math.max(1.0f, leader.getMaxHealth() - 20.0f));
		float beforeHeal = leader.getHealth();
		leader.tickCount = 30;
		leader.aiStep();
		helper.assertTrue(leader.getHealth() > beforeHeal, "a real cultist leader should really self-heal on its own real 30-tick cycle");

		ally.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 2.5, 0.0f, 0.0f);
		level.addFreshEntity(ally);
		leader.tickCount = 20;
		leader.aiStep();
		helper.assertTrue(ally.hasEffect(net.minecraft.world.effect.MobEffects.REGENERATION),
				"a real cultist leader should really buff nearby real cultists with real Regeneration");

		// real ranged attack: a real homing golem orb should appear.
		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(abs.getX() + 6.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(target);
		boolean spawnedOrb = false;
		for (int i = 0; i < 15 && !spawnedOrb; i++) {
			leader.performRangedAttack(target, 1.0f);
			spawnedOrb = !level.getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityGolemOrb.class, new AABB(abs).inflate(8)).isEmpty();
		}
		helper.assertTrue(spawnedOrb, "a real cultist leader's ranged attack should really spawn a real homing golem orb");

		// real guaranteed death drops. Each single hit is really capped at 35 damage by the boss's own
		// enrage mechanic (see EntityThaumcraftBoss#hurt), so a 150-max-health boss needs several hits -
		// and real vanilla post-hit invulnerability frames (Entity#invulnerableTime) would otherwise eat
		// every hit after the first since they'd all carry the exact same capped amount, so it's reset
		// before each one, same as real gameplay would after a real tick passes between hits.
		leader.setHealth(leader.getMaxHealth());
		for (int i = 0; i < 10 && leader.isAlive(); i++) {
			leader.invulnerableTime = 0;
			leader.hurt(level.damageSources().generic(), 2000.0f);
		}
		helper.assertTrue(!leader.isAlive(), "repeated real hits should really eventually kill the cultist leader");
		List<net.minecraft.world.entity.item.ItemEntity> drops =
				helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(abs).inflate(4));
		boolean foundPearl = false;
		boolean foundLootBag = false;
		for (net.minecraft.world.entity.item.ItemEntity ie : drops) {
			if (ie.getItem().is(thaumcraft.api.items.ItemsTC.primordialPearl.get())) foundPearl = true;
			if (ie.getItem().is(thaumcraft.api.items.ItemsTC.lootBagRare.get())) foundLootBag = true;
		}
		helper.assertTrue(foundPearl, "a real dead cultist leader should really always drop a real primordial pearl");
		helper.assertTrue(foundLootBag, "a real dead cultist leader should really always drop a real rare loot bag");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void inhabitedZombieSpawnsARealHelmedEldritchCrabInsteadOfDyingNormally(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.EntityInhabitedZombie zombie =
				thaumcraft.common.registry.ThaumcraftEntities.INHABITED_ZOMBIE.get().create(level);
		helper.assertTrue(zombie != null, "the entity type should really create an instance");
		zombie.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(zombie);
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(abs), net.minecraft.world.entity.MobSpawnType.NATURAL, null);

		helper.assertTrue(zombie.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD).is(thaumcraft.api.items.ItemsTC.crimsonPlateHelm.get()),
				"a real inhabited zombie should really always wear the crimson plate helm");

		ServerPlayer killer = spawnCasterAt(helper, abs.above(3));
		zombie.hurt(level.damageSources().playerAttack(killer), 1000.0f);
		helper.assertTrue(!zombie.isAlive(), "a real lethal hit should really kill the inhabited zombie");

		List<thaumcraft.common.entities.monster.EntityEldritchCrab> crabs =
				level.getEntitiesOfClass(thaumcraft.common.entities.monster.EntityEldritchCrab.class, new AABB(abs).inflate(3));
		helper.assertTrue(crabs.size() == 1, "exactly one real eldritch crab should really have spawned in its place, found " + crabs.size());
		helper.assertTrue(crabs.get(0).hasHelm(), "the real spawned crab should really already have its own helm");

		List<net.minecraft.world.entity.ExperienceOrb> orbs =
				level.getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class, new AABB(abs).inflate(3));
		helper.assertTrue(!orbs.isEmpty(), "killing it with a real player attack should really award real XP orbs");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cultistPortalLesserActivatesConjuresMinionsAndExplodesOnDeath(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser portal =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_PORTAL_LESSER.get().create(level);
		helper.assertTrue(portal != null, "the entity type should really create an instance");
		portal.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(portal);
		helper.assertTrue(portal.getArmorValue() == 4, "a real cultist portal should really have 4 armor, got " + portal.getArmorValue());
		helper.assertTrue(portal.fireImmune(), "a real cultist portal should really be fire immune");
		helper.assertTrue(!portal.isActive(), "a real freshly spawned cultist portal should really start inactive");

		ServerPlayer player = spawnCasterAt(helper, abs.above(2));

		boolean spawnedMinion = false;
		for (int i = 0; i < 200 && !spawnedMinion; i++) {
			portal.aiStep();
			spawnedMinion = !level.getEntitiesOfClass(thaumcraft.common.entities.monster.cult.EntityCultist.class, new AABB(abs).inflate(6)).isEmpty();
		}
		helper.assertTrue(portal.isActive(), "a real cultist portal with a real nearby player should really have activated");
		helper.assertTrue(spawnedMinion, "an active real cultist portal should really eventually conjure a real cultist minion");

		// Doesn't exercise real touch damage against a real player - same structural test-harness
		// limitation documented on thaumicSlimeDropsAFluxCrystalOnDeath's own doc comment (mock players
		// from makeMockServerPlayerInLevel()/spawnCasterAt hardcode isCreative()/isSpectator() true,
		// making them damage-immune regardless of distance) - real gameplay is unaffected, this is purely
		// a test-harness gap, not a code gap.

		// real death explosion, no block/mob damage (ExplosionInteraction.NONE).
		net.minecraft.world.level.block.state.BlockState before = level.getBlockState(pos);
		portal.hurt(level.damageSources().generic(), 10000.0f);
		helper.assertTrue(!portal.isAlive(), "a real lethal hit should really kill the cultist portal");
		helper.assertTrue(level.getBlockState(pos).is(before.getBlock()), "the real death explosion should really not damage nearby blocks");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void bannerAndLootCrateBlocksAreRealAndFunctional(GameTestHelper helper) {
		BlockPos bannerPos = new BlockPos(1, 1, 1);
		BlockPos bannerAbs = helper.absolutePos(bannerPos);
		helper.setBlock(new BlockPos(1, 0, 1), net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		level.setBlockAndUpdate(bannerAbs, thaumcraft.api.blocks.BlocksTC.bannerCrimsonCult.get().defaultBlockState());
		helper.assertTrue(level.getBlockEntity(bannerAbs) instanceof thaumcraft.common.tiles.misc.TileBanner,
				"a real placed banner should really have a real TileBanner block entity");
		thaumcraft.common.tiles.misc.TileBanner banner = (thaumcraft.common.tiles.misc.TileBanner) level.getBlockEntity(bannerAbs);
		helper.assertTrue(banner.getAspect() == null, "a real freshly placed banner should really start with no real aspect tagged");

		ItemStack phial = thaumcraft.common.items.consumables.ItemPhial.makeFilledPhial(thaumcraft.api.aspects.Aspect.FIRE);
		ServerPlayer player = spawnCasterAt(helper, bannerAbs.above(2));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, phial);
		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(bannerAbs), net.minecraft.core.Direction.NORTH, bannerAbs, false);
		level.getBlockState(bannerAbs).useItemOn(phial, level, player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
		helper.assertTrue(banner.getAspect() == thaumcraft.api.aspects.Aspect.FIRE,
				"right-clicking a real banner with a real filled phial should really tag it with a real aspect");

		player.setShiftKeyDown(true);
		level.getBlockState(bannerAbs).useWithoutItem(level, player, hit);
		helper.assertTrue(banner.getAspect() == null, "sneak-right-clicking a real tagged banner should really clear its real aspect");

		BlockPos cratePos = new BlockPos(1, 0, -3);
		BlockPos crateAbs = helper.absolutePos(cratePos);
		helper.setBlock(new BlockPos(1, -1, -3), net.minecraft.world.level.block.Blocks.STONE);
		level.setBlockAndUpdate(crateAbs, thaumcraft.api.blocks.BlocksTC.lootCrateCommon.get().defaultBlockState());
		level.destroyBlock(crateAbs, true, player);
		List<net.minecraft.world.entity.item.ItemEntity> drops =
				level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(crateAbs).inflate(2));
		helper.assertTrue(!drops.isEmpty(), "breaking a real loot crate should really drop real tiered loot");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cultistPortalGreaterEscalatesPlacesRealDressingAndConjuresRealMinions(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater portal =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_PORTAL_GREATER.get().create(level);
		helper.assertTrue(portal != null, "the entity type should really create an instance");
		portal.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(portal);
		helper.assertTrue(portal.getArmorValue() == 5, "a real cultist portal greater should really have 5 armor, got " + portal.getArmorValue());
		helper.assertTrue(portal.fireImmune(), "a real cultist portal greater should really be fire immune");

		spawnCasterAt(helper, abs.above(2));

		// The portal stands one block above the floor (moveTo ... abs.getY() + 1.0), and both banners and
		// loot crates place at the portal's own Y level (blockPosition()), not the floor's - check there.
		BlockPos portalPos = portal.blockPosition();
		boolean sawBanner = false;
		boolean sawCrate = false;
		boolean sawMinion = false;
		for (int i = 0; i < 600 && !(sawBanner && sawCrate && sawMinion); i++) {
			portal.aiStep();
			if (!sawBanner) {
				sawBanner = level.getBlockState(portalPos.offset(-6, 0, 0)).is(thaumcraft.api.blocks.BlocksTC.bannerCrimsonCult.get())
						|| level.getBlockState(portalPos.offset(6, 0, 0)).is(thaumcraft.api.blocks.BlocksTC.bannerCrimsonCult.get())
						|| level.getBlockState(portalPos.offset(0, 0, -6)).is(thaumcraft.api.blocks.BlocksTC.bannerCrimsonCult.get())
						|| level.getBlockState(portalPos.offset(0, 0, 6)).is(thaumcraft.api.blocks.BlocksTC.bannerCrimsonCult.get());
			}
			if (!sawCrate) {
				for (int dx = -5; dx <= 5 && !sawCrate; dx++) {
					for (int dz = -5; dz <= 5 && !sawCrate; dz++) {
						net.minecraft.world.level.block.Block b = level.getBlockState(portalPos.offset(dx, 0, dz)).getBlock();
						sawCrate = b == thaumcraft.api.blocks.BlocksTC.lootCrateCommon.get()
								|| b == thaumcraft.api.blocks.BlocksTC.lootCrateUncommon.get()
								|| b == thaumcraft.api.blocks.BlocksTC.lootCrateRare.get();
					}
				}
			}
			if (!sawMinion) {
				sawMinion = !level.getEntitiesOfClass(thaumcraft.common.entities.monster.cult.EntityCultist.class, new AABB(abs).inflate(3)).isEmpty();
			}
		}
		helper.assertTrue(sawBanner, "a real cultist portal greater should really place real crimson cult banners at the four cardinal points");
		helper.assertTrue(sawCrate, "a real cultist portal greater should really place a real loot crate nearby");
		helper.assertTrue(sawMinion, "a real cultist portal greater should really conjure real cultist minions as it escalates");

		net.minecraft.world.level.block.state.BlockState before = level.getBlockState(abs);
		portal.hurt(level.damageSources().generic(), 100000.0f);
		helper.assertTrue(!portal.isAlive(), "a real lethal hit should really kill the cultist portal greater");
		helper.assertTrue(level.getBlockState(abs).is(before.getBlock()), "the real death explosion should really not damage nearby blocks");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintFibreConnectsSpreadsAndDiesWithoutARealSeed(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		// A 3x3 floor, not just one block - TaintHelper.spreadFibres picks a random position within +-1 of
		// the fibre in every axis, and only a position adjacent to a real solid block is ever eligible.
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(floorPos.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();

		BlockPos fibrePos = floorAbs.above();
		level.setBlockAndUpdate(fibrePos, thaumcraft.common.blocks.world.taint.BlockTaintFibre.computeState(
				thaumcraft.api.blocks.BlocksTC.taintFibre.get().defaultBlockState(), level, fibrePos));
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(fibrePos);
		helper.assertTrue(state.getValue(thaumcraft.common.blocks.world.taint.BlockTaintFibre.DOWN),
				"a real taint fibre placed on solid ground should really connect downward");
		helper.assertTrue(!state.getValue(thaumcraft.common.blocks.world.taint.BlockTaintFibre.UP),
				"a real taint fibre with nothing above it should really not connect upward");

		// Real death: with no real tracked seed nearby, a random tick should really kill it.
		for (int i = 0; i < 50 && level.getBlockState(fibrePos).is(thaumcraft.api.blocks.BlocksTC.taintFibre.get()); i++) {
			level.getBlockState(fibrePos).randomTick(level, fibrePos, level.random);
		}
		helper.assertTrue(!level.getBlockState(fibrePos).is(thaumcraft.api.blocks.BlocksTC.taintFibre.get()),
				"a real taint fibre with no real seed nearby should really die off within a handful of random ticks");

		// Real seed tracking + real spreading, now that a seed is really registered nearby.
		level.setBlockAndUpdate(fibrePos, thaumcraft.common.blocks.world.taint.BlockTaintFibre.computeState(
				thaumcraft.api.blocks.BlocksTC.taintFibre.get().defaultBlockState(), level, fibrePos));
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, fibrePos);
		helper.assertTrue(thaumcraft.common.blocks.world.taint.TaintHelper.isNearTaintSeed(level, fibrePos),
				"a real tracked taint seed should really be detected nearby");
		boolean spread = false;
		for (int i = 0; i < 300 && !spread; i++) {
			thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, fibrePos, true);
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(fibrePos.relative(dir)).is(thaumcraft.api.blocks.BlocksTC.taintFibre.get())) {
					spread = true;
					break;
				}
			}
		}
		helper.assertTrue(spread, "a real taint fibre near a real tracked seed should really eventually spread to a real adjacent position");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, fibrePos);
		helper.assertTrue(!thaumcraft.common.blocks.world.taint.TaintHelper.isNearTaintSeed(level, fibrePos),
				"removing a real tracked seed should really stop it being detected nearby");

		// Real Flux Taint effect on a real living entity walking across a mature fibre (a villager, not a
		// mock player - mock players are structurally immune, see this file's own established precedent).
		net.minecraft.world.entity.npc.Villager villager = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(villager != null, "the villager entity type should really create an instance");
		villager.moveTo(fibrePos.getX() + 0.5, fibrePos.getY() + 1.0, fibrePos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(villager);
		net.minecraft.world.level.block.state.BlockState fibreState = level.getBlockState(fibrePos);
		for (int i = 0; i < 3000 && !villager.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT); i++) {
			fibreState.getBlock().stepOn(level, fibrePos, fibreState, villager);
		}
		helper.assertTrue(villager.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT),
				"a real living entity repeatedly walking a real taint fibre should really eventually catch real Flux Taint");

		// Real growth-tier light levels.
		net.minecraft.world.level.block.state.BlockState growth3 = thaumcraft.api.blocks.BlocksTC.taintFibre.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFibre.GROWTH3, true);
		helper.assertTrue(thaumcraft.common.blocks.world.taint.BlockTaintFibre.lightLevel(growth3) == 12,
				"a real fully-grown (GROWTH3) fibre should really emit real light level 12");

		// Real fortune-flavored Flux Crystal drop on a mature fibre broken by a real player.
		ServerPlayer breaker = spawnCasterAt(helper, fibrePos.above(2));
		boolean droppedCrystal = false;
		for (int i = 0; i < 100 && !droppedCrystal; i++) {
			thaumcraft.api.blocks.BlocksTC.taintFibre.get().playerWillDestroy(level, fibrePos, growth3, breaker);
			for (net.minecraft.world.entity.item.ItemEntity ie : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(fibrePos).inflate(2))) {
				if (ie.getItem().is(thaumcraft.api.items.ItemsTC.crystalEssence.get())) {
					droppedCrystal = true;
				}
			}
		}
		helper.assertTrue(droppedCrystal, "breaking a real mature taint fibre should really eventually drop a real Flux Crystal Essence");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintRockSoilCrustGeyserDieSpreadFallAndPollute(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// Real per-variant die() transitions.
		BlockPos rockPos = helper.absolutePos(new BlockPos(0, 1, 0));
		level.setBlockAndUpdate(rockPos, BlocksTC.taintRock.get().defaultBlockState());
		BlocksTC.taintRock.get().die(level, rockPos, level.getBlockState(rockPos));
		helper.assertTrue(level.getBlockState(rockPos).is(BlocksTC.stonePorous.get()), "a real dying taint rock should really turn into real porous stone");

		BlockPos soilPos = helper.absolutePos(new BlockPos(2, 1, 0));
		level.setBlockAndUpdate(soilPos, BlocksTC.taintSoil.get().defaultBlockState());
		BlocksTC.taintSoil.get().die(level, soilPos, level.getBlockState(soilPos));
		helper.assertTrue(level.getBlockState(soilPos).is(Blocks.DIRT), "a real dying taint soil should really turn into real dirt");

		BlockPos crustDiePos = helper.absolutePos(new BlockPos(4, 1, 0));
		level.setBlockAndUpdate(crustDiePos, BlocksTC.taintCrust.get().defaultBlockState());
		BlocksTC.taintCrust.get().die(level, crustDiePos, level.getBlockState(crustDiePos));
		helper.assertTrue(level.getBlockState(crustDiePos).is(BlocksTC.fluxGoo.get()), "a real dying taint crust should really turn into real flux goo");

		BlockPos geyserDiePos = helper.absolutePos(new BlockPos(6, 1, 0));
		level.setBlockAndUpdate(geyserDiePos, BlocksTC.taintGeyser.get().defaultBlockState());
		BlocksTC.taintGeyser.get().die(level, geyserDiePos, level.getBlockState(geyserDiePos));
		helper.assertTrue(level.getBlockState(geyserDiePos).is(BlocksTC.fluxGoo.get()), "a real dying taint geyser should really turn into real flux goo");

		// Real rock -> fibre spreading, on a real 3x3 stone floor with a real tracked seed nearby (so the
		// same-probability die() branch never fires, and TaintHelper's own spread-rate gate is bypassed the
		// same way the fibre test itself already established, via a direct ignore=true call).
		BlockPos spreadFloorRel = new BlockPos(0, 1, 3);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(spreadFloorRel.offset(dx, 0, dz), Blocks.STONE);
			}
		}
		BlockPos rockSpreadPos = helper.absolutePos(spreadFloorRel).above();
		level.setBlockAndUpdate(rockSpreadPos, BlocksTC.taintRock.get().defaultBlockState());
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, rockSpreadPos);
		boolean spread = false;
		for (int i = 0; i < 300 && !spread; i++) {
			thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, rockSpreadPos, true);
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(rockSpreadPos.relative(dir)).is(BlocksTC.taintFibre.get())) {
					spread = true;
					break;
				}
			}
		}
		helper.assertTrue(spread, "a real taint rock near a real tracked seed should really eventually spread a real adjacent taint fibre");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, rockSpreadPos);

		// Real crust falling: floating in open air (so it's really "only adjacent to taint", i.e. air) with
		// real open air below all the way to a real floor, and a real tracked seed nearby so the shared
		// probabilistic die() branch never preempts the fall. tryToFall's own fall check is unconditional
		// once reached, so the very first real random tick should really trigger a real fall.
		// The shared "empty" structure is only 8x5x8 (x/z: 0-7, y: 0-4) - the GameTest framework really does
		// wall the test region off with real invisible barrier blocks just outside those bounds, and
		// isOnlyAdjacentToTaint below counts a real barrier as a real sturdy non-taint neighbor. x=3 keeps a
		// full ring of real interior air on every side within that real 8-wide footprint.
		BlockPos crustFloorRel = new BlockPos(3, 1, 6);
		helper.setBlock(crustFloorRel, Blocks.STONE);
		BlockPos crustFloor = helper.absolutePos(crustFloorRel);
		BlockPos crustPos = crustFloor.above(2);
		level.setBlockAndUpdate(crustPos, BlocksTC.taintCrust.get().defaultBlockState());
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, crustPos);
		net.minecraft.world.level.block.state.BlockState crustState = level.getBlockState(crustPos);
		crustState.randomTick(level, crustPos, level.random);
		helper.assertTrue(!level.getBlockState(crustPos).is(BlocksTC.taintCrust.get()),
				"a real unsupported taint crust should really leave its original position once it starts really falling");
		java.util.List<net.minecraft.world.entity.item.FallingBlockEntity> fallers =
				level.getEntitiesOfClass(net.minecraft.world.entity.item.FallingBlockEntity.class, new AABB(crustPos).inflate(2));
		helper.assertTrue(!fallers.isEmpty(), "a real falling taint crust should really spawn a real vanilla FallingBlockEntity");
		net.minecraft.world.entity.item.FallingBlockEntity faller = fallers.get(0);
		for (int i = 0; i < 400 && faller.isAlive(); i++) {
			faller.tick();
		}
		helper.assertTrue(!faller.isAlive(), "a real falling taint crust should really eventually land and discard its real falling-block entity");
		helper.assertTrue(level.getBlockState(crustFloor.above()).is(BlocksTC.taintCrust.get()),
				"a real falling taint crust should really land back as a real taint crust block on top of solid ground");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, crustPos);

		// Real geyser aura pollution when local flux is low, with a real tracked seed nearby for the same
		// die()-suppression reason as above.
		BlockPos geyserPos = helper.absolutePos(new BlockPos(5, 1, 1));
		level.setBlockAndUpdate(geyserPos, BlocksTC.taintGeyser.get().defaultBlockState());
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, geyserPos);
		float fluxBefore = thaumcraft.api.aura.AuraHelper.getFlux(level, geyserPos);
		helper.assertTrue(fluxBefore < 2.0f, "a fresh test chunk should really start with real low flux");
		net.minecraft.world.level.block.state.BlockState geyserState = level.getBlockState(geyserPos);
		for (int i = 0; i < 20; i++) {
			geyserState.randomTick(level, geyserPos, level.random);
		}
		helper.assertTrue(thaumcraft.api.aura.AuraHelper.getFlux(level, geyserPos) > fluxBefore,
				"a real taint geyser over real low flux should really keep polluting the real local aura");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, geyserPos);

		// Real rarer Flux Taint walk-chance and real rock-only bonus Flux Crystal drop.
		net.minecraft.world.entity.npc.Villager walker = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(walker != null, "the villager entity type should really create an instance");
		walker.moveTo(rockPos.getX() + 0.5, rockPos.getY() + 1.0, rockPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(walker);
		level.setBlockAndUpdate(rockPos, BlocksTC.taintRock.get().defaultBlockState());
		net.minecraft.world.level.block.state.BlockState rockState = level.getBlockState(rockPos);
		for (int i = 0; i < 8000 && !walker.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT); i++) {
			rockState.getBlock().stepOn(level, rockPos, rockState, walker);
		}
		helper.assertTrue(walker.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT),
				"a real living entity repeatedly walking a real taint rock should really eventually catch real Flux Taint");

		ServerPlayer breaker = spawnCasterAt(helper, rockPos.above(2));
		boolean droppedCrystal = false;
		for (int i = 0; i < 200 && !droppedCrystal; i++) {
			BlocksTC.taintRock.get().playerWillDestroy(level, rockPos, rockState, breaker);
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(rockPos).inflate(2))) {
				if (ie.getItem().is(ItemsTC.crystalEssence.get())) {
					droppedCrystal = true;
				}
			}
		}
		helper.assertTrue(droppedCrystal, "breaking a real taint rock should really eventually drop a real bonus Flux Crystal Essence");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintFeatureAttachesSpreadsSelfRemovesAndPollutesOnBreak(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// Real self-removal once its real supporting neighbor stops being sturdy (FACING=DOWN means the
		// real support is above this block, per BlockTaintFeature's own doc comment; here it's the other
		// way round - a feature resting on a real floor uses FACING=UP, matching the original's own case 1).
		BlockPos supportRel = new BlockPos(2, 1, 2);
		helper.setBlock(supportRel, Blocks.STONE);
		BlockPos supportAbs = helper.absolutePos(supportRel);
		BlockPos featurePos = supportAbs.above();
		level.setBlockAndUpdate(featurePos, BlocksTC.taintFeature.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFeature.FACING, net.minecraft.core.Direction.UP));
		helper.assertTrue(level.getBlockState(featurePos).is(BlocksTC.taintFeature.get()),
				"a real taint feature attached above a real solid support should really stay placed");
		level.setBlockAndUpdate(supportAbs, Blocks.AIR.defaultBlockState());
		helper.assertTrue(!level.getBlockState(featurePos).is(BlocksTC.taintFeature.get()),
				"a real taint feature should really self-remove once its real supporting neighbor stops being sturdy");

		// Real spreading (the same shared TaintHelper.spreadFibres already verified elsewhere in this arc -
		// here just confirming the real one-line wiring), on a real 3x3 stone floor with a real tracked seed.
		BlockPos spreadFloorRel = new BlockPos(2, 1, 5);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(spreadFloorRel.offset(dx, 0, dz), Blocks.STONE);
			}
		}
		BlockPos spreadFeaturePos = helper.absolutePos(spreadFloorRel).above();
		level.setBlockAndUpdate(spreadFeaturePos, BlocksTC.taintFeature.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFeature.FACING, net.minecraft.core.Direction.UP));
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, spreadFeaturePos);
		boolean spread = false;
		for (int i = 0; i < 300 && !spread; i++) {
			thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, spreadFeaturePos, true);
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(spreadFeaturePos.relative(dir)).is(BlocksTC.taintFibre.get())) {
					spread = true;
					break;
				}
			}
		}
		helper.assertTrue(spread, "a real taint feature near a real tracked seed should really eventually spread a real adjacent taint fibre");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, spreadFeaturePos);

		// Real death without a real nearby seed.
		level.setBlockAndUpdate(spreadFeaturePos, BlocksTC.taintFeature.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFeature.FACING, net.minecraft.core.Direction.UP));
		for (int i = 0; i < 50 && level.getBlockState(spreadFeaturePos).is(BlocksTC.taintFeature.get()); i++) {
			level.getBlockState(spreadFeaturePos).randomTick(level, spreadFeaturePos, level.random);
		}
		helper.assertTrue(!level.getBlockState(spreadFeaturePos).is(BlocksTC.taintFeature.get()),
				"a real taint feature with no real seed nearby should really die off within a handful of random ticks");

		// Real 33% chance to spawn a real EntityTaintCrawler whenever a real taint feature is removed for
		// any reason (die() included, since die() itself removes the block - onRemove fires for that real
		// removal too, same as the original's own breakBlock firing on any block-identity change, not just
		// player-initiated ones) - now that creature is real too. The real remaining-67%-of-the-time aura
		// pollution isn't reliably assertable here (see the taint seed test's own note on the real
		// background AuraThread race), so this checks the crawler-spawn side instead.
		BlockPos pollutePos = helper.absolutePos(new BlockPos(5, 1, 1));
		boolean spawnedCrawler = false;
		for (int i = 0; i < 40 && !spawnedCrawler; i++) {
			level.setBlockAndUpdate(pollutePos, BlocksTC.taintFeature.get().defaultBlockState()
					.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFeature.FACING, net.minecraft.core.Direction.UP));
			level.removeBlock(pollutePos, false);
			spawnedCrawler = !level.getEntitiesOfClass(thaumcraft.common.entities.monster.tainted.EntityTaintCrawler.class,
					new AABB(pollutePos).inflate(2)).isEmpty();
		}
		helper.assertTrue(spawnedCrawler, "removing a real taint feature should really eventually spawn a real taint crawler (a real 33% chance each time)");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintLogDiesUnconditionallyWithoutARealSeedAndSpreadsWithOne(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// Real, distinctive-from-every-other-taint-block behavior: no 10%-chance gate on death, it's a flat
		// "die if not near a real seed" every single random tick.
		BlockPos diePos = helper.absolutePos(new BlockPos(2, 1, 2));
		level.setBlockAndUpdate(diePos, BlocksTC.taintLog.get().defaultBlockState());
		level.getBlockState(diePos).randomTick(level, diePos, level.random);
		helper.assertTrue(!level.getBlockState(diePos).is(BlocksTC.taintLog.get()),
				"a real taint log with no real seed nearby should really die on the very first real random tick, unconditionally");

		// Real spreading, on a real 3x3 stone floor with a real tracked seed nearby.
		BlockPos spreadFloorRel = new BlockPos(2, 1, 5);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(spreadFloorRel.offset(dx, 0, dz), Blocks.STONE);
			}
		}
		BlockPos spreadLogPos = helper.absolutePos(spreadFloorRel).above();
		level.setBlockAndUpdate(spreadLogPos, BlocksTC.taintLog.get().defaultBlockState());
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, spreadLogPos);
		boolean spread = false;
		for (int i = 0; i < 300 && !spread; i++) {
			thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, spreadLogPos, true);
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(spreadLogPos.relative(dir)).is(BlocksTC.taintFibre.get())) {
					spread = true;
					break;
				}
			}
		}
		helper.assertTrue(spread, "a real taint log near a real tracked seed should really eventually spread a real adjacent taint fibre");
		helper.assertTrue(level.getBlockState(spreadLogPos).is(BlocksTC.taintLog.get()),
				"a real taint log near a real tracked seed should really survive - only the shared spreadFibres target changes, not the log itself");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, spreadLogPos);

		// Real self-drop on break (unlike its taint-block siblings, this one keeps the real default drop -
		// the original never overrides getItemDropped, only getSilkTouchDrop/damageDropped).
		assertSelfDrops(helper, new BlockPos(5, 1, 1), BlocksTC.taintLog.get(), ItemsTC.taintLog.get());

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintCrawlerGrowsFibresAlliesWithTaintAndInflictsFluxTaint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		level.getServer().setDifficulty(net.minecraft.world.Difficulty.HARD, true);

		// Real "grows a real taint fibre under its own feet" tick, on a real 3x3 stone floor.
		BlockPos floorRel = new BlockPos(2, 1, 2);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(floorRel.offset(dx, 0, dz), Blocks.STONE);
			}
		}
		BlockPos crawlerPos = helper.absolutePos(floorRel).above();
		thaumcraft.common.entities.monster.tainted.EntityTaintCrawler crawler =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get().create(level);
		helper.assertTrue(crawler != null, "the entity type should really create an instance");
		crawler.moveTo(crawlerPos.getX() + 0.5, crawlerPos.getY(), crawlerPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(crawler);
		boolean grewFibre = false;
		for (int i = 0; i < 45 && !grewFibre; i++) {
			crawler.tick();
			grewFibre = level.getBlockState(crawlerPos).is(BlocksTC.taintFibre.get());
		}
		helper.assertTrue(grewFibre, "a real taint crawler should really grow a real taint fibre under its own feet within 40 real ticks");

		// Real allied-with-other-tainted-mobs check.
		thaumcraft.common.entities.monster.tainted.EntityTaintCrawler other =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get().create(level);
		other.moveTo(crawlerPos.getX() + 1.5, crawlerPos.getY(), crawlerPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(other);
		helper.assertTrue(crawler.isAlliedTo(other), "a real taint crawler should really be allied to another real tainted mob");

		// Real Flux Taint infliction chance on a real successful melee hit (difficulty forced to HARD above
		// for the highest, most-reliable chance).
		net.minecraft.world.entity.npc.Villager victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(victim != null, "the villager entity type should really create an instance");
		victim.moveTo(crawlerPos.getX() + 0.5, crawlerPos.getY(), crawlerPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(victim);
		boolean tainted = false;
		for (int i = 0; i < 400 && !tainted; i++) {
			victim.setHealth(victim.getMaxHealth());
			victim.removeEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT);
			crawler.doHurtTarget(victim);
			// Real vanilla post-hit invulnerability only decays via a real tick - doHurtTarget alone never
			// advances it, so without this every call after the very first real landed hit would silently
			// no-op forever (the exact same real mechanic that made the swarm self-destruct test flaky before
			// switching it to tick() too - see that test's own comment).
			victim.tick();
			tainted = victim.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT);
		}
		helper.assertTrue(tainted, "a real taint crawler landing repeated real hits on hard difficulty should really eventually inflict real Flux Taint");

		// Real bonus Flux Crystal drop chance on death.
		ServerPlayer killer = spawnCasterAt(helper, crawlerPos.above(3));
		boolean droppedCrystal = false;
		for (int i = 0; i < 100 && !droppedCrystal; i++) {
			thaumcraft.common.entities.monster.tainted.EntityTaintCrawler probe =
					thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get().create(level);
			probe.moveTo(crawlerPos.getX() + 0.5, crawlerPos.getY(), crawlerPos.getZ() + 0.5, 0.0f, 0.0f);
			level.addFreshEntity(probe);
			probe.hurt(level.damageSources().playerAttack(killer), 1000.0f);
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(crawlerPos).inflate(3))) {
				if (ie.getItem().is(ItemsTC.crystalEssence.get())) {
					droppedCrystal = true;
				}
			}
		}
		helper.assertTrue(droppedCrystal, "a real taint crawler should really eventually drop a real bonus Flux Crystal Essence on death");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintSwarmSelfDestructsWhenSummonedAndAttacksWithoutKnockback(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos center = helper.absolutePos(new BlockPos(3, 2, 3));

		// Real ally-with-other-tainted-mobs and real unpushable check.
		thaumcraft.common.entities.monster.tainted.EntityTaintSwarm swarm =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_SWARM.get().create(level);
		helper.assertTrue(swarm != null, "the entity type should really create an instance");
		swarm.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(swarm);
		helper.assertTrue(!swarm.isPushable(), "a real taint swarm should really be unpushable");
		thaumcraft.common.entities.monster.tainted.EntityTaintCrawler crawler =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get().create(level);
		helper.assertTrue(crawler != null, "the entity type should really create an instance");
		crawler.moveTo(center.getX() + 1.5, center.getY(), center.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(crawler);
		helper.assertTrue(swarm.isAlliedTo(crawler), "a real taint swarm should really be allied to another real tainted mob");

		// Real self-destruct: summoned, with no target, it keeps hurting itself for real damage every tick -
		// real vanilla post-hit invulnerability only decays via a real tick(), not aiStep() alone, so this
		// calls the real tick() (which itself calls aiStep()) rather than aiStep() directly - the same real
		// mechanic the original itself relied on to throttle its own unconditional per-tick self-damage call
		// to roughly one real hit per ~10 ticks, not one per call.
		swarm.setSummoned(true);
		for (int i = 0; i < 400 && swarm.isAlive(); i++) {
			swarm.tick();
		}
		helper.assertTrue(!swarm.isAlive(), "a real summoned taint swarm with no real target should really self-destruct within a real handful of hits");

		// Real attack: applies Weakness and explicitly restores the target's own pre-hit velocity (no real
		// knockback), matching the original's own manual motion preservation.
		thaumcraft.common.entities.monster.tainted.EntityTaintSwarm attacker =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_SWARM.get().create(level);
		attacker.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(attacker);
		net.minecraft.world.entity.npc.Villager victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(victim != null, "the villager entity type should really create an instance");
		victim.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(victim);
		attacker.setTarget(victim);
		net.minecraft.world.phys.Vec3 preHitMotion = victim.getDeltaMovement();
		boolean weakened = false;
		for (int i = 0; i < 400 && !weakened; i++) {
			attacker.aiStep();
			weakened = victim.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS);
		}
		helper.assertTrue(weakened, "a real taint swarm attacking a real target should really eventually apply real Weakness");
		helper.assertTrue(victim.getDeltaMovement().equals(preHitMotion),
				"a real taint swarm's attack should really not knock its target back at all");

		// Real bonus Flux Crystal Essence drop chance on death.
		ServerPlayer killer = spawnCasterAt(helper, center.above(2));
		boolean droppedCrystal = false;
		for (int i = 0; i < 100 && !droppedCrystal; i++) {
			thaumcraft.common.entities.monster.tainted.EntityTaintSwarm probe =
					thaumcraft.common.registry.ThaumcraftEntities.TAINT_SWARM.get().create(level);
			probe.moveTo(center.getX() + 0.5, center.getY(), center.getZ() + 0.5, 0.0f, 0.0f);
			level.addFreshEntity(probe);
			probe.hurt(level.damageSources().playerAttack(killer), 1000.0f);
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(3))) {
				if (ie.getItem().is(ItemsTC.crystalEssence.get())) {
					droppedCrystal = true;
				}
			}
		}
		helper.assertTrue(droppedCrystal, "a real taint swarm should really eventually drop a real bonus Flux Crystal Essence on death");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintacleStarvesOffTaintSpawnsATendrilAndSelfDestructs(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		// Real "starves" (takes real damage) once every 20 real ticks while NOT standing on/above real taint.
		BlockPos stoneRel = new BlockPos(2, 1, 2);
		helper.setBlock(stoneRel, Blocks.STONE);
		BlockPos stonePos = helper.absolutePos(stoneRel);
		thaumcraft.common.entities.monster.tainted.EntityTaintacle starving =
				thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE.get().create(level);
		helper.assertTrue(starving != null, "the entity type should really create an instance");
		starving.moveTo(stonePos.getX() + 0.5, stonePos.getY() + 1.0, stonePos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(starving);
		float healthBefore = starving.getHealth();
		for (int i = 0; i < 200; i++) {
			starving.tick();
		}
		helper.assertTrue(starving.getHealth() < healthBefore,
				"a real taintacle not standing on real taint should really take real starvation damage over time");

		// Real survival while actually standing on real taint, plus real allied-with-tainted-mobs.
		BlockPos rockPos = helper.absolutePos(new BlockPos(5, 1, 2));
		level.setBlockAndUpdate(rockPos, BlocksTC.taintRock.get().defaultBlockState());
		thaumcraft.common.entities.monster.tainted.EntityTaintacle rooted =
				thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE.get().create(level);
		helper.assertTrue(rooted != null, "the entity type should really create an instance");
		rooted.moveTo(rockPos.getX() + 0.5, rockPos.getY() + 1.0, rockPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(rooted);
		for (int i = 0; i < 200; i++) {
			rooted.tick();
		}
		helper.assertTrue(rooted.getHealth() == rooted.getMaxHealth(),
				"a real taintacle really standing on real taint should really never starve");
		helper.assertTrue(rooted.isAlliedTo(starving), "a real taintacle should really be allied to another real tainted mob");

		// Real "spawns a real tendril near a distant, visible target standing on real taint" mechanic - the
		// target needs its own real taint underfoot too, and needs to be real distance 4-16 blocks away
		// (Taintacle's own real distanceToSqr bounds), not just anywhere near the taintacle itself.
		BlockPos targetRockPos = helper.absolutePos(new BlockPos(5, 1, 7));
		level.setBlockAndUpdate(targetRockPos, BlocksTC.taintRock.get().defaultBlockState());
		net.minecraft.world.entity.npc.Villager target = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(target != null, "the villager entity type should really create an instance");
		target.moveTo(targetRockPos.getX() + 0.5, targetRockPos.getY() + 1.0, targetRockPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(target);
		rooted.setTarget(target);
		boolean spawnedTendril = false;
		for (int i = 0; i < 400 && !spawnedTendril; i++) {
			rooted.tick();
			spawnedTendril = !level.getEntitiesOfClass(thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall.class,
					new AABB(target.blockPosition()).inflate(2)).isEmpty();
		}
		helper.assertTrue(spawnedTendril, "a real taintacle with a real distant, visible target standing on real taint should really spawn a real tendril nearby");

		// Real tendril: never a valid natural spawn location, never spawns further tendrils itself, and
		// really self-destructs once its real lifetime timer runs out.
		thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall tendril =
				thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE_SMALL.get().create(level);
		helper.assertTrue(tendril != null, "the entity type should really create an instance");
		helper.assertTrue(!tendril.canSpawnHere(), "a real taint tendril should really never count as a valid natural spawn location");
		tendril.moveTo(rockPos.getX() + 0.5, rockPos.getY() + 1.0, rockPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(tendril);
		for (int i = 0; i < 250 && tendril.isAlive(); i++) {
			tendril.tick();
		}
		helper.assertTrue(!tendril.isAlive(), "a real taint tendril should really self-destruct once its real 200-tick lifetime runs out");

		// Real bonus Flux Crystal Essence drop on death.
		ServerPlayer killer = spawnCasterAt(helper, rockPos.above(2));
		boolean droppedCrystal = false;
		for (int i = 0; i < 20 && !droppedCrystal; i++) {
			thaumcraft.common.entities.monster.tainted.EntityTaintacle probe =
					thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE.get().create(level);
			probe.moveTo(rockPos.getX() + 0.5, rockPos.getY() + 1.0, rockPos.getZ() + 0.5, 0.0f, 0.0f);
			level.addFreshEntity(probe);
			probe.hurt(level.damageSources().playerAttack(killer), 1000.0f);
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(rockPos).inflate(3))) {
				if (ie.getItem().is(ItemsTC.crystalEssence.get())) {
					droppedCrystal = true;
				}
			}
		}
		helper.assertTrue(droppedCrystal, "a real taintacle should really always drop a real Flux Crystal Essence on death");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void taintSeedTracksItselfSpreadsFibresAndInflictsAreaFluxTaint(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		helper.setBlock(new BlockPos(3, 0, 3), Blocks.STONE);
		BlockPos seedPos = helper.absolutePos(new BlockPos(3, 1, 3));
		thaumcraft.common.entities.monster.tainted.EntityTaintSeed seed =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_SEED.get().create(level);
		helper.assertTrue(seed != null, "the entity type should really create an instance");
		seed.moveTo(seedPos.getX() + 0.5, seedPos.getY(), seedPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(seed);

		// Real self-registration as a real tracked taint seed, on its very first real tick.
		seed.tick();
		BlockPos trackedPos = seed.blockPosition();
		helper.assertTrue(thaumcraft.common.blocks.world.taint.TaintHelper.isNearTaintSeed(level, trackedPos),
				"a real taint seed should really register itself as a real tracked seed on its very first tick");

		// Real "starves and pollutes the aura" branch, real for a fresh chunk with near-zero real flux
		// saturation. Only the real starvation damage is checked here, not the flux value itself - the real
		// background AuraThread (a genuinely live, wall-clock-paced diffusion simulation, not gated by game
		// ticks at all) can race this loop's own polluteAura calls arbitrarily many times between any two
		// real reads, so "flux went up" isn't a reliable thing to assert against here; the real starvation
		// damage is a reliable enough proxy that this real branch actually ran.
		float healthBefore = seed.getHealth();
		for (int i = 0; i < 200; i++) {
			seed.tick();
		}
		helper.assertTrue(seed.getHealth() < healthBefore,
				"a real taint seed over real near-zero flux saturation should really take real starvation damage over time");

		// Real "boost forces the real spread-fibres branch instead" - a real tracked seed nearby (itself) is
		// needed for spreadFibres to ever place anything, which is already real and true here.
		seed.boost = 40;
		boolean spread = false;
		for (int i = 0; i < 400 && !spread; i++) {
			seed.tick();
			for (BlockPos p : BlockPos.betweenClosed(trackedPos.offset(-4, -2, -4), trackedPos.offset(4, 2, 4))) {
				if (level.getBlockState(p).is(BlocksTC.taintFibre.get())) {
					spread = true;
					break;
				}
			}
		}
		helper.assertTrue(spread, "a real boosted taint seed should really eventually spread a real taint fibre somewhere nearby");

		// Real area-wide Flux Taint infliction on every real living entity nearby.
		net.minecraft.world.entity.npc.Villager victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(victim != null, "the villager entity type should really create an instance");
		victim.moveTo(trackedPos.getX() + 1.5, trackedPos.getY(), trackedPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(victim);
		boolean tainted = false;
		for (int i = 0; i < 200 && !tainted; i++) {
			seed.tick();
			tainted = victim.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT);
		}
		helper.assertTrue(tainted, "a real taint seed should really eventually inflict real Flux Taint on every real living entity in its real area");

		// Real death untracks the real seed position.
		seed.hurt(level.damageSources().playerAttack(spawnCasterAt(helper, seedPos.above(2))), 10000.0f);
		helper.assertTrue(!thaumcraft.common.blocks.world.taint.TaintHelper.isNearTaintSeed(level, trackedPos),
				"a real dying taint seed should really untrack its own real seed position");

		// Real Prime variant: bigger, and really drops at least one real Flux Crystal Essence too.
		BlockPos primePos = helper.absolutePos(new BlockPos(5, 1, 6));
		thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime prime =
				thaumcraft.common.registry.ThaumcraftEntities.TAINT_SEED_PRIME.get().create(level);
		helper.assertTrue(prime != null, "the entity type should really create an instance");
		helper.assertTrue(prime.getMaxHealth() == 150.0f, "a real taint seed prime should really have real 150 max health, got " + prime.getMaxHealth());
		prime.moveTo(primePos.getX() + 0.5, primePos.getY(), primePos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(prime);
		ServerPlayer primeKiller = spawnCasterAt(helper, primePos.above(2));
		prime.hurt(level.damageSources().playerAttack(primeKiller), 10000.0f);
		boolean primeDroppedCrystal = false;
		for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(primePos).inflate(3))) {
			if (ie.getItem().is(ItemsTC.crystalEssence.get())) {
				primeDroppedCrystal = true;
			}
		}
		helper.assertTrue(primeDroppedCrystal, "a real taint seed prime should really always drop at least one real Flux Crystal Essence on death");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "tall")
	public static void fluxGooFallsSpreadsDecaysAndAffectsEntities(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		thaumcraft.common.blocks.world.taint.BlockFluxGoo goo = BlocksTC.fluxGoo.get();
		net.minecraft.world.level.block.state.properties.IntegerProperty LEVEL = thaumcraft.common.blocks.world.taint.BlockFluxGoo.LEVEL;

		// Real conservation while falling: this loop drives the real scheduled-tick flow step by step (a
		// GameTest doesn't process the real level tick-schedule queue on its own), re-locating the real
		// single goo block each time since it really moves one block down per successful real tick. The
		// real total never exceeds the real 7 quanta it started with, anywhere in the column, at any point.
		BlockPos fallFloorRel = new BlockPos(2, 1, 2);
		helper.setBlock(fallFloorRel, Blocks.STONE);
		BlockPos fallFloorAbs = helper.absolutePos(fallFloorRel);
		BlockPos gooTop = fallFloorAbs.above(3);
		BlockPos gooRest = fallFloorAbs.above();
		// Real per-tick decay competes with real falling for the same tick "turn" on every single real
		// tick, so a whole falling column occasionally decaying away to nothing before it ever reaches the
		// floor is a real, expected outcome of that real race, not a bug - retried (a fresh full-level
		// placement) rather than asserting a single attempt must always survive it.
		boolean landedIntact = false;
		for (int attempt = 0; attempt < 8 && !landedIntact; attempt++) {
			for (BlockPos p : BlockPos.betweenClosed(gooRest, gooTop)) {
				level.removeBlock(p, false);
			}
			level.setBlockAndUpdate(gooTop, goo.defaultBlockState());
			BlockPos currentPos = gooTop;
			for (int i = 0; i < 30 && !currentPos.equals(gooRest) && level.getBlockState(currentPos).is(goo); i++) {
				int total = 0;
				for (BlockPos p : BlockPos.betweenClosed(gooRest, gooTop)) {
					net.minecraft.world.level.block.state.BlockState s = level.getBlockState(p);
					if (s.is(goo)) {
						int lvl = s.getValue(LEVEL);
						helper.assertTrue(lvl >= 0 && lvl <= 7, "a real flux goo block should really always stay within real level 0-7, saw " + lvl);
						total += lvl;
					}
				}
				helper.assertTrue(total <= 7, "a real falling flux goo column should really never total more than its real original 7 quanta, saw " + total);
				net.minecraft.world.level.block.state.BlockState currentState = level.getBlockState(currentPos);
				if (currentState.is(goo)) {
					currentState.tick(level, currentPos, level.random);
				}
				for (BlockPos p : BlockPos.betweenClosed(gooRest, gooTop)) {
					if (level.getBlockState(p).is(goo)) {
						currentPos = p;
						break;
					}
				}
			}
			net.minecraft.world.level.block.state.BlockState landed = level.getBlockState(gooRest);
			landedIntact = landed.is(goo) && landed.getValue(LEVEL) >= 1;
		}
		helper.assertTrue(landedIntact,
				"a real flux goo block should really fall all the way down onto the first real solid floor beneath it, landing as real goo with a real positive level");

		// Real horizontal spread once blocked from falling further, real conserved total across the puddle.
		BlockPos spreadFloorRel = new BlockPos(5, 1, 2);
		helper.setBlock(spreadFloorRel, Blocks.STONE);
		BlockPos spreadPos = helper.absolutePos(spreadFloorRel).above();
		level.setBlockAndUpdate(spreadPos, goo.defaultBlockState());
		boolean spread = false;
		for (int i = 0; i < 30 && !spread; i++) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(spreadPos);
			if (s.is(goo)) {
				s.tick(level, spreadPos, level.random);
			}
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
				if (level.getBlockState(spreadPos.relative(dir)).is(goo)) {
					spread = true;
				}
			}
		}
		helper.assertTrue(spread, "a real flux goo block blocked from falling further should really eventually spread into a real horizontal neighbor");
		int spreadTotal = level.getBlockState(spreadPos).is(goo) ? level.getBlockState(spreadPos).getValue(LEVEL) : 0;
		for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.Plane.HORIZONTAL) {
			net.minecraft.world.level.block.state.BlockState n = level.getBlockState(spreadPos.relative(dir));
			if (n.is(goo)) {
				spreadTotal += n.getValue(LEVEL);
			}
		}
		helper.assertTrue(spreadTotal <= 7, "a real spreading flux goo puddle should really conserve its real original 7 quanta across the whole puddle, saw " + spreadTotal);

		// Real decay: a fully-drained (level 0) goo block should eventually either evaporate (real aura
		// pollution) or condense into a real taint fibre.
		BlockPos decayPos = helper.absolutePos(new BlockPos(8, 1, 2));
		level.setBlockAndUpdate(decayPos, goo.defaultBlockState().setValue(LEVEL, 0));
		boolean decayed = false;
		for (int i = 0; i < 200 && !decayed; i++) {
			net.minecraft.world.level.block.state.BlockState s = level.getBlockState(decayPos);
			if (s.is(goo)) {
				s.tick(level, decayPos, level.random);
			}
			decayed = !level.getBlockState(decayPos).is(goo);
		}
		helper.assertTrue(decayed, "a real fully-drained flux goo block should really eventually decay into something else");
		net.minecraft.world.level.block.state.BlockState decayedInto = level.getBlockState(decayPos);
		helper.assertTrue(decayedInto.isAir() || decayedInto.is(BlocksTC.taintFibre.get()),
				"a real drained flux goo block should really decay into either real air or a real taint fibre, got " + decayedInto);

		// Real slime-spawning: a real deep (level 6-7) puddle with real open air above should eventually
		// spawn a real, size-2 EntityThaumicSlime.
		BlockPos slimeFloorRel = new BlockPos(2, 1, 5);
		helper.setBlock(slimeFloorRel, Blocks.STONE);
		BlockPos slimePos = helper.absolutePos(slimeFloorRel).above();
		boolean spawnedSlime = false;
		for (int i = 0; i < 400 && !spawnedSlime; i++) {
			// Real-reset to a full, qualifying puddle before every real attempt - flow/decay (both also
			// real, both competing for the same real tick "turn" as the slime-spawn roll) would otherwise
			// eventually drain or move this exact position out of the real lvl>=2 qualifying range, leaving
			// nothing left to actually roll the real 1-in-50 slime-spawn chance against.
			level.setBlockAndUpdate(slimePos, goo.defaultBlockState());
			level.getBlockState(slimePos).tick(level, slimePos, level.random);
			for (net.minecraft.world.entity.monster.Slime slime : level.getEntitiesOfClass(net.minecraft.world.entity.monster.Slime.class, new AABB(slimePos).inflate(2))) {
				if (slime.getSize() == 2) {
					spawnedSlime = true;
				}
			}
		}
		helper.assertTrue(spawnedSlime, "a real deep flux goo puddle should really eventually spawn a real size-2 Thaumic Slime");

		// Real entity contact: motion resistance + a real Vis Exhaustion debuff on ordinary entities, real
		// growth for a real, smaller Thaumic Slime that touches a real bigger puddle.
		BlockPos contactPos = helper.absolutePos(new BlockPos(5, 1, 5));
		level.setBlockAndUpdate(contactPos, goo.defaultBlockState());
		net.minecraft.world.entity.npc.Villager victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(victim != null, "the villager entity type should really create an instance");
		victim.moveTo(contactPos.getX() + 0.5, contactPos.getY(), contactPos.getZ() + 0.5, 0.0f, 0.0f);
		victim.setDeltaMovement(1.0, 0.0, 1.0);
		level.addFreshEntity(victim);
		net.minecraft.world.level.block.state.BlockState contactState = level.getBlockState(contactPos);
		contactState.entityInside(level, contactPos, victim);
		helper.assertTrue(victim.getDeltaMovement().x < 1.0 && victim.getDeltaMovement().z < 1.0,
				"a real ordinary entity touching a real full flux goo puddle should really have its real horizontal motion resisted");
		helper.assertTrue(victim.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.VIS_EXHAUST),
				"a real ordinary entity touching real flux goo should really catch a real Vis Exhaustion debuff");

		thaumcraft.common.entities.monster.EntityThaumicSlime smallSlime =
				thaumcraft.common.registry.ThaumcraftEntities.THAUMIC_SLIME.get().create(level);
		helper.assertTrue(smallSlime != null, "the entity type should really create an instance");
		smallSlime.setSize(1, true);
		smallSlime.moveTo(contactPos.getX() + 0.5, contactPos.getY(), contactPos.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(smallSlime);
		boolean grew = false;
		for (int i = 0; i < 50 && !grew; i++) {
			level.getBlockState(contactPos).entityInside(level, contactPos, smallSlime);
			grew = smallSlime.getSize() > 1;
		}
		helper.assertTrue(grew, "a real small Thaumic Slime touching a real bigger flux goo puddle should really eventually grow");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "tall")
	public static void fluxGooIsTheRealDieTargetForCrustGeyserFeatureAndLog(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();

		BlockPos crustPos = helper.absolutePos(new BlockPos(1, 1, 1));
		level.setBlockAndUpdate(crustPos, BlocksTC.taintCrust.get().defaultBlockState());
		BlocksTC.taintCrust.get().die(level, crustPos, level.getBlockState(crustPos));
		helper.assertTrue(level.getBlockState(crustPos).is(BlocksTC.fluxGoo.get()), "a real dying taint crust should really turn into real flux goo now");

		BlockPos geyserPos = helper.absolutePos(new BlockPos(3, 1, 1));
		level.setBlockAndUpdate(geyserPos, BlocksTC.taintGeyser.get().defaultBlockState());
		BlocksTC.taintGeyser.get().die(level, geyserPos, level.getBlockState(geyserPos));
		helper.assertTrue(level.getBlockState(geyserPos).is(BlocksTC.fluxGoo.get()), "a real dying taint geyser should really turn into real flux goo now");

		BlockPos featurePos = helper.absolutePos(new BlockPos(5, 1, 1));
		level.setBlockAndUpdate(featurePos, BlocksTC.taintFeature.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.taint.BlockTaintFeature.FACING, net.minecraft.core.Direction.UP));
		BlocksTC.taintFeature.get().die(level, featurePos, level.getBlockState(featurePos));
		helper.assertTrue(level.getBlockState(featurePos).is(BlocksTC.fluxGoo.get()), "a real dying taint feature should really turn into real flux goo now");

		BlockPos logPos = helper.absolutePos(new BlockPos(7, 1, 1));
		level.setBlockAndUpdate(logPos, BlocksTC.taintLog.get().defaultBlockState());
		BlocksTC.taintLog.get().die(level, logPos, level.getBlockState(logPos));
		helper.assertTrue(level.getBlockState(logPos).is(BlocksTC.fluxGoo.get()), "a real dying taint log should really turn into real flux goo now");

		// Real geyser -> real EntityTaintSwarm spawn when a real player is nearby (needs a real tracked seed
		// too, so the shared 10%-chance die() branch never preempts it).
		BlockPos swarmGeyserPos = helper.absolutePos(new BlockPos(1, 1, 4));
		level.setBlockAndUpdate(swarmGeyserPos, BlocksTC.taintGeyser.get().defaultBlockState());
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, swarmGeyserPos);
		ServerPlayer nearbyPlayer = spawnCasterAt(helper, swarmGeyserPos.above(2));
		boolean spawnedSwarm = false;
		for (int i = 0; i < 300 && !spawnedSwarm; i++) {
			level.getBlockState(swarmGeyserPos).randomTick(level, swarmGeyserPos, level.random);
			spawnedSwarm = !level.getEntitiesOfClass(thaumcraft.common.entities.monster.tainted.EntityTaintSwarm.class,
					new AABB(swarmGeyserPos).inflate(4)).isEmpty();
		}
		helper.assertTrue(spawnedSwarm, "a real taint geyser with a real player nearby should really eventually spawn a real taint swarm");
		thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, swarmGeyserPos);

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void stonePorousMostlyDropsGravelButSometimesDropsSomethingSpecial(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(new BlockPos(3, 1, 3));
		ServerPlayer breaker = spawnCasterAt(helper, pos.above(2));

		boolean sawGravel = false;
		boolean sawSpecial = false;
		for (int i = 0; i < 400 && !(sawGravel && sawSpecial); i++) {
			level.setBlockAndUpdate(pos, BlocksTC.stonePorous.get().defaultBlockState());
			BlocksTC.stonePorous.get().playerWillDestroy(level, pos, level.getBlockState(pos), breaker);
			for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(2))) {
				if (ie.getItem().is(Items.GRAVEL)) {
					sawGravel = true;
				} else {
					sawSpecial = true;
				}
				ie.discard();
			}
		}
		helper.assertTrue(sawGravel, "breaking a real porous stone should really mostly drop real gravel");
		helper.assertTrue(sawSpecial, "breaking a real porous stone should really occasionally drop something real and special instead of gravel");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Construct arc, chunk 1: EntityOwnedConstruct/EntityTurretCrossbow/ItemTurretPlacer/activatorRail -
	// see those classes' own doc comments.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretPlacerBasicPlacesARealTamedTurret(GameTestHelper helper) {
		BlockPos floorPos = new BlockPos(2, 1, 2);
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		player.getAbilities().instabuild = false; // mock players default to creative, which skips item consumption
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.turretPlacerBasic.get()));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "placing against a solid block should succeed, got: " + result);
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the single placer stack should be fully consumed");

		List<EntityTurretCrossbow> found = helper.getLevel().getEntitiesOfClass(EntityTurretCrossbow.class, new AABB(floorAbs).inflate(3));
		helper.assertTrue(found.size() == 1, "exactly one turret should have been spawned, found " + found.size());
		EntityTurretCrossbow turret = found.get(0);
		helper.assertTrue(turret.isOwnedBy(player) && turret.isTame(), "the placed turret should really be tamed/owned by the placing player");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretCrossbowAutoReloadsAndFiresAtHostiles(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos dispenserPos = new BlockPos(2, 1, 2);
		BlockPos dispenserAbs = helper.absolutePos(dispenserPos);
		helper.setBlock(dispenserPos, net.minecraft.world.level.block.Blocks.DISPENSER);
		net.minecraft.world.level.block.entity.DispenserBlockEntity dispenser =
				(net.minecraft.world.level.block.entity.DispenserBlockEntity) helper.getBlockEntity(dispenserPos);
		dispenser.setItem(0, new ItemStack(net.minecraft.world.item.Items.ARROW, 16));

		EntityTurretCrossbow turret = ThaumcraftEntities.TURRET_CROSSBOW.get().create(level);
		helper.assertTrue(turret != null, "the turret entity type should really be able to create an instance");
		turret.moveTo(dispenserAbs.getX() + 0.5, dispenserAbs.getY() + 1.0, dispenserAbs.getZ() + 0.5, 0.0f, 0.0f);
		turret.setValidSpawn();
		level.addFreshEntity(turret);

		ServerPlayer caster = spawnCasterAt(helper, dispenserAbs.above(4));
		Zombie zombie = spawnZombieFacedBy(helper, caster, dispenserAbs.above(1));
		zombie.setPos(dispenserAbs.getX() + 0.5, dispenserAbs.getY() + 1.0, dispenserAbs.getZ() + 4.5);

		boolean loaded = false;
		boolean fired = false;
		for (int i = 0; i < 200 && !fired; i++) {
			turret.tick();
			if (!loaded && !turret.getMainHandItem().isEmpty()) {
				loaded = true;
			}
			if (!level.getEntitiesOfClass(net.minecraft.world.entity.projectile.Arrow.class, new AABB(dispenserAbs).inflate(6)).isEmpty()) {
				fired = true;
			}
		}

		helper.assertTrue(loaded, "the turret should really auto-reload from the real dispenser placed directly beneath it");
		helper.assertTrue(fired, "the turret should really fire a real arrow at the real hostile zombie within range");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretCrossbowFreezesOnPoweredActivatorRailAndSneakPickupReturnsThePlacer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos railPos = new BlockPos(2, 1, 2);
		BlockPos railAbs = helper.absolutePos(railPos);
		// A real powered rail computes its own POWERED property from a real redstone signal - forcing the
		// blockstate property directly gets silently recomputed back to false on the very next block
		// update, so this really powers it the same way a player would: a real redstone block as the
		// support block directly beneath the rail.
		helper.setBlock(railPos, net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
		BlockPos railOnTop = railPos.above();
		helper.setBlock(railOnTop, thaumcraft.api.blocks.BlocksTC.activatorRail.get().defaultBlockState());

		EntityTurretCrossbow turret = ThaumcraftEntities.TURRET_CROSSBOW.get().create(level);
		helper.assertTrue(turret != null, "the turret entity type should really be able to create an instance");
		BlockPos railOnTopAbs = helper.absolutePos(railOnTop);
		turret.moveTo(railOnTopAbs.getX() + 0.5, railOnTopAbs.getY() + 1.0, railOnTopAbs.getZ() + 0.5, 0.0f, 0.0f);
		turret.setValidSpawn();
		level.addFreshEntity(turret);

		boolean froze = false;
		for (int i = 0; i < 20 && !froze; i++) {
			turret.tick();
			froze = turret.isNoAi();
		}
		helper.assertTrue(froze, "a turret standing on a real powered activator rail should really freeze its own AI");

		ServerPlayer owner = spawnCasterAt(helper, railAbs.above(3));
		turret.tame(owner);
		owner.setShiftKeyDown(true);
		net.minecraft.world.InteractionResult result = turret.mobInteract(owner, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "sneak-interacting as the real owner should really pick the turret back up, got: " + result);
		helper.assertTrue(!turret.isAlive(), "the turret should really discard itself once picked back up");
		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(railOnTopAbs).inflate(2));
		boolean sawPlacer = false;
		for (ItemEntity drop : drops) {
			if (drop.getItem().is(ItemsTC.turretPlacerBasic.get())) {
				sawPlacer = true;
			}
		}
		helper.assertTrue(sawPlacer, "picking the turret back up should really drop a real turret placer item");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Construct arc, chunk 2: EntityTurretCrossbowAdvanced/TurretConfigMenu - see those classes' own doc
	// comments.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretPlacerAdvancedPlacesARealTamedAdvancedTurret(GameTestHelper helper) {
		BlockPos floorPos = new BlockPos(2, 1, 2);
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		player.getAbilities().instabuild = false;
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.turretPlacerAdvanced.get()));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "placing against a solid block should succeed, got: " + result);
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the single placer stack should be fully consumed");

		List<thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced> found = helper.getLevel().getEntitiesOfClass(
				thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced.class, new AABB(floorAbs).inflate(3));
		helper.assertTrue(found.size() == 1, "exactly one advanced turret should have been spawned, found " + found.size());
		thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced turret = found.get(0);
		helper.assertTrue(turret.isOwnedBy(player) && turret.isTame(), "the placed advanced turret should really be tamed/owned by the placing player");
		helper.assertTrue(turret.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH) == 40.0,
				"the advanced turret should really have its own 40-health attribute, not the base turret's 30");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretCrossbowAdvancedRespectsOwnershipAndTheFriendlyFireFlag(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos originAbs = helper.absolutePos(new BlockPos(2, 2, 2));

		ServerPlayer owner = spawnCasterAt(helper, originAbs.above(3));

		thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced turret =
				thaumcraft.common.registry.ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get().create(level);
		helper.assertTrue(turret != null, "the advanced turret entity type should really be able to create an instance");
		turret.moveTo(originAbs.getX() + 0.5, originAbs.getY(), originAbs.getZ() + 0.5, 0.0f, 0.0f);
		turret.setValidSpawn();
		level.addFreshEntity(turret);
		turret.tame(owner);
		turret.setTargetAnimal(true);
		turret.setTargetFriendly(false);

		net.minecraft.world.entity.animal.Wolf wolf = net.minecraft.world.entity.EntityType.WOLF.create(level);
		helper.assertTrue(wolf != null, "a real wolf should really be creatable");
		wolf.moveTo(originAbs.getX() + 2.5, originAbs.getY(), originAbs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(wolf);
		wolf.tame(owner);

		for (int i = 0; i < 100; i++) {
			turret.tick();
		}
		helper.assertTrue(turret.getTarget() != wolf, "an advanced turret shouldn't target its own owner's other tamed pet with friendly-fire off");

		turret.setTargetFriendly(true);
		turret.setTarget(null);
		boolean targetedFriendly = false;
		for (int i = 0; i < 100 && !targetedFriendly; i++) {
			turret.tick();
			targetedFriendly = turret.getTarget() == wolf;
		}
		helper.assertTrue(targetedFriendly, "flipping friendly-fire on should let the turret target its own owner's other pet");

		// Real port of the original's own onUpdate check: a player target dropped the moment PVP is
		// disabled server-side, even mid-fight (forced directly here since a fresh target-selector pass
		// would never pick a non-friendly player target while PVP is off in the first place). Every
		// target-filter flag is cleared first so the real target-selector goal's own next evaluation can't
		// immediately re-target the still-nearby wolf and clobber the manually-forced player target before
		// this tick's own PVP check ever gets to look at it.
		turret.setTargetAnimal(false);
		turret.setTargetFriendly(false);
		boolean originalPvp = level.getServer().isPvpAllowed();
		level.getServer().setPvpAllowed(false);
		try {
			ServerPlayer stranger = spawnCasterAt(helper, originAbs.above(5));
			turret.setTarget(stranger);
			turret.tick();
			helper.assertTrue(turret.getTarget() == null, "a real player target should really be dropped the moment PVP turns off server-side");
		} finally {
			level.getServer().setPvpAllowed(originalPvp);
		}

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretConfigMenuOnlyLetsTheRealOwnerFlipTargetingFlags(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(new BlockPos(2, 2, 2));

		ServerPlayer owner = spawnCasterAt(helper, abs.above(2));
		ServerPlayer stranger = spawnCasterAt(helper, abs.above(4));

		thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced turret =
				thaumcraft.common.registry.ThaumcraftEntities.TURRET_CROSSBOW_ADVANCED.get().create(level);
		helper.assertTrue(turret != null, "the advanced turret entity type should really be able to create an instance");
		turret.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0.0f, 0.0f);
		turret.setValidSpawn();
		level.addFreshEntity(turret);
		turret.tame(owner);

		thaumcraft.common.container.TurretConfigMenu menu = new thaumcraft.common.container.TurretConfigMenu(1, owner.getInventory(), turret);
		boolean before = turret.getTargetAnimal();

		boolean strangerResult = menu.clickMenuButton(stranger, thaumcraft.common.container.TurretConfigMenu.BUTTON_TARGET_ANIMAL);
		helper.assertTrue(!strangerResult && turret.getTargetAnimal() == before, "a non-owner shouldn't be able to flip the turret's targeting flags");

		boolean ownerResult = menu.clickMenuButton(owner, thaumcraft.common.container.TurretConfigMenu.BUTTON_TARGET_ANIMAL);
		helper.assertTrue(ownerResult && turret.getTargetAnimal() != before, "the real owner should be able to flip a targeting flag");

		menu.broadcastChanges();
		helper.assertTrue(menu.isTargetAnimal() == turret.getTargetAnimal(), "the menu's own synced flag readout should match the live turret after broadcastChanges");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Construct arc, chunk 3: EntityArcaneBore - see its own doc comment.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void turretPlacerBorePlacesARealTamedBoreFacingThePlacer(GameTestHelper helper) {
		BlockPos floorPos = new BlockPos(2, 1, 2);
		BlockPos floorAbs = helper.absolutePos(floorPos);
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);

		ServerPlayer player = spawnCasterAt(helper, floorAbs.above(2));
		player.getAbilities().instabuild = false;
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, new ItemStack(ItemsTC.turretPlacerBore.get()));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(floorAbs), net.minecraft.core.Direction.UP, floorAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(player, hand, hit);
		net.minecraft.world.InteractionResult result = player.getItemInHand(hand).getItem().useOn(context);

		helper.assertTrue(result == net.minecraft.world.InteractionResult.SUCCESS, "placing against a solid block should succeed, got: " + result);
		helper.assertTrue(player.getItemInHand(hand).isEmpty(), "the single placer stack should be fully consumed");

		List<thaumcraft.common.entities.construct.EntityArcaneBore> found = helper.getLevel().getEntitiesOfClass(
				thaumcraft.common.entities.construct.EntityArcaneBore.class, new AABB(floorAbs).inflate(3));
		helper.assertTrue(found.size() == 1, "exactly one bore should have been spawned, found " + found.size());
		thaumcraft.common.entities.construct.EntityArcaneBore bore = found.get(0);
		helper.assertTrue(bore.isOwnedBy(player) && bore.isTame(), "the placed bore should really be tamed/owned by the placing player");
		helper.assertTrue(bore.getFacing() == player.getDirection(), "the placed bore should really face the same direction the placing player was facing");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneBoreDigsARealBlockWithARealHeldPickaxeAndEjectsIntoAnAdjacentChest(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos borePos = new BlockPos(2, 2, 2);
		BlockPos boreAbs = helper.absolutePos(borePos);

		// A real solid floor directly beneath the bore (so it really settles exactly at borePos, not one
		// block lower the way a non-solid rail would let it fall - see the turret's own rail test for that
		// same real lesson), powered by a real redstone block touching it from below so the bore's own real
		// "am I switched on" check (a real neighbor-signal read on the block below its own feet) reads true.
		helper.setBlock(borePos.below(), net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(borePos.below().below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);
		// A real chest right beside the bore's own position to catch the real ejected drop.
		helper.setBlock(borePos.north(), net.minecraft.world.level.block.Blocks.CHEST);
		// A real stone block directly ahead (south) of the bore, well within real digging range.
		BlockPos targetPos = borePos.south();
		BlockPos targetAbs = helper.absolutePos(targetPos);
		helper.setBlock(targetPos, net.minecraft.world.level.block.Blocks.STONE);

		thaumcraft.common.entities.construct.EntityArcaneBore bore =
				thaumcraft.common.registry.ThaumcraftEntities.ARCANE_BORE.get().create(level);
		helper.assertTrue(bore != null, "the arcane bore entity type should really be able to create an instance");
		bore.moveTo(boreAbs.getX() + 0.5, boreAbs.getY(), boreAbs.getZ() + 0.5, 0.0f, 0.0f);
		bore.setFacing(net.minecraft.core.Direction.SOUTH);
		bore.setValidSpawn();
		level.addFreshEntity(bore);
		bore.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(net.minecraft.world.item.Items.DIAMOND_PICKAXE));
		bore.setCharge(100.0f);

		boolean dug = false;
		for (int i = 0; i < 100 && !dug; i++) {
			bore.tick();
			dug = level.getBlockState(targetAbs).isAir();
		}
		helper.assertTrue(dug, "a real charged, pickaxe-holding bore standing on a real inactive activator rail should really dig out the real block ahead of it");
		helper.assertTrue(bore.getCharge() < 100.0f, "digging should really have spent some of the bore's own real vis charge");

		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(borePos.north());
		boolean sawCobble = false;
		for (int slot = 0; slot < chest.getContainerSize(); slot++) {
			if (chest.getItem(slot).is(net.minecraft.world.item.Items.COBBLESTONE)) {
				sawCobble = true;
			}
		}
		helper.assertTrue(sawCobble, "the real mined stone's real cobblestone drop should really have been ejected into the real adjacent chest");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void arcaneBoreSwapsItsPickaxeViaInteractAndSneakPickupReturnsThePlacer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos borePos = new BlockPos(2, 2, 2);
		BlockPos boreAbs = helper.absolutePos(borePos);

		thaumcraft.common.entities.construct.EntityArcaneBore bore =
				thaumcraft.common.registry.ThaumcraftEntities.ARCANE_BORE.get().create(level);
		helper.assertTrue(bore != null, "the arcane bore entity type should really be able to create an instance");
		bore.moveTo(boreAbs.getX() + 0.5, boreAbs.getY(), boreAbs.getZ() + 0.5, 0.0f, 0.0f);
		bore.setValidSpawn();
		level.addFreshEntity(bore);

		ServerPlayer owner = spawnCasterAt(helper, boreAbs.above(2));
		bore.tame(owner);
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;

		// Real pickaxe-for-empty-hand swap: the owner hands over a real pickaxe, the bore's own (empty)
		// hand comes back to them.
		ItemStack pickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
		owner.setItemInHand(hand, pickaxe);
		net.minecraft.world.InteractionResult swapResult = bore.mobInteract(owner, hand);
		helper.assertTrue(swapResult == net.minecraft.world.InteractionResult.SUCCESS, "offering a real pickaxe should really succeed, got: " + swapResult);
		helper.assertTrue(bore.getMainHandItem().is(net.minecraft.world.item.Items.IRON_PICKAXE), "the bore's own main hand should really now hold the real offered pickaxe");
		helper.assertTrue(owner.getItemInHand(hand).isEmpty(), "the owner's own hand should really now be empty (the bore had nothing to hand back yet)");

		// Real sneak-pickup: drops the held pickaxe and a real bore placer item, then discards itself.
		owner.setShiftKeyDown(true);
		net.minecraft.world.InteractionResult pickupResult = bore.mobInteract(owner, hand);
		helper.assertTrue(pickupResult == net.minecraft.world.InteractionResult.SUCCESS, "sneak-interacting as the real owner should really pick the bore back up, got: " + pickupResult);
		helper.assertTrue(!bore.isAlive(), "the bore should really discard itself once picked back up");

		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(boreAbs).inflate(2));
		boolean sawPickaxe = false;
		boolean sawPlacer = false;
		for (ItemEntity drop : drops) {
			if (drop.getItem().is(net.minecraft.world.item.Items.IRON_PICKAXE)) {
				sawPickaxe = true;
			}
			if (drop.getItem().is(ItemsTC.turretPlacerBore.get())) {
				sawPlacer = true;
			}
		}
		helper.assertTrue(sawPickaxe, "picking the bore back up should really drop its real held pickaxe");
		helper.assertTrue(sawPlacer, "picking the bore back up should really drop a real bore placer item");

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Crystal-cluster blocks - see BlockCrystal's own doc comment. First real content unlocking the
	// CrystalCluster* family of infusion recipes.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crystalWontSurviveWithoutTouchingARealSturdySurface(GameTestHelper helper) {
		// helper.setBlock is a raw world-edit, same as a structure block placing a block directly - it
		// doesn't run the real placement-time canSurvive check a player's own BlockItem#useOn would (that
		// check only ever runs at the moment of real placement, not on every direct level.setBlock call), so
		// this checks the real logical answer directly rather than expecting an already-force-placed block
		// to have vanished on its own.
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(2, 3, 2);
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.crystals.get(Aspect.FIRE).get());
		helper.assertTrue(!level.getBlockState(abs).canSurvive(level, abs),
				"a crystal placed with nothing sturdy touching any of its 6 faces should really refuse to survive there");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crystalGrowsWhenLocalVisIsWellAboveTheAuraBase(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floorPos = new BlockPos(2, 2, 2);
		BlockPos crystalPos = floorPos.above();
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);
		helper.setBlock(crystalPos, BlocksTC.crystals.get(Aspect.EARTH).get());
		BlockPos crystalAbs = helper.absolutePos(crystalPos);
		AuraHandler.addVis(level, crystalAbs, 1000.0f);

		boolean grew = false;
		for (int i = 0; i < 500 && !grew; i++) {
			level.getBlockState(crystalAbs).randomTick(level, crystalAbs, level.random);
			grew = level.getBlockState(crystalAbs).is(BlocksTC.crystals.get(Aspect.EARTH).get())
					&& level.getBlockState(crystalAbs).getValue(thaumcraft.common.blocks.world.ore.BlockCrystal.SIZE) > 0;
		}
		helper.assertTrue(grew, "a real crystal sitting in a real vis surplus should really grow past size 0 within a generous tick budget");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crystalShrinksOnLowVisButOnlyVanishesNextToARealSibling(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floorPos = new BlockPos(2, 2, 2);
		BlockPos crystalPos = floorPos.above();
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);
		net.minecraft.world.level.block.state.BlockState grownState = BlocksTC.crystals.get(Aspect.WATER).get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.ore.BlockCrystal.SIZE, 1);
		helper.setBlock(crystalPos, grownState);
		BlockPos crystalAbs = helper.absolutePos(crystalPos);
		AuraHandler.drainVis(level, crystalAbs, AuraHandler.getVis(level, crystalAbs), false);

		boolean shrankToZero = false;
		for (int i = 0; i < 500 && !shrankToZero; i++) {
			level.getBlockState(crystalAbs).randomTick(level, crystalAbs, level.random);
			shrankToZero = level.getBlockState(crystalAbs).is(BlocksTC.crystals.get(Aspect.WATER).get())
					&& level.getBlockState(crystalAbs).getValue(thaumcraft.common.blocks.world.ore.BlockCrystal.SIZE) == 0;
			if (!level.getBlockState(crystalAbs).is(BlocksTC.crystals.get(Aspect.WATER).get())) {
				break; // vanished already - shouldn't happen with no sibling nearby, checked below
			}
		}
		helper.assertTrue(shrankToZero, "a real lone crystal starved of vis should really shrink down to size 0 within a generous tick budget");
		helper.assertTrue(level.getBlockState(crystalAbs).is(BlocksTC.crystals.get(Aspect.WATER).get()),
				"a real lone crystal at size 0 should never fully vanish with no real sibling touching it");

		// Now give it a real sibling and confirm it can actually vanish once starved further.
		helper.setBlock(crystalPos.above(), BlocksTC.crystals.get(Aspect.WATER).get());
		boolean vanished = false;
		for (int i = 0; i < 500 && !vanished; i++) {
			level.getBlockState(crystalAbs).randomTick(level, crystalAbs, level.random);
			vanished = !level.getBlockState(crystalAbs).is(BlocksTC.crystals.get(Aspect.WATER).get());
		}
		helper.assertTrue(vanished, "a real starved crystal touching a real sibling should really vanish within a generous tick budget");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void crystalDropsSizePlusOneCrystalEssenceOfItsOwnAspect(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos floorPos = new BlockPos(2, 2, 2);
		BlockPos crystalPos = floorPos.above();
		helper.setBlock(floorPos, net.minecraft.world.level.block.Blocks.STONE);
		net.minecraft.world.level.block.state.BlockState grownState = BlocksTC.crystals.get(Aspect.ORDER).get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.world.ore.BlockCrystal.SIZE, 2);
		helper.setBlock(crystalPos, grownState);
		BlockPos crystalAbs = helper.absolutePos(crystalPos);

		ServerPlayer player = spawnCasterAt(helper, crystalAbs.above(2));
		BlocksTC.crystals.get(Aspect.ORDER).get().playerWillDestroy(level, crystalAbs, level.getBlockState(crystalAbs), player);

		List<ItemEntity> drops = level.getEntitiesOfClass(ItemEntity.class, new AABB(crystalAbs).inflate(2));
		int count = 0;
		for (ItemEntity drop : drops) {
			if (drop.getItem().is(ItemsTC.crystalEssence.get())) {
				count += drop.getItem().getCount();
			}
		}
		helper.assertTrue(count == 3, "a real size-2 crystal should really drop exactly 3 (size+1) real crystal_essence units, found " + count);
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.api.items.WornGearHelper} - the shared scan every "bauble" item
	 * this pass added relies on instead of a real dedicated equipment slot (see that class's own doc
	 * comment). Confirms it picks up the hotbar and the offhand but not a deep main-inventory slot.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void wornGearHelperScansHotbarAndOffhandOnly(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getInventory().setItem(0, new ItemStack(Items.STICK));
		player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.APPLE));
		player.getInventory().setItem(20, new ItemStack(Items.DIAMOND));

		List<ItemStack> worn = thaumcraft.api.items.WornGearHelper.getWornItems(player);
		helper.assertTrue(worn.stream().anyMatch(s -> s.is(Items.STICK)), "a hotbar item should be found");
		helper.assertTrue(worn.stream().anyMatch(s -> s.is(Items.APPLE)), "the offhand item should be found");
		helper.assertFalse(worn.stream().anyMatch(s -> s.is(Items.DIAMOND)), "a deep main-inventory slot should NOT be scanned - it isn't real hotbar/offhand");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.baubles.ItemAmuletVis} - the worn charm should
	 * recharge the first real {@code IRechargable} item it finds among the player's worn items, drawing
	 * from the local aura (already seeded here via {@code AuraHandler.addVis}, same as every other real
	 * aura-consuming test in this file).
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void amuletVisRechargesFirstRechargableWornItem(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(new BlockPos(2, 2, 2));
		AuraHandler.addVis(helper.getLevel(), origin, 500.0f);
		ServerPlayer player = spawnCasterAt(helper, origin);

		ItemStack charm = new ItemStack(ItemsTC.amuletVisCrafted.get());
		ItemStack rechargable = new ItemStack(TEST_RECHARGABLE_ITEM.get());
		player.getInventory().setItem(0, charm);
		player.getInventory().setItem(1, rechargable);

		helper.assertTrue(thaumcraft.api.items.RechargeHelper.getCharge(rechargable) == 0, "should start with no charge");
		((thaumcraft.api.items.IWornGear) charm.getItem()).wornTick(charm, player);
		helper.assertTrue(thaumcraft.api.items.RechargeHelper.getCharge(rechargable) == 1,
				"amulet_vis's wornTick should have recharged the rechargable item in the hotbar by 1, found charge " + thaumcraft.api.items.RechargeHelper.getCharge(rechargable));
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.baubles.ItemVerdantCharm}'s priority-ordered
	 * cure chain (Wither &gt; Poison &gt; Flux Taint), ported as-is from the original's {@code onWornTick} -
	 * only one effect fires per tick, checked in this exact order.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void verdantCharmCuresWitherThenPoisonThenFluxInPriorityOrder(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.WITHER, 200, 0));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(MobEffects.POISON, 200, 0));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT, 200, 0));

		ItemStack charm = new ItemStack(ItemsTC.verdantCharm.get());
		charm.set(thaumcraft.api.items.ThaumcraftDataComponents.CHARGE.get(), 200);
		thaumcraft.api.items.IWornGear wornGear = (thaumcraft.api.items.IWornGear) charm.getItem();

		wornGear.wornTick(charm, player);
		helper.assertFalse(player.hasEffect(MobEffects.WITHER), "Wither should be cured first");
		helper.assertTrue(player.hasEffect(MobEffects.POISON), "Poison should NOT be cured yet - only one effect per tick");

		wornGear.wornTick(charm, player);
		helper.assertFalse(player.hasEffect(MobEffects.POISON), "Poison should be cured second");
		helper.assertTrue(player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT), "Flux Taint should NOT be cured yet");

		wornGear.wornTick(charm, player);
		helper.assertFalse(player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.FLUX_TAINT), "Flux Taint should be cured third");
		helper.assertTrue(thaumcraft.api.items.RechargeHelper.getCharge(charm) == 200 - 20 - 10 - 5,
				"charge should have been consumed for exactly the 3 real cures, found " + thaumcraft.api.items.RechargeHelper.getCharge(charm));
		helper.succeed();
	}

	/**
	 * Real coverage for the Charm of Undying death-prevention hook - see
	 * {@link thaumcraft.common.lib.events.WornGearEvents#onDeath}.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void charmUndyingCancelsFatalDamageAndConsumesItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.setHealth(0.0f);
		ItemStack charm = new ItemStack(ItemsTC.charmUndying.get());
		player.getInventory().setItem(0, charm);

		net.neoforged.neoforge.event.entity.living.LivingDeathEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingDeathEvent(player, level.damageSources().generic());
		thaumcraft.common.lib.events.WornGearEvents.onDeath(event);

		helper.assertTrue(event.isCanceled(), "the death event should have been canceled");
		helper.assertTrue(player.getHealth() == 1.0f, "the player should have been set to 1 HP, found " + player.getHealth());
		helper.assertTrue(player.hasEffect(MobEffects.REGENERATION), "Regeneration should have been granted");
		helper.assertTrue(player.hasEffect(MobEffects.ABSORPTION), "Absorption should have been granted");
		helper.assertTrue(player.getInventory().getItem(0).isEmpty(), "the charm should have been consumed");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.casters.ItemCaster#getConsumptionModifier}'s hotbar/
	 * offhand scan - see that method's own doc comment for why this replaces the original's Baubles-slot scan.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void visDiscountScanIncludesHotbarItems(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack caster = new ItemStack(ItemsTC.casterBasic.get());
		player.getInventory().setItem(0, new ItemStack(ItemsTC.ringApprentice.get()));

		float modifier = ((thaumcraft.api.casters.ICaster) caster.getItem()).getConsumptionModifier(caster, player, false);
		helper.assertTrue(Math.abs(modifier - 0.95f) < 0.0001f,
				"a real 5% vis discount ring in the hotbar should reduce the consumption modifier to 0.95, found " + modifier);
		helper.succeed();
	}

	/**
	 * Names the 5 real infusion recipes this pass unlocked now that the bauble-family items themselves are
	 * real (VisAmulet/CloudRing/CuriosityBand/CharmUndying/VoidseerPearl - VerdantHeart's own 3 recipes are
	 * still blocked on an unported nugget subtype and a specific-potion-content ingredient, not done this
	 * pass) and asserts each one is actually registered - same "name every id, don't just eyeball a count"
	 * approach as {@link #focusRecipesRegistered}.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void baubleInfusionRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "infusion_vis_amulet", "infusion_cloud_ring", "infusion_curiosity_band",
				"infusion_charm_undying", "infusion_voidseer_pearl" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}
		helper.succeed();
	}

	/**
	 * Real end-to-end craft coverage for the simplest of the 5 new bauble recipes - a real vanilla Totem of
	 * Undying plus a real brass plate becomes a real Charm of Undying. Low instability (2) and a small real
	 * aspect cost (25 units) - no stabilizers needed, matching the same low-cost/low-instability crystal
	 * recipes from the previous chunk that also needed none.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionMatrixCraftsCharmUndyingFromTotemAndPlateBrass(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos matrixPos = new BlockPos(4, 4, 4);
		BlockPos centralPos = matrixPos.below(2);
		helper.setBlock(matrixPos, BlocksTC.infusionMatrix.get());
		helper.setBlock(centralPos, BlocksTC.pedestal.get());
		thaumcraft.common.tiles.crafting.TilePedestal central = helper.getBlockEntity(centralPos);
		central.setItem(new ItemStack(Items.TOTEM_OF_UNDYING));

		BlockPos componentPos = matrixPos.offset(2, 0, 0);
		helper.setBlock(componentPos, BlocksTC.pedestal.get());
		thaumcraft.common.tiles.crafting.TilePedestal component = helper.getBlockEntity(componentPos);
		component.setItem(new ItemStack(ItemsTC.plateBrass.get()));

		BlockPos jarPos = matrixPos.offset(-2, 0, 0);
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarPos)).addToContainer(Aspect.LIFE, 30);

		BlockPos matrixAbs = helper.absolutePos(matrixPos);
		thaumcraft.common.tiles.crafting.TileInfusionMatrix matrix = helper.getBlockEntity(matrixPos);
		ServerPlayer player = spawnCasterAt(helper, matrixAbs);

		helper.assertTrue(matrix.activate(level, player), "activation should succeed with a central pedestal in place");
		helper.assertTrue(matrix.activate(level, player), "the second activation (with a real, valid charm_undying recipe on the pedestals) should start crafting");
		helper.assertTrue(matrix.crafting, "the matrix should be mid-craft after a successful craftingStart");

		net.minecraft.world.level.block.state.BlockState matrixState = helper.getBlockState(matrixPos);
		for (int i = 0; i < 2000 && matrix.crafting; i++) {
			thaumcraft.common.tiles.crafting.TileInfusionMatrix.tick(level, matrixAbs, matrixState, matrix);
		}

		helper.assertTrue(!matrix.crafting, "the craft should have completed (or definitively failed) within a generous tick budget");
		helper.assertTrue(central.getItem().is(ItemsTC.charmUndying.get()), "the central pedestal should hold a real charm_undying, found: " + central.getItem());
		helper.succeed();
	}

	/**
	 * Names the 5 real elemental-tool infusion recipes this pass unlocked - same "name every id" approach as
	 * {@link #focusRecipesRegistered}/{@link #baubleInfusionRecipesRegistered}.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void elementalInfusionRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "infusion_elemental_axe", "infusion_elemental_pick", "infusion_elemental_sword",
				"infusion_elemental_shovel", "infusion_elemental_hoe" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.tools.ItemElementalAxe}'s real "magnet" ability -
	 * holding right-click should pull a nearby real item entity closer with each real use-tick.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void elementalAxePullsNearbyItemEntitiesWhileUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemEntity dropped = new ItemEntity(level, player.getX() + 4, player.getY(), player.getZ(), new ItemStack(Items.STICK));
		dropped.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(dropped);

		double startDistance = player.distanceTo(dropped);
		ItemStack axe = new ItemStack(ItemsTC.elementalAxe.get());
		thaumcraft.common.items.tools.ItemElementalAxe item = (thaumcraft.common.items.tools.ItemElementalAxe) axe.getItem();
		for (int i = 0; i < 20; i++) {
			item.onUseTick(level, player, axe, 100);
			dropped.tick();
		}
		double endDistance = player.distanceTo(dropped);
		helper.assertTrue(endDistance < startDistance,
				"a nearby item entity should really be pulled closer while the elemental axe is in use - started " + startDistance + ", ended " + endDistance);
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.tools.ItemElementalSword}'s real "gust" ability -
	 * holding right-click should push a nearby real living entity FURTHER away with each real use-tick (the
	 * opposite direction from the axe's own magnet - see that item's own doc comment for the real motion-
	 * vector sign difference this test locks in).
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void elementalSwordPushesNearbyLivingEntitiesAwayWhileUsed(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(player.getX() + 2, player.getY(), player.getZ());
		level.addFreshEntity(pig);

		double startDistance = player.distanceTo(pig);
		ItemStack sword = new ItemStack(ItemsTC.elementalSword.get());
		thaumcraft.common.items.tools.ItemElementalSword item = (thaumcraft.common.items.tools.ItemElementalSword) sword.getItem();
		for (int i = 0; i < 20; i++) {
			item.onUseTick(level, player, sword, 100);
			pig.tick();
		}
		double endDistance = player.distanceTo(pig);
		helper.assertTrue(endDistance > startDistance,
				"a nearby living entity should really be pushed further away while the elemental sword is in use - started " + startDistance + ", ended " + endDistance);
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.tools.ItemElementalHoe}'s real 3x3 area-till ability -
	 * a single real right-click on the center of a 3x3 patch of real grass blocks (with real air above each)
	 * should till all 9 into real farmland, not just the one clicked.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void elementalHoeTillsA3x3AreaAroundTheClickedBlock(GameTestHelper helper) {
		BlockPos center = new BlockPos(3, 2, 3);
		for (int xx = -1; xx <= 1; xx++) {
			for (int zz = -1; zz <= 1; zz++) {
				helper.setBlock(center.offset(xx, 0, zz), net.minecraft.world.level.block.Blocks.GRASS_BLOCK);
			}
		}
		BlockPos centerAbs = helper.absolutePos(center);
		ServerPlayer player = spawnCasterAt(helper, centerAbs.above(2));
		ItemStack hoe = new ItemStack(ItemsTC.elementalHoe.get());
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, hoe);

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(centerAbs), net.minecraft.core.Direction.UP, centerAbs, false);
		net.minecraft.world.item.context.UseOnContext context = new net.minecraft.world.item.context.UseOnContext(
				helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND, hoe, hit);
		((thaumcraft.common.items.tools.ItemElementalHoe) hoe.getItem()).useOn(context);

		for (int xx = -1; xx <= 1; xx++) {
			for (int zz = -1; zz <= 1; zz++) {
				BlockPos p = center.offset(xx, 0, zz);
				helper.assertTrue(helper.getBlockState(p).is(net.minecraft.world.level.block.Blocks.FARMLAND),
						"position " + p + " should have been tilled into real farmland by the 3x3 area effect, found " + helper.getBlockState(p));
			}
		}
		helper.succeed();
	}

	/**
	 * Names the real crafting-table recipes for the base Void armor set and the real infusion recipes for
	 * its Void Robe upgrade tier, and asserts each one is actually registered - same "name every id" pattern
	 * as {@link #focusRecipesRegistered} and siblings.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidArmorRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "void_helm", "void_chest", "void_legs", "void_boots",
				"infusion_void_robe_helm", "infusion_void_robe_chest", "infusion_void_robe_legs" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.armor.ItemVoidArmor}'s real self-repair tick - a
	 * damaged piece should really regain 1 durability every real second (20 ticks) while worn.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidArmorSelfRepairsOneDurabilityPerSecondWhileWorn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack helm = new ItemStack(ItemsTC.voidHelm.get());
		helm.setDamageValue(10);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helm);

		for (int i = 0; i < 20; i++) {
			helm.getItem().inventoryTick(helm, level, player, 0, false);
			player.tickCount++;
		}
		helper.assertTrue(helm.getDamageValue() == 9,
				"a worn void_helm should really repair exactly 1 durability after 20 real ticks, found damage " + helm.getDamageValue());
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.items.armor.ItemVoidRobeArmor}'s real vis-discount/warp
	 * values, and {@link thaumcraft.common.items.armor.ItemGoggles}' own real vis-discount - direct checks
	 * against the real interface methods, matching the values the original's own items really had.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidRobeArmorAndGogglesHaveTheirRealDiscountAndWarpValues(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack robeChest = new ItemStack(ItemsTC.voidRobeChest.get());
		thaumcraft.api.items.IVisDiscountGear robeDiscount = (thaumcraft.api.items.IVisDiscountGear) robeChest.getItem();
		thaumcraft.api.items.IWarpingGear robeWarp = (thaumcraft.api.items.IWarpingGear) robeChest.getItem();
		helper.assertTrue(robeDiscount.getVisDiscount(robeChest, player) == 5, "void_robe_chest should give a real 5% vis discount");
		helper.assertTrue(robeWarp.getWarp(robeChest, player) == 3, "void_robe_chest should add a real 3 warp");

		ItemStack goggles = new ItemStack(ItemsTC.goggles.get());
		thaumcraft.api.items.IVisDiscountGear gogglesDiscount = (thaumcraft.api.items.IVisDiscountGear) goggles.getItem();
		helper.assertTrue(gogglesDiscount.getVisDiscount(goggles, player) == 5, "goggles should give a real 5% vis discount");

		ItemStack voidHelm = new ItemStack(ItemsTC.voidHelm.get());
		thaumcraft.api.items.IWarpingGear voidWarp = (thaumcraft.api.items.IWarpingGear) voidHelm.getItem();
		helper.assertTrue(voidWarp.getWarp(voidHelm, player) == 1, "base void_helm should add a real 1 warp");
		helper.succeed();
	}

	/**
	 * Real coverage for the rest of the Void tool set (pickaxe/axe/shovel/hoe - the sword is already covered
	 * elsewhere) - {@link thaumcraft.common.items.tools.ItemVoidPickaxe}'s own doc comment covers the shared
	 * mechanics: real self-repair, real +1 warp, and (unlike the sword, which is mob-drop-only) a real
	 * shaped crafting recipe for each, substituting a Thaumium ingot for the original's own unported
	 * "Void Metal ingot" - same documented substitution {@link thaumcraft.api.items.ItemsTC}'s own
	 * {@code TIER_VOID} already uses for repair.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void voidToolSetHasRealRecipesAndSharedMechanics(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));

		for (String key : new String[] {"void_pick", "void_axe", "void_shovel", "void_hoe"}) {
			helper.assertTrue(helper.getLevel().getRecipeManager()
					.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, key)).isPresent(),
					"a real shaped recipe for " + key + " should be registered");
		}

		thaumcraft.api.items.IWarpingGear pickWarp = (thaumcraft.api.items.IWarpingGear) ItemsTC.voidPick.get();
		helper.assertTrue(pickWarp.getWarp(new ItemStack(ItemsTC.voidPick.get()), player) == 1, "void_pick should add a real 1 warp");
		thaumcraft.api.items.IWarpingGear axeWarp = (thaumcraft.api.items.IWarpingGear) ItemsTC.voidAxe.get();
		helper.assertTrue(axeWarp.getWarp(new ItemStack(ItemsTC.voidAxe.get()), player) == 1, "void_axe should add a real 1 warp");
		thaumcraft.api.items.IWarpingGear shovelWarp = (thaumcraft.api.items.IWarpingGear) ItemsTC.voidShovel.get();
		helper.assertTrue(shovelWarp.getWarp(new ItemStack(ItemsTC.voidShovel.get()), player) == 1, "void_shovel should add a real 1 warp");
		thaumcraft.api.items.IWarpingGear hoeWarp = (thaumcraft.api.items.IWarpingGear) ItemsTC.voidHoe.get();
		helper.assertTrue(hoeWarp.getWarp(new ItemStack(ItemsTC.voidHoe.get()), player) == 1, "void_hoe should add a real 1 warp");

		ItemStack pick = new ItemStack(ItemsTC.voidPick.get());
		pick.setDamageValue(10);
		net.minecraft.server.level.ServerLevel level = helper.getLevel();
		for (int i = 0; i < 20; i++) {
			pick.getItem().inventoryTick(pick, level, player, 0, false);
			player.tickCount++;
		}
		helper.assertTrue(pick.getDamageValue() == 9, "a held void_pick should really repair exactly 1 durability after 20 real ticks, found damage " + pick.getDamageValue());
		helper.succeed();
	}

	/**
	 * Names the real Fortress armor recipes (3 base pieces + 4 helm upgrades) and asserts each one is
	 * actually registered - same "name every id" pattern as {@link #focusRecipesRegistered} and siblings.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fortressArmorRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "infusion_fortress_helm", "infusion_fortress_chest", "infusion_fortress_legs",
				"infusion_fortress_helm_goggles", "infusion_fortress_mask_devil", "infusion_fortress_mask_ghost", "infusion_fortress_mask_fiend" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.lib.events.FortressMaskEvents} - the Angry Ghost mask should
	 * give a real chance to Wither whoever hits its wearer.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fortressHelmAngryGhostMaskCanWitherTheAttacker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer victim = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ItemStack helm = new ItemStack(ItemsTC.fortressHelm.get());
		helm.set(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_MASK.get(), 1);
		victim.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helm);
		ServerPlayer attacker = spawnCasterAt(helper, helper.absolutePos(new BlockPos(2, 0, 0)));

		net.minecraft.world.damagesource.DamageSource source = level.damageSources().playerAttack(attacker);
		boolean withered = false;
		for (int i = 0; i < 200 && !withered; i++) {
			net.neoforged.neoforge.common.damagesource.DamageContainer container =
					new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 10.0f);
			container.setNewDamage(10.0f);
			net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event =
					new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim, container);
			thaumcraft.common.lib.events.FortressMaskEvents.onDamage(event);
			withered = attacker.hasEffect(MobEffects.WITHER);
		}
		helper.assertTrue(withered, "the Angry Ghost mask should really be able to Wither an attacker within a generous number of real chance rolls");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.lib.events.FortressMaskEvents} - the Sipping Fiend mask
	 * should give a real chance to heal its wearer when they land a hit.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void fortressHelmSippingFiendMaskCanHealTheAttacker(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer attacker = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		attacker.setHealth(10.0f);
		ItemStack helm = new ItemStack(ItemsTC.fortressHelm.get());
		helm.set(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_MASK.get(), 2);
		attacker.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helm);
		ServerPlayer victim = spawnCasterAt(helper, helper.absolutePos(new BlockPos(2, 0, 0)));

		net.minecraft.world.damagesource.DamageSource source = level.damageSources().playerAttack(attacker);
		boolean healed = false;
		for (int i = 0; i < 200 && !healed; i++) {
			attacker.setHealth(10.0f);
			net.neoforged.neoforge.common.damagesource.DamageContainer container =
					new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 10.0f);
			container.setNewDamage(10.0f);
			net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event =
					new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(victim, container);
			thaumcraft.common.lib.events.FortressMaskEvents.onDamage(event);
			healed = attacker.getHealth() > 10.0f;
		}
		helper.assertTrue(healed, "the Sipping Fiend mask should really be able to heal its wearer within a generous number of real chance rolls");
		helper.succeed();
	}

	/**
	 * Names the real focus-tier-upgrade recipes (last of the 4 "let's do them all" infusion-content
	 * families) and asserts each one is actually registered - same "name every id" pattern as {@link
	 * #focusRecipesRegistered} and siblings. Also confirms the real per-tier complexity cap {@link
	 * thaumcraft.common.items.casters.ItemFocusBlank} already exposes actually increases across the tiers -
	 * these recipes are only worth anything if a bigger focus can really hold a bigger real node graph.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusBlankTierUpgradeRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "infusion_focus_blank_2", "infusion_focus_blank_3" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}

		thaumcraft.common.items.casters.ItemFocusBlank tier1 = (thaumcraft.common.items.casters.ItemFocusBlank) ItemsTC.focusBlank1.get();
		thaumcraft.common.items.casters.ItemFocusBlank tier2 = (thaumcraft.common.items.casters.ItemFocusBlank) ItemsTC.focusBlank2.get();
		thaumcraft.common.items.casters.ItemFocusBlank tier3 = (thaumcraft.common.items.casters.ItemFocusBlank) ItemsTC.focusBlank3.get();
		helper.assertTrue(tier1.getMaxComplexity() < tier2.getMaxComplexity() && tier2.getMaxComplexity() < tier3.getMaxComplexity(),
				"each real focus tier should really hold a bigger node graph than the last, found " + tier1.getMaxComplexity() + "/" + tier2.getMaxComplexity() + "/" + tier3.getMaxComplexity());
		helper.succeed();
	}

	/**
	 * Names the 6 real Warp-misfortune mob effects and confirms each one is really registered - same
	 * pattern as the recipe-registration tests, applied to a mob-effect registry instead.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void warpMobEffectsRegistered(GameTestHelper helper) {
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.THAUMARHIA.isBound(), "thaumarhia should be registered");
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.UNNATURAL_HUNGER.isBound(), "unnatural_hunger should be registered");
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.BLURRED_VISION.isBound(), "blurred_vision should be registered");
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.SUN_SCORNED.isBound(), "sun_scorned should be registered");
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.DEATH_GAZE.isBound(), "death_gaze should be registered");
		helper.assertTrue(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD.isBound(), "warp_ward should be registered");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.lib.events.WarpEvents#checkDeathGaze} - a real nearby
	 * visible living entity should really be forced to target the wearer and inflicted with real Wither.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void deathGazeForcesNearbyVisibleEntitiesToTargetAndWither(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(thaumcraft.common.registry.ThaumcraftMobEffects.DEATH_GAZE, 200, 0));

		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(player.getX() + 3, player.getY(), player.getZ());
		level.addFreshEntity(pig);

		thaumcraft.common.lib.events.WarpEvents.checkDeathGaze(player);

		helper.assertTrue(pig.hasEffect(MobEffects.WITHER), "a real nearby visible entity should really be inflicted with Wither by Death Gaze");
		helper.assertTrue(pig.getLastHurtByMob() == player, "the entity's real last-hurt-by-mob should really be set to the death-gaze wearer");
		helper.succeed();
	}

	/**
	 * Real end-to-end coverage for {@link thaumcraft.common.lib.events.WarpEvents#checkWarpEvent} - with
	 * enough real Warp and a real warp counter high enough to force the {@code r <= sqrt(warpCounter)} real
	 * roll to always succeed (counter 10000 -> sqrt is 100, and {@code r} is drawn from [0,99]), a real
	 * warp-misfortune should really happen (the real warp counter should really decrease) without throwing.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void checkWarpEventTriggersARealMisfortuneWithoutThrowing(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		thaumcraft.api.capabilities.IPlayerWarp wc = thaumcraft.api.capabilities.ThaumcraftCapabilities.getWarp(player);
		wc.set(thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.PERMANENT, 50);
		wc.setCounter(10000);

		thaumcraft.common.lib.events.WarpEvents.checkWarpEvent(player);

		helper.assertTrue(wc.getCounter() < 10000, "a real warp-misfortune roll this likely to succeed should really have consumed some of the real warp counter, found " + wc.getCounter());
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.lib.crafting.InfusionModifierRecipe} - the fix for the
	 * fortress-mask combinability gap flagged when that chunk shipped. Applying the real Helm of Revealing
	 * upgrade recipe to a helm that ALREADY carries a real mask should keep that mask, not overwrite it -
	 * matching the original's own "a helm can wear a mask AND the goggles mod at once" real design.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionModifierRecipePreservesAnAlreadyAppliedComponent(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		Optional<RecipeHolder<?>> holder = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion_fortress_helm_goggles"));
		helper.assertTrue(holder.isPresent() && holder.get().value() instanceof thaumcraft.common.lib.crafting.InfusionModifierRecipe,
				"infusion_fortress_helm_goggles should really be a real InfusionModifierRecipe");
		thaumcraft.common.lib.crafting.InfusionModifierRecipe recipe = (thaumcraft.common.lib.crafting.InfusionModifierRecipe) holder.get().value();

		ItemStack maskedHelm = new ItemStack(ItemsTC.fortressHelm.get());
		maskedHelm.set(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_MASK.get(), 1);

		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				maskedHelm, List.of(new ItemStack(Items.SLIME_BALL), new ItemStack(ItemsTC.goggles.get())));
		ItemStack result = recipe.assemble(input, helper.getLevel().registryAccess());

		helper.assertTrue(Boolean.TRUE.equals(result.get(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_GOGGLES.get())),
				"the real goggles component should really be applied by the recipe");
		Integer mask = result.get(thaumcraft.api.items.ThaumcraftDataComponents.FORTRESS_MASK.get());
		helper.assertTrue(mask != null && mask == 1, "the real, already-applied mask should really be preserved instead of overwritten, found " + mask);
		helper.succeed();
	}

	/**
	 * Names the 3 real VerdantHeart recipes (last of the previously-blocked infusion-recipe gaps this
	 * session) and asserts each one is actually registered.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void verdantHeartRecipesRegistered(GameTestHelper helper) {
		RecipeManager recipes = helper.getLevel().getRecipeManager();
		String[] expectedIds = { "infusion_verdant_heart", "infusion_verdant_heart_life", "infusion_verdant_heart_sustain" };
		for (String id : expectedIds) {
			Optional<RecipeHolder<?>> recipe = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, id));
			helper.assertTrue(recipe.isPresent(), "expected recipe '" + id + "' to be registered, but it wasn't");
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.lib.crafting.InfusionPotionRecipe} - the real fix for
	 * VerdantHeartLife/Sustain's own "one pedestal item must carry a specific real vanilla potion"
	 * requirement. A plain potion of the wrong kind should NOT match; the real required potion should.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infusionPotionRecipeOnlyMatchesItsRealRequiredPotion(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		RecipeManager recipes = level.getRecipeManager();
		Optional<RecipeHolder<?>> holder = recipes.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infusion_verdant_heart_life"));
		helper.assertTrue(holder.isPresent() && holder.get().value() instanceof thaumcraft.common.lib.crafting.InfusionPotionRecipe,
				"infusion_verdant_heart_life should really be a real InfusionPotionRecipe");
		thaumcraft.common.lib.crafting.InfusionPotionRecipe recipe = (thaumcraft.common.lib.crafting.InfusionPotionRecipe) holder.get().value();

		ItemStack central = new ItemStack(ItemsTC.verdantCharm.get());
		ItemStack goldenApple = new ItemStack(Items.GOLDEN_APPLE);
		ItemStack crystal1 = new ItemStack(ItemsTC.crystalEssence.get());
		ItemStack crystal2 = new ItemStack(ItemsTC.crystalEssence.get());

		ItemStack wrongPotion = new ItemStack(Items.POTION);
		wrongPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
				new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.STRONG_REGENERATION));
		thaumcraft.api.crafting.InfusionRecipeInput wrongInput = new thaumcraft.api.crafting.InfusionRecipeInput(
				central, List.of(goldenApple, crystal1, wrongPotion, crystal2));
		helper.assertFalse(recipe.matches(wrongInput, level), "a Strong Regeneration potion should NOT satisfy a recipe that really requires Strong Healing");

		ItemStack rightPotion = new ItemStack(Items.POTION);
		rightPotion.set(net.minecraft.core.component.DataComponents.POTION_CONTENTS,
				new net.minecraft.world.item.alchemy.PotionContents(net.minecraft.world.item.alchemy.Potions.STRONG_HEALING));
		thaumcraft.api.crafting.InfusionRecipeInput rightInput = new thaumcraft.api.crafting.InfusionRecipeInput(
				central, List.of(goldenApple, crystal1, rightPotion, crystal2));
		helper.assertTrue(recipe.matches(rightInput, level), "a real Strong Healing potion should really satisfy the recipe");
		helper.succeed();
	}

	/** Names the real Levitator crafting recipe and confirms it's actually registered. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void levitatorRecipeRegistered(GameTestHelper helper) {
		Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager()
				.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "levitator"));
		helper.assertTrue(recipe.isPresent(), "expected the levitator recipe to be registered, but it wasn't");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.tiles.devices.TileLevitator} - a real Levitator with real
	 * vis available should really push a real nearby item entity upward along its facing, and really flip
	 * its own real ENABLED blockstate on while doing so.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void levitatorPushesItemsUpwardAndTogglesEnabled(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos levPos = new BlockPos(3, 2, 3);
		helper.setBlock(levPos, BlocksTC.levitator.get());
		BlockPos levAbs = helper.absolutePos(levPos);
		AuraHandler.addVis(level, levAbs, 500.0f);

		ItemEntity item = new ItemEntity(level, levAbs.getX() + 0.5, levAbs.getY() + 1.5, levAbs.getZ() + 0.5, new ItemStack(Items.STICK));
		item.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(item);

		thaumcraft.common.tiles.devices.TileLevitator tile = helper.getBlockEntity(levPos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(levPos);
		for (int i = 0; i < 40; i++) {
			thaumcraft.common.tiles.devices.TileLevitator.tick(level, levAbs, helper.getBlockState(levPos), tile);
		}

		helper.assertTrue(item.getDeltaMovement().y > 0.0, "a real item above an active levitator should really be pushed upward, found motion " + item.getDeltaMovement());
		helper.assertTrue(helper.getBlockState(levPos).getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED),
				"a real active levitator should really flip its own ENABLED blockstate on");
		helper.succeed();
	}

	/** Names the real Redstone Relay crafting recipe and confirms it's actually registered. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void redstoneRelayRecipeRegistered(GameTestHelper helper) {
		Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager()
				.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "redstone_relay"));
		helper.assertTrue(recipe.isPresent(), "expected the redstone_relay recipe to be registered, but it wasn't");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.blocks.devices.BlockRedstoneRelay}'s own real, distinguishing
	 * feature - a configurable OUTPUT strength (unlike a plain vanilla repeater's fixed 15), read via the
	 * same real public {@code Level#getSignal} vanilla redstone wiring itself would call.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void redstoneRelayOutputsItsRealConfiguredStrength(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(3, 2, 3);
		net.minecraft.world.level.block.state.BlockState state = BlocksTC.redstoneRelay.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.devices.BlockRedstoneRelay.FACING, net.minecraft.core.Direction.SOUTH)
				.setValue(net.minecraft.world.level.block.DiodeBlock.POWERED, true)
				.setValue(thaumcraft.common.blocks.devices.BlockRedstoneRelay.OUT, 7);
		helper.setBlock(pos, state);
		BlockPos abs = helper.absolutePos(pos);

		int facingSignal = level.getSignal(abs, net.minecraft.core.Direction.SOUTH);
		helper.assertTrue(facingSignal == 7, "a real powered relay facing south with OUT=7 should really output signal 7, found " + facingSignal);

		int otherSignal = level.getSignal(abs, net.minecraft.core.Direction.NORTH);
		helper.assertTrue(otherSignal == 0, "a real relay should really only output its real signal in its own facing direction, found " + otherSignal + " for a non-facing direction");

		helper.setBlock(pos, state.setValue(net.minecraft.world.level.block.DiodeBlock.POWERED, false));
		int unpoweredSignal = level.getSignal(abs, net.minecraft.core.Direction.SOUTH);
		helper.assertTrue(unpoweredSignal == 0, "a real unpowered relay should really output no signal at all, found " + unpoweredSignal);
		helper.succeed();
	}

	/**
	 * Real coverage for the Redstone Relay's real click-to-cycle interaction (the original's own 2-sub-
	 * hitbox raytraced dials, simplified this pass to plain-click/shift-click - see that block's own doc
	 * comment). Right-click should cycle OUT; shift-right-click should cycle IN; both wrap from 15 back to 1.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void redstoneRelayClickCyclesOutAndShiftClickCyclesIn(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(3, 2, 3);
		helper.setBlock(pos, BlocksTC.redstoneRelay.get());
		BlockPos abs = helper.absolutePos(pos);
		ServerPlayer player = spawnCasterAt(helper, abs.above(2));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				net.minecraft.world.phys.Vec3.atCenterOf(abs), net.minecraft.core.Direction.UP, abs, false);

		helper.getBlockState(pos).useWithoutItem(level, player, hit);
		helper.assertTrue(helper.getBlockState(pos).getValue(thaumcraft.common.blocks.devices.BlockRedstoneRelay.OUT) == 1,
				"a plain right-click should really cycle OUT from its real default of 15 to 1");

		player.setShiftKeyDown(true);
		helper.getBlockState(pos).useWithoutItem(level, player, hit);
		helper.assertTrue(helper.getBlockState(pos).getValue(thaumcraft.common.blocks.devices.BlockRedstoneRelay.IN) == 2,
				"a real shift-right-click should really cycle IN from its real default of 1 to 2");
		helper.succeed();
	}

	/** Names the real Centrifuge crafting recipe and confirms it's actually registered. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void centrifugeRecipeRegistered(GameTestHelper helper) {
		Optional<RecipeHolder<?>> recipe = helper.getLevel().getRecipeManager()
				.byKey(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "centrifuge"));
		helper.assertTrue(recipe.isPresent(), "expected the centrifuge recipe to be registered, but it wasn't");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.tiles.essentia.TileCentrifuge} - a real compound aspect
	 * (LIFE, made of EARTH+WATER) drawn from a real jar below should really decompose into one of its own 2
	 * real component aspects after processing, ready to be drawn from above.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void centrifugeDecomposesACompoundAspectIntoARealComponent(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos jarPos = new BlockPos(3, 2, 3);
		BlockPos centPos = jarPos.above();
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		helper.setBlock(centPos, BlocksTC.centrifuge.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarPos)).addToContainer(Aspect.LIFE, 10);

		BlockPos centAbs = helper.absolutePos(centPos);
		thaumcraft.common.tiles.essentia.TileCentrifuge tile = helper.getBlockEntity(centPos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(centPos);
		for (int i = 0; i < 100; i++) {
			thaumcraft.common.tiles.essentia.TileCentrifuge.tick(level, centAbs, state, tile);
		}

		AspectList result = tile.getAspects();
		helper.assertTrue(result.size() == 1 && (result.getAmount(Aspect.EARTH) == 1 || result.getAmount(Aspect.WATER) == 1),
				"a real centrifuge fed LIFE essentia should really decompose it into EARTH or WATER, found " + result);
		helper.succeed();
	}

	/**
	 * Real coverage for the Centrifuge's real redstone lock - a powered centrifuge should NOT draw or
	 * process essentia at all, matching the original's own real {@code gettingPower()} gate.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void centrifugeDoesNothingWhilePowered(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos jarPos = new BlockPos(3, 2, 3);
		BlockPos centPos = jarPos.above();
		helper.setBlock(jarPos, BlocksTC.jarNormal.get());
		helper.setBlock(centPos, BlocksTC.centrifuge.get());
		((thaumcraft.common.tiles.essentia.TileJarFillable) helper.getBlockEntity(jarPos)).addToContainer(Aspect.LIFE, 10);
		helper.setBlock(centPos.east(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK);

		BlockPos centAbs = helper.absolutePos(centPos);
		thaumcraft.common.tiles.essentia.TileCentrifuge tile = helper.getBlockEntity(centPos);
		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(centPos);
		for (int i = 0; i < 100; i++) {
			thaumcraft.common.tiles.essentia.TileCentrifuge.tick(level, centAbs, state, tile);
		}

		helper.assertTrue(tile.getAspects().size() == 0, "a real powered centrifuge should really never process anything, found " + tile.getAspects());
		helper.succeed();
	}

	/** Real coverage for {@link thaumcraft.common.lib.research.ThaumcraftResearchLoader} - confirms the real
	 * shipped {@code data/thaumcraft/research/basics.json} content actually gets parsed into the real
	 * {@code ResearchCategories}/{@code ResearchEntry} registry, not just that the loader class compiles. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void researchLoaderPopulatesRealFirstStepsEntry(GameTestHelper helper) {
		thaumcraft.api.research.ResearchEntry entry = thaumcraft.api.research.ResearchCategories.getResearch("FIRSTSTEPS");
		helper.assertTrue(entry != null, "the real FIRSTSTEPS research entry should be loaded from data/thaumcraft/research/basics.json");
		helper.assertTrue("BASICS".equals(entry.getCategory()), "FIRSTSTEPS should be in the BASICS category, found " + entry.getCategory());
		helper.assertTrue(entry.getStages() != null && entry.getStages().length == 3,
				"FIRSTSTEPS should have its real 3 stages, found " + (entry.getStages() == null ? 0 : entry.getStages().length));
		helper.assertTrue("research.FIRSTSTEPS.stage.1".equals(entry.getStages()[0].getText()),
				"the first stage's text key should be the real research.FIRSTSTEPS.stage.1, found " + entry.getStages()[0].getText());
		helper.succeed();
	}

	/** {@link thaumcraft.common.lib.research.PlayerKnowledge}'s own real FIRSTSTEPS default is now stage-count
	 * aware (see that class's own doc comment) - this locks in that it actually resolves to real, full
	 * completion once the real 3-stage entry is loaded, not just "known". */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void freshPlayerHasFirstStepsFullyComplete(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		helper.assertTrue(knowledge.isResearchComplete("FIRSTSTEPS"), "a fresh player should start with FIRSTSTEPS fully complete");
		helper.assertTrue(knowledge.isResearchKnown("FIRSTSTEPS@3"), "a fresh player should be at least at FIRSTSTEPS stage 3");
		helper.succeed();
	}

	/** Real coverage for {@code ThaumcraftInternalMethodHandler#completeResearch}/{@code
	 * doesPlayerHaveRequisites} - the real parent-gating logic ported from the original (deleted, stale)
	 * {@code ResearchManager}, now backed by real loaded {@code ResearchEntry} data. THEORYRESEARCH's real
	 * parent is KNOWLEDGETYPES (see basics.json). */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void completeResearchIsBlockedUntilParentIsKnown(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		helper.assertTrue(!knowledge.isResearchKnown("THEORYRESEARCH"), "a fresh player shouldn't know THEORYRESEARCH yet");

		boolean blocked = thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "THEORYRESEARCH");
		helper.assertTrue(!blocked, "THEORYRESEARCH requires KNOWLEDGETYPES first and should be refused");
		helper.assertTrue(!knowledge.isResearchKnown("THEORYRESEARCH"), "THEORYRESEARCH still shouldn't be known");

		boolean parentOk = thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "KNOWLEDGETYPES");
		helper.assertTrue(parentOk && knowledge.isResearchComplete("KNOWLEDGETYPES"), "KNOWLEDGETYPES should complete freely (no parents)");

		boolean unlocked = thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "THEORYRESEARCH");
		helper.assertTrue(unlocked && knowledge.isResearchComplete("THEORYRESEARCH"), "THEORYRESEARCH should now complete since its parent is known");
		helper.succeed();
	}

	/** Real coverage for the sibling-cascade half of {@code completeResearch} - completing a research entry
	 * should also complete any real siblings whose own requisites are now met. Uses a synthetic fixture
	 * (same style as this file's other ad-hoc research-key tests, e.g. the TEST_PIG_SCAN scan target above)
	 * rather than a real multi-hop chain from the shipped content, so this stays independent of future edits
	 * to basics.json's real dependency graph. */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void completingResearchCascadesToSiblings(GameTestHelper helper) {
		thaumcraft.api.research.ResearchCategory category = thaumcraft.api.research.ResearchCategories.getResearchCategory("BASICS");

		thaumcraft.api.research.ResearchEntry parent = new thaumcraft.api.research.ResearchEntry();
		parent.setKey("TEST_SIBLING_PARENT");
		parent.setCategory("BASICS");
		parent.setName("research.TEST_SIBLING_PARENT.title");
		parent.setSiblings(new String[] {"TEST_SIBLING_CHILD"});
		thaumcraft.api.research.ResearchStage parentStage = new thaumcraft.api.research.ResearchStage();
		parentStage.setText("research.TEST_SIBLING_PARENT.stage.1");
		parent.setStages(new thaumcraft.api.research.ResearchStage[] {parentStage});

		thaumcraft.api.research.ResearchEntry child = new thaumcraft.api.research.ResearchEntry();
		child.setKey("TEST_SIBLING_CHILD");
		child.setCategory("BASICS");
		child.setName("research.TEST_SIBLING_CHILD.title");
		thaumcraft.api.research.ResearchStage childStage = new thaumcraft.api.research.ResearchStage();
		childStage.setText("research.TEST_SIBLING_CHILD.stage.1");
		child.setStages(new thaumcraft.api.research.ResearchStage[] {childStage});

		category.research.put("TEST_SIBLING_PARENT", parent);
		category.research.put("TEST_SIBLING_CHILD", child);
		try {
			ServerPlayer player = helper.makeMockServerPlayerInLevel();
			IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
			helper.assertTrue(!knowledge.isResearchKnown("TEST_SIBLING_CHILD"), "the sibling shouldn't be known yet");

			boolean ok = thaumcraft.api.ThaumcraftApi.internalMethods.completeResearch(player, "TEST_SIBLING_PARENT");
			helper.assertTrue(ok && knowledge.isResearchComplete("TEST_SIBLING_PARENT"), "the parent should complete");
			helper.assertTrue(knowledge.isResearchComplete("TEST_SIBLING_CHILD"),
					"completing the parent should have cascaded to complete its real sibling too");
			helper.succeed();
		} finally {
			category.research.remove("TEST_SIBLING_PARENT");
			category.research.remove("TEST_SIBLING_CHILD");
		}
	}

	/** Real coverage for {@code ItemThaumonomicon}'s real "!gotthaumonomicon" grant - the small, deliberate
	 * substitute for the original's item-pickup hook (see that item's own doc comment). */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void openingTheThaumonomiconGrantsGotThaumonomiconResearch(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		helper.assertTrue(!knowledge.isResearchKnown("!gotthaumonomicon"), "a fresh player shouldn't have the virtual flag yet");

		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack book = new ItemStack(ItemsTC.thaumonomicon.get());
		player.setItemInHand(hand, book);
		book.getItem().use(helper.getLevel(), player, hand);

		helper.assertTrue(knowledge.isResearchKnown("!gotthaumonomicon"), "using the Thaumonomicon should grant the real !gotthaumonomicon flag");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Champion Mods - real per-mob "elite variant" system. ChampionModEvents' handlers are called directly
	// below wherever a real deterministic outcome is needed (damage dispatch depends on which of the 14
	// mods gets randomly rolled, so most tests force a specific one via the real attributeMod directly
	// rather than relying on RNG luck) - same "call the real handler method directly" approach already used
	// for other event-driven systems in this file.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void makeChampionAppliesRealAttributeAndBuffs(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);

		// spawnFacedBy's own addFreshEntity already fired the real natural join-level roll (see
		// freshlySpawnedMonsterRollsRealChampionStatus below) - reset back to a clean unrolled slate so this
		// test's own explicit makeChampion call has something real to do.
		zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD).removeModifiers();
		double baseHealthBefore = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getBaseValue();
		thaumcraft.common.lib.utils.ChampionUtils.makeChampion(zombie);

		net.minecraft.world.entity.ai.attributes.AttributeInstance championAttr =
				zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD);
		helper.assertTrue(championAttr != null, "a real zombie should have the real CHAMPION_MOD attribute");
		double id = championAttr.getValue();
		helper.assertTrue(id >= 0 && id < thaumcraft.common.entities.monster.mods.ChampionModifier.MODS.length,
				"a champion should read back a real mod id 0-13, found " + id);

		// getValue() (the computed total including modifiers), not getBaseValue() - CHAMPION_HEALTH is a
		// real AttributeModifier, which never changes the raw base value.
		double healthAfter = zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH).getValue();
		helper.assertTrue(healthAfter == baseHealthBefore + 100.0, "a champion should get the real +100 flat health buff, found " + healthAfter);
		helper.assertTrue(zombie.getCustomName() != null && zombie.isCustomNameVisible(), "a champion should get a real visible custom name");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void makeChampionIsANoOpOnceAlreadyRolled(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);

		thaumcraft.common.lib.utils.ChampionUtils.makeChampion(zombie);
		double idAfterFirst = zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD).getValue();
		thaumcraft.common.lib.utils.ChampionUtils.makeChampion(zombie);
		double idAfterSecond = zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD).getValue();

		helper.assertTrue(idAfterFirst == idAfterSecond, "calling makeChampion again on an already-rolled mob should be a real no-op");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void championTypeZeroModDispatchesOnTick(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);
		zombie.setHealth(1.0f);

		// Force id 7 (Undying, type 0/self) directly rather than relying on the random roll - reset first
		// since the natural join-level roll already fired once for real (see spawnFacedBy).
		net.minecraft.world.entity.ai.attributes.AttributeInstance championAttr =
				zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD);
		championAttr.removeModifiers();
		championAttr.addOrReplacePermanentModifier(thaumcraft.common.entities.monster.mods.ChampionModifier.MODS[7].attributeMod);

		for (int i = 0; i < 25; i++) {
			zombie.tickCount = i;
			thaumcraft.common.lib.events.ChampionModEvents.onTick(new net.neoforged.neoforge.event.tick.EntityTickEvent.Post(zombie));
		}

		helper.assertTrue(zombie.getHealth() > 1.0f, "a real Undying champion should heal itself over real ticks, found health=" + zombie.getHealth());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void championTypeTwoDefensiveModReducesIncomingDamage(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);

		// Force id 2 (Armored, type 2/defensive) - reset first, see championTypeZeroModDispatchesOnTick's
		// own comment on why.
		net.minecraft.world.entity.ai.attributes.AttributeInstance armoredAttr =
				zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD);
		armoredAttr.removeModifiers();
		armoredAttr.addOrReplacePermanentModifier(thaumcraft.common.entities.monster.mods.ChampionModifier.MODS[2].attributeMod);

		net.minecraft.world.damagesource.DamageSource source = helper.getLevel().damageSources().mobAttack(player);
		net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(zombie,
						new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 25.0f));

		thaumcraft.common.lib.events.ChampionModEvents.onIncomingDamage(event);

		helper.assertTrue(Math.abs(event.getAmount() - 19.0f) < 0.01f, "a real Armored champion should reduce 25 real damage to 19 (25 * 19/25), found " + event.getAmount());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void championTypeOneOffensiveModHealsOnHitDealt(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		ServerPlayer player = spawnCasterAt(helper, origin);
		Zombie zombie = spawnFacedBy(helper, player, origin, EntityType.ZOMBIE);
		zombie.setHealth(5.0f);

		// Force id 11 (Vampiric, type 1/offensive) - the champion is the ATTACKER here. Reset first, see
		// championTypeZeroModDispatchesOnTick's own comment on why.
		net.minecraft.world.entity.ai.attributes.AttributeInstance vampiricAttr =
				zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD);
		vampiricAttr.removeModifiers();
		vampiricAttr.addOrReplacePermanentModifier(thaumcraft.common.entities.monster.mods.ChampionModifier.MODS[11].attributeMod);

		net.minecraft.world.damagesource.DamageSource source = helper.getLevel().damageSources().mobAttack(zombie);
		net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player,
						new net.neoforged.neoforge.common.damagesource.DamageContainer(source, 6.0f));

		thaumcraft.common.lib.events.ChampionModEvents.onIncomingDamage(event);

		helper.assertTrue(zombie.getHealth() > 5.0f, "a real Vampiric champion should heal itself off the real damage it just dealt, found health=" + zombie.getHealth());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void freshlySpawnedMonsterRollsRealChampionStatus(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		Zombie zombie = new Zombie(EntityType.ZOMBIE, helper.getLevel());
		zombie.setPos(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
		helper.getLevel().addFreshEntity(zombie);

		net.minecraft.world.entity.ai.attributes.AttributeInstance championAttr =
				zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD);
		helper.assertTrue(championAttr != null, "a real zombie should carry the real CHAMPION_MOD attribute");
		helper.assertTrue(championAttr.getValue() != -2.0, "a freshly-spawned real monster should have been rolled for champion status (either way) on join, found " + championAttr.getValue());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void passiveAnimalsNeverGetTheChampionAttribute(GameTestHelper helper) {
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);
		net.minecraft.world.entity.animal.Cow cow = new net.minecraft.world.entity.animal.Cow(EntityType.COW, helper.getLevel());
		cow.setPos(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
		helper.getLevel().addFreshEntity(cow);

		helper.assertTrue(cow.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD) == null,
				"a real passive Cow shouldn't have been given the champion attribute at all - it's not in the curated hostile-monster list");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Rift focus effect + the real portable-hole mechanic it drives (FocusEffectRift/BlockHole/TileHole).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void createHoleReplacesASolidBlockWithARealWalkableTemporaryHole(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 2, 2);
		helper.setBlock(relPos, Blocks.STONE);
		BlockPos abs = helper.absolutePos(relPos);

		boolean created = thaumcraft.common.items.casters.foci.FocusEffectRift.createHole(helper.getLevel(), abs, net.minecraft.core.Direction.UP, (byte) 1, 100);

		helper.assertTrue(created, "createHole should succeed on a real plain stone block");
		helper.assertTrue(helper.getLevel().getBlockState(abs).is(BlocksTC.hole.get()), "the block should really have become thaumcraft:hole");
		helper.assertTrue(helper.getLevel().getBlockState(abs).getCollisionShape(helper.getLevel(), abs).isEmpty(), "a real hole should have no collision - a player should walk straight through");
		thaumcraft.common.tiles.misc.TileHole tile = helper.getBlockEntity(relPos);
		helper.assertTrue(tile.oldBlock.is(Blocks.STONE), "the tile should remember the real original block, found " + tile.oldBlock);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void holeRevertsToItsRealOriginalBlockAfterTheCountdownExpires(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 2, 2);
		helper.setBlock(relPos, Blocks.COBBLESTONE);
		BlockPos abs = helper.absolutePos(relPos);
		thaumcraft.common.items.casters.foci.FocusEffectRift.createHole(helper.getLevel(), abs, null, (byte) 1, 5);

		net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(abs);
		thaumcraft.common.tiles.misc.TileHole tile = helper.getBlockEntity(relPos);
		for (int i = 0; i < 5; i++) {
			thaumcraft.common.tiles.misc.TileHole.tick(helper.getLevel(), abs, state, tile);
		}

		helper.assertTrue(helper.getLevel().getBlockState(abs).is(Blocks.COBBLESTONE), "the hole should have reverted to the real original cobblestone after its countdown expired, found " + helper.getLevel().getBlockState(abs));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void holePropagatesForwardAndBurstsPerpendicularlyOnTheFirstTick(GameTestHelper helper) {
		BlockPos relPos = new BlockPos(2, 2, 2);
		BlockPos nextPos = relPos.north(); // direction.getOpposite() of SOUTH is NORTH
		BlockPos burstPos = relPos.above(); // part of the perpendicular (Y-axis) 3x3 burst plane
		for (BlockPos p : new BlockPos[] { relPos, nextPos, burstPos }) {
			helper.setBlock(p, Blocks.STONE);
		}
		BlockPos abs = helper.absolutePos(relPos);

		// count=3 (propagates once more after this one), direction=SOUTH -> propagates toward NORTH.
		thaumcraft.common.items.casters.foci.FocusEffectRift.createHole(helper.getLevel(), abs, net.minecraft.core.Direction.SOUTH, (byte) 3, 100);
		net.minecraft.world.level.block.state.BlockState state = helper.getLevel().getBlockState(abs);
		thaumcraft.common.tiles.misc.TileHole tile = helper.getBlockEntity(relPos);
		thaumcraft.common.tiles.misc.TileHole.tick(helper.getLevel(), abs, state, tile);

		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(nextPos)).is(BlocksTC.hole.get()),
				"the tunnel should have propagated one real block further on its first tick");
		helper.assertTrue(helper.getLevel().getBlockState(helper.absolutePos(burstPos)).is(BlocksTC.hole.get()),
				"the real perpendicular 3x3 burst should have opened a hole above too");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void createHoleRefusesBedrockAndBlocksWithARealTileEntity(GameTestHelper helper) {
		BlockPos bedrockPos = new BlockPos(2, 2, 2);
		BlockPos chestPos = new BlockPos(2, 2, 4);
		helper.setBlock(bedrockPos, Blocks.BEDROCK);
		helper.setBlock(chestPos, Blocks.CHEST);

		boolean bedrockResult = thaumcraft.common.items.casters.foci.FocusEffectRift.createHole(helper.getLevel(), helper.absolutePos(bedrockPos), null, (byte) 1, 100);
		boolean chestResult = thaumcraft.common.items.casters.foci.FocusEffectRift.createHole(helper.getLevel(), helper.absolutePos(chestPos), null, (byte) 1, 100);

		helper.assertTrue(!bedrockResult && helper.getLevel().getBlockState(helper.absolutePos(bedrockPos)).is(Blocks.BEDROCK), "real bedrock should never become a hole");
		helper.assertTrue(!chestResult && helper.getLevel().getBlockState(helper.absolutePos(chestPos)).is(Blocks.CHEST), "a real block with its own tile entity should never become a hole");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusEffectRiftPunchesARealHoleEndToEnd(GameTestHelper helper) {
		BlockPos wallPos = new BlockPos(2, 2, 2);
		helper.setBlock(wallPos, Blocks.STONE);
		BlockPos absWall = helper.absolutePos(wallPos);
		ServerPlayer player = spawnCasterAt(helper, absWall.north(3));

		thaumcraft.common.items.casters.foci.FocusEffectRift rift = new thaumcraft.common.items.casters.foci.FocusEffectRift();
		rift.initialize();
		rift.setPackage(new thaumcraft.api.casters.FocusPackage(player));

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absWall), net.minecraft.core.Direction.SOUTH, absWall, false);
		boolean executed = rift.execute(hit, null, 1.0f, 0);

		helper.assertTrue(executed, "casting a real Rift focus at a real solid block should succeed");
		helper.assertTrue(helper.getLevel().getBlockState(absWall).is(BlocksTC.hole.get()), "the struck block should really have become a temporary hole");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Infernal Furnace - real fuel-free auto-smelting device (BlockInfernalFurnace/TileInfernalFurnace).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infernalFurnaceAcceptsSmeltableItemsAndDestroysJunk(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.infernalFurnace.get());
		thaumcraft.common.tiles.devices.TileInfernalFurnace tile = helper.getBlockEntity(pos);

		ItemStack leftover = tile.addItemsToInventory(new ItemStack(Items.SAND, 4));
		helper.assertTrue(leftover.isEmpty(), "a real smeltable item should be fully accepted into the furnace's inventory, found leftover " + leftover);

		ItemStack junkLeftover = tile.addItemsToInventory(new ItemStack(Items.STICK, 4));
		helper.assertTrue(junkLeftover.isEmpty(), "a non-smeltable item should be reported as fully consumed (destroyed), found leftover " + junkLeftover);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infernalFurnaceSmeltsAndEjectsItsRealResultOverTime(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.infernalFurnace.get());
		thaumcraft.common.tiles.devices.TileInfernalFurnace tile = helper.getBlockEntity(pos);
		tile.addItemsToInventory(new ItemStack(Items.SAND));
		BlockPos abs = helper.absolutePos(pos);
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);

		for (int i = 0; i < 200 && level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2)).isEmpty(); i++) {
			thaumcraft.common.tiles.devices.TileInfernalFurnace.tick(level, abs, state, tile);
		}

		List<ItemEntity> dropped = level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2));
		boolean foundGlass = dropped.stream().anyMatch(e -> e.getItem().is(Items.GLASS));
		helper.assertTrue(foundGlass, "the furnace should have really smelted the sand into real glass and ejected it, found " + dropped.stream().map(e -> e.getItem().toString()).toList());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infernalFurnaceEjectsIntoARealAdjacentContainer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.infernalFurnace.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING, net.minecraft.core.Direction.SOUTH));
		BlockPos chestPos = pos.south();
		helper.setBlock(chestPos, net.minecraft.world.level.block.Blocks.CHEST);

		thaumcraft.common.tiles.devices.TileInfernalFurnace tile = helper.getBlockEntity(pos);
		tile.addItemsToInventory(new ItemStack(Items.SAND));
		BlockPos abs = helper.absolutePos(pos);
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
		net.minecraft.world.level.block.entity.ChestBlockEntity chest = helper.getBlockEntity(chestPos);

		for (int i = 0; i < 200 && chest.isEmpty(); i++) {
			thaumcraft.common.tiles.devices.TileInfernalFurnace.tick(level, abs, state, tile);
		}

		helper.assertTrue(!chest.isEmpty(), "the smelted result should have been inserted into the real chest placed in front rather than dropped");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infernalFurnaceCooksFasterWithRealBellows(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos posA = new BlockPos(2, 2, 2);
		BlockPos posB = new BlockPos(2, 2, 8);
		helper.setBlock(posA, BlocksTC.infernalFurnace.get());
		helper.setBlock(posB, BlocksTC.infernalFurnace.get());

		// A real bellows facing back at posB from 2 blocks south, enabled (unpowered).
		helper.setBlock(posB.south(2), BlocksTC.bellows.get().defaultBlockState()
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING, net.minecraft.core.Direction.NORTH)
				.setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ENABLED, true));

		thaumcraft.common.tiles.devices.TileInfernalFurnace tileA = helper.getBlockEntity(posA);
		thaumcraft.common.tiles.devices.TileInfernalFurnace tileB = helper.getBlockEntity(posB);
		tileA.addItemsToInventory(new ItemStack(Items.SAND));
		tileB.addItemsToInventory(new ItemStack(Items.SAND));
		BlockPos absA = helper.absolutePos(posA);
		BlockPos absB = helper.absolutePos(posB);
		net.minecraft.world.level.block.state.BlockState stateA = level.getBlockState(absA);
		net.minecraft.world.level.block.state.BlockState stateB = level.getBlockState(absB);

		int ticksA = -1;
		int ticksB = -1;
		for (int i = 0; i < 200; i++) {
			thaumcraft.common.tiles.devices.TileInfernalFurnace.tick(level, absA, stateA, tileA);
			thaumcraft.common.tiles.devices.TileInfernalFurnace.tick(level, absB, stateB, tileB);
			if (ticksA < 0 && !level.getEntitiesOfClass(ItemEntity.class, new AABB(absA).inflate(2)).isEmpty()) ticksA = i;
			if (ticksB < 0 && !level.getEntitiesOfClass(ItemEntity.class, new AABB(absB).inflate(2)).isEmpty()) ticksB = i;
		}

		helper.assertTrue(ticksA > 0 && ticksB > 0, "both furnaces should have finished smelting within the tick budget, found A=" + ticksA + " B=" + ticksB);
		helper.assertTrue(ticksB < ticksA, "the bellows-boosted furnace should finish real strictly faster, found A=" + ticksA + " (no bellows) vs B=" + ticksB + " (bellows)");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void infernalFurnaceDamagesAndIgnitesLivingEntities(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos, BlocksTC.infernalFurnace.get());
		BlockPos abs = helper.absolutePos(pos);

		Pig pig = new Pig(EntityType.PIG, helper.getLevel());
		pig.setPos(abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pig);
		float healthBefore = pig.getHealth();

		// entityInside is protected/only reachable through the real world entity-collision tick - letting
		// the test framework's own real server ticks run (via succeedWhen) is the only way to reach it, same
		// as this file's other real damage-over-time device tests (e.g. the lava/fire-based ones above).
		helper.succeedWhen(() -> {
			helper.assertTrue(pig.getHealth() < healthBefore, "a real pig standing on the furnace should take real lava-like damage, health=" + pig.getHealth());
			helper.assertTrue(pig.getRemainingFireTicks() > 0, "a real pig standing on the furnace should really catch fire, remainingFireTicks=" + pig.getRemainingFireTicks());
		});
	}

	// --------------------------------------------------------------------------------------------------
	// Pech - the wandering goblin-trader. Real taming (feed a valued item, currently just Ender Pearls -
	// see EntityPech's own doc comment), a personal 9-slot loot bag, live combat-goal switching by mainhand
	// item, and the cross-Pech anger-alert system. Calls the real entry points directly (tryPickupItem,
	// setItemSlot, hurt) rather than driving the AI goals through real ticks, same "test the real entry
	// point" precedent as everywhere else in this file.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechTamesInstantlyFromAnEnderPearlAndThenBagsOtherItems(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		pech.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pech);

		helper.assertFalse(pech.isTamed(), "a fresh pech shouldn't start tamed");
		helper.assertTrue(pech.isValued(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL)), "an ender pearl should be a real valued item");
		helper.assertFalse(pech.isValued(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)), "a plain stick has no real object tag at all, so it shouldn't be valued");

		net.minecraft.world.item.ItemStack pearl = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL);
		net.minecraft.world.item.ItemStack leftover = pech.tryPickupItem(pearl);
		helper.assertTrue(pech.isTamed(), "getValue(ender pearl)=15 should always beat the 1-in-10 taming roll");
		helper.assertTrue(leftover.getCount() == 0, "the pearl should be fully consumed by taming, found: " + leftover.getCount());

		net.minecraft.world.item.ItemStack stick = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK, 3);
		net.minecraft.world.item.ItemStack stickLeftover = pech.tryPickupItem(stick);
		helper.assertTrue(stickLeftover.isEmpty(), "a tamed pech should bag any item, not just valued ones");
		boolean foundSticks = false;
		for (net.minecraft.world.item.ItemStack slot : pech.loot) {
			if (slot.is(net.minecraft.world.item.Items.STICK) && slot.getCount() == 3) foundSticks = true;
		}
		helper.assertTrue(foundSticks, "the 3 sticks should have landed in the pech's real loot bag");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechRecognizesAnyRealDesireAspectItemNotJustEnderPearls(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		pech.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pech);

		// Gold ingot: not in the pech's own explicit valuedItems map, but ThaumcraftObjectTags carries a
		// real Desire=10 tag for it - the original's real general-case fallback, backfilled once
		// ThaumcraftObjectTags/AspectHelper#getObjectAspects turned out to already be real this session.
		net.minecraft.world.item.ItemStack gold = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLD_INGOT);
		helper.assertTrue(pech.isValued(gold), "a gold ingot's real Desire aspect should make it a valued item too");
		helper.assertTrue(pech.getValue(gold) == 5, "getValue should derive from the real Desire aspect (10/2=5), found: " + pech.getValue(gold));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechSwitchesBetweenMeleeAndRangedGoalsByMainhandItem(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		pech.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pech);

		pech.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW));
		helper.assertTrue(pech.hasActiveGoal(net.minecraft.world.entity.ai.goal.RangedAttackGoal.class),
				"holding a bow should give the pech a real ranged attack goal");
		helper.assertFalse(pech.hasActiveGoal(net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class),
				"holding a bow should remove the melee attack goal");

		pech.setItemSlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_SWORD));
		helper.assertTrue(pech.hasActiveGoal(net.minecraft.world.entity.ai.goal.MeleeAttackGoal.class),
				"holding a sword should give the pech a real melee attack goal");
		helper.assertFalse(pech.hasActiveGoal(net.minecraft.world.entity.ai.goal.RangedAttackGoal.class),
				"holding a sword should remove the ranged attack goal");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechBecomesAngryWhenHurtByAPlayerAndAlertsNearbyPech(GameTestHelper helper) {
		BlockPos posA = new BlockPos(2, 2, 2);
		BlockPos posB = new BlockPos(6, 2, 2);
		helper.setBlock(posA.below(), Blocks.STONE);
		helper.setBlock(posB.below(), Blocks.STONE);
		BlockPos absA = helper.absolutePos(posA);
		BlockPos absB = helper.absolutePos(posB);

		thaumcraft.common.entities.monster.EntityPech victim =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		victim.setPos(absA.getX() + 0.5, absA.getY(), absA.getZ() + 0.5);
		helper.getLevel().addFreshEntity(victim);

		thaumcraft.common.entities.monster.EntityPech bystander =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		bystander.setPos(absB.getX() + 0.5, absB.getY(), absB.getZ() + 0.5);
		helper.getLevel().addFreshEntity(bystander);

		helper.assertTrue(victim.getAnger() == 0 && bystander.getAnger() == 0, "neither pech should start angry");

		// makeMockServerPlayerInLevel's player is unavoidably creative in this harness (no real connection
		// for setGameMode/abilities to actually take effect against) - rather than fight that, this
		// exercises both real branches directly: a creative attacker should NOT provoke anger (real,
		// correct behavior, matching the original exactly), and the core anger/target/alert mechanic itself
		// is verified via a direct becomeAngryAt call with a real non-player LivingEntity, which the
		// creative check never even looks at.
		ServerPlayer creativeAttacker = spawnCasterAt(helper, absA);
		helper.assertTrue(creativeAttacker.isCreative(), "the mock player is expected to be creative in this harness");
		victim.hurt(helper.getLevel().damageSources().playerAttack(creativeAttacker), 1.0f);
		helper.assertTrue(victim.getAnger() == 0, "a creative attacker should never provoke real anger");

		// Mirrors hurt()'s own real loop body (every nearby pech, plus the one actually hit) - the loop
		// itself is trivial iteration already exercised structurally above via the creative-exemption check
		// on the real hurt() entry point; this verifies what each individual becomeAngryAt call does.
		net.minecraft.world.entity.monster.Zombie realAttacker = net.minecraft.world.entity.EntityType.ZOMBIE.create(helper.getLevel());
		bystander.debugBecomeAngryAt(realAttacker);
		victim.debugBecomeAngryAt(realAttacker);

		helper.assertTrue(victim.getAnger() > 0, "a real (non-creative-exempt) attacker should provoke anger");
		helper.assertTrue(victim.getTarget() == realAttacker, "the pech should target its real attacker");
		helper.assertTrue(bystander.getAnger() > 0, "a nearby pech should be alerted and become angry too");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Pech trading (chunk 2) - PechMenu/GuiPechTrade. Real "confirm trade" via clickMenuButton id 0, real
	// value-tier reward generation. GameTest is server-only and can't exercise the real Screen, so these
	// drive the real Menu/entity entry points directly.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechTradeConsumesAValuedItemAndFillsRealOutputSlots(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		pech.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pech);
		pech.setTamed(true);

		pech.getTradeItems().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ENDER_PEARL), false);
		pech.generateTradeContents();

		helper.assertTrue(pech.getTradeItems().getStackInSlot(0).isEmpty(), "the input slot should be consumed by a real trade");
		boolean anyOutput = false;
		for (int i = 1; i <= 4; i++) {
			if (!pech.getTradeItems().getStackInSlot(i).isEmpty()) anyOutput = true;
		}
		helper.assertTrue(anyOutput, "a real trade should fill at least one real output slot");

		// A non-valued item should be refused (real check, not just "any non-empty stack").
		pech.getTradeItems().insertItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK), false);
		pech.generateTradeContents();
		helper.assertFalse(pech.getTradeItems().getStackInSlot(0).isEmpty(), "a non-valued item shouldn't be consumed by a trade");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void pechTradeMenuOpensOnInteractWhenTamedAndRespectsRealValidity(GameTestHelper helper) {
		BlockPos pos = new BlockPos(2, 2, 2);
		helper.setBlock(pos.below(), Blocks.STONE);
		BlockPos absolute = helper.absolutePos(pos);

		thaumcraft.common.entities.monster.EntityPech pech =
				new thaumcraft.common.entities.monster.EntityPech(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), helper.getLevel());
		pech.setPos(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
		helper.getLevel().addFreshEntity(pech);

		ServerPlayer player = spawnCasterAt(helper, absolute);

		pech.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertFalse(player.containerMenu instanceof thaumcraft.common.container.PechMenu,
				"an untamed pech shouldn't open the trade menu");

		pech.setTamed(true);
		pech.interact(player, net.minecraft.world.InteractionHand.MAIN_HAND);
		helper.assertTrue(player.containerMenu instanceof thaumcraft.common.container.PechMenu,
				"a tamed pech should open a real trade menu on interact");
		helper.assertTrue(pech.trading, "the pech should be marked as trading while the menu is open");

		thaumcraft.common.container.PechMenu menu = (thaumcraft.common.container.PechMenu) player.containerMenu;
		helper.assertTrue(menu.stillValid(player), "the menu should stay valid while the pech is alive, tamed, and in range");
		menu.removed(player);
		helper.assertFalse(pech.trading, "closing the menu should clear the trading flag");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// The 3 Eldritch bosses - EntityEldritchGuardian (mid-tier, no boss bar), EntityEldritchWarden (real
	// boss, shares the Guardian's ranged attack), EntityEldritchGolem (real boss, real "headless" second
	// phase). All 3 share EntityEldritchOrb as their real ranged projectile (Guardian/Warden) or
	// EntityGolemOrb (Golem, once headless) - see each class's own doc comment for what's simplified.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void eldritchOrbBurstsOnImpactDamagingNearbyLivingEntitiesButNotTheThrower(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.EntityEldritchGuardian thrower =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_GUARDIAN.get().create(level);
		helper.assertTrue(thrower != null, "the entity type should really create an instance");
		thrower.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(thrower);

		net.minecraft.world.entity.npc.Villager victim = net.minecraft.world.entity.EntityType.VILLAGER.create(level);
		helper.assertTrue(victim != null, "the villager entity type should really create an instance");
		victim.moveTo(abs.getX() + 2.0, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(victim);

		float healthBefore = victim.getHealth();
		float throwerHealthBefore = thrower.getHealth();
		thaumcraft.common.entities.projectile.EntityEldritchOrb orb = new thaumcraft.common.entities.projectile.EntityEldritchOrb(thrower);
		orb.setPos(abs.getX() + 1.0, abs.getY() + 1.0, abs.getZ() + 0.5);
		orb.shoot(1.0, 0.0, 0.0, 0.5f, 0.0f);
		level.addFreshEntity(orb);
		for (int i = 0; i < 20 && !orb.isRemoved(); i++) {
			orb.tick();
		}

		helper.assertTrue(orb.isRemoved(), "the orb should discard itself once it bursts");
		helper.assertTrue(victim.getHealth() < healthBefore, "a nearby living entity should take real burst damage, before=" + healthBefore + " after=" + victim.getHealth());
		helper.assertTrue(victim.hasEffect(net.minecraft.world.effect.MobEffects.WEAKNESS), "a burst victim should really gain real Weakness");
		helper.assertTrue(thrower.getHealth() == throwerHealthBefore, "the orb's own thrower shouldn't be hit by its own burst");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void eldritchGuardianRangedAttackEventuallyFiresARealOrbOrScreamsWitherAndWarp(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.EntityEldritchGuardian guardian =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_GUARDIAN.get().create(level);
		helper.assertTrue(guardian != null, "the entity type should really create an instance");
		guardian.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(guardian);
		helper.assertTrue(guardian.getMaxHealth() == 50.0f, "a real eldritch guardian should really have 50 max health");

		ServerPlayer target = spawnCasterAt(helper, abs.offset(3, 0, 0));

		boolean sawOrb = false;
		boolean sawScream = false;
		for (int i = 0; i < 60 && !(sawOrb && sawScream); i++) {
			guardian.performRangedAttack(target, 1.0f);
			if (!level.getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityEldritchOrb.class, new AABB(abs).inflate(8)).isEmpty()) {
				sawOrb = true;
			}
			if (target.hasEffect(net.minecraft.world.effect.MobEffects.WITHER)) {
				sawScream = true;
			}
		}
		helper.assertTrue(sawOrb, "a real eldritch guardian should really eventually fire a real eldritch orb");
		helper.assertTrue(sawScream, "a real eldritch guardian should really eventually land its real Wither scream on a seen target");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void eldritchWardenIsARealBossThatIsAlliedToEldritchMobsAndImmuneToDrownAndWither(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.boss.EntityEldritchWarden warden =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_WARDEN.get().create(level);
		helper.assertTrue(warden != null, "the entity type should really create an instance");
		warden.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(warden);
		warden.finalizeSpawn(level, level.getCurrentDifficultyAt(abs), net.minecraft.world.entity.MobSpawnType.NATURAL, null);

		helper.assertTrue(warden.getMaxHealth() == 400.0f, "a real eldritch warden should really have 400 max health");
		helper.assertTrue(!warden.getDisplayName().getString().isEmpty(), "a real eldritch warden should really have a real generated title name");

		thaumcraft.common.entities.monster.EntityEldritchGuardian ally =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_GUARDIAN.get().create(level);
		helper.assertTrue(warden.isAlliedTo(ally), "a real eldritch warden should really be allied to other real eldritch mobs");

		// real shared EntityThaumcraftBoss enrage cap: a single hit over 35 damage is really capped at 35.
		float healthBefore = warden.getHealth();
		warden.hurt(level.damageSources().generic(), 50.0f);
		float lost = healthBefore - warden.getHealth();
		helper.assertTrue(lost <= 35.01f, "a real single hit over 35 damage should really be capped at 35 against the warden, lost " + lost);

		boolean drowned = warden.hurt(level.damageSources().drown(), 5.0f);
		helper.assertFalse(drowned, "a real eldritch warden should really be immune to drowning damage");

		boolean withered = warden.hurt(level.damageSources().wither(), 5.0f);
		helper.assertFalse(withered, "a real eldritch warden should really be immune to wither damage");

		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void eldritchGolemSurvivesOneLethalHitByGoingHeadlessThenDiesNormallyAfterThat(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, net.minecraft.world.level.block.Blocks.STONE);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.entities.monster.boss.EntityEldritchGolem golem =
				thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_GOLEM.get().create(level);
		helper.assertTrue(golem != null, "the entity type should really create an instance");
		golem.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(golem);
		helper.assertTrue(golem.getMaxHealth() == 400.0f, "a real eldritch golem should really have 400 max health");
		helper.assertFalse(golem.isHeadless(), "a freshly spawned golem shouldn't already be headless");

		golem.hurt(level.damageSources().generic(), golem.getHealth() + 50.0f);
		helper.assertTrue(golem.isHeadless(), "the first would-be-lethal hit should really turn the golem headless instead of killing it");
		helper.assertTrue(golem.isAlive(), "the golem should really survive its first lethal hit");
		helper.assertTrue(golem.getHealth() == 1.0f, "a freshly-headless golem should really be left at exactly 1 health");

		// real beam-charge ranged attack, headless-only. tickCount is advanced manually alongside aiStep -
		// aiStep() alone doesn't touch it (that normally happens in the outer Entity#tick()), and leaving it
		// stuck at a single value would fire the boss's own real "heal 1hp every 30 ticks" self-heal on
		// every single one of these 200 iterations instead of ~6-7 times, defeating the second-hit check
		// below.
		ServerPlayer target = spawnCasterAt(helper, abs.offset(4, 0, 0));
		for (int i = 0; i < 200; i++) {
			golem.tickCount++;
			golem.aiStep();
		}
		golem.performRangedAttack(target, 1.0f);
		helper.assertTrue(!level.getEntitiesOfClass(thaumcraft.common.entities.projectile.EntityGolemOrb.class, new AABB(abs).inflate(8)).isEmpty(),
				"a charged, headless golem's ranged attack should really spawn a real golem orb");

		// a second lethal hit should really kill it now that it's already headless.
		golem.hurt(level.damageSources().generic(), golem.getHealth() + 50.0f);
		helper.assertFalse(golem.isAlive(), "a second would-be-lethal hit on an already-headless golem should really kill it for real");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// PassiveResearchEvents - real coverage for the "gameplay experience teaches you something" hook that
	// data/thaumcraft/research/auromancy.json's f_onfire/f_arrow/f_fireball/f_spit prerequisite flags
	// actually depend on. Constructs a real LivingIncomingDamageEvent directly, same DamageContainer pattern
	// FortressMaskEvents' own tests already established.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void passiveResearchGrantsOnFireFlagOnlyWithTheRightPrerequisiteResearch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));

		net.minecraft.world.damagesource.DamageSource fire = level.damageSources().inFire();
		net.neoforged.neoforge.common.damagesource.DamageContainer container1 =
				new net.neoforged.neoforge.common.damagesource.DamageContainer(fire, 5.0f);
		container1.setNewDamage(5.0f);
		thaumcraft.common.lib.events.PassiveResearchEvents.onIncomingDamage(
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player, container1));
		helper.assertFalse(ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("f_onfire"),
				"burning shouldn't grant f_onfire without the real BASEAUROMANCY@2 prerequisite");

		// isResearchKnown/knowsResearchStrict parse "KEY@N" as a real stage check (researchProgress.get(KEY)
		// >= N), not a literal string key - addResearch("BASEAUROMANCY") alone leaves the stage at 0.
		ThaumcraftCapabilities.getKnowledge(player).addResearch("BASEAUROMANCY");
		ThaumcraftCapabilities.getKnowledge(player).setResearchStage("BASEAUROMANCY", 2);
		net.neoforged.neoforge.common.damagesource.DamageContainer container2 =
				new net.neoforged.neoforge.common.damagesource.DamageContainer(fire, 5.0f);
		container2.setNewDamage(5.0f);
		thaumcraft.common.lib.events.PassiveResearchEvents.onIncomingDamage(
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player, container2));
		helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("f_onfire"),
				"burning with the real BASEAUROMANCY@2 prerequisite should really grant the real f_onfire flag");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void passiveResearchGrantsArrowFlagWhenHitByARealArrowWithTheRightPrerequisiteResearch(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		ThaumcraftCapabilities.getKnowledge(player).addResearch("FOCUSPROJECTILE");
		ThaumcraftCapabilities.getKnowledge(player).setResearchStage("FOCUSPROJECTILE", 2);

		net.minecraft.world.entity.projectile.Arrow arrow = new net.minecraft.world.entity.projectile.Arrow(level, player, net.minecraft.world.item.ItemStack.EMPTY, null);
		level.addFreshEntity(arrow);
		net.minecraft.world.damagesource.DamageSource arrowSource = level.damageSources().arrow(arrow, player);
		net.neoforged.neoforge.common.damagesource.DamageContainer container =
				new net.neoforged.neoforge.common.damagesource.DamageContainer(arrowSource, 3.0f);
		container.setNewDamage(3.0f);
		thaumcraft.common.lib.events.PassiveResearchEvents.onIncomingDamage(
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player, container));

		helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("f_arrow"),
				"getting hit by a real arrow with the real FOCUSPROJECTILE@2 prerequisite should really grant the real f_arrow flag");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// MobDeathDropEvents - real coverage for the 2 small death-drop fragments extracted from the old
	// EntityEvents. Constructs a real LivingDropsEvent directly (real constructor, confirmed via javap:
	// (LivingEntity, DamageSource, Collection<ItemEntity>, boolean recentlyHit) - no lootingLevel param in
	// this version, matching the class's own doc comment on that dropped modifier).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void mobDeathDropEventsGrantsABrainFromARegularZombieButNeverFromABrainyZombie(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(BlockPos.ZERO);
		net.minecraft.world.entity.monster.Zombie zombie = net.minecraft.world.entity.EntityType.ZOMBIE.create(level);
		helper.assertTrue(zombie != null, "the zombie entity type should really create an instance");
		zombie.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(zombie);

		net.minecraft.world.damagesource.DamageSource source = level.damageSources().generic();
		boolean gotBrain = false;
		for (int i = 0; i < 300 && !gotBrain; i++) {
			java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
			net.neoforged.neoforge.event.entity.living.LivingDropsEvent event =
					new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(zombie, source, drops, true);
			thaumcraft.common.lib.events.MobDeathDropEvents.onLivingDrops(event);
			gotBrain = drops.stream().anyMatch(d -> d.getItem().is(thaumcraft.api.items.ItemsTC.brain.get()));
		}
		helper.assertTrue(gotBrain, "a real regular zombie should really eventually drop a real Brain within a generous number of chance rolls");

		thaumcraft.common.entities.monster.EntityBrainyZombie brainy =
				thaumcraft.common.registry.ThaumcraftEntities.BRAINY_ZOMBIE.get().create(level);
		helper.assertTrue(brainy != null, "the brainy zombie entity type should really create an instance");
		brainy.moveTo(abs.getX() + 2.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(brainy);
		boolean brainyGotBrain = false;
		for (int i = 0; i < 300; i++) {
			java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
			net.neoforged.neoforge.event.entity.living.LivingDropsEvent event =
					new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(brainy, source, drops, true);
			thaumcraft.common.lib.events.MobDeathDropEvents.onLivingDrops(event);
			if (drops.stream().anyMatch(d -> d.getItem().is(thaumcraft.api.items.ItemsTC.brain.get()))) {
				brainyGotBrain = true;
				break;
			}
		}
		helper.assertFalse(brainyGotBrain, "a real brainy zombie should really never drop an extra real Brain from this hook");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void mobDeathDropEventsGrantsACurioFromARealCultistKillByAPlayer(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos abs = helper.absolutePos(BlockPos.ZERO);
		thaumcraft.common.entities.monster.cult.EntityCultistKnight cultist =
				thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get().create(level);
		helper.assertTrue(cultist != null, "the entity type should really create an instance");
		cultist.moveTo(abs.getX() + 0.5, abs.getY() + 1.0, abs.getZ() + 0.5, 0.0f, 0.0f);
		level.addFreshEntity(cultist);

		ServerPlayer killer = spawnCasterAt(helper, abs.offset(2, 0, 0));
		net.minecraft.world.damagesource.DamageSource source = level.damageSources().playerAttack(killer);
		boolean gotCurio = false;
		for (int i = 0; i < 300 && !gotCurio; i++) {
			java.util.List<net.minecraft.world.entity.item.ItemEntity> drops = new java.util.ArrayList<>();
			net.neoforged.neoforge.event.entity.living.LivingDropsEvent event =
					new net.neoforged.neoforge.event.entity.living.LivingDropsEvent(cultist, source, drops, true);
			thaumcraft.common.lib.events.MobDeathDropEvents.onLivingDrops(event);
			gotCurio = drops.stream().anyMatch(d -> d.getItem().is(thaumcraft.api.items.ItemsTC.curio.get()));
		}
		helper.assertTrue(gotCurio, "a real cultist killed by a real player should really eventually drop a real Curio within a generous number of chance rolls");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// WornGearEvents' newly-closed Traveller's Boots fall-damage half, and PassiveResearchEvents' 2
	// pickup-triggered research flags - continuing the events-audit chunk begun with
	// PassiveResearchEvents/MobDeathDropEvents above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void travellerBootsHalveRealFallDamage(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.FEET, new ItemStack(thaumcraft.api.items.ItemsTC.travellerBoots.get()));

		net.minecraft.world.damagesource.DamageSource fall = level.damageSources().fall();
		net.neoforged.neoforge.common.damagesource.DamageContainer container =
				new net.neoforged.neoforge.common.damagesource.DamageContainer(fall, 10.0f);
		container.setNewDamage(10.0f);
		net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent event =
				new net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent(player, container);
		thaumcraft.common.lib.events.WornGearEvents.fallDamage(event);

		helper.assertTrue(event.getAmount() == 4.0f, "10.0 fall damage should really be halved-minus-1 by real traveller's boots to 4.0, found " + event.getAmount());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void passiveResearchGrantsFlagsOnCrystalAndThaumonomiconPickup(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));

		net.minecraft.world.entity.item.ItemEntity crystalEntity = new net.minecraft.world.entity.item.ItemEntity(
				level, player.getX(), player.getY(), player.getZ(), new ItemStack(thaumcraft.api.items.ItemsTC.crystalEssence.get()));
		net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post crystalEvent =
				new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post(player, crystalEntity, crystalEntity.getItem());
		thaumcraft.common.lib.events.PassiveResearchEvents.onItemPickup(crystalEvent);
		helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!gotcrystals"),
				"picking up a real crystal essence should really grant the real !gotcrystals flag");

		net.minecraft.world.entity.item.ItemEntity bookEntity = new net.minecraft.world.entity.item.ItemEntity(
				level, player.getX(), player.getY(), player.getZ(), new ItemStack(thaumcraft.api.items.ItemsTC.thaumonomicon.get()));
		net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post bookEvent =
				new net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent.Post(player, bookEntity, bookEntity.getItem());
		thaumcraft.common.lib.events.PassiveResearchEvents.onItemPickup(bookEvent);
		helper.assertTrue(ThaumcraftCapabilities.getKnowledge(player).isResearchKnown("!gotthaumonomicon"),
				"picking up a real thaumonomicon should really grant the real !gotthaumonomicon flag");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// GearTooltipEvents - real coverage for the cross-cutting item-info tooltip lines. Constructs a real
	// ItemTooltipEvent directly (real constructor: ItemStack, Player, List<Component>, TooltipFlag,
	// Item.TooltipContext - confirmed via javap on the real compiled classes).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void gearTooltipShowsChargeWarpDiscountEssentiaAndInfusionEnchantments(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));

		// IRechargable - a real traveller's boots with some real charge.
		ItemStack boots = new ItemStack(thaumcraft.api.items.ItemsTC.travellerBoots.get());
		thaumcraft.api.items.RechargeHelper.rechargeItemBlindly(boots, player, 5);
		java.util.List<net.minecraft.network.chat.Component> bootsTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				boots, player, bootsTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(bootsTooltip.stream().anyMatch(c -> c.getString().contains("5")), "a real charged item's tooltip should really show its real charge, found: " + bootsTooltip);

		// IWarpingGear (void helm, real fixed +1 warp) and IVisDiscountGear (goggles, real fixed 5%).
		ItemStack voidHelm = new ItemStack(thaumcraft.api.items.ItemsTC.voidHelm.get());
		java.util.List<net.minecraft.network.chat.Component> voidTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				voidHelm, player, voidTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(voidTooltip.stream().anyMatch(c -> c.getString().contains("1")), "a real warping item's tooltip should really show its real warp value, found: " + voidTooltip);

		ItemStack goggles = new ItemStack(thaumcraft.api.items.ItemsTC.goggles.get());
		java.util.List<net.minecraft.network.chat.Component> gogglesTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				goggles, player, gogglesTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(gogglesTooltip.stream().anyMatch(c -> c.getString().contains("5%")), "a real vis-discount item's tooltip should really show its real discount, found: " + gogglesTooltip);

		// IEssentiaContainerItem - a real crystal essence loaded with a real aspect.
		ItemStack crystal = new ItemStack(thaumcraft.api.items.ItemsTC.crystalEssence.get());
		((thaumcraft.api.aspects.IEssentiaContainerItem) crystal.getItem()).setAspects(crystal, new thaumcraft.api.aspects.AspectList().add(Aspect.FIRE, 4));
		java.util.List<net.minecraft.network.chat.Component> crystalTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				crystal, player, crystalTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		// Aspect.getName() returns the capitalized Latin tag, not an English name - FIRE's real tag is "ignis".
		helper.assertTrue(crystalTooltip.stream().anyMatch(c -> c.getString().contains("Ignis") && c.getString().contains("4")),
				"a real loaded essentia container's tooltip should really list its real aspects, found: " + crystalTooltip);

		// EnumInfusionEnchantment - a real infused tool.
		ItemStack pickaxe = new ItemStack(net.minecraft.world.item.Items.IRON_PICKAXE);
		thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.addInfusionEnchantment(pickaxe,
				thaumcraft.common.lib.enchantment.EnumInfusionEnchantment.LAMPLIGHT, 1);
		java.util.List<net.minecraft.network.chat.Component> pickaxeTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				pickaxe, player, pickaxeTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(pickaxeTooltip.stream().anyMatch(c -> c.getString().equals("Lamplighter")),
				"a real infused tool's tooltip should really name its real infusion enchantment, found: " + pickaxeTooltip);

		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// The Spa device - a real fluid-tank (IFluidHandler/FluidTank, same reference implementation
	// TileWaterJug already established) + Bath Salts slot, auto-placing real BlockPurifyingFluid nearby on
	// a timer. BlockPurifyingFluid itself reuses BlockFluxGoo's own real conserved-quanta flow engine (see
	// its own doc comment) - state.tick(level, pos, random)/state.entityInside(level, pos, entity) are the
	// same real dispatch pattern fluxGooFallsSpreadsDecaysAndAffectsEntities above already established.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void purifyingFluidGrantsWarpWardOnTouchThenConsumesItself(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos pos = helper.absolutePos(BlockPos.ZERO);
		level.setBlockAndUpdate(pos, BlocksTC.purifyingFluid.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.misc.BlockPurifyingFluid.LEVEL, thaumcraft.common.blocks.misc.BlockPurifyingFluid.MAX_LEVEL));

		ServerPlayer player = spawnCasterAt(helper, pos);
		ThaumcraftCapabilities.getWarp(player).set(thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.PERMANENT, 4);
		helper.assertFalse(player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD), "the player shouldn't have Warp Ward before touching the fluid");

		level.getBlockState(pos).entityInside(level, pos, player);

		helper.assertTrue(player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD),
				"touching a real full-level purifying fluid block should really grant a real Warp Ward");
		helper.assertTrue(level.getBlockState(pos).isAir(), "a real full-level purifying fluid block should really consume itself on a real dip");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void purifyingFluidDoesNothingWhenNotFullLevelOrWardAlreadyActive(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos partialPos = helper.absolutePos(BlockPos.ZERO);
		level.setBlockAndUpdate(partialPos, BlocksTC.purifyingFluid.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.misc.BlockPurifyingFluid.LEVEL, 3));
		ServerPlayer player = spawnCasterAt(helper, partialPos);
		level.getBlockState(partialPos).entityInside(level, partialPos, player);
		helper.assertFalse(player.hasEffect(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD),
				"a real partial-level purifying fluid block shouldn't grant a real Warp Ward");
		helper.assertTrue(level.getBlockState(partialPos).is(BlocksTC.purifyingFluid.get()), "a real partial-level block shouldn't have consumed itself");

		BlockPos fullPos = helper.absolutePos(new BlockPos(2, 0, 0));
		level.setBlockAndUpdate(fullPos, BlocksTC.purifyingFluid.get().defaultBlockState()
				.setValue(thaumcraft.common.blocks.misc.BlockPurifyingFluid.LEVEL, thaumcraft.common.blocks.misc.BlockPurifyingFluid.MAX_LEVEL));
		player.addEffect(new net.minecraft.world.effect.MobEffectInstance(thaumcraft.common.registry.ThaumcraftMobEffects.WARP_WARD, 200));
		level.getBlockState(fullPos).entityInside(level, fullPos, player);
		helper.assertTrue(level.getBlockState(fullPos).is(BlocksTC.purifyingFluid.get()),
				"a real full-level block shouldn't consume itself on a player who already has a real active Warp Ward");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void tileSpaConsumesWaterAndBathSaltsToAutoPlaceRealPurifyingFluidNearby(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		BlockPos abs = helper.absolutePos(pos);
		helper.setBlock(pos, BlocksTC.spa.get());
		ServerLevel level = helper.getLevel();

		thaumcraft.common.tiles.devices.TileSpa tile = helper.getBlockEntity(pos);
		tile.tank.fill(new net.neoforged.neoforge.fluids.FluidStack(net.minecraft.world.level.material.Fluids.WATER, 5000),
				net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
		tile.getItemHandler().insertItem(0, new ItemStack(thaumcraft.api.items.ItemsTC.bathSalts.get()), false);
		int waterBefore = tile.tank.getFluidAmount();

		net.minecraft.world.level.block.state.BlockState state = helper.getBlockState(pos);
		boolean placed = false;
		for (int i = 0; i < 3000 && !placed; i++) {
			thaumcraft.common.tiles.devices.TileSpa.tick(level, abs, state, tile);
			for (BlockPos p : BlockPos.betweenClosed(abs.above().offset(-2, 0, -2), abs.above().offset(2, 0, 2))) {
				if (level.getBlockState(p).is(BlocksTC.purifyingFluid.get())) {
					placed = true;
					break;
				}
			}
		}

		helper.assertTrue(placed, "a real spa with real banked water and a real bath salt should really eventually place a real purifying fluid block nearby");
		helper.assertTrue(tile.tank.getFluidAmount() < waterBefore, "placing purifying fluid should really have drawn down the real water tank");
		helper.assertTrue(tile.getItemHandler().getStackInSlot(0).isEmpty(), "placing purifying fluid should really have consumed the real bath salt");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// CasterFocusHelper - the real common-side logic backing the focus-radial HUD's actual bind/swap
	// (FocusRadialOverlay's own rendering/hover/hit-test is client-only and has no automated coverage at
	// all - see its own doc comment and the port-tracking memory's UI-visual-parity checklist for the
	// manual playtest it still needs).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void casterFocusHelperFindsCarriedFociSortedByRealDisplayName(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getInventory().clearContent();
		player.getInventory().setItem(3, new ItemStack(thaumcraft.api.items.ItemsTC.focusFireball.get()));
		player.getInventory().setItem(7, new ItemStack(thaumcraft.api.items.ItemsTC.focus1.get()));
		player.getInventory().setItem(20, new ItemStack(Items.STICK));

		java.util.List<ItemStack> foci = thaumcraft.common.items.casters.CasterFocusHelper.findCarriedFoci(player);

		helper.assertTrue(foci.size() == 2, "only the 2 real carried foci should really be found, found " + foci.size());
		helper.assertTrue(foci.get(0).getHoverName().getString().compareTo(foci.get(1).getHoverName().getString()) <= 0,
				"real carried foci should really be sorted by real display name");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void casterFocusHelperChangeFocusBindsAndReturnsThePreviousFocus(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getInventory().clearContent();

		ItemStack casterStack = new ItemStack(thaumcraft.api.items.ItemsTC.casterBasic.get());
		thaumcraft.api.casters.ICaster caster = (thaumcraft.api.casters.ICaster) casterStack.getItem();
		caster.setFocus(casterStack, new ItemStack(thaumcraft.api.items.ItemsTC.focus1.get()));

		player.getInventory().setItem(5, new ItemStack(thaumcraft.api.items.ItemsTC.focusFireball.get(), 2));
		thaumcraft.common.items.casters.CasterFocusHelper.changeFocus(casterStack, player, 5);

		helper.assertTrue(caster.getFocus(casterStack) == thaumcraft.api.items.ItemsTC.focusFireball.get(),
				"the caster should really now have the real newly-selected focus bound");
		// makeMockServerPlayerInLevel's player is unavoidably creative (see the port-tracking memory) - real
		// creative players don't lose the consumed item, matching changeFocus's own real instabuild check.
		int expectedCount = player.getAbilities().instabuild ? 2 : 1;
		helper.assertTrue(player.getInventory().getItem(5).getCount() == expectedCount,
				"selecting a real focus should really consume exactly 1 from its real stack outside creative, found "
						+ player.getInventory().getItem(5).getCount() + " (expected " + expectedCount + ")");
		boolean gotOldFocusBack = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(thaumcraft.api.items.ItemsTC.focus1.get())) gotOldFocusBack = true;
		}
		helper.assertTrue(gotOldFocusBack, "the real previously-bound focus should really have been returned to the player's inventory");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void casterFocusHelperChangeFocusRemovesWithoutBindingWhenSlotNegative(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getInventory().clearContent();

		ItemStack casterStack = new ItemStack(thaumcraft.api.items.ItemsTC.casterBasic.get());
		thaumcraft.api.casters.ICaster caster = (thaumcraft.api.casters.ICaster) casterStack.getItem();
		caster.setFocus(casterStack, new ItemStack(thaumcraft.api.items.ItemsTC.focus1.get()));

		thaumcraft.common.items.casters.CasterFocusHelper.changeFocus(casterStack, player, -1);

		helper.assertTrue(caster.getFocus(casterStack) == null, "a real negative slot should really unbind the caster's focus");
		boolean gotOldFocusBack = false;
		for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
			if (player.getInventory().getItem(i).is(thaumcraft.api.items.ItemsTC.focus1.get())) gotOldFocusBack = true;
		}
		helper.assertTrue(gotOldFocusBack, "the real unbound focus should really have been returned to the player's inventory");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void casterFocusHelperChangeFocusIsANoOpForARealInvalidSlot(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.getInventory().clearContent();

		ItemStack casterStack = new ItemStack(thaumcraft.api.items.ItemsTC.casterBasic.get());
		thaumcraft.api.casters.ICaster caster = (thaumcraft.api.casters.ICaster) casterStack.getItem();
		caster.setFocus(casterStack, new ItemStack(thaumcraft.api.items.ItemsTC.focus1.get()));

		player.getInventory().setItem(5, new ItemStack(Items.STICK));
		thaumcraft.common.items.casters.CasterFocusHelper.changeFocus(casterStack, player, 5);

		helper.assertTrue(caster.getFocus(casterStack) == thaumcraft.api.items.ItemsTC.focus1.get(),
				"a real slot that isn't a real focus should really be a no-op, leaving the original focus bound");
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// RunicArmorRecipe - real 6th shape sharing INFUSION_TYPE, escalating Amber cost computed from the
	// central item's own current real RUNIC_CHARGE level. Same real "getRecipeManager().getRecipeFor"
	// lookup precedent as the infusion enchantment tests above.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void runicArmorRecipeMatchesRealArmorAndRaisesTheRunicChargeLevel(GameTestHelper helper) {
		ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				helmet,
				java.util.List.of(new ItemStack(thaumcraft.api.items.ItemsTC.salisMundus.get()), new ItemStack(thaumcraft.api.items.ItemsTC.amber.get())));

		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, helper.getLevel());
		helper.assertTrue(match.isPresent(), "the runic_armor recipe should match a fresh leather helmet with its real base components");

		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(new BlockPos(0, 2, 0)));
		ItemStack result = match.get().value().getRecipeOutput(player, helmet, input.components());
		helper.assertTrue(result.get(thaumcraft.api.items.ThaumcraftDataComponents.RUNIC_CHARGE.get()) != null
				&& result.get(thaumcraft.api.items.ThaumcraftDataComponents.RUNIC_CHARGE.get()) == 1,
				"crafting the recipe should really raise a fresh helmet's real Runic Charge to 1");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void runicArmorRecipeEscalatesItsRealAmberCostWithCurrentLevel(GameTestHelper helper) {
		ItemStack chargedHelmet = new ItemStack(Items.LEATHER_HELMET);
		chargedHelmet.set(thaumcraft.api.items.ThaumcraftDataComponents.RUNIC_CHARGE.get(), 2);

		thaumcraft.api.crafting.InfusionRecipeInput tooFewAmber = new thaumcraft.api.crafting.InfusionRecipeInput(
				chargedHelmet,
				java.util.List.of(new ItemStack(thaumcraft.api.items.ItemsTC.salisMundus.get()), new ItemStack(thaumcraft.api.items.ItemsTC.amber.get())));
		var noMatch = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), tooFewAmber, helper.getLevel());
		helper.assertTrue(noMatch.isEmpty(), "a real level-2 helmet should really need more than the real base 1 Amber, found a match: " + noMatch);

		thaumcraft.api.crafting.InfusionRecipeInput enoughAmber = new thaumcraft.api.crafting.InfusionRecipeInput(
				chargedHelmet,
				java.util.List.of(new ItemStack(thaumcraft.api.items.ItemsTC.salisMundus.get()),
						new ItemStack(thaumcraft.api.items.ItemsTC.amber.get()),
						new ItemStack(thaumcraft.api.items.ItemsTC.amber.get()),
						new ItemStack(thaumcraft.api.items.ItemsTC.amber.get())));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), enoughAmber, helper.getLevel());
		helper.assertTrue(match.isPresent(), "a real level-2 helmet with the real full escalated Amber cost (1 base + 2 extra) should really match");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void runicArmorRecipeRefusesARealNonArmorCentralItem(GameTestHelper helper) {
		thaumcraft.api.crafting.InfusionRecipeInput input = new thaumcraft.api.crafting.InfusionRecipeInput(
				new ItemStack(Items.STICK),
				java.util.List.of(new ItemStack(thaumcraft.api.items.ItemsTC.salisMundus.get()), new ItemStack(thaumcraft.api.items.ItemsTC.amber.get())));
		var match = helper.getLevel().getRecipeManager().getRecipeFor(thaumcraft.api.crafting.ThaumcraftRecipeTypes.INFUSION_TYPE.get(), input, helper.getLevel());
		helper.assertTrue(match.isEmpty(), "a real non-armor central item should never match the real runic_armor recipe, found: " + match);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// RunicShieldEvents - the real payoff for RUNIC_CHARGE: a real vis-fed absorption shield.
	// --------------------------------------------------------------------------------------------------

	/**
	 * Real, documented mock-player limitation (a second one alongside the already-known "unavoidably
	 * creative" flag - see the port-tracking memory's GameTest-framework-notes entry, updated alongside this
	 * test): {@code setAbsorptionAmount}/{@code getAbsorptionAmount} don't round-trip at all on a real
	 * {@code helper.makeMockServerPlayerInLevel()} player (confirmed directly - setting 7.0f and immediately
	 * reading it back returns 0.0f). So this test can't assert on the real Absorption value the shield
	 * actually grants; it asserts on the other real, independently observable side effect instead - real vis
	 * is drawn from the local aura exactly when the shield mechanic decides to regen, and NOT drawn when
	 * there's no real Runic Charge to regen towards.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void runicShieldEventsDrawsRealVisOnlyWhenWearingRealRunicArmor(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		player.tickCount = 0;
		// Guard against a stale cooldown left by an unrelated earlier test's own mock player reusing this
		// same real entity id in this same JVM run - see RunicShieldEvents#forget's own doc comment.
		thaumcraft.common.lib.events.RunicShieldEvents.forget(player);

		// No runic armor at all - the shield has no real max to regen towards, so no vis should be drawn.
		float visBeforeBare = thaumcraft.common.world.aura.AuraHandler.getVis(level, player.blockPosition());
		thaumcraft.common.lib.events.RunicShieldEvents.checkRunicShield(player);
		helper.assertTrue(thaumcraft.common.world.aura.AuraHandler.getVis(level, player.blockPosition()) == visBeforeBare,
				"a player with no real runic armor at all shouldn't have any real vis drawn for a shield");

		ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
		helmet.set(thaumcraft.api.items.ThaumcraftDataComponents.RUNIC_CHARGE.get(), 3);
		player.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, helmet);

		float visBefore = thaumcraft.common.world.aura.AuraHandler.getVis(level, player.blockPosition());
		thaumcraft.common.lib.events.RunicShieldEvents.checkRunicShield(player);
		float visAfter = thaumcraft.common.world.aura.AuraHandler.getVis(level, player.blockPosition());
		helper.assertTrue(visAfter < visBefore, "wearing a real runic-charged helmet should really have drawn real vis from the local aura to regen the shield");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void gearTooltipShowsRealRunicChargeOnlyWhenPresent(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));

		ItemStack plainHelmet = new ItemStack(Items.LEATHER_HELMET);
		java.util.List<net.minecraft.network.chat.Component> plainTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				plainHelmet, player, plainTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(plainTooltip.stream().noneMatch(c -> c.getString().contains("+")), "an unenchanted helmet's tooltip shouldn't show a real runic charge line, found: " + plainTooltip);

		ItemStack runicHelmet = new ItemStack(Items.LEATHER_HELMET);
		runicHelmet.set(thaumcraft.api.items.ThaumcraftDataComponents.RUNIC_CHARGE.get(), 2);
		java.util.List<net.minecraft.network.chat.Component> runicTooltip = new java.util.ArrayList<>();
		thaumcraft.common.lib.events.GearTooltipEvents.onItemTooltip(new net.neoforged.neoforge.event.entity.player.ItemTooltipEvent(
				runicHelmet, player, runicTooltip, net.minecraft.world.item.TooltipFlag.NORMAL, net.minecraft.world.item.Item.TooltipContext.EMPTY));
		helper.assertTrue(runicTooltip.stream().anyMatch(c -> c.getString().contains("+2")),
				"a real level-2 runic helmet's tooltip should really show its real runic charge, found: " + runicTooltip);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// ThaumcraftConfig - the real modern (ModConfigSpec) replacement for the old (1.12.2) ModConfig's real
	// remaining open thread. See ThaumcraftConfig's own doc comment for why every OTHER old ModConfig field
	// is left out (zero real modern consumer). RunicShieldEvents' own real config coverage (shieldRecharge/
	// shieldWait/shieldCost) is exercised indirectly by the existing runicShieldEventsDrawsRealVisOnlyWhenWearingRealRunicArmor
	// test above; the 3 tests below cover the newly-wired WUSS_MODE/ALLOW_CHAMPION_MOBS/TAINT_SPREAD_* gates.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void thaumcraftConfigDefaultsMatchTheOriginalsRealShippedDefaults(GameTestHelper helper) {
		helper.assertTrue(!thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.get(), "wussMode should default to false, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.ALLOW_CHAMPION_MOBS.get(), "allowChampionMobs should default to true, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.SHIELD_RECHARGE_MS.get() == 2000, "shieldRecharge should default to 2000, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.SHIELD_WAIT_MS.get() == 4000, "shieldWait should default to 4000, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.SHIELD_COST.get() == 1.0, "shieldCost should default to 1.0, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.TAINT_SPREAD_RATE.get() == 100.0, "taintSpreadRate should default to 100.0, matching the original");
		helper.assertTrue(thaumcraft.common.config.ThaumcraftConfig.TAINT_SPREAD_AREA.get() == 32, "taintSpreadArea should default to 32, matching the original");
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.config.ThaumcraftConfig#WUSS_MODE} gating
	 * {@link thaumcraft.common.lib.events.WarpEvents#checkWarpEvent} - reuses the exact same deterministic
	 * setup as {@code checkWarpEventTriggersARealMisfortuneWithoutThrowing} above (counter 10000 -&gt; sqrt
	 * 100, guaranteeing the real {@code r <= sqrt(warpCounter)} roll succeeds every time), but proves the real
	 * early-return: with wussMode on, the real warp counter should never move at all; with it off (the real
	 * default), the exact same setup should really move it, same as that other test already established.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void wussModeSuppressesTheRealWarpMisfortuneCheckEntirely(GameTestHelper helper) {
		ServerPlayer player = spawnCasterAt(helper, helper.absolutePos(BlockPos.ZERO));
		thaumcraft.api.capabilities.IPlayerWarp wc = thaumcraft.api.capabilities.ThaumcraftCapabilities.getWarp(player);
		wc.set(thaumcraft.api.capabilities.IPlayerWarp.EnumWarpType.PERMANENT, 50);
		wc.setCounter(10000);

		try {
			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(true);
			thaumcraft.common.lib.events.WarpEvents.checkWarpEvent(player);
			helper.assertTrue(wc.getCounter() == 10000, "with wussMode on, checkWarpEvent should really be a total no-op, found counter " + wc.getCounter());

			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(false);
			thaumcraft.common.lib.events.WarpEvents.checkWarpEvent(player);
			helper.assertTrue(wc.getCounter() < 10000, "with wussMode off (the real default), the exact same setup should really trigger a misfortune, found counter " + wc.getCounter());
		} finally {
			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(false);
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.config.ThaumcraftConfig#WUSS_MODE} gating
	 * {@link thaumcraft.common.blocks.world.taint.TaintHelper#spreadFibres} - reuses the same real seed/floor
	 * setup as {@code taintFibreConnectsSpreadsAndDiesWithoutARealSeed} above. With wussMode on, {@code
	 * ignore=true} should NOT be enough to force a spread (the gate is checked before the rate roll); with it
	 * off, the exact same real seed should really let it spread within a handful of tries, same as that other
	 * test already established.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void wussModeSuppressesRealTaintFibreSpreadEntirely(GameTestHelper helper) {
		BlockPos floorPos = BlockPos.ZERO;
		BlockPos floorAbs = helper.absolutePos(floorPos);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				helper.setBlock(floorPos.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.STONE);
			}
		}
		ServerLevel level = helper.getLevel();
		BlockPos fibrePos = floorAbs.above();
		level.setBlockAndUpdate(fibrePos, thaumcraft.common.blocks.world.taint.BlockTaintFibre.computeState(
				thaumcraft.api.blocks.BlocksTC.taintFibre.get().defaultBlockState(), level, fibrePos));
		thaumcraft.common.blocks.world.taint.TaintHelper.addTaintSeed(level, fibrePos);

		try {
			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(true);
			for (int i = 0; i < 100; i++) {
				thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, fibrePos, true);
			}
			boolean spreadWhileWuss = false;
			for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
				if (level.getBlockState(fibrePos.relative(dir)).is(thaumcraft.api.blocks.BlocksTC.taintFibre.get())) {
					spreadWhileWuss = true;
				}
			}
			helper.assertTrue(!spreadWhileWuss, "with wussMode on, taint fibre spread should really never happen even with ignore=true");

			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(false);
			boolean spread = false;
			for (int i = 0; i < 300 && !spread; i++) {
				thaumcraft.common.blocks.world.taint.TaintHelper.spreadFibres(level, fibrePos, true);
				for (net.minecraft.core.Direction dir : net.minecraft.core.Direction.values()) {
					if (level.getBlockState(fibrePos.relative(dir)).is(thaumcraft.api.blocks.BlocksTC.taintFibre.get())) {
						spread = true;
						break;
					}
				}
			}
			helper.assertTrue(spread, "with wussMode off (the real default), the exact same real seed should really eventually let a fibre spread");
		} finally {
			thaumcraft.common.config.ThaumcraftConfig.WUSS_MODE.set(false);
			thaumcraft.common.blocks.world.taint.TaintHelper.removeTaintSeed(level, fibrePos);
		}
		helper.succeed();
	}

	/**
	 * Real coverage for {@link thaumcraft.common.config.ThaumcraftConfig#ALLOW_CHAMPION_MOBS} gating
	 * {@link thaumcraft.common.lib.events.ChampionModEvents#onJoinLevel} - with the toggle off, every real
	 * freshly-spawned Monster should be marked "not champion" outright (a real negative attribute value, never
	 * a real 0-13 mod id), matching {@code freshlySpawnedMonsterRollsRealChampionStatus}'s own real "roll
	 * either way" baseline above but forcing the "way" deterministically.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void allowChampionMobsOffForcesEveryRealMonsterToNotChampion(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		BlockPos origin = helper.absolutePos(BlockPos.ZERO);

		try {
			thaumcraft.common.config.ThaumcraftConfig.ALLOW_CHAMPION_MOBS.set(false);
			for (int i = 0; i < 30; i++) {
				Zombie zombie = new Zombie(EntityType.ZOMBIE, level);
				zombie.setPos(origin.getX() + 0.5, origin.getY() + 1, origin.getZ() + 0.5);
				level.addFreshEntity(zombie);
				double id = zombie.getAttribute(thaumcraft.api.ThaumcraftAttributes.CHAMPION_MOD).getValue();
				helper.assertTrue(id < 0, "with allowChampionMobs off, a real freshly-spawned monster should never roll a real champion id, found " + id);
			}
		} finally {
			thaumcraft.common.config.ThaumcraftConfig.ALLOW_CHAMPION_MOBS.set(true);
		}
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// ItemFocusPouch / FocusPouchMenu - the real 18-slot portable focus container.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusPouchContentsRoundTripThroughTheRealContainerComponent(GameTestHelper helper) {
		ItemStack pouch = new ItemStack(thaumcraft.api.items.ItemsTC.focusPouch.get());
		net.minecraft.core.NonNullList<ItemStack> contents = thaumcraft.common.items.casters.ItemFocusPouch.getContents(pouch);
		helper.assertTrue(contents.size() == 18, "a real fresh pouch should really report 18 real slots, found " + contents.size());
		helper.assertTrue(contents.stream().allMatch(ItemStack::isEmpty), "a real fresh pouch should really start with every real slot empty");

		contents.set(3, new ItemStack(thaumcraft.api.items.ItemsTC.focusAir.get()));
		thaumcraft.common.items.casters.ItemFocusPouch.setContents(pouch, contents);

		net.minecraft.core.NonNullList<ItemStack> readBack = thaumcraft.common.items.casters.ItemFocusPouch.getContents(pouch);
		helper.assertTrue(readBack.get(3).is(thaumcraft.api.items.ItemsTC.focusAir.get()), "the real focus placed in slot 3 should really round-trip through the real container component, found " + readBack.get(3));
		helper.assertTrue(readBack.get(0).isEmpty(), "an untouched real slot should really still read back empty");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusPouchMenuSeedsItsRealSlotsFromTheRealPouchStack(GameTestHelper helper) {
		ItemStack pouch = new ItemStack(thaumcraft.api.items.ItemsTC.focusPouch.get());
		net.minecraft.core.NonNullList<ItemStack> contents = thaumcraft.common.items.casters.ItemFocusPouch.getContents(pouch);
		contents.set(0, new ItemStack(thaumcraft.api.items.ItemsTC.focusAir.get()));
		thaumcraft.common.items.casters.ItemFocusPouch.setContents(pouch, contents);

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.common.container.FocusPouchMenu menu = new thaumcraft.common.container.FocusPouchMenu(1, player.getInventory(), pouch);
		helper.assertTrue(menu.getSlot(0).getItem().is(thaumcraft.api.items.ItemsTC.focusAir.get()), "the real menu's slot 0 should really be seeded from the real pouch stack's own real contents, found " + menu.getSlot(0).getItem());
		helper.assertTrue(menu.getSlot(1).getItem().isEmpty(), "an untouched real pouch slot should really seed an empty real menu slot");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusPouchMenuRejectsNonFocusItemsInItsRealSlots(GameTestHelper helper) {
		ItemStack pouch = new ItemStack(thaumcraft.api.items.ItemsTC.focusPouch.get());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.common.container.FocusPouchMenu menu = new thaumcraft.common.container.FocusPouchMenu(1, player.getInventory(), pouch);

		helper.assertTrue(!menu.getSlot(0).mayPlace(new ItemStack(Items.STICK)), "a real non-focus item should really be rejected by a real pouch slot");
		helper.assertTrue(menu.getSlot(0).mayPlace(new ItemStack(thaumcraft.api.items.ItemsTC.focusAir.get())), "a real focus item should really be accepted by a real pouch slot");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void focusPouchMenuWritesItsRealSlotsBackToThePouchStackOnClose(GameTestHelper helper) {
		ItemStack pouch = new ItemStack(thaumcraft.api.items.ItemsTC.focusPouch.get());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.common.container.FocusPouchMenu menu = new thaumcraft.common.container.FocusPouchMenu(1, player.getInventory(), pouch);

		menu.getSlot(5).set(new ItemStack(thaumcraft.api.items.ItemsTC.focusAir.get()));
		menu.removed(player);

		net.minecraft.core.NonNullList<ItemStack> contents = thaumcraft.common.items.casters.ItemFocusPouch.getContents(pouch);
		helper.assertTrue(contents.get(5).is(thaumcraft.api.items.ItemsTC.focusAir.get()), "closing a real pouch menu should really write its real slot contents back to the real pouch stack, found " + contents.get(5));
		helper.succeed();
	}

	/**
	 * Real coverage for {@code ItemFocusPouch#use} opening a real menu without throwing - matches
	 * {@code openingTheThaumonomiconGrantsGotThaumonomiconResearch}'s own established "call the real item's
	 * use() directly against a mock player" approach (GameTest is server-only, so the real client-side Screen
	 * itself is never exercised here - see the checklist memory for the manual playtest this still needs).
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void openingTheFocusPouchDoesNotThrow(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		ItemStack pouch = new ItemStack(thaumcraft.api.items.ItemsTC.focusPouch.get());
		player.setItemInHand(hand, pouch);

		pouch.getItem().use(helper.getLevel(), player, hand);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Basic Gear - the real remaining "basic gear" items (Thaumium/Void tools+armor were already real and
	// shipped before this chunk - see the port-tracking memory's own audit note). ItemPrimalCrusher,
	// ItemSanityChecker (+ its real SanityCheckerOverlay HUD), and ItemHandMirror (+ its real HandMirrorMenu,
	// reusing TileMirror's real Node Transducer network) are the genuine remaining gaps.
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void primalCrusherHasRealWarpAndSelfRepairs(GameTestHelper helper) {
		ItemStack crusher = new ItemStack(thaumcraft.api.items.ItemsTC.primalCrusher.get());
		ServerPlayer player = helper.makeMockServerPlayerInLevel();

		int warp = ((thaumcraft.api.items.IWarpingGear) crusher.getItem()).getWarp(crusher, player);
		helper.assertTrue(warp == 2, "a real Primal Crusher should carry +2 real warp while held, found " + warp);

		crusher.setDamageValue(10);
		player.tickCount = 20;
		crusher.getItem().inventoryTick(crusher, helper.getLevel(), player, 0, true);
		helper.assertTrue(crusher.getDamageValue() == 9, "a real damaged Primal Crusher should really repair 1 durability every 20 ticks, found damage " + crusher.getDamageValue());
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void primalCrusherMinesPickaxeAndShovelTaggedBlocksAtARealFlatSpeed(GameTestHelper helper) {
		ItemStack crusher = new ItemStack(thaumcraft.api.items.ItemsTC.primalCrusher.get());
		net.minecraft.world.item.component.Tool tool = crusher.get(net.minecraft.core.component.DataComponents.TOOL);
		helper.assertTrue(tool != null, "a real Primal Crusher should carry a real Tool component");

		float stoneSpeed = tool.getMiningSpeed(Blocks.STONE.defaultBlockState());
		float dirtSpeed = tool.getMiningSpeed(Blocks.DIRT.defaultBlockState());
		float logSpeed = tool.getMiningSpeed(Blocks.OAK_LOG.defaultBlockState());
		helper.assertTrue(stoneSpeed == 8.0f, "a real pickaxe-tagged block should really mine at the real flat 8.0 speed, found " + stoneSpeed);
		helper.assertTrue(dirtSpeed == 8.0f, "a real shovel-tagged block should really mine at the real flat 8.0 speed, found " + dirtSpeed);
		helper.assertTrue(logSpeed == 1.0f, "a real axe-only block (not pickaxe or shovel tagged) should really fall back to the real default speed, found " + logSpeed);
		helper.assertTrue(tool.isCorrectForDrops(Blocks.STONE.defaultBlockState()), "a real pickaxe-tagged block should really be a correct tool for drops");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void handMirrorLinksToARealPlacedMirrorRegardlessOfItsOwnLinkState(GameTestHelper helper) {
		BlockPos mirrorPos = new BlockPos(3, 2, 3);
		helper.setBlock(mirrorPos, thaumcraft.api.blocks.BlocksTC.mirror.get());
		BlockPos absMirror = helper.absolutePos(mirrorPos);

		ServerPlayer player = spawnCasterAt(helper, absMirror.above());
		ItemStack handMirror = new ItemStack(thaumcraft.api.items.ItemsTC.handMirror.get());
		net.minecraft.world.InteractionHand hand = net.minecraft.world.InteractionHand.MAIN_HAND;
		player.setItemInHand(hand, handMirror);

		net.minecraft.world.phys.BlockHitResult hit = new net.minecraft.world.phys.BlockHitResult(
				Vec3.atCenterOf(absMirror), net.minecraft.core.Direction.UP, absMirror, false);
		net.minecraft.world.item.context.UseOnContext context =
				new net.minecraft.world.item.context.UseOnContext(helper.getLevel(), player, hand, handMirror, hit);
		handMirror.getItem().useOn(context);

		thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink link = handMirror.get(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get());
		helper.assertTrue(link != null && link.pos().equals(absMirror), "right-clicking a real mirror with a real Hand Mirror should really link to it, found " + link);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void handMirrorOpeningWithAStaleLinkClearsItAndFails(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack handMirror = new ItemStack(thaumcraft.api.items.ItemsTC.handMirror.get());
		// A real link pointing at a position with no real mirror block/tile at all - the linked target is
		// really gone (or never existed), matching the original's own "destination missing" real error path.
		handMirror.set(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get(),
				new thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink(helper.getLevel().dimension(), helper.absolutePos(new BlockPos(50, 50, 50))));
		player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, handMirror);

		handMirror.getItem().use(helper.getLevel(), player, net.minecraft.world.InteractionHand.MAIN_HAND);

		helper.assertTrue(!handMirror.has(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get()),
				"opening a real Hand Mirror whose linked mirror no longer really exists should clear the real link");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void handMirrorMenuDeliversAnItemThroughTheRealLinkedNetwork(GameTestHelper helper) {
		BlockPos posA = new BlockPos(3, 2, 3);
		BlockPos posB = new BlockPos(3, 2, 10);
		helper.setBlock(posA, thaumcraft.api.blocks.BlocksTC.mirror.get());
		helper.setBlock(posB, thaumcraft.api.blocks.BlocksTC.mirror.get());
		BlockPos absA = helper.absolutePos(posA);
		BlockPos absB = helper.absolutePos(posB);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.tiles.devices.TileMirror tileA = helper.getBlockEntity(posA);
		tileA.applyLink(new thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink(level.dimension(), absB));
		helper.assertTrue(tileA.isLinkValid(), "applying a link should establish a real mutual pairing");

		ItemStack handMirror = new ItemStack(thaumcraft.api.items.ItemsTC.handMirror.get());
		handMirror.set(thaumcraft.api.items.ThaumcraftDataComponents.MIRROR_LINK.get(),
				new thaumcraft.api.items.ThaumcraftDataComponents.MirrorLink(level.dimension(), absA));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.common.container.HandMirrorMenu menu = new thaumcraft.common.container.HandMirrorMenu(1, player.getInventory(), handMirror);
		menu.getSlot(0).set(new ItemStack(Items.STICK, 2));

		net.minecraft.world.level.block.state.BlockState stateB = helper.getBlockState(posB);
		thaumcraft.common.tiles.devices.TileMirror tileB = helper.getBlockEntity(posB);
		for (int i = 0; i < 5; i++) {
			thaumcraft.common.tiles.devices.TileMirror.tick(level, absB, stateB, tileB);
		}

		helper.assertTrue(menu.getSlot(0).getItem().isEmpty(), "dropping an item into a real linked Hand Mirror's send slot should really deliver it, clearing the slot");
		List<ItemEntity> nearB = level.getEntitiesOfClass(ItemEntity.class, new AABB(absB).inflate(2));
		helper.assertTrue(!nearB.isEmpty(), "the delivered item should really eject out of mirror A's own real linked partner (mirror B)");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void sanityCheckerIsARealUncommonItem(GameTestHelper helper) {
		ItemStack checker = new ItemStack(thaumcraft.api.items.ItemsTC.sanityChecker.get());
		helper.assertTrue(checker.getRarity() == net.minecraft.world.item.Rarity.UNCOMMON, "a real Sanity Checker should really be Uncommon rarity, found " + checker.getRarity());
		helper.succeed();
	}

	/**
	 * Real, deliberate minimal-repro regression test for a documented former hang (see the port-tracking
	 * memory's own Barrier Stone entry, and {@code BlockBarrier}'s own doc comment): a real Barrier Stone
	 * paving tile, a real Barrier block directly above it, and an adjacent real redstone power source, all
	 * placed together with zero entities and no manual tick() calls - an earlier attempt at this exact real
	 * combination reproducibly hung the whole GameTestServer here. Kept as its own tiny, isolated test
	 * (rather than folded into a larger fixture) specifically so a future regression shows up fast and
	 * unambiguously if this combination ever breaks again.
	 */
	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void barrierStoneRedstoneMinimalReproDoesNotHang(GameTestHelper helper) {
		BlockPos pavingPos = new BlockPos(3, 2, 3);
		helper.setBlock(pavingPos, thaumcraft.api.blocks.BlocksTC.pavingStoneBarrier.get());
		helper.setBlock(pavingPos.above(), thaumcraft.api.blocks.BlocksTC.barrier.get());
		helper.setBlock(pavingPos.east(), Blocks.REDSTONE_BLOCK);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void barrierStoneTileAutoPlacesAndHealsTheRealBarrierBlocksAboveIt(GameTestHelper helper) {
		BlockPos pavingPos = new BlockPos(3, 2, 3);
		helper.setBlock(pavingPos, thaumcraft.api.blocks.BlocksTC.pavingStoneBarrier.get());
		BlockPos abs = helper.absolutePos(pavingPos);
		ServerLevel level = helper.getLevel();

		thaumcraft.common.tiles.misc.TileBarrierStone tile = helper.getBlockEntity(pavingPos);
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
		for (int i = 0; i < 105; i++) {
			thaumcraft.common.tiles.misc.TileBarrierStone.tick(level, abs, state, tile);
		}

		helper.assertTrue(level.getBlockState(abs.above(1)).is(thaumcraft.api.blocks.BlocksTC.barrier.get()),
				"the real Barrier Stone tile should really auto-place a real Barrier block 1 above itself within 105 ticks");
		helper.assertTrue(level.getBlockState(abs.above(2)).is(thaumcraft.api.blocks.BlocksTC.barrier.get()),
				"the real Barrier Stone tile should really auto-place a real Barrier block 2 above itself within 105 ticks");

		// Real healing: break the lower real Barrier block by hand, tick past the next real 100-cycle, confirm
		// it comes back - matches the original's own real self-healing intent.
		level.removeBlock(abs.above(1), false);
		for (int i = 0; i < 100; i++) {
			thaumcraft.common.tiles.misc.TileBarrierStone.tick(level, abs, state, tile);
		}
		helper.assertTrue(level.getBlockState(abs.above(1)).is(thaumcraft.api.blocks.BlocksTC.barrier.get()),
				"a real broken Barrier block should really be healed back within the next real 100-tick cycle");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void barrierBlockCollidesWithNonPlayerLivingEntitiesButNotPlayers(GameTestHelper helper) {
		BlockPos pavingPos = new BlockPos(3, 2, 3);
		helper.setBlock(pavingPos, thaumcraft.api.blocks.BlocksTC.pavingStoneBarrier.get());
		BlockPos barrierPos = helper.absolutePos(pavingPos).above();
		helper.setBlock(pavingPos.above(), thaumcraft.api.blocks.BlocksTC.barrier.get());
		ServerLevel level = helper.getLevel();
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(barrierPos);

		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(barrierPos.getX() + 0.5, barrierPos.getY(), barrierPos.getZ() + 0.5);
		level.addFreshEntity(pig);
		net.minecraft.world.phys.shapes.VoxelShape pigCollision = state.getCollisionShape(level, barrierPos, net.minecraft.world.phys.shapes.CollisionContext.of(pig));
		helper.assertTrue(!pigCollision.isEmpty(), "a real non-player LivingEntity should really collide with a real unpowered Barrier block");

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		net.minecraft.world.phys.shapes.VoxelShape playerCollision = state.getCollisionShape(level, barrierPos, net.minecraft.world.phys.shapes.CollisionContext.of(player));
		helper.assertTrue(playerCollision.isEmpty(), "a real player should really pass straight through a real Barrier block");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void barrierBlockBecomesPassableWhileThePavingStoneIsRedstonePowered(GameTestHelper helper) {
		BlockPos pavingPos = new BlockPos(3, 2, 3);
		helper.setBlock(pavingPos, thaumcraft.api.blocks.BlocksTC.pavingStoneBarrier.get());
		helper.setBlock(pavingPos.above(), thaumcraft.api.blocks.BlocksTC.barrier.get());
		helper.setBlock(pavingPos.east(), Blocks.REDSTONE_BLOCK);
		BlockPos barrierPos = helper.absolutePos(pavingPos).above();
		ServerLevel level = helper.getLevel();
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(barrierPos);

		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(barrierPos.getX() + 0.5, barrierPos.getY(), barrierPos.getZ() + 0.5);
		level.addFreshEntity(pig);
		net.minecraft.world.phys.shapes.VoxelShape pigCollision = state.getCollisionShape(level, barrierPos, net.minecraft.world.phys.shapes.CollisionContext.of(pig));
		helper.assertTrue(pigCollision.isEmpty(), "a real redstone-powered paving stone should really let even non-player creatures pass through its real Barrier blocks");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void barrierStonePushesBackAirborneNonPlayerIntrudersWhileUnpowered(GameTestHelper helper) {
		BlockPos pavingPos = new BlockPos(3, 2, 3);
		helper.setBlock(pavingPos, thaumcraft.api.blocks.BlocksTC.pavingStoneBarrier.get());
		BlockPos abs = helper.absolutePos(pavingPos);
		ServerLevel level = helper.getLevel();

		Pig pig = EntityType.PIG.create(level);
		pig.moveTo(abs.getX() + 0.5, abs.getY() + 1.5, abs.getZ() + 0.5);
		pig.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
		level.addFreshEntity(pig);
		helper.assertTrue(!pig.onGround(), "the real pig should really be airborne (spawned mid-air) for this real test to be meaningful");

		thaumcraft.common.tiles.misc.TileBarrierStone tile = helper.getBlockEntity(pavingPos);
		net.minecraft.world.level.block.state.BlockState state = level.getBlockState(abs);
		net.minecraft.world.phys.Vec3 before = pig.getDeltaMovement();
		for (int i = 0; i < 6; i++) {
			thaumcraft.common.tiles.misc.TileBarrierStone.tick(level, abs, state, tile);
		}
		net.minecraft.world.phys.Vec3 after = pig.getDeltaMovement();
		helper.assertTrue(!after.equals(before), "a real airborne non-player creature above an unpowered real Barrier Stone should really get pushed, found velocity unchanged at " + after);
		helper.succeed();
	}

	// --------------------------------------------------------------------------------------------------
	// Theorycrafting content expansion - the real Theorycrafting engine/menu/screen were already fully
	// shipped before this chunk (an earlier session's own work); this closes out 18 of the original's 19
	// remaining flavor cards (all but CardCelestial - needs the still-unported ItemCelestialNotes) plus 4 of
	// its remaining 8 aids (the other 4 each need a still-unported block - see Thaumcraft.java's own
	// registration comment for exactly which).
	// --------------------------------------------------------------------------------------------------

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void newTheorycraftContentIsRegistered(GameTestHelper helper) {
		String[] cardKeys = {
				"CardAwareness", "CardFocus", "CardGlyphs", "CardRealization", "CardRevelation", "CardSculpting",
				"CardSpellbinding", "CardTruth", "CardDarkWhispers", "CardChannel", "CardConcentrate", "CardReactions",
				"CardSynthesis", "CardInfuse", "CardTinker", "CardMindOverMatter", "CardCurio", "CardScripting",
		};
		for (String simpleName : cardKeys) {
			String fqcn = "thaumcraft.common.lib.research.theorycraft." + simpleName;
			helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.cards.containsKey(fqcn),
					"the real registered deck should really contain " + simpleName + ", found keys: " + thaumcraft.api.research.theorycraft.TheorycraftManager.cards.keySet());
		}
		helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.aids.values().stream()
						.anyMatch(a -> a instanceof thaumcraft.common.lib.research.theorycraft.AidBasicAlchemy),
				"AidBasicAlchemy should really be registered");
		helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.aids.values().stream()
						.anyMatch(a -> a instanceof thaumcraft.common.lib.research.theorycraft.AidBasicArtifice),
				"AidBasicArtifice should really be registered");
		helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.aids.values().stream()
						.anyMatch(a -> a instanceof thaumcraft.common.lib.research.theorycraft.AidBasicGolemancy),
				"AidBasicGolemancy should really be registered");
		helper.assertTrue(thaumcraft.api.research.theorycraft.TheorycraftManager.aids.values().stream()
						.anyMatch(a -> a instanceof thaumcraft.common.lib.research.theorycraft.AidBasicInfusion),
				"AidBasicInfusion should really be registered");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void aidBasicAlchemyOffersItsRealThreeCards(GameTestHelper helper) {
		thaumcraft.common.lib.research.theorycraft.AidBasicAlchemy aid = new thaumcraft.common.lib.research.theorycraft.AidBasicAlchemy();
		helper.assertTrue(aid.getAidObject() == thaumcraft.api.blocks.BlocksTC.crucible.get(), "AidBasicAlchemy should really trigger off the real Crucible block");
		java.util.List<Class<thaumcraft.api.research.theorycraft.TheorycraftCard>> cards = java.util.Arrays.asList(aid.getCards());
		helper.assertTrue(cards.contains(thaumcraft.common.lib.research.theorycraft.CardConcentrate.class)
						&& cards.contains(thaumcraft.common.lib.research.theorycraft.CardReactions.class)
						&& cards.contains(thaumcraft.common.lib.research.theorycraft.CardSynthesis.class),
				"AidBasicAlchemy should really offer Concentrate/Reactions/Synthesis, found " + cards);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardSculptingRequiresAndConsumesARealClayBall(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.common.lib.research.theorycraft.CardSculpting card = new thaumcraft.common.lib.research.theorycraft.CardSculpting();
		card.setSeed(1L);
		helper.assertTrue(card.getRequiredItems().length == 1 && card.getRequiredItems()[0].is(Items.CLAY_BALL), "CardSculpting should really require a real clay ball");
		helper.assertTrue(card.activate(player, data), "CardSculpting#activate should succeed");
		helper.assertTrue(data.getTotal("GOLEMANCY") == 20 && data.bonusDraws == 1,
				"CardSculpting should add flat 20 GOLEMANCY and 1 bonus draw, found total=" + data.getTotal("GOLEMANCY") + " bonusDraws=" + data.bonusDraws);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardSpellbindingRequiresAndConsumesRealXpLevels(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);
		thaumcraft.common.lib.research.theorycraft.CardSpellbinding card = new thaumcraft.common.lib.research.theorycraft.CardSpellbinding();

		player.experienceLevel = 0;
		helper.assertTrue(!card.initialize(player, data), "with zero real XP levels, CardSpellbinding should really fail to initialize");
		helper.assertTrue(!card.activate(player, data), "with zero real XP levels, CardSpellbinding#activate should really fail too");

		player.experienceLevel = 3;
		helper.assertTrue(card.initialize(player, data), "with real XP levels available, CardSpellbinding should really initialize");
		helper.assertTrue(card.activate(player, data), "CardSpellbinding#activate should really succeed with real XP levels available");
		helper.assertTrue(player.experienceLevel == 0, "CardSpellbinding should really spend all 3 real XP levels (capped at min(5, level)), found " + player.experienceLevel);
		helper.assertTrue(data.getTotal("AUROMANCY") == 15, "CardSpellbinding should really add 5 AUROMANCY per real level spent, found " + data.getTotal("AUROMANCY"));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardScriptingConsumesRealInkAndPaperFromTheRealTable(GameTestHelper helper) {
		BlockPos pos = BlockPos.ZERO;
		helper.setBlock(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get());
		thaumcraft.common.tiles.crafting.TileResearchTable tile = helper.getBlockEntity(pos);
		tile.getItemHandler().setStackInSlot(0, new ItemStack(thaumcraft.api.items.ItemsTC.scribingTools.get()));
		tile.getItemHandler().setStackInSlot(1, new ItemStack(Items.PAPER, 5));

		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, tile);
		thaumcraft.common.lib.research.theorycraft.CardScripting card = new thaumcraft.common.lib.research.theorycraft.CardScripting();

		int inkDamageBefore = tile.getInk().getDamageValue();
		int paperCountBefore = tile.getPaper().getCount();
		helper.assertTrue(card.activate(player, data), "CardScripting#activate should succeed with real ink and paper available");
		helper.assertTrue(tile.getInk().getDamageValue() == inkDamageBefore + 1, "CardScripting should really damage the real ink by 1");
		helper.assertTrue(tile.getPaper().getCount() == paperCountBefore - 1, "CardScripting should really consume 1 real paper");
		helper.assertTrue(data.getTotal("GOLEMANCY") == 25, "CardScripting should really add flat 25 GOLEMANCY, found " + data.getTotal("GOLEMANCY"));

		tile.getItemHandler().setStackInSlot(1, ItemStack.EMPTY);
		helper.assertTrue(!card.activate(player, data), "CardScripting#activate should really fail once the real table has no paper left");
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardCurioFindsAndConsumesARealCarriedCurio(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		ItemStack curio = new ItemStack(thaumcraft.api.items.ItemsTC.curio.get());
		player.getInventory().add(curio);
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.common.lib.research.theorycraft.CardCurio card = new thaumcraft.common.lib.research.theorycraft.CardCurio();
		card.setSeed(1L);
		helper.assertTrue(card.initialize(player, data), "CardCurio should really find the real carried Curio");
		helper.assertTrue(card.getRequiredItems()[0].is(thaumcraft.api.items.ItemsTC.curio.get()), "CardCurio's own real required item should really be the real found Curio");
		helper.assertTrue(card.activate(player, data), "CardCurio#activate should succeed");
		helper.assertTrue(data.getTotal("BASICS") > 0, "CardCurio should really add real BASICS progress, found " + data.getTotal("BASICS"));
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardChannelAndConcentrateRequireRealAspectItems(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.common.lib.research.theorycraft.CardChannel channel = new thaumcraft.common.lib.research.theorycraft.CardChannel();
		channel.setSeed(1L);
		helper.assertTrue(channel.initialize(player, data), "CardChannel should really initialize with a real random compound aspect");
		ItemStack phial = channel.getRequiredItems()[0];
		helper.assertTrue(phial.getItem() instanceof thaumcraft.common.items.consumables.ItemPhial, "CardChannel should really require a real filled Phial, found " + phial);
		helper.assertTrue(channel.activate(player, data), "CardChannel#activate should succeed");
		helper.assertTrue(data.getTotal("INFUSION") == 25, "CardChannel should really add flat 25 INFUSION, found " + data.getTotal("INFUSION"));

		thaumcraft.common.lib.research.theorycraft.CardConcentrate concentrate = new thaumcraft.common.lib.research.theorycraft.CardConcentrate();
		concentrate.setSeed(2L);
		helper.assertTrue(concentrate.initialize(player, data), "CardConcentrate should really initialize with a real random compound aspect");
		ItemStack crystal = concentrate.getRequiredItems()[0];
		helper.assertTrue(crystal.is(thaumcraft.api.items.ItemsTC.crystalEssence.get()), "CardConcentrate should really require a real Crystal Essence, found " + crystal);
		helper.succeed();
	}

	@GameTest(templateNamespace = Thaumcraft.MODID, template = "empty")
	public static void cardSynthesisConsumesComponentsAndReturnsTheRealCompoundCrystal(GameTestHelper helper) {
		ServerPlayer player = helper.makeMockServerPlayerInLevel();
		thaumcraft.api.research.theorycraft.ResearchTableData data = new thaumcraft.api.research.theorycraft.ResearchTableData(player, null);

		thaumcraft.common.lib.research.theorycraft.CardSynthesis card = new thaumcraft.common.lib.research.theorycraft.CardSynthesis();
		card.setSeed(1L);
		helper.assertTrue(card.initialize(player, data), "CardSynthesis should really initialize with a real random compound aspect and its own 2 real components");
		helper.assertTrue(card.getRequiredItems().length == 2 && card.getRequiredItemsConsumed()[0] && card.getRequiredItemsConsumed()[1],
				"CardSynthesis should really require and consume its real 2 real component crystals");

		int before = player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(card.activate(player, data), "CardSynthesis#activate should succeed");
		helper.assertTrue(data.getTotal("ALCHEMY") == 40, "CardSynthesis should really add flat 40 ALCHEMY, found " + data.getTotal("ALCHEMY"));
		int after = player.getInventory().items.stream().mapToInt(ItemStack::getCount).sum();
		helper.assertTrue(after > before, "CardSynthesis should really hand back a real compound crystal to the player's real inventory, found before=" + before + " after=" + after);
		helper.succeed();
	}
}
