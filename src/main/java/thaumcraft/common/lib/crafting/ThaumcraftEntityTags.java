package thaumcraft.common.lib.crafting;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectHelper;
import thaumcraft.api.aspects.AspectList;

/**
 * Sibling to {@link ThaumcraftObjectTags} - the original (1.12.2) {@code AspectHelper} kept one parallel
 * {@code registerEntityTag(String entityName, AspectList, EntityTagsNBT...)} table alongside its item-tag
 * one, feeding {@code getEntityAspects} (see that method's own doc comment on
 * {@link thaumcraft.api.aspects.AspectHelper#getEntityAspects} for why it was a permanent stub until now).
 * {@link ThaumcraftObjectTags}'s own doc comment explicitly named this table as cut when it shipped -
 * backfilled here once {@link EnumInfusionEnchantment#ESSENCE} needed a real "does this mob have aspects"
 * answer.
 *
 * The original matched by a raw {@code EntityList.getEntityString(entity)} name string, with an optional
 * list of NBT sub-tag conditions ({@code EntityTagsNBT}) for the handful of entries that varied by mob
 * state (a powered Creeper, an Elder Guardian). This port matches by real {@link EntityType} constants
 * instead (compiler-checked, no raw-string typos) - and doesn't need an NBT-condition system at all: Elder
 * Guardian is its own real {@link EntityType} now (not an NBT flag on Guardian, unlike 1.12.2), so it's just
 * a second plain map entry; Creeper's "powered" state is still a live field check
 * ({@link Creeper#isPowered()}), handled directly in {@link #getEntityTags} rather than a generic
 * predicate-registration mechanism for what's genuinely a single real case.
 *
 * Only vanilla mobs were registered here at first - the original's ~30 additional {@code "Thaumcraft.*"}-
 * prefixed entries (Pech, Wisp, Taintacle, the Eldritch/Cultist bosses, ...) needed their own real
 * {@link EntityType} to exist first. All of the original's entries whose mob has since shipped are added
 * below, values kept verbatim from {@code ConfigAspects#registerEntityAspects}. Wisp's original entry
 * looped over every real {@link Aspect} (a Wisp carrying that element's own tag, +Aura+Flight) - collapsed
 * to one flat representative entry here rather than porting the original's per-instance NBT-conditioned
 * variant lookup this class deliberately doesn't have (see this class's own doc comment on why Creeper's
 * powered check is a one-off special case, not a general mechanism). {@code EntityTaintCrawler} and {@code
 * EntitySpellBat} have no original entry to carry forward (real additions from later in this project's own
 * history) - given reasonable values in the same spirit as their nearest original siblings.
 */
public final class ThaumcraftEntityTags {

	private static final Map<EntityType<?>, AspectList> ENTITY_TAGS = new LinkedHashMap<>();
	private static AspectList creeperPowered;

	private ThaumcraftEntityTags() {
	}

	public static void register(EntityType<?> type, AspectList aspects) {
		ENTITY_TAGS.put(type, aspects);
	}

	/** The real implementation behind the non-player half of {@link AspectHelper#getEntityAspects}. */
	public static AspectList getEntityTags(Entity entity) {
		if (entity instanceof Creeper creeper && creeper.isPowered()) {
			return creeperPowered;
		}
		return ENTITY_TAGS.get(entity.getType());
	}

