package thaumcraft.common.items.baubles;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;

/**
 * Replaces the old (1.12.2) {@code ItemCloudRing}. The original's real double-jump ability read the client's
 * raw jump keybind state every frame while airborne (a {@code KeyBinding} poll plus a custom
 * packet-to-server sync) - this port has no client key-input infrastructure at all yet (nothing else has
 * needed one), so that ability is a deliberate, documented cut rather than something faked with a worse
 * substitute (e.g. triggering off {@code LivingJumpEvent}, which only fires for the *first* jump and can't
 * tell a held key from a fresh press). Worth a real follow-up once this port has a reason to add key-binding
 * plumbing for something else too.
 *
 * The ring's other real effect - reducing fall damage while worn - has no such dependency, so it IS ported;
 * see {@link thaumcraft.common.lib.events.WornGearEvents#fallDamage}.
 */
public class ItemCloudRing extends Item {

	public ItemCloudRing(Properties properties) {
		super(properties.rarity(Rarity.RARE));
	}
}
