package thaumcraft.common.tiles.devices;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import thaumcraft.common.blocks.devices.BlockArcaneEar;
import thaumcraft.common.registry.ThaumcraftBlockEntities;

/**
 * Replaces the old (1.12.2) {@code TileArcaneEar} - a note-block-like device with two roles: right-clicking
 * cycles through 25 pitches and plays the resulting note back as audio feedback; separately, it listens for
 * ANY note block anywhere in the same dimension playing that exact pitch on a matching "tone" (instrument),
 * within 64 blocks, and reacts by pulsing redstone for 10 ticks - or, for the {@code arcane_ear_toggle}
 * variant (see {@link BlockArcaneEar#isToggle()}), toggling permanently instead.
 *
 * {@code net.neoforged.neoforge.event.level.NoteBlockEvent.Play} is another Forge-era event that survived
 * into NeoForge 1.21.1 with the same shape - the whole cross-dimension listening mechanic ports over close
 * to mechanically as a result. Two new {@code @SubscribeEvent} handlers on {@code Thaumcraft} itself
 * populate {@link #NOTE_EVENTS} (a per-dimension list, mirroring the original's own static field) whenever a
 * real note block plays anywhere, and clear it once per level per tick via {@code LevelTickEvent.Post} -
 * the same "populate mid-tick, clear at tick end" window the original had via its own
 * {@code WorldEvents}/{@code ServerEvents} pair.
 *
 * Real improvement over the original, not just a simplification: the "tone" (which instrument the ear reacts
 * to) is now the actual support block's real {@code BlockState#instrument()} - the same per-block accessor
 * vanilla's own {@link NoteBlock} placement logic uses - instead of the original's hand-picked 10-case
 * material/block if-chain (stone/sand/glass/wood/clay/gold/wool/packed_ice/bone_block only). Every block with
 * a real vanilla instrument mapping (which is most of them) works correctly now, not just those ten.
 *
 * Simplified vs the original: the note sound is played directly via {@code Level#playSound} (broadcasts to
 * nearby players on its own when called server-side with a null player) instead of the original's
 * block-event round trip ({@code addBlockEvent}/{@code eventReceived}) - that indirection existed to sync a
 * client-side visual/audio trigger in 1.12.2's networking model and has no functional purpose here. No note
 * particle (client FX, dropped like everywhere else this session).
 */
public class TileArcaneEar extends BlockEntity {

	private static final Map<ResourceKey<Level>, List<NoteEvent>> NOTE_EVENTS = new HashMap<>();

	private record NoteEvent(BlockPos pos, int instrumentOrdinal, int noteId) {
	}

	private int note;
	private int redstoneSignal;

	public TileArcaneEar(BlockPos pos, BlockState state) {
		super(ThaumcraftBlockEntities.ARCANE_EAR.get(), pos, state);
	}

	/** Called from {@code Thaumcraft}'s {@code NoteBlockEvent.Play} handler whenever any real note block
	 * plays anywhere - records it for every Arcane Ear in that dimension to check against this tick. */
	public static void recordNoteEvent(Level level, BlockPos pos, NoteBlockInstrument instrument, int noteId) {
		if (level.isClientSide) return;
		NOTE_EVENTS.computeIfAbsent(level.dimension(), key -> new ArrayList<>())
				.add(new NoteEvent(pos.immutable(), instrument.ordinal(), noteId));
	}

	/** Called from {@code Thaumcraft}'s {@code LevelTickEvent.Post} handler - clears this dimension's list
	 * once every Arcane Ear tile has had a chance to check it this tick. */
	public static void clearNoteEvents(Level level) {
		List<NoteEvent> events = NOTE_EVENTS.get(level.dimension());
		if (events != null) events.clear();
	}

	public static void tick(Level level, BlockPos pos, BlockState state, TileArcaneEar tile) {
		if (level.isClientSide) return;
		boolean toggle = ((BlockArcaneEar) state.getBlock()).isToggle();

		if (!toggle && tile.redstoneSignal > 0) {
			tile.redstoneSignal--;
			if (tile.redstoneSignal == 0) {
				level.setBlock(pos, level.getBlockState(pos).setValue(BlockStateProperties.ENABLED, false), 3);
			}
		}

		List<NoteEvent> events = NOTE_EVENTS.get(level.dimension());
		if (events == null || events.isEmpty()) return;

		int tone = tile.instrumentAt(level, pos, level.getBlockState(pos)).ordinal();
		for (NoteEvent event : events) {
			if (event.instrumentOrdinal() == tone && event.noteId() == tile.note && event.pos().distSqr(pos) <= 4096.0) {
				BlockState current = level.getBlockState(pos);
				tile.playNote(level, pos, current);
				if (toggle) {
					level.setBlock(pos, current.cycle(BlockStateProperties.ENABLED), 3);
				} else {
					tile.redstoneSignal = 10;
					level.setBlock(pos, current.setValue(BlockStateProperties.ENABLED, true), 3);
				}
				break;
			}
		}
	}

	/** Right-click handler: cycles to the next pitch (0-24) and plays it back as audio feedback - doesn't
	 * trigger the redstone/toggle output itself, same as the original. */
	public void changePitchAndPlay(Level level, BlockPos pos, BlockState state) {
		note = (note + 1) % 25;
		setChanged();
		playNote(level, pos, state);
	}

	private void playNote(Level level, BlockPos pos, BlockState state) {
		NoteBlockInstrument instrument = instrumentAt(level, pos, state);
		float pitch = NoteBlock.getPitchFromNote(note);
		level.playSound(null, pos, instrument.getSoundEvent().value(), SoundSource.RECORDS, 3.0f, pitch);
	}

	private NoteBlockInstrument instrumentAt(Level level, BlockPos pos, BlockState state) {
		Direction facing = state.getValue(BlockStateProperties.FACING);
		BlockPos supportPos = pos.relative(facing.getOpposite());
		return level.getBlockState(supportPos).instrument();
	}

	@Override
	protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.saveAdditional(tag, registries);
		tag.putInt("Note", note);
	}

	@Override
	protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
		super.loadAdditional(tag, registries);
		note = tag.getInt("Note");
	}
}