	public static void init() {
		register(EntityType.ZOMBIE, new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.EARTH, 5));
		register(EntityType.HUSK, new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.FIRE, 5));
		register(EntityType.SKELETON, new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 5).add(Aspect.EARTH, 5));
		register(EntityType.WITHER_SKELETON, new AspectList().add(Aspect.UNDEAD, 25).add(Aspect.MAN, 5).add(Aspect.ENTROPY, 10));
		register(EntityType.STRAY, new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 5).add(Aspect.TRAP, 5));

		creeperPowered = new AspectList().add(Aspect.PLANT, 15).add(Aspect.FIRE, 15).add(Aspect.ENERGY, 15);
		register(EntityType.CREEPER, new AspectList().add(Aspect.PLANT, 15).add(Aspect.FIRE, 15));

		register(EntityType.HORSE, new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
		register(EntityType.DONKEY, new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
		register(EntityType.MULE, new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
		register(EntityType.SKELETON_HORSE, new AspectList().add(Aspect.BEAST, 5).add(Aspect.UNDEAD, 10).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
		register(EntityType.ZOMBIE_HORSE, new AspectList().add(Aspect.BEAST, 10).add(Aspect.UNDEAD, 5).add(Aspect.EARTH, 5).add(Aspect.AIR, 5));
		register(EntityType.LLAMA, new AspectList().add(Aspect.BEAST, 15).add(Aspect.WATER, 5));

		register(EntityType.PIG, new AspectList().add(Aspect.BEAST, 10).add(Aspect.EARTH, 10).add(Aspect.DESIRE, 5));
		register(EntityType.EXPERIENCE_ORB, new AspectList().add(Aspect.MIND, 10));
		register(EntityType.SHEEP, new AspectList().add(Aspect.BEAST, 10).add(Aspect.EARTH, 10));
		register(EntityType.COW, new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 15));
		register(EntityType.MOOSHROOM, new AspectList().add(Aspect.BEAST, 15).add(Aspect.PLANT, 15).add(Aspect.EARTH, 15));
		register(EntityType.SNOW_GOLEM, new AspectList().add(Aspect.COLD, 10).add(Aspect.MAN, 5).add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 5));
		register(EntityType.OCELOT, new AspectList().add(Aspect.BEAST, 10).add(Aspect.ENTROPY, 10));
		register(EntityType.CAT, new AspectList().add(Aspect.BEAST, 10).add(Aspect.ENTROPY, 10));
		register(EntityType.CHICKEN, new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.AIR, 5));
		register(EntityType.SQUID, new AspectList().add(Aspect.BEAST, 5).add(Aspect.WATER, 10));
		register(EntityType.WOLF, new AspectList().add(Aspect.BEAST, 15).add(Aspect.EARTH, 10).add(Aspect.AVERSION, 5));
		register(EntityType.BAT, new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.DARKNESS, 5));
		register(EntityType.RABBIT, new AspectList().add(Aspect.BEAST, 5).add(Aspect.EARTH, 5).add(Aspect.MOTION, 5));
		register(EntityType.POLAR_BEAR, new AspectList().add(Aspect.BEAST, 15).add(Aspect.COLD, 10));
		register(EntityType.PARROT, new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.SENSES, 5));

		register(EntityType.SPIDER, new AspectList().add(Aspect.BEAST, 10).add(Aspect.ENTROPY, 10).add(Aspect.TRAP, 10));
		register(EntityType.CAVE_SPIDER, new AspectList().add(Aspect.BEAST, 5).add(Aspect.DEATH, 10).add(Aspect.TRAP, 10));
		register(EntityType.SLIME, new AspectList().add(Aspect.LIFE, 10).add(Aspect.WATER, 10).add(Aspect.ALCHEMY, 5));
		register(EntityType.MAGMA_CUBE, new AspectList().add(Aspect.WATER, 5).add(Aspect.FIRE, 10).add(Aspect.ALCHEMY, 5));
		register(EntityType.GHAST, new AspectList().add(Aspect.UNDEAD, 15).add(Aspect.FIRE, 15));
		register(EntityType.ZOMBIFIED_PIGLIN, new AspectList().add(Aspect.UNDEAD, 15).add(Aspect.FIRE, 15).add(Aspect.BEAST, 10));
		register(EntityType.ENDERMAN, new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.MOTION, 15).add(Aspect.DESIRE, 5));
		register(EntityType.ENDERMITE, new AspectList().add(Aspect.BEAST, 5).add(Aspect.ELDRITCH, 5).add(Aspect.MOTION, 5));
		register(EntityType.SILVERFISH, new AspectList().add(Aspect.BEAST, 5).add(Aspect.EARTH, 10));
		register(EntityType.BLAZE, new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.FIRE, 15).add(Aspect.FLIGHT, 5));
		register(EntityType.GUARDIAN, new AspectList().add(Aspect.BEAST, 10).add(Aspect.ELDRITCH, 10).add(Aspect.WATER, 10));
		register(EntityType.ELDER_GUARDIAN, new AspectList().add(Aspect.BEAST, 10).add(Aspect.ELDRITCH, 15).add(Aspect.WATER, 15));
		register(EntityType.SHULKER, new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.TRAP, 5).add(Aspect.FLIGHT, 5).add(Aspect.PROTECT, 5));
		register(EntityType.VEX, new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.FLIGHT, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 5));

		register(EntityType.ENDER_DRAGON, new AspectList().add(Aspect.ELDRITCH, 50).add(Aspect.BEAST, 30).add(Aspect.ENTROPY, 50).add(Aspect.FLIGHT, 10));
		register(EntityType.WITHER, new AspectList().add(Aspect.UNDEAD, 50).add(Aspect.ENTROPY, 25).add(Aspect.FIRE, 25));

		register(EntityType.WITCH, new AspectList().add(Aspect.MAN, 15).add(Aspect.MAGIC, 5).add(Aspect.ALCHEMY, 10));
		register(EntityType.VILLAGER, new AspectList().add(Aspect.MAN, 15));
		register(EntityType.IRON_GOLEM, new AspectList().add(Aspect.METAL, 15).add(Aspect.MAN, 5).add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 5));
		register(EntityType.END_CRYSTAL, new AspectList().add(Aspect.ELDRITCH, 15).add(Aspect.AURA, 15).add(Aspect.LIFE, 15));
		register(EntityType.ITEM_FRAME, new AspectList().add(Aspect.SENSES, 5).add(Aspect.CRAFT, 5));
		register(EntityType.PAINTING, new AspectList().add(Aspect.SENSES, 10).add(Aspect.CRAFT, 5));
		register(EntityType.EVOKER, new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));
		register(EntityType.VINDICATOR, new AspectList().add(Aspect.AVERSION, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));
		register(EntityType.ILLUSIONER, new AspectList().add(Aspect.SENSES, 5).add(Aspect.MAGIC, 5).add(Aspect.MAN, 10));

		register(thaumcraft.common.registry.ThaumcraftEntities.WISP.get(), new AspectList().add(Aspect.AURA, 10).add(Aspect.FLIGHT, 5).add(Aspect.MAGIC, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.GOLEM.get(), new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MAN, 10).add(Aspect.MOTION, 10));
		register(thaumcraft.common.registry.ThaumcraftEntities.THAUMIC_SLIME.get(), new AspectList().add(Aspect.LIFE, 5).add(Aspect.WATER, 5).add(Aspect.FLUX, 5).add(Aspect.ALCHEMY, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.BRAINY_ZOMBIE.get(), new AspectList().add(Aspect.UNDEAD, 20).add(Aspect.MAN, 10).add(Aspect.MIND, 5).add(Aspect.AVERSION, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.GIANT_BRAINY_ZOMBIE.get(), new AspectList().add(Aspect.UNDEAD, 25).add(Aspect.MAN, 15).add(Aspect.MIND, 5).add(Aspect.AVERSION, 10));
		register(thaumcraft.common.registry.ThaumcraftEntities.FIRE_BAT.get(), new AspectList().add(Aspect.BEAST, 5).add(Aspect.FLIGHT, 5).add(Aspect.FIRE, 10));
		register(thaumcraft.common.registry.ThaumcraftEntities.MIND_SPIDER.get(), new AspectList().add(Aspect.FLUX, 5).add(Aspect.FIRE, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.SPELL_BAT.get(), new AspectList().add(Aspect.MAGIC, 10).add(Aspect.FLIGHT, 5).add(Aspect.BEAST, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.ELDRITCH_CRAB.get(), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.BEAST, 10).add(Aspect.TRAP, 10));
		register(thaumcraft.common.registry.ThaumcraftEntities.CULTIST_KNIGHT.get(), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAN, 15).add(Aspect.AVERSION, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.CULTIST_CLERIC.get(), new AspectList().add(Aspect.ELDRITCH, 5).add(Aspect.MAN, 15).add(Aspect.AVERSION, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.CULTIST_LEADER.get(), new AspectList().add(Aspect.ELDRITCH, 40).add(Aspect.AVERSION, 40).add(Aspect.MAN, 40));
		register(thaumcraft.common.registry.ThaumcraftEntities.INHABITED_ZOMBIE.get(), new AspectList().add(Aspect.ELDRITCH, 10).add(Aspect.UNDEAD, 10).add(Aspect.MAN, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINT_CRAWLER.get(), new AspectList().add(Aspect.FLUX, 10).add(Aspect.BEAST, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINT_SWARM.get(), new AspectList().add(Aspect.FLUX, 15).add(Aspect.AIR, 5));
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE.get(), new AspectList().add(Aspect.FLUX, 15).add(Aspect.BEAST, 10));
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINTACLE_SMALL.get(), new AspectList().add(Aspect.FLUX, 5).add(Aspect.BEAST, 5));
		// The original registered "Thaumcraft.TaintSeed"/"TaintSeedPrime" twice with different values - the
		// second registration silently overwrote the first in its own registry, so only the second (real,
		// winning) value is carried forward here.
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINT_SEED.get(), new AspectList().add(Aspect.PLANT, 20).add(Aspect.BEAST, 20).add(Aspect.FLUX, 20));
		register(thaumcraft.common.registry.ThaumcraftEntities.TAINT_SEED_PRIME.get(), new AspectList().add(Aspect.PLANT, 30).add(Aspect.BEAST, 30).add(Aspect.FLUX, 30));
		// The original varied Pech's entry by its PechType NBT flag (forage/thaum/stalker) via an
		// EntityTagsNBT condition this class's own generic lookup doesn't support (see this class's own doc
		// comment) - the forage/type-0 values are used as the one flat representative entry.
		register(thaumcraft.common.registry.ThaumcraftEntities.PECH.get(), new AspectList().add(Aspect.MAN, 10).add(Aspect.AURA, 5).add(Aspect.EXCHANGE, 10).add(Aspect.DESIRE, 5));
	}
}
