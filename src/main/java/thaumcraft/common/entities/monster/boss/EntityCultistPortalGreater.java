package thaumcraft.common.entities.monster.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.phys.Vec3;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.registry.ThaumcraftEntities;
import thaumcraft.common.tiles.misc.TileBanner;

/**
 * Replaces the old (1.12.2) {@code EntityCultistPortalGreater} - the cult faction's boss-tier "cathedral"
 * monolith. Real immobile {@link Monster} subclass (same {@code move()}/{@code aiStep()} no-super shape as
 * {@link thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser}). Deliberately NOT a subclass
 * of {@link EntityThaumcraftBoss} despite the shared boss-bar shape - the original's own version isn't
 * either (a standalone {@code EntityMob} with its own inline {@code BossInfoServer}), and inheriting from
 * that base class here would silently grant this entity real behaviors it never had in the original
 * (spawn-timer invulnerability, the 35-damage enrage cap, a periodic self-heal on its own separate cadence).
 * The real boss-bar wiring itself ({@link ServerBossEvent}, added/removed via {@link #startSeenByPlayer}/
 * {@link #stopSeenByPlayer}) is duplicated inline instead, matching the original's own scope exactly.
 *
 * Real 13-stage escalation, matching the original's own {@code onUpdate} exactly: stage 0 real-spawns real
 * {@link BlocksTC#bannerCrimsonCult} banners at the four cardinal points (real facing set on each) and real
 * {@link BlocksTC#lootCrateCommon}/{@code Uncommon}/{@code Rare} placement nearby (5%/15%/80% tier odds,
 * matching the original), both real once-per-encounter setup dressing. Stages 0-4 conjure real minions on a
 * real fixed-ish cadence; stages 5-11 and 13+ scale that cadence by a real {@link #getTiming} (nearby real
 * {@link EntityCultist} count × 20, a real, portable version of the original's own {@code EntityUtils}-
 * wrapped scan); stage 12 conjures the real {@link EntityCultistLeader} boss itself. Real per-tick self-heal
 * while below stage 12, matching the original's own {@code stage < 12} gate. Real touch damage (8, doubled
 * from the lesser portal's 4), real potion immunity, real death explosion (radius 2.0, no block/mob damage).
 *
 * Real, documented cuts: no "always full-bright" self-illumination and no renderer (same real reasons
 * {@link thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser}'s own doc comment already
 * explains - entities don't carry a light-emission override point any more, and the original's own version
 * is a fully custom raw-immediate-mode-GL billboard). Real {@code PacketFXBlockArc} stage-progress "pulse"
 * signal restored 2026-09-15 (see {@link #sendArcPulse}) alongside its real {@code wandfail} sound (was a
 * vanilla {@code EVOKER_CAST_SPELL} stand-in).
 */
public class EntityCultistPortalGreater extends Monster {

	private final ServerBossEvent bossEvent;
	private int stage;
	private int stageCounter = 200;

