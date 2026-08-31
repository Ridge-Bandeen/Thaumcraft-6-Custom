package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.common.entities.projectile.EntityFocusProjectile;

/** First entity type this port has needed - see {@link EntityFocusProjectile}'s own doc comment. */
public final class ThaumcraftEntities {

	public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, "thaumcraft");

	public static final DeferredHolder<EntityType<?>, EntityType<EntityFocusProjectile>> FOCUS_PROJECTILE = ENTITY_TYPES.register("focus_projectile",
			() -> EntityType.Builder.<EntityFocusProjectile>of(EntityFocusProjectile::new, MobCategory.MISC)
					.sized(0.15f, 0.15f)
					.clientTrackingRange(64)
					.updateInterval(10)
					.noSave()
					.build("focus_projectile"));

	private ThaumcraftEntities() {
	}
}
