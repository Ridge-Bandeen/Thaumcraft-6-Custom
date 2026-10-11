package thaumcraft.common.entities.construct;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.NameTagItem;
import net.minecraft.world.level.Level;
import thaumcraft.common.registry.ThaumcraftSounds;

/**
 * Replaces the old (1.12.2) {@code EntityOwnedConstruct} - the shared base for the turret/bore family. The
 * original hand-rolled a real synced owner UUID + tamed flag and real owner-aware team/ally checks; modern
 * vanilla's own {@link TamableAnimal} already provides exactly that (real {@code getOwnerUUID}/{@code
 * setOwnerUUID}/{@code isTame}/{@code setTame}/{@code isOwnedBy}, and its own real {@link #getTeam}/{@link
 * #isAlliedTo} overrides already defer to the owner's team, matching the original's own overrides almost
 * line for line) - reused directly rather than hand-rolled again, the same "reuse vanilla wherever it
 * already does the job" reasoning this whole port has followed throughout. {@code Animal}'s own breeding
 * hooks ({@link #isFood}/{@link #getBreedOffspring}) are real no-ops - constructs don't breed, matching the
 * original never wiring up anything breeding-related either.
 *
 * Real "not a valid, player-placed spawn" self-destruct (constructs summoned any other way - a natural
 * spawn, a spawn egg - immediately discard themselves, matching the original's own {@code validSpawn} guard
 * exactly), real never-despawns persistence, real "clear my attack target if it's now an ally" per-tick
 * check, and the real "you don't own this" interaction message for non-owners.
 *
 * Real custom {@code clack}/{@code tool} sounds restored 2026-09-15 (were vanilla iron-golem stand-ins) -
 * see {@link ThaumcraftSounds}'s own doc comment.
 */
public class EntityOwnedConstruct extends TamableAnimal {

	private boolean validSpawn;

	protected EntityOwnedConstruct(EntityType<? extends EntityOwnedConstruct> type, Level level) {
		super(type, level);
	}

	@Override
	public boolean isFood(ItemStack stack) {
		return false;
	}

	@Override
	public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob otherParent) {
		return null;
	}

	@Override
	protected int decreaseAirSupply(int air) {
		return air;
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return ThaumcraftSounds.CLACK.get();
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSource) {
		return ThaumcraftSounds.CLACK.get();
	}

	@Override
	protected SoundEvent getDeathSound() {
		return ThaumcraftSounds.TOOL.get();
	}

	@Override
	public boolean isPersistenceRequired() {
		return true;
	}

	public void setValidSpawn() {
		validSpawn = true;
	}

	@Override
	public void tick() {
		super.tick();
		if (getTarget() != null && isAlliedTo(getTarget())) {
			setTarget(null);
		}
		if (!level().isClientSide && !validSpawn) {
			discard();
		}
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putBoolean("v", validSpawn);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		validSpawn = tag.getBoolean("v");
	}

	@Override
	public InteractionResult mobInteract(Player player, InteractionHand hand) {
		if (!isAlive()) {
			return InteractionResult.PASS;
		}
		if (player.isShiftKeyDown() || player.getItemInHand(hand).getItem() instanceof NameTagItem) {
			return InteractionResult.PASS;
		}
		if (!level().isClientSide && !isOwnedBy(player)) {
			player.displayClientMessage(Component.translatable("tc.notowned"), true);
			return InteractionResult.SUCCESS;
		}
		return super.mobInteract(player, hand);
	}

	/** As the original ({@code canDespawn} false): never despawns, however far away the players are. */
	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}
}
