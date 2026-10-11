package thaumcraft.common.entities.projectile;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * The original's {@code EntityGolemDart}: what a golem's Dart Launcher arm fires - an ordinary arrow with a small
 * (0.2) hitbox, drawn smaller ({@code RenderDart}). Fired by a golem it can't be picked up, as an arrow from any mob.
 */
public class EntityGolemDart extends AbstractArrow {

	public EntityGolemDart(EntityType<? extends EntityGolemDart> type, Level level) {
		super(type, level);
	}

	public EntityGolemDart(Level level, LivingEntity shooter) {
		super(ThaumcraftEntities.GOLEM_DART.get(), shooter, level, new ItemStack(Items.ARROW), null);
	}

	@Override
	protected ItemStack getDefaultPickupItem() {
		return new ItemStack(Items.ARROW);
	}
}
