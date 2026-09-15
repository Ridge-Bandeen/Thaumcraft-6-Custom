package thaumcraft.common.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.Thaumcraft;

/**
 * Replaces the old (1.12.2) {@code SoundsTC} - real, restored 2026-09-15 during the full parity audit
 * (the client-only sound layer's own first chunk). All 65 real custom sounds the true original registered,
 * matching every real {@code sounds.json} entry (already present, unused, in this port's own resource
 * tree the whole time - only the Java-side registration was ever missing) and every real {@code .ogg}
 * file backing them (confirmed via a real file-existence check across every real sound reference, not
 * assumed - caught and fixed one real pre-existing typo along the way: {@code sounds.json}'s own
 * {@code runicshieldecharge} key didn't match the real {@code runicshieldcharge} id every other real
 * source, including the {@code .ogg} filename itself, actually used).
 *
 * <p>{@link net.minecraft.world.level.block.SoundType}s the true original built from 4 of these
 * ({@code GORE}/{@code CRYSTAL}/{@code JAR}/{@code URN}) are not ported here - each real consumer wires
 * its own {@code SoundType} substitute already (see e.g. {@code BlockTaintFibre}'s own doc comment),
 * this class only needed to carry the raw {@link SoundEvent}s themselves.
 */
public final class ThaumcraftSounds {

