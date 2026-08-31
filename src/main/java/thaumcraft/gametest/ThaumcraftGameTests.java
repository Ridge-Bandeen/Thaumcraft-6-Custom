package thaumcraft.gametest;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.crafting.CrucibleRecipe;

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
				"caster_basic", "thaumometer", "quicksilver_smelting"
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
}
