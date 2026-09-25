package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.entities.EntityFollowingItem;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.entities.projectile.EntityAlumentum;
import thaumcraft.common.entities.projectile.EntityBottleTaint;
import thaumcraft.common.entities.projectile.EntityFocusCloud;
import thaumcraft.common.entities.projectile.EntityFocusMine;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;
import thaumcraft.common.entities.projectile.EntityGrapple;

/** First entity types this port has needed - see {@link EntityFocusProjectile}'s own doc comment. */
public final class ThaumcraftEntities {

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "thaumcraft");

	public static final DeferredHolder<EntityType<?>, EntityType<EntityFocusProjectile>> FOCUS_PROJECTILE = ENTITY_TYPES.register("focus_projectile",
			() -> EntityType.Builder.<EntityFocusProjectile>of(EntityFocusProjectile::new, MobCategory.MISC)
					.sized(0.15f, 0.15f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("focus_projectile"));

	public static final DeferredHolder<EntityType<?>, EntityType<EntityFocusMine>> FOCUS_MINE = ENTITY_TYPES.register("focus_mine",
			() -> EntityType.Builder.<EntityFocusMine>of(EntityFocusMine::new, MobCategory.MISC)
					.sized(0.15f, 0.15f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("focus_mine"));

	public static final DeferredHolder<EntityType<?>, EntityType<EntityFocusCloud>> FOCUS_CLOUD = ENTITY_TYPES.register("focus_cloud",
			() -> EntityType.Builder.<EntityFocusCloud>of(EntityFocusCloud::new, MobCategory.MISC)
					.sized(1.0f, 0.5f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("focus_cloud"));

	/** A real, visible thrown item (unlike the invisible focus-carrier entities above) - allowed to save/
	 * persist across chunk reloads mid-flight, matching vanilla's own snowball/egg convention. */
	public static final DeferredHolder<EntityType<?>, EntityType<EntityAlumentum>> ALUMENTUM = ENTITY_TYPES.register("alumentum",
			() -> EntityType.Builder.<EntityAlumentum>of(EntityAlumentum::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.build("alumentum"));

	public static final DeferredHolder<EntityType<?>, EntityType<EntityBottleTaint>> BOTTLE_TAINT = ENTITY_TYPES.register("bottle_taint",
			() -> EntityType.Builder.<EntityBottleTaint>of(EntityBottleTaint::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.build("bottle_taint"));

	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.projectile.EntityCausalityCollapser>> CAUSALITY_COLLAPSER = ENTITY_TYPES.register("causality_collapser",
			() -> EntityType.Builder.<thaumcraft.common.entities.projectile.EntityCausalityCollapser>of(thaumcraft.common.entities.projectile.EntityCausalityCollapser::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.build("causality_collapser"));

	/** See {@link EntityFollowingItem}'s own doc comment - homes a dropped item toward whoever/wherever
	 * earned it (COLLECTOR) instead of leaving it to fall normally. Persists across reloads like a real
	 * dropped item (unlike the invisible focus-carrier entities above), same reasoning as Alumentum/BottleTaint. */
	public static final DeferredHolder<EntityType<?>, EntityType<EntityFollowingItem>> FOLLOWING_ITEM = ENTITY_TYPES.register("following_item",
			() -> EntityType.Builder.<EntityFollowingItem>of(EntityFollowingItem::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.build("following_item"));

	/** See {@link EntityGrapple}'s own doc comment. Invisible/short-lived like the focus-carrier entities
	 * above (no custom renderer, and a mid-flight-or-pulling hookshot isn't worth persisting across a
	 * reload), so {@code noSave()} matches those rather than Alumentum/BottleTaint's own real-dropped-item
	 * persistence. */
	public static final DeferredHolder<EntityType<?>, EntityType<EntityGrapple>> GRAPPLE = ENTITY_TYPES.register("grapple",
			() -> EntityType.Builder.<EntityGrapple>of(EntityGrapple::new, MobCategory.MISC)
					.sized(0.1f, 0.1f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("grapple"));

	/** First real living {@link net.minecraft.world.entity.Mob} this port has registered (every entry above
	 * is a non-living projectile/carrier, {@code MobCategory.MISC}) - see {@link EntityWisp}'s own doc
	 * comment. Needs a matching {@code EntityAttributeCreationEvent} entry, see
	 * {@code Thaumcraft#registerEntityAttributes}. */
	public static final DeferredHolder<EntityType<?>, EntityType<EntityWisp>> WISP = ENTITY_TYPES.register("wisp",
			() -> EntityType.Builder.<EntityWisp>of(EntityWisp::new, MobCategory.MONSTER)
					.sized(0.9f, 0.9f)
					.clientTrackingRange(64)
					.build("wisp"));

	/** See {@link EntityFluxRift}'s own doc comment - a plain, non-living {@code Entity} with an entirely
	 * custom bounding box recomputed every time its size changes, so {@code sized(...)} here is only ever
	 * the placeholder used before the first real recompute. */
	public static final DeferredHolder<EntityType<?>, EntityType<EntityFluxRift>> FLUX_RIFT = ENTITY_TYPES.register("flux_rift",
			() -> EntityType.Builder.<EntityFluxRift>of(EntityFluxRift::new, MobCategory.MISC)
					.sized(2.0f, 2.0f)
					.clientTrackingRange(64)
					.fireImmune()
					.build("flux_rift"));

	/** See {@link thaumcraft.common.golems.EntityThaumcraftGolem}'s own doc comment - the second real
	 * living {@link net.minecraft.world.entity.Mob} this port has registered (after {@link #WISP}), same
	 * matching {@code EntityAttributeCreationEvent} entry requirement (see
	 * {@code Thaumcraft#registerEntityAttributes}). */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.golems.EntityThaumcraftGolem>> GOLEM = ENTITY_TYPES.register("golem",
			() -> EntityType.Builder.<thaumcraft.common.golems.EntityThaumcraftGolem>of(thaumcraft.common.golems.EntityThaumcraftGolem::new, MobCategory.MISC)
					.sized(0.7f, 1.1f)
					.clientTrackingRange(64)
					.build("golem"));

	/** First of a new real content area for this port - hostile mobs (see {@code EntityThaumicSlime}'s own
	 * doc comment). {@code sized(2.04f, 2.04f)} matches vanilla's own {@code EntityType.SLIME} registration -
	 * the max-size placeholder before {@link net.minecraft.world.entity.monster.Slime}'s own real
	 * per-instance {@code getDefaultDimensions}/{@code refreshDimensions} (inherited, unchanged) take over.
	 * Same matching {@code EntityAttributeCreationEvent} entry requirement as {@link #WISP}/{@link #GOLEM}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityThaumicSlime>> THAUMIC_SLIME = ENTITY_TYPES.register("thaumic_slime",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityThaumicSlime>of(thaumcraft.common.entities.monster.EntityThaumicSlime::new, MobCategory.MONSTER)
					// Base box PER SIZE UNIT: vanilla's Slime#getDefaultDimensions scales this by getSize(), and the original
					// used 0.51 * size. This used to be 2.04 (the original's size-4 box), which vanilla then multiplied by the size
					// again - a size-4 slime had an 8-block-tall hitbox (found 2026-09-25: its landing particles spawned at the
					// middle of that box, far above the slime).
					.sized(0.51000005f, 0.51000005f)
					.eyeHeight(0.319f)
					.spawnDimensionsScale(4.0f)
					.clientTrackingRange(64)
					.build("thaumic_slime"));

	/** Real {@link net.minecraft.world.entity.monster.Zombie} subclass - {@code sized(0.6f, 1.95f)} matches
	 * vanilla's own {@code EntityType.ZOMBIE} sizing exactly (a plain, non-baby humanoid). Same matching
	 * {@code EntityAttributeCreationEvent} entry requirement. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityBrainyZombie>> BRAINY_ZOMBIE = ENTITY_TYPES.register("brainy_zombie",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityBrainyZombie>of(thaumcraft.common.entities.monster.EntityBrainyZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.clientTrackingRange(64)
					.build("brainy_zombie"));

	/** Real dynamic size ({@code sized(0.6f, 1.95f)} is only the resting/zero-anger placeholder - see
	 * {@code EntityGiantBrainyZombie#getDimensions}'s own real per-tick scaling). */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityGiantBrainyZombie>> GIANT_BRAINY_ZOMBIE = ENTITY_TYPES.register("giant_brainy_zombie",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityGiantBrainyZombie>of(thaumcraft.common.entities.monster.EntityGiantBrainyZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.clientTrackingRange(64)
					.build("giant_brainy_zombie"));

	/** Real fire immunity via the builder flag itself (see {@code EntityFireBat}'s own doc comment) rather
	 * than a per-hit damage-source check. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityFireBat>> FIRE_BAT = ENTITY_TYPES.register("fire_bat",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityFireBat>of(thaumcraft.common.entities.monster.EntityFireBat::new, MobCategory.MONSTER)
					.sized(0.5f, 0.9f)
					.clientTrackingRange(64)
					.fireImmune()
					.build("fire_bat"));

	/** Real {@link net.minecraft.world.entity.monster.Spider} subclass - real vanilla spider texture/model
	 * reused unchanged (no bundled custom texture in the original either - see that class's own doc comment
	 * for why an "illusory" mob makes sense looking identical to a real one). {@code sized(1.4f, 0.9f)}
	 * matches vanilla's own {@code EntityType.SPIDER} sizing exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityMindSpider>> MIND_SPIDER = ENTITY_TYPES.register("mind_spider",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityMindSpider>of(thaumcraft.common.entities.monster.EntityMindSpider::new, MobCategory.MONSTER)
					.sized(1.4f, 0.9f)
					.clientTrackingRange(64)
					.build("mind_spider"));

	/** Never naturally spawned - only ever conjured by {@code FocusMediumSpellBat#execute}, same
	 * {@code MobCategory.MISC} treatment {@link #GOLEM} (also never a natural spawn) already got. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntitySpellBat>> SPELL_BAT = ENTITY_TYPES.register("spell_bat",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntitySpellBat>of(thaumcraft.common.entities.monster.EntitySpellBat::new, MobCategory.MISC)
					.sized(0.5f, 0.9f)
					.clientTrackingRange(64)
					.build("spell_bat"));

	/** Real, documented gap: no custom {@code EntityRenderer}/model yet - no vanilla creature shaped like a
	 * crab exists to reuse the way {@code EntityThaumicSlime}/{@code EntityBrainyZombie}/{@code EntityMindSpider}
	 * all did, same real "hand-rolled entity, no free renderer" situation {@link #FIRE_BAT}/{@link #SPELL_BAT}
	 * already carry. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityEldritchCrab>> ELDRITCH_CRAB = ENTITY_TYPES.register("eldritch_crab",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityEldritchCrab>of(thaumcraft.common.entities.monster.EntityEldritchCrab::new, MobCategory.MONSTER)
					.sized(0.8f, 0.6f)
					.clientTrackingRange(64)
					.build("eldritch_crab"));

	/** First of the "cult faction" arc - see {@code EntityCultist}'s own doc comment. Real {@code
	 * net.minecraft.world.entity.monster.Monster} subclass, {@code sized(0.6f, 1.8f)} matches the original's
	 * own constructor-time {@code setSize(0.6f, 1.8f)} exactly. Only this "regular" melee tier is registered
	 * so far - the original never registered a bare {@code EntityCultist} as its own spawnable type either
	 * (only its subclasses), matching this port's own {@code EntityCultist}/{@code EntityCultistKnight}
	 * split. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.cult.EntityCultistKnight>> CULTIST_KNIGHT = ENTITY_TYPES.register("cultist_knight",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.cult.EntityCultistKnight>of(thaumcraft.common.entities.monster.cult.EntityCultistKnight::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(64)
					.build("cultist_knight"));

	/** Second sub-chunk of the cult faction arc's "regular" tier - see {@code EntityCultistCleric}'s own doc
	 * comment. Same {@code sized(0.6f, 1.8f)} as {@link #CULTIST_KNIGHT} - the original's own constructor
	 * never called {@code setSize} differently for this subclass either. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.cult.EntityCultistCleric>> CULTIST_CLERIC = ENTITY_TYPES.register("cultist_cleric",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.cult.EntityCultistCleric>of(thaumcraft.common.entities.monster.cult.EntityCultistCleric::new, MobCategory.MONSTER)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(64)
					.build("cultist_cleric"));

	/** The cult faction's boss - {@code sized(0.75f, 2.25f)} matches the original's own constructor-time
	 * {@code setSize(0.75f, 2.25f)} exactly. See {@code EntityCultistLeader}'s own doc comment. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.boss.EntityCultistLeader>> CULTIST_LEADER = ENTITY_TYPES.register("cultist_leader",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.boss.EntityCultistLeader>of(thaumcraft.common.entities.monster.boss.EntityCultistLeader::new, MobCategory.MONSTER)
					.sized(0.75f, 2.25f)
					.clientTrackingRange(64)
					.build("cultist_leader"));

	/** {@link thaumcraft.common.entities.projectile.EntityGolemOrb}'s own type - same {@code noSave()}/
	 * small-hitbox/fast-update-interval shape as {@link #FOCUS_PROJECTILE} (also a short-lived, server-only
	 * homing bolt). */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.projectile.EntityGolemOrb>> GOLEM_ORB = ENTITY_TYPES.register("golem_orb",
			() -> EntityType.Builder.<thaumcraft.common.entities.projectile.EntityGolemOrb>of(thaumcraft.common.entities.projectile.EntityGolemOrb::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("golem_orb"));

	/** See {@code EntityInhabitedZombie}'s own doc comment. {@code sized(0.6f, 1.95f)} matches vanilla's own
	 * zombie sizing, same as {@link #BRAINY_ZOMBIE}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityInhabitedZombie>> INHABITED_ZOMBIE = ENTITY_TYPES.register("inhabited_zombie",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityInhabitedZombie>of(thaumcraft.common.entities.monster.EntityInhabitedZombie::new, MobCategory.MONSTER)
					.sized(0.6f, 1.95f)
					.clientTrackingRange(64)
					.build("inhabited_zombie"));

	/** See {@code EntityCultistPortalLesser}'s own doc comment. {@code sized(1.5f, 3.0f)} matches the
	 * original's own constructor-time {@code setSize(1.5f, 3.0f)} exactly. No renderer yet - the original's
	 * own version is a fully custom raw-immediate-mode-GL animated billboard (no model at all), which
	 * doesn't translate to the modern {@code VertexConsumer}/{@code PoseStack} pipeline directly - same
	 * real "no renderer yet" gap {@link #FIRE_BAT}/{@link #SPELL_BAT}/{@link #ELDRITCH_CRAB} already carry,
	 * not a missing-model oversight. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser>> CULTIST_PORTAL_LESSER = ENTITY_TYPES.register("cultist_portal_lesser",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser>of(thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser::new, MobCategory.MONSTER)
					.sized(1.5f, 3.0f)
					.clientTrackingRange(64)
					.build("cultist_portal_lesser"));

	/** See {@code EntityCultistPortalGreater}'s own doc comment. Same {@code sized(1.5f, 3.0f)}/no-renderer
	 * shape as {@link #CULTIST_PORTAL_LESSER}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater>> CULTIST_PORTAL_GREATER = ENTITY_TYPES.register("cultist_portal_greater",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater>of(thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater::new, MobCategory.MONSTER)
					.sized(1.5f, 3.0f)
					.clientTrackingRange(64)
					.build("cultist_portal_greater"));

	/** First of the "tainted creature" faction - see {@code EntityTaintCrawler}'s own doc comment.
	 * {@code sized(0.5f, 0.4f)} matches the original's own constructor-time {@code setSize(0.5f, 0.4f)}
	 * exactly (a real vanilla-silverfish-shaped hitbox). */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintCrawler>> TAINT_CRAWLER = ENTITY_TYPES.register("taint_crawler",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintCrawler>of(thaumcraft.common.entities.monster.tainted.EntityTaintCrawler::new, MobCategory.MONSTER)
					.sized(0.5f, 0.4f)
					.clientTrackingRange(64)
					.build("taint_crawler"));

	/** See {@code EntityTaintSwarm}'s own doc comment. {@code sized(2.0f, 2.0f)} matches the original's own
	 * constructor-time {@code setSize(2.0f, 2.0f)} exactly. No real renderer either - the original's own
	 * version is 100% client-side particle FX with a literal no-op {@code doRender}, same real "no visible
	 * model" shape {@link #FOCUS_PROJECTILE}/{@link #GOLEM_ORB} already reuse {@code RenderFocusProjectile}
	 * for. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintSwarm>> TAINT_SWARM = ENTITY_TYPES.register("taint_swarm",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintSwarm>of(thaumcraft.common.entities.monster.tainted.EntityTaintSwarm::new, MobCategory.MONSTER)
					.sized(2.0f, 2.0f)
					.clientTrackingRange(64)
					.build("taint_swarm"));

	/** See {@code EntityTaintacle}'s own doc comment. {@code sized(0.8f, 3.0f)} matches the original's own
	 * constructor-time {@code setSize(0.8f, 3.0f)} exactly. No real renderer - the original's own version
	 * needs a bespoke {@code ModelBase}-style tentacle model this port hasn't built, same real "no renderer
	 * yet" gap {@link #FIRE_BAT}/{@link #SPELL_BAT}/{@link #TAINT_SWARM} already carry. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintacle>> TAINTACLE = ENTITY_TYPES.register("taintacle",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintacle>of(thaumcraft.common.entities.monster.tainted.EntityTaintacle::new, MobCategory.MONSTER)
					.sized(0.8f, 3.0f)
					.clientTrackingRange(64)
					.build("taintacle"));

	/** See {@code EntityTaintacleSmall}'s own doc comment. Same real no-renderer shape as {@link #TAINTACLE}.
	 * {@code sized(0.22f, 1.0f)} matches the original's own constructor-time {@code setSize(0.22f, 1.0f)}
	 * exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall>> TAINTACLE_SMALL = ENTITY_TYPES.register("taintacle_small",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall>of(thaumcraft.common.entities.monster.tainted.EntityTaintacleSmall::new, MobCategory.MONSTER)
					.sized(0.22f, 1.0f)
					.clientTrackingRange(64)
					.build("taintacle_small"));

	/** The Taint faction's own boss - see {@code EntityTaintacleGiant}'s own doc comment. {@code sized(1.1f,
	 * 6.0f)} matches the original's own constructor-time {@code setSize(1.1f, 6.0f)} exactly. Same real
	 * no-renderer shape as {@link #TAINTACLE}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.boss.EntityTaintacleGiant>> TAINTACLE_GIANT = ENTITY_TYPES.register("taintacle_giant",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.boss.EntityTaintacleGiant>of(thaumcraft.common.entities.monster.boss.EntityTaintacleGiant::new, MobCategory.MONSTER)
					.sized(1.1f, 6.0f)
					.clientTrackingRange(64)
					.build("taintacle_giant"));

	/** See {@code EntityTaintSeed}'s own doc comment. Same real no-renderer shape as {@link #TAINTACLE} -
	 * the original's own {@code ModelTaintSeed} is also a bespoke model this port hasn't built.
	 * {@code sized(1.5f, 1.25f)} matches the original's own constructor-time {@code setSize(1.5f, 1.25f)}
	 * exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintSeed>> TAINT_SEED = ENTITY_TYPES.register("taint_seed",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintSeed>of(thaumcraft.common.entities.monster.tainted.EntityTaintSeed::new, MobCategory.MONSTER)
					.sized(1.5f, 1.25f)
					.clientTrackingRange(64)
					.build("taint_seed"));

	/** See {@code EntityTaintSeedPrime}'s own doc comment. Same real no-renderer shape as {@link #TAINT_SEED}.
	 * {@code sized(2.0f, 2.0f)} matches the original's own constructor-time {@code setSize(2.0f, 2.0f)}
	 * exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime>> TAINT_SEED_PRIME = ENTITY_TYPES.register("taint_seed_prime",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime>of(thaumcraft.common.entities.monster.tainted.EntityTaintSeedPrime::new, MobCategory.MONSTER)
					.sized(2.0f, 2.0f)
					.clientTrackingRange(64)
					.build("taint_seed_prime"));

	/** First of the "construct" faction - see {@code EntityOwnedConstruct}'s/{@code EntityTurretCrossbow}'s
	 * own doc comments. {@code sized(0.95f, 1.25f)} matches the original's own constructor-time {@code
	 * setSize(0.95f, 1.25f)} exactly. Same matching {@code EntityAttributeCreationEvent} entry requirement
	 * as every other real {@link net.minecraft.world.entity.Mob} above. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.construct.EntityTurretCrossbow>> TURRET_CROSSBOW = ENTITY_TYPES.register("turret_crossbow",
			() -> EntityType.Builder.<thaumcraft.common.entities.construct.EntityTurretCrossbow>of(thaumcraft.common.entities.construct.EntityTurretCrossbow::new, MobCategory.MISC)
					.sized(0.95f, 1.25f)
					.clientTrackingRange(64)
					.build("turret_crossbow"));

	/** See {@code EntityTurretCrossbowAdvanced}'s own doc comment. {@code sized(0.95f, 1.5f)} matches the
	 * original's own constructor-time {@code setSize(0.95f, 1.5f)} exactly - taller than {@link
	 * #TURRET_CROSSBOW}'s own 1.25 for the extra armor plating. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced>> TURRET_CROSSBOW_ADVANCED = ENTITY_TYPES.register("turret_crossbow_advanced",
			() -> EntityType.Builder.<thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced>of(thaumcraft.common.entities.construct.EntityTurretCrossbowAdvanced::new, MobCategory.MISC)
					.sized(0.95f, 1.5f)
					.clientTrackingRange(64)
					.build("turret_crossbow_advanced"));

	/** See {@code EntityArcaneBore}'s own doc comment. {@code sized(0.9f, 0.9f)} matches the original's own
	 * constructor-time {@code setSize(0.9f, 0.9f)} exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.construct.EntityArcaneBore>> ARCANE_BORE = ENTITY_TYPES.register("arcane_bore",
			() -> EntityType.Builder.<thaumcraft.common.entities.construct.EntityArcaneBore>of(thaumcraft.common.entities.construct.EntityArcaneBore::new, MobCategory.MISC)
					.sized(0.9f, 0.9f)
					.clientTrackingRange(64)
					.build("arcane_bore"));

	/** The Pech - see {@code EntityPech}'s own doc comment. {@code sized(0.6f, 1.8f)} matches the original's
	 * own constructor-time {@code setSize(0.6f, 1.8f)} exactly. {@code MobCategory.CREATURE} (not {@code
	 * MONSTER}) since it starts passive-until-provoked rather than innately hostile, matching the original's
	 * own lack of any natural-spawn hostility flag (it's {@code EntityMob} for AI/combat plumbing only). */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityPech>> PECH = ENTITY_TYPES.register("pech",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityPech>of(thaumcraft.common.entities.monster.EntityPech::new, MobCategory.CREATURE)
					.sized(0.6f, 1.8f)
					.clientTrackingRange(64)
					.build("pech"));

	/** {@link thaumcraft.common.entities.projectile.EntityEldritchOrb}'s own type - same short-lived,
	 * server-only bolt shape as {@link #GOLEM_ORB}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.projectile.EntityEldritchOrb>> ELDRITCH_ORB = ENTITY_TYPES.register("eldritch_orb",
			() -> EntityType.Builder.<thaumcraft.common.entities.projectile.EntityEldritchOrb>of(thaumcraft.common.entities.projectile.EntityEldritchOrb::new, MobCategory.MISC)
					.sized(0.25f, 0.25f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("eldritch_orb"));

	/** The Eldritch dimension's mid-tier mob - see {@code EntityEldritchGuardian}'s own doc comment.
	 * {@code sized(0.8f, 2.25f)} matches the original's own constructor-time {@code setSize(0.8f, 2.25f)}
	 * exactly. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.EntityEldritchGuardian>> ELDRITCH_GUARDIAN = ENTITY_TYPES.register("eldritch_guardian",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.EntityEldritchGuardian>of(thaumcraft.common.entities.monster.EntityEldritchGuardian::new, MobCategory.MONSTER)
					.sized(0.8f, 2.25f)
					.clientTrackingRange(64)
					.build("eldritch_guardian"));

	/** The Eldritch dimension's real boss - see {@code EntityEldritchWarden}'s own doc comment. Same
	 * {@code sized(0.8f, 2.25f)} as {@link #ELDRITCH_GUARDIAN} - the original's own constructor never called
	 * {@code setSize} differently for this subclass either. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.boss.EntityEldritchWarden>> ELDRITCH_WARDEN = ENTITY_TYPES.register("eldritch_warden",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.boss.EntityEldritchWarden>of(thaumcraft.common.entities.monster.boss.EntityEldritchWarden::new, MobCategory.MONSTER)
					.sized(0.8f, 2.25f)
					.clientTrackingRange(64)
					.build("eldritch_warden"));

	/** The Eldritch dimension's second boss - see {@code EntityEldritchGolem}'s own doc comment.
	 * {@code sized(1.75f, 3.5f)} matches the original's own constructor-time {@code setSize(1.75f, 3.5f)}
	 * exactly. Fire-immune, matching the original's own {@code isImmuneToFire = true}. */
	public static final DeferredHolder<EntityType<?>, EntityType<thaumcraft.common.entities.monster.boss.EntityEldritchGolem>> ELDRITCH_GOLEM = ENTITY_TYPES.register("eldritch_golem",
			() -> EntityType.Builder.<thaumcraft.common.entities.monster.boss.EntityEldritchGolem>of(thaumcraft.common.entities.monster.boss.EntityEldritchGolem::new, MobCategory.MONSTER)
					.sized(1.75f, 3.5f)
					.clientTrackingRange(64)
					.fireImmune()
					.build("eldritch_golem"));

	private ThaumcraftEntities() {
	}
}
