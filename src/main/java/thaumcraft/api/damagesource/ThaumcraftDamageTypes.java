package thaumcraft.api.damagesource;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Replaces the old (1.12.2) {@code DamageSourceThaumcraft} / {@code DamageSourceIndirectThaumcraftEntity}
 * classes. Damage sources are no longer subclassable: since 1.19.4 a "damage source" is a
 * {@link Holder} of a data-driven {@link net.minecraft.world.damagesource.DamageType} (defined as JSON
 * under {@code data/thaumcraft/damage_type/}), and armor/shield/effect-bypass behaviour that used to be
 * set with builder methods (e.g. {@code setDamageBypassesArmor()}) is now expressed as datapack tags on
 * that damage type (see {@code data/minecraft/tags/damage_type/bypasses_armor.json}).
 *
 * This class just holds the {@link ResourceKey}s for our damage types and the equivalent of the old
 * {@code causeXDamage(...)} factory methods.
 */
public final class ThaumcraftDamageTypes {

    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> TAINT = key("taint");
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> TENTACLE = key("tentacle");
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> SWARM = key("swarm");
    public static final ResourceKey<net.minecraft.world.damagesource.DamageType> DISSOLVE = key("dissolve");

    private ThaumcraftDamageTypes() {
    }

    private static ResourceKey<net.minecraft.world.damagesource.DamageType> key(String path) {
        return ResourceKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath("thaumcraft", path));
    }

    private static DamageSource source(Level level, ResourceKey<net.minecraft.world.damagesource.DamageType> key) {
        return new DamageSource(holder(level, key));
    }

    private static Holder<net.minecraft.world.damagesource.DamageType> holder(Level level, ResourceKey<net.minecraft.world.damagesource.DamageType> key) {
        return level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(key);
    }

    public static DamageSource taint(Level level) {
        return source(level, TAINT);
    }

    public static DamageSource dissolve(Level level) {
        return source(level, DISSOLVE);
    }

    public static DamageSource causeSwarmDamage(LivingEntity attacker) {
        return new DamageSource(holder(attacker.level(), SWARM), attacker);
    }

    public static DamageSource causeTentacleDamage(LivingEntity attacker) {
        return new DamageSource(holder(attacker.level(), TENTACLE), attacker);
    }

    public static DamageSource causeTentacleDamage(Entity indirectSource, LivingEntity attacker) {
        return new DamageSource(holder(attacker.level(), TENTACLE), indirectSource, attacker);
    }
}
