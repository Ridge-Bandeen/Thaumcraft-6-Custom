package thaumcraft;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Client-only entry point (replaces the old {@code ClientProxy}). Does not load on a dedicated
 * server, so client-only classes (renderers, GUIs, etc.) are safe to reference here as they get
 * ported.
 */
@Mod(value = Thaumcraft.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public class ThaumcraftClient {
    public ThaumcraftClient(ModContainer container) {
    }

    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        Thaumcraft.LOGGER.info("Thaumcraft {} client setup", Thaumcraft.VERSION);
    }
}