	public EntityCultistPortalGreater(EntityType<? extends EntityCultistPortalGreater> type, Level level) {
		super(type, level);
		bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_6);
		bossEvent.setDarkenScreen(true);
		xpReward = 30;
	}

	public static AttributeSupplier.Builder createAttributes() {
		return Monster.createMonsterAttributes()
				.add(Attributes.MAX_HEALTH, 500.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0);
	}

	@Override
	public void startSeenByPlayer(ServerPlayer player) {
		super.startSeenByPlayer(player);
		bossEvent.addPlayer(player);
	}

	@Override
	public void stopSeenByPlayer(ServerPlayer player) {
		super.stopSeenByPlayer(player);
		bossEvent.removePlayer(player);
	}

	@Override
	public void setCustomName(Component name) {
		super.setCustomName(name);
		bossEvent.setName(getDisplayName());
	}

	@Override
	public int getArmorValue() {
		return 5;
	}

	@Override
	public boolean fireImmune() {
		return true;
	}

	@Override
	public boolean removeWhenFarAway(double distanceSquared) {
		return false;
	}

	@Override
	public boolean isPickable() {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void move(net.minecraft.world.entity.MoverType type, Vec3 movement) {
		// Immobile - see the class doc comment.
	}

	@Override
	public boolean canBeAffected(net.minecraft.world.effect.MobEffectInstance effect) {
		return false;
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		bossEvent.setName(getDisplayName());
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	@Override
	public void addAdditionalSaveData(CompoundTag tag) {
		super.addAdditionalSaveData(tag);
		tag.putInt("stage", stage);
	}

	@Override
	public void readAdditionalSaveData(CompoundTag tag) {
		super.readAdditionalSaveData(tag);
		stage = tag.getInt("stage");
	}

	private int getTiming() {
		return level().getEntitiesOfClass(EntityCultist.class, getBoundingBox().inflate(32.0)).size() * 20;
	}

	@Override
	public void aiStep() {
		// No super.aiStep() call - see the class doc comment / EntityCultistPortalLesser's own.
		if (level().isClientSide) {
			return;
		}
		bossEvent.setProgress(getHealth() / getMaxHealth());

		if (stageCounter > 0) {
			stageCounter--;
			if (stageCounter == 160 && stage == 0) {
				placeBanners();
			}
			if (stageCounter > 20 && stageCounter < 150 && stage == 0 && stageCounter % 13 == 0) {
				placeLootCrate();
			}
		}
		else if (level().getNearestPlayer(this, 48.0) != null) {
			switch (stage) {
				case 0, 1, 2, 3, 4 -> {
					stageCounter = 15 + random.nextInt(Math.max(1, 10 - stage)) - stage;
					spawnMinion();
				}
				case 12 -> {
					stageCounter = 50 + getTiming() * 2 + random.nextInt(50);
					spawnBoss();
				}
				default -> {
					int t = getTiming();
					stageCounter = t + random.nextInt(5 + t / 3);
					spawnMinion();
				}
			}
			stage++;
		}
		else {
			stageCounter = 30 + random.nextInt(30);
		}

		if (stage < 12) {
			heal(1.0f);
		}
	}

	private void placeBanners() {
		Direction[] dirs = { Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST };
		for (Direction dir : dirs) {
			BlockPos bp = blockPosition().offset(-dir.getStepX() * 6, 0, dir.getStepZ() * 6);
			level().setBlockAndUpdate(bp, BlocksTC.bannerCrimsonCult.get().defaultBlockState());
			if (level().getBlockEntity(bp) instanceof TileBanner banner) {
				byte face = switch (dir) {
					case NORTH -> 8;
					case SOUTH -> 0;
					case WEST -> 12;
					case EAST -> 4;
					default -> 0;
				};
				banner.setBannerFacing(face);
			}
			sendArcPulse(bp);
			playSound(thaumcraft.common.registry.ThaumcraftSounds.WANDFAIL.get(), 1.0f, 1.0f);
		}
	}

	private void placeLootCrate() {
		BlockPos pos = blockPosition();
		int a = pos.getX() + random.nextInt(5) - random.nextInt(5);
		int b = pos.getZ() + random.nextInt(5) - random.nextInt(5);
		if (a == pos.getX() || b == pos.getZ()) return;
		BlockPos bp = new BlockPos(a, pos.getY(), b);
		if (!level().isEmptyBlock(bp)) return;
		float rr = random.nextFloat();
		var crate = rr < 0.05f ? BlocksTC.lootCrateRare.get() : rr < 0.2f ? BlocksTC.lootCrateUncommon.get() : BlocksTC.lootCrateCommon.get();
		level().setBlockAndUpdate(bp, crate.defaultBlockState());
		sendArcPulse(bp);
		playSound(thaumcraft.common.registry.ThaumcraftSounds.WANDFAIL.get(), 1.0f, 1.0f);
	}

	/** Real client "pulse" signal from a real stage-0 setup block to this portal's own centre, restored
	 * 2026-09-15 - matches the true original's own real {@code PacketFXBlockArc} call at both real call
	 * sites exactly (same reddish colour roll, same real 32-block broadcast radius). This purely cosmetic
	 * packet failing to send (e.g. a GameTest mock player, no real client connection) never takes the real
	 * setup event down with it. */
	private void sendArcPulse(BlockPos from) {
		if (!(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		try {
			net.neoforged.neoforge.network.PacketDistributor.sendToPlayersNear(serverLevel, null, getX(), getY(), getZ(), 32.0,
					thaumcraft.common.lib.network.fx.PacketFXBlockArc.of(from, this, 0.5f + random.nextFloat() * 0.2f, 0.0f, 0.0f));
		} catch (Exception e) {
			thaumcraft.Thaumcraft.LOGGER.debug("Cultist portal arc FX packet failed to send (no real client connection?)", e);
		}
	}

	private void spawnMinion() {
		EntityCultist cultist = random.nextFloat() > 0.33f
				? ThaumcraftEntities.CULTIST_KNIGHT.get().create(level())
				: ThaumcraftEntities.CULTIST_CLERIC.get().create(level());
		if (cultist == null || !(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		cultist.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + 0.25, getZ() + random.nextFloat() - random.nextFloat(), 0.0f, 0.0f);
		cultist.finalizeSpawn(serverLevel, level().getCurrentDifficultyAt(cultist.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
		cultist.restrictTo(blockPosition(), 32);
		serverLevel.addFreshEntity(cultist);
		cultist.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.0f);
		if (stage > 12) {
			hurt(damageSources().magic(), 5 + random.nextInt(5));
		}
	}

	private void spawnBoss() {
		EntityCultistLeader leader = ThaumcraftEntities.CULTIST_LEADER.get().create(level());
		if (leader == null || !(level() instanceof net.minecraft.server.level.ServerLevel serverLevel)) return;
		leader.moveTo(getX() + random.nextFloat() - random.nextFloat(), getY() + 0.25, getZ() + random.nextFloat() - random.nextFloat(), 0.0f, 0.0f);
		leader.finalizeSpawn(serverLevel, level().getCurrentDifficultyAt(leader.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
		leader.restrictTo(blockPosition(), 32);
		serverLevel.addFreshEntity(leader);
		leader.playSound(SoundEvents.EVOKER_CAST_SPELL, 1.0f, 1.0f);
	}

	@Override
	public void playerTouch(Player player) {
		if (distanceToSqr(player) < 3.0 && player.hurt(damageSources().indirectMagic(this, this), 8.0f)) {
			playSound(SoundEvents.LIGHTNING_BOLT_THUNDER, 1.0f, (random.nextFloat() - random.nextFloat()) * 0.1f + 1.0f);
		}
	}

	@Override
	public void die(DamageSource source) {
		if (level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
			serverLevel.explode(this, getX(), getY(), getZ(), 2.0f, Level.ExplosionInteraction.NONE);
		}
		super.die(source);
	}

	@Override
	public boolean isAlliedTo(Entity other) {
		return other instanceof EntityCultist || super.isAlliedTo(other);
	}

	@Override
	protected SoundEvent getAmbientSound() {
		return SoundEvents.BEACON_AMBIENT;
	}

	@Override
	protected SoundEvent getHurtSound(DamageSource damageSourceIn) {
		return SoundEvents.LIGHTNING_BOLT_THUNDER;
	}

	@Override
	protected SoundEvent getDeathSound() {
		return SoundEvents.LIGHTNING_BOLT_IMPACT;
	}
}