	public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, Thaumcraft.MODID);

	public static final DeferredHolder<SoundEvent, SoundEvent> ZAP = SOUNDS.register("zap", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "zap")));
	public static final DeferredHolder<SoundEvent, SoundEvent> HEARTBEAT = SOUNDS.register("heartbeat", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "heartbeat")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SPILL = SOUNDS.register("spill", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "spill")));
	public static final DeferredHolder<SoundEvent, SoundEvent> BUBBLE = SOUNDS.register("bubble", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "bubble")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CREAK = SOUNDS.register("creak", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "creak")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SQUEEK = SOUNDS.register("squeek", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "squeek")));
	public static final DeferredHolder<SoundEvent, SoundEvent> JAR = SOUNDS.register("jar", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "jar")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SWARM = SOUNDS.register("swarm", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "swarm")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SWARMATTACK = SOUNDS.register("swarmattack", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "swarmattack")));
	public static final DeferredHolder<SoundEvent, SoundEvent> FLY = SOUNDS.register("fly", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "fly")));
	public static final DeferredHolder<SoundEvent, SoundEvent> KEY = SOUNDS.register("key", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "key")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TICKS = SOUNDS.register("ticks", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "ticks")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CLACK = SOUNDS.register("clack", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "clack")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PUMP = SOUNDS.register("pump", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pump")));
	public static final DeferredHolder<SoundEvent, SoundEvent> POOF = SOUNDS.register("poof", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "poof")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PAGE = SOUNDS.register("page", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "page")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PAGETURN = SOUNDS.register("pageturn", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pageturn")));
	public static final DeferredHolder<SoundEvent, SoundEvent> LEARN = SOUNDS.register("learn", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "learn")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WRITE = SOUNDS.register("write", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "write")));
	public static final DeferredHolder<SoundEvent, SoundEvent> ERASE = SOUNDS.register("erase", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "erase")));
	public static final DeferredHolder<SoundEvent, SoundEvent> BRAIN = SOUNDS.register("brain", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "brain")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRYSTAL = SOUNDS.register("crystal", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "crystal")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WISPDEAD = SOUNDS.register("wispdead", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "wispdead")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WISPLIVE = SOUNDS.register("wisplive", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "wisplive")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WAND = SOUNDS.register("wand", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "wand")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WANDFAIL = SOUNDS.register("wandfail", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "wandfail")));
	public static final DeferredHolder<SoundEvent, SoundEvent> RUMBLE = SOUNDS.register("rumble", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "rumble")));
	public static final DeferredHolder<SoundEvent, SoundEvent> ICE = SOUNDS.register("ice", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "ice")));
	public static final DeferredHolder<SoundEvent, SoundEvent> JACOBS = SOUNDS.register("jacobs", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "jacobs")));
	public static final DeferredHolder<SoundEvent, SoundEvent> HHOFF = SOUNDS.register("hhoff", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "hhoff")));
	public static final DeferredHolder<SoundEvent, SoundEvent> HHON = SOUNDS.register("hhon", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "hhon")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_IDLE = SOUNDS.register("pech_idle", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_idle")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_TRADE = SOUNDS.register("pech_trade", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_trade")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_DICE = SOUNDS.register("pech_dice", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_dice")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_HIT = SOUNDS.register("pech_hit", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_hit")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_DEATH = SOUNDS.register("pech_death", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_death")));
	public static final DeferredHolder<SoundEvent, SoundEvent> PECH_CHARGE = SOUNDS.register("pech_charge", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "pech_charge")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SHOCK = SOUNDS.register("shock", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "shock")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAFTFAIL = SOUNDS.register("craftfail", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "craftfail")));
	public static final DeferredHolder<SoundEvent, SoundEvent> SCAN = SOUNDS.register("scan", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "scan")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRAFTSTART = SOUNDS.register("craftstart", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "craftstart")));
	public static final DeferredHolder<SoundEvent, SoundEvent> RUNICSHIELDEFFECT = SOUNDS.register("runicshieldeffect", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "runicshieldeffect")));
	public static final DeferredHolder<SoundEvent, SoundEvent> RUNICSHIELDCHARGE = SOUNDS.register("runicshieldcharge", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "runicshieldcharge")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WIND = SOUNDS.register("wind", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "wind")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TOOL = SOUNDS.register("tool", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "tool")));
	public static final DeferredHolder<SoundEvent, SoundEvent> GORE = SOUNDS.register("gore", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "gore")));
	public static final DeferredHolder<SoundEvent, SoundEvent> TENTACLE = SOUNDS.register("tentacle", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "tentacle")));
	public static final DeferredHolder<SoundEvent, SoundEvent> UPGRADE = SOUNDS.register("upgrade", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "upgrade")));
	public static final DeferredHolder<SoundEvent, SoundEvent> WHISPERS = SOUNDS.register("whispers", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "whispers")));
	public static final DeferredHolder<SoundEvent, SoundEvent> MONOLITH = SOUNDS.register("monolith", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "monolith")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INFUSER = SOUNDS.register("infuser", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infuser")));
	public static final DeferredHolder<SoundEvent, SoundEvent> INFUSERSTART = SOUNDS.register("infuserstart", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "infuserstart")));
	public static final DeferredHolder<SoundEvent, SoundEvent> EGIDLE = SOUNDS.register("egidle", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "egidle")));
	public static final DeferredHolder<SoundEvent, SoundEvent> EGATTACK = SOUNDS.register("egattack", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "egattack")));
	public static final DeferredHolder<SoundEvent, SoundEvent> EGDEATH = SOUNDS.register("egdeath", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "egdeath")));
	public static final DeferredHolder<SoundEvent, SoundEvent> EGSCREECH = SOUNDS.register("egscreech", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "egscreech")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRABCLAW = SOUNDS.register("crabclaw", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "crabclaw")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRABDEATH = SOUNDS.register("crabdeath", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "crabdeath")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CRABTALK = SOUNDS.register("crabtalk", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "crabtalk")));
	public static final DeferredHolder<SoundEvent, SoundEvent> CHANT = SOUNDS.register("chant", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "chant")));
	public static final DeferredHolder<SoundEvent, SoundEvent> COINS = SOUNDS.register("coins", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "coins")));
	public static final DeferredHolder<SoundEvent, SoundEvent> URNBREAK = SOUNDS.register("urnbreak", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "urnbreak")));
	public static final DeferredHolder<SoundEvent, SoundEvent> EVILPORTAL = SOUNDS.register("evilportal", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "evilportal")));
	public static final DeferredHolder<SoundEvent, SoundEvent> GRIND = SOUNDS.register("grind", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "grind")));
	public static final DeferredHolder<SoundEvent, SoundEvent> DUST = SOUNDS.register("dust", () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "dust")));

	private ThaumcraftSounds() {
	}
}
