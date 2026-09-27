package thaumcraft.api.items;

import java.util.Map;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.casters.FocusPackage;

/**
 * ItemStacks no longer carry freeform NBT (that's an escape hatch now, the {@code CUSTOM_DATA} component -
 * see {@link thaumcraft.api.golems.ProvisionRequest} for a case where that shortcut was reasonable). For
 * something as central and long-lived as an essentia container's held aspects, a proper typed
 * {@link DataComponentType} is the idiomatic replacement for what {@code IEssentiaContainerItem}
 * implementations used to store as raw NBT: it's persisted, synced to the client, and shows up in
 * ItemStack equality/hashing for free.
 */
public final class ThaumcraftDataComponents {

	public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, "thaumcraft");

	/** The {@link AspectList} held by an essentia container item (crystals, phials, jar labels, etc). */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<AspectList>> ASPECTS = DATA_COMPONENTS.register("aspects",
			() -> DataComponentType.<AspectList>builder()
					.persistent(AspectList.CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(AspectList.CODEC))
					.build());

	/** Was the {@code "tc.charge"} NBT int tag on {@link thaumcraft.api.items.IRechargable} items. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> CHARGE = DATA_COMPONENTS.register("charge",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Current stored vis in a caster (wand). Was a per-item internal field in the original, since 1.12.2
	 *  casters didn't persist their own charge across saves the way this needs to now. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> WAND_VIS = DATA_COMPONENTS.register("wand_vis",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/**
	 * The focus currently loaded into a caster (wand). Was the {@code "focus"} NBT sub-compound.
	 *
	 * Wrapped in {@link WandFocus} rather than storing a bare {@link ItemStack} directly - found by the
	 * test suite, not by playing the game: {@code ItemStack} deliberately doesn't override
	 * {@code equals()}/{@code hashCode()} (vanilla's own convention - use {@code ItemStack#matches}/
	 * {@code isSameItemSameComponents} instead), which trips the exact same dev-mode-only data-component
	 * validator {@link AspectList}'s missing equals/hashCode did (see that class's own doc comment for the
	 * full story). Vanilla's own {@code ItemStack}-holding components (bundle contents, container
	 * contents) never store a bare {@code ItemStack} either, always a small wrapper record - this follows
	 * that same idiomatic pattern.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandFocus>> WAND_FOCUS = DATA_COMPONENTS.register("wand_focus",
			() -> DataComponentType.<WandFocus>builder()
					.persistent(WandFocus.CODEC)
					.networkSynchronized(WandFocus.STREAM_CODEC)
					.build());

	/** See {@link #WAND_FOCUS}'s doc comment for why this wrapper exists. */
	public record WandFocus(ItemStack stack) {
		public static final Codec<WandFocus> CODEC = ItemStack.CODEC.xmap(WandFocus::new, WandFocus::stack);
		public static final StreamCodec<RegistryFriendlyByteBuf, WandFocus> STREAM_CODEC = ItemStack.STREAM_CODEC.map(WandFocus::new, WandFocus::stack);
	}

	/**
	 * The block a real {@code IFocusBlockPicker} focus (e.g. {@code FocusEffectExchange}) has stored via a
	 * real sneak-right-click on a bare block. Was the {@code "picked"} NBT sub-compound. Restored 2026-09-15
	 * alongside {@code FocusEffectExchange}/{@code FocusMediumPlan}. Wrapped the same way {@link #WAND_FOCUS}
	 * is, for the same real reason - see that field's own doc comment.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<WandFocus>> WAND_PICKED_BLOCK = DATA_COMPONENTS.register("wand_picked_block",
			() -> DataComponentType.<WandFocus>builder()
					.persistent(WandFocus.CODEC)
					.networkSynchronized(WandFocus.STREAM_CODEC)
					.build());

	/**
	 * Levels of each {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment} an item carries, keyed
	 * by the enchantment's enum name. Was the {@code "infench"} NBTTagList (a list of {@code {id: short,
	 * lvl: short}} compounds, {@code id} an enum ordinal) on the original {@code EnumInfusionEnchantment} -
	 * a name-keyed map is the same idea with a more stable (rename-and-reorder-safe) key than a raw ordinal.
	 * {@link Map#copyOf} on both directions keeps the stored value immutable, same reasoning as
	 * {@link #WAND_FOCUS}'s wrapper - see that field's doc comment. No wrapper record is needed here (unlike
	 * {@code WandFocus}) since a plain {@link Map} already has well-defined {@code equals()}/{@code hashCode()}.
	 */
	private static final Codec<Map<String, Integer>> INFUSION_ENCHANTMENTS_CODEC =
			Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(Map::copyOf, Map::copyOf);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Map<String, Integer>>> INFUSION_ENCHANTMENTS = DATA_COMPONENTS.register("infusion_enchantments",
			() -> DataComponentType.<Map<String, Integer>>builder()
					.persistent(INFUSION_ENCHANTMENTS_CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(INFUSION_ENCHANTMENTS_CODEC))
					.build());

	/**
	 * A "pre-charged" Node Transducer's link target - was 4 raw NBT ints ({@code linkX}/{@code linkY}/
	 * {@code linkZ}/{@code linkDim}, the last a raw dimension id) on the original's dropped-and-relinked
	 * {@code BlockMirrorItem} stack. {@code linkDim} becomes a real {@link ResourceKey} here (dimensions
	 * aren't raw ints anymore - same fix {@code GolemHelper} already needed), and the 3 position ints become
	 * one real {@link BlockPos}, both already-{@code Codec}-equipped vanilla types.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<MirrorLink>> MIRROR_LINK = DATA_COMPONENTS.register("mirror_link",
			() -> DataComponentType.<MirrorLink>builder()
					.persistent(MirrorLink.CODEC)
					.networkSynchronized(MirrorLink.STREAM_CODEC)
					.build());

	public record MirrorLink(ResourceKey<Level> dimension, BlockPos pos) {
		public static final Codec<MirrorLink> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(MirrorLink::dimension),
				BlockPos.CODEC.fieldOf("pos").forGetter(MirrorLink::pos)
			).apply(inst, MirrorLink::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, MirrorLink> STREAM_CODEC = StreamCodec.composite(
				ResourceKey.streamCodec(Registries.DIMENSION), MirrorLink::dimension,
				BlockPos.STREAM_CODEC, MirrorLink::pos,
				MirrorLink::new);
	}

	/**
	 * A player-assembled focus package (built at the Focal Manipulator, see {@code TileFocalManipulator}'s
	 * own doc comment) attached to an {@link thaumcraft.common.items.casters.ItemFocus} stack - was an
	 * arbitrary NBT sub-compound on the original. {@link FocusPackage} already carries its own real
	 * {@code serialize()}/{@code deserialize(CompoundTag, Level)} pair (built for a different reason -
	 * copying a live in-flight cast, see that method's own doc comment on a bug it caught) - reused here
	 * as-is rather than inventing a second, parallel node-graph {@code Codec}. {@code deserialize} is
	 * always called with a {@code null} {@link Level} on this path: a stored package is a reusable
	 * template, never a live cast (no caster/world attached yet) - {@code FocusEngine#castFocusPackage}'s
	 * own {@code initialize(caster)} step (already called on every real cast) overwrites both fields
	 * before the package is ever used, so the {@code null} is never actually read.
	 *
	 * Wrapped in {@link AssembledFocus} for the same reason {@link #WAND_FOCUS} wraps a bare
	 * {@code ItemStack} - see that field's own doc comment. {@code FocusPackage} is a plain mutable class
	 * with no {@code equals()}/{@code hashCode()} of its own (same dev-mode data-component validator this
	 * session already hit twice), and unlike {@code AspectList} isn't a good candidate to give real value
	 * semantics to (it's the live in-flight-cast object elsewhere in this same API) - the wrapper record's
	 * own synthesized {@code equals()}/{@code hashCode()} satisfies the validator without touching it.
	 */
	private static final Codec<FocusPackage> FOCUS_PACKAGE_CODEC = CompoundTag.CODEC.xmap(tag -> {
		FocusPackage p = new FocusPackage();
		p.deserialize(tag, null);
		return p;
	}, FocusPackage::serialize);

	public static final DeferredHolder<DataComponentType<?>, DataComponentType<AssembledFocus>> ASSEMBLED_FOCUS = DATA_COMPONENTS.register("assembled_focus",
			() -> DataComponentType.<AssembledFocus>builder()
					.persistent(AssembledFocus.CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(AssembledFocus.CODEC))
					.build());

	/** See {@link #ASSEMBLED_FOCUS}'s doc comment for why this wrapper exists. */
	public record AssembledFocus(FocusPackage focusPackage) {
		public static final Codec<AssembledFocus> CODEC = FOCUS_PACKAGE_CODEC.xmap(AssembledFocus::new, AssembledFocus::focusPackage);
	}

	/**
	 * A golem placer's stored assembled properties + accumulated rank XP - was the {@code "props"} (long) +
	 * {@code "xp"} (int) NBT tags on the original {@code ItemGolemPlacer} stack (see
	 * {@code thaumcraft.common.golems.ItemGolemPlacer} and {@code GolemProperties}/{@code EntityThaumcraftGolem}
	 * for the packed shape being carried). A small wrapper record rather than two separate components - the
	 * two values are only ever meaningful together (rank XP without the properties that define the golem's
	 * rank curve is meaningless), same one-unit-of-data reasoning as {@link #MIRROR_LINK}.
	 */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<GolemPlacerData>> GOLEM_PLACER_DATA = DATA_COMPONENTS.register("golem_placer_data",
			() -> DataComponentType.<GolemPlacerData>builder()
					.persistent(GolemPlacerData.CODEC)
					.networkSynchronized(GolemPlacerData.STREAM_CODEC)
					.build());

	public record GolemPlacerData(long props, int xp) {
		public static final Codec<GolemPlacerData> CODEC = RecordCodecBuilder.create(inst -> inst.group(
				Codec.LONG.fieldOf("props").forGetter(GolemPlacerData::props),
				Codec.INT.fieldOf("xp").forGetter(GolemPlacerData::xp)
			).apply(inst, GolemPlacerData::new));

		public static final StreamCodec<RegistryFriendlyByteBuf, GolemPlacerData> STREAM_CODEC = StreamCodec.composite(
				ByteBufCodecs.VAR_LONG, GolemPlacerData::props,
				ByteBufCodecs.VAR_INT, GolemPlacerData::xp,
				GolemPlacerData::new);
	}

	/** Was the {@code "mask"} NBT int tag on the original {@code ItemFortressArmor} - which of the 3 real
	 * Fortress Helm mask upgrades (0=Grinning Devil, 1=Angry Ghost, 2=Sipping Fiend) is applied. Absent means
	 * no mask. Orthogonal to {@link #FORTRESS_GOGGLES} - the original's own real design lets a helm carry
	 * both at once (see {@code thaumcraft.common.items.armor.ItemFortressArmor}'s own doc comment for the
	 * real, documented simplification this port makes there instead). */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> FORTRESS_MASK = DATA_COMPONENTS.register("fortress_mask",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Was the {@code "TC.RUNIC"} NBT byte tag - how many times a real
	 * {@link thaumcraft.common.lib.crafting.RunicArmorRecipe} craft has augmented this item. Absent (or 0)
	 * means no runic charge at all. See that recipe class's own doc comment, and
	 * {@code thaumcraft.common.lib.events.RunicShieldEvents} for the real periodic-absorption-shield payoff
	 * this drives. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> RUNIC_CHARGE = DATA_COMPONENTS.register("runic_charge",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	/** Was the {@code "goggles"} NBT tag (presence-only) on the original {@code ItemFortressArmor} - the
	 * Helm of Revealing upgrade. See {@link #FORTRESS_MASK}'s own doc comment. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Boolean>> FORTRESS_GOGGLES = DATA_COMPONENTS.register("fortress_goggles",
			() -> DataComponentType.<Boolean>builder()
					.persistent(Codec.BOOL)
					.networkSynchronized(ByteBufCodecs.BOOL)
					.build());

	/** A jar's label - restored 2026-09-27 - was the {@code "AspectFilter"} NBT string tag on the original's
	 * {@code BlockJarItem}: which single aspect a picked-up jar is locked to (via {@link
	 * thaumcraft.api.blocks.ILabelable}), carried onto the dropped item so replacing it keeps the lock. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Aspect>> ASPECT_FILTER = DATA_COMPONENTS.register("aspect_filter",
			() -> DataComponentType.<Aspect>builder()
					.persistent(Aspect.CODEC)
					.networkSynchronized(ByteBufCodecs.fromCodec(Aspect.CODEC))
					.build());

	/** A Brain in a Jar's stored XP - restored 2026-09-27 - was the {@code "xp"} NBT int tag on the original's
	 * {@code BlockJarBrainItem}: a broken jar keeps what it was holding. */
	public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> JAR_XP = DATA_COMPONENTS.register("jar_xp",
			() -> DataComponentType.<Integer>builder()
					.persistent(Codec.INT)
					.networkSynchronized(ByteBufCodecs.VAR_INT)
					.build());

	private ThaumcraftDataComponents() {
	}
}
