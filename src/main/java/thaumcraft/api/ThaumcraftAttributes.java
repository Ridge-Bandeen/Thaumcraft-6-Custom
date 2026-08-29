package thaumcraft.api;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom entity attributes. Split out of the old (1.12.2) {@code ThaumcraftApiHelper}, which just
 * instantiated a {@code RangedAttribute} directly as a static field: since 1.16, attributes are a
 * proper datapack-aware registry and must go through a {@link DeferredRegister} like any other
 * registry content, so this needs its own {@code register(modEventBus)} call from the main mod
 * class instead of being usable at class-load time.
 */
public final class ThaumcraftAttributes {

    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(Registries.ATTRIBUTE, "thaumcraft");

    /** Marks a mob as a "champion" of a particular taint mutation; -2 = not a champion. */
    public static final DeferredHolder<Attribute, Attribute> CHAMPION_MOD = ATTRIBUTES.register("champion_mod",
            () -> new RangedAttribute("attribute.name.thaumcraft.champion_mod", -2D, -2D, 100D).setSyncable(true));

    private ThaumcraftAttributes() {
    }
}
