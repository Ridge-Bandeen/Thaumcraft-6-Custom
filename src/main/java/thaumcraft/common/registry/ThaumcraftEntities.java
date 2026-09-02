package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
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

	private ThaumcraftEntities() {
	}
}
