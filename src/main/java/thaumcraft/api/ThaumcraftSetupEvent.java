package thaumcraft.api;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.Event;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.common.lib.crafting.ThaumcraftEntityTags;
import thaumcraft.common.lib.crafting.ThaumcraftObjectTags;

/**
 * Posted once on the mod event bus (listen with {@code modEventBus.addListener(ThaumcraftSetupEvent.class, ...)}) when
 * Thaumcraft has finished its own setup registrations - the successor of
 * the original's {@code AspectRegistryEvent}, which it posted right after registering its own aspects. It runs on the
 * main thread (from {@code enqueueWork} at the end of common setup), after every mod's parallel common setup, on both
 * the client and a dedicated server.
 *
 * <p>This is the one safe point for an addon to add to Thaumcraft's registries: item and entity aspects (the methods
 * below), and the public registries of scans ({@code ScanningManager.addScannableThing}), Salis Mundus triggers
 * ({@code IDustTrigger.registerDustTrigger}), seals and golem parts. Registering earlier, from an addon's own common
 * setup, races Thaumcraft's setup, which clears and refills these tables. A later registration of the same item or
 * entity replaces Thaumcraft's own.
 *
 * <p>It is a mod-bus event: {@code NeoForge.EVENT_BUS} is not running yet during common setup, so a post there
 * reached no one.
 */
public class ThaumcraftSetupEvent extends Event implements net.neoforged.fml.event.IModBusEvent {

	/** Fixed aspects for one item. */
	public void registerObjectTag(Item item, AspectList aspects) {
		ThaumcraftObjectTags.item(item, aspects);
	}

	/** Fixed aspects for every item in a tag. */
	public void registerObjectTag(TagKey<Item> tag, AspectList aspects) {
		ThaumcraftObjectTags.tag(tag, aspects);
	}

	/** Aspects added on top of what the item's recipes give (the original's complex object tags). */
	public void registerComplexObjectTag(Item item, AspectList aspects) {
		ThaumcraftObjectTags.complexItem(item, aspects);
	}

	/** Aspects added on top of what the recipes give, for every item in a tag. */
	public void registerComplexObjectTag(TagKey<Item> tag, AspectList aspects) {
		ThaumcraftObjectTags.complexTag(tag, aspects);
	}

	/** Aspects of an entity type, as the Thaumometer scans them. */
	public void registerEntityTag(EntityType<?> type, AspectList aspects) {
		ThaumcraftEntityTags.register(type, aspects);
	}
}
