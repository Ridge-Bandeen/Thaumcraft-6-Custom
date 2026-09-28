package thaumcraft.common.entities;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import thaumcraft.common.registry.ThaumcraftEntities;

/**
 * The original's {@code EntitySpecialItem}: the item a Salis Mundus conversion leaves behind when its result isn't a
 * block (a bookshelf turning into the Thaumonomicon). It pops up, then drifts slowly upward instead of falling, and
 * explosions don't destroy it.
 */
public class EntitySpecialItem extends ItemEntity {

	public EntitySpecialItem(EntityType<? extends EntitySpecialItem> type, Level level) {
		super(type, level);
	}

	public EntitySpecialItem(Level level, double x, double y, double z, ItemStack stack) {
		this(ThaumcraftEntities.SPECIAL_ITEM.get(), level);
		setPos(x, y, z);
		setItem(stack);
		setYRot((float) (Math.random() * 360.0));
		setDeltaMovement(Math.random() * 0.2 - 0.1, 0.2, Math.random() * 0.2 - 0.1);
	}

	@Override
	public void tick() {
		if (tickCount > 1) {
			Vec3 m = getDeltaMovement();
			double y = m.y;
			if (y > 0.0) y *= 0.9;
			y += 0.04; // cancels item gravity: it hangs and drifts rather than falls
			setDeltaMovement(m.x, y, m.z);
		}
		super.tick();
	}

	@Override
	public boolean hurt(DamageSource source, float amount) {
		return !source.is(DamageTypeTags.IS_EXPLOSION) && super.hurt(source, amount);
	}
}
