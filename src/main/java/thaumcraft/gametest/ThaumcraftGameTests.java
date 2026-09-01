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

	public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
		TEST_ITEMS.register(modEventBus);
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
				"recharge_pedestal", "hungry_chest", "salis_mundus",
				"amber_block", "amber_block_to_amber", "amber_brick", "amber_brick_to_block",
				"crystal_essence_aer", "crystal_essence_aqua", "crystal_essence_ignis",
				"crystal_essence_terra", "crystal_essence_ordo", "crystal_essence_perditio",
				"crystal_essence_auram", "crystal_essence_praecantatio", "crystal_essence_vitreus",
				"stone_arcane", "stone_arcane_stairs", "stone_arcane_slab",
				"stone_arcane_brick", "stone_arcane_brick_stairs", "stone_arcane_brick_slab",
				"plank_silverwood", "silverwood_stairs", "silverwood_slab"
		};
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
}
