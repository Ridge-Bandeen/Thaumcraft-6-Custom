package thaumcraft.client.fx;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.Thaumcraft;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.client.fx.beams.FXArc;
import thaumcraft.client.fx.beams.FXBolt;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.lib.network.fx.PacketFXBlockArc;
import thaumcraft.common.lib.network.fx.PacketFXBlockBamf;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;
import thaumcraft.common.lib.network.fx.PacketFXWispZap;
import thaumcraft.common.lib.network.fx.PacketFXZap;

/**
 * Client-side handlers for {@code thaumcraft.common.lib.network.fx}'s payloads - referenced only by method
 * reference from {@code Thaumcraft#registerPayloads} (common code, runs on both sides), never called
 * directly from there. Safe on a dedicated server because a {@code playToClient}-registered handler is only
 * ever invoked on the receiving (client) side - registering it is a common concern (both sides need the
 * payload's codec to en/decode it), actually running it isn't. See that method's own doc comment.
 *
 * Most effects here are still built from plain vanilla particles/sounds, not the original's custom
 * {@code FXDispatcher}/{@code ParticleEngine} renderer pipeline - the same boundary this whole port kept
 * since Cinderpearl's own client FX, now extended to cover networked effects too. {@link #handleBlockArc} is
 * the first real exception, restored 2026-09-15 - see {@link thaumcraft.client.fx.beams.FXArc}'s own doc
 * comment for the real modern rendering.
 *
 * Also carries a real, minimal delayed-spawn queue ({@link #addDelayed}), replacing the true original's own
 * {@code ParticleEngine.addEffectWithDelay} - restored 2026-09-15 alongside {@link
 * thaumcraft.client.fx.particles.FXGeneric}, since 2 real real call sites want it ({@link #handleScanSource}'s
 * own real distance-staggered "sonar ping" reveal, {@link FXArc}'s own real trailing-ember spawn).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ClientFxHandlers {

	private record DelayedParticle(Particle particle, int ticksRemaining) {
	}

	private static final List<DelayedParticle> DELAYED = new ArrayList<>();

	/** Real modern equivalent of the true original's own {@code ParticleEngine.addEffectWithDelay} - queues
	 * a real particle to be added to the real {@link net.minecraft.client.particle.ParticleEngine} after
	 * {@code delayTicks} real client ticks rather than immediately. */
	public static void addDelayed(Particle particle, int delayTicks) {
		if (delayTicks <= 0) {
			Minecraft.getInstance().particleEngine.add(particle);
			return;
		}
		DELAYED.add(new DelayedParticle(particle, delayTicks));
	}

	@SubscribeEvent
	static void onClientTick(ClientTickEvent.Post event) {
		if (DELAYED.isEmpty()) {
			return;
		}
		var particleEngine = Minecraft.getInstance().particleEngine;
		DELAYED.removeIf(dp -> {
			if (dp.ticksRemaining() <= 1) {
				particleEngine.add(dp.particle());
				return true;
			}
			return false;
		});
		DELAYED.replaceAll(dp -> new DelayedParticle(dp.particle(), dp.ticksRemaining() - 1));
	}

	private ClientFxHandlers() {
	}

	/** {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#ARCING}'s cleave hit - the original drew a textured slash quad between
	 * the two entities ({@code FXDispatcher.drawSlash}); this spawns a small burst of vanilla's own sweep-attack
	 * particle at the target instead, the same particle a vanilla sword sweep itself uses. */
	public static void handleSlash(PacketFXSlash payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Entity target = resolveEntity(mc, level, payload.target());
		if (target == null) {
			return;
		}
		double x = target.getX();
		double y = target.getBoundingBox().minY + target.getBbHeight() / 2.0;
		double z = target.getZ();
		for (int i = 0; i < 4; i++) {
			level.addParticle(ParticleTypes.SWEEP_ATTACK, x, y, z, 0.0, 0.0, 0.0);
		}
	}

	private static Entity resolveEntity(Minecraft mc, ClientLevel level, int entityId) {
		return mc.player != null && mc.player.getId() == entityId ? mc.player : level.getEntity(entityId);
	}

	/** Real {@link FXArc} spawn - see that class's own doc comment for the real modern rendering this
	 * replaces the vanilla-particle-placeholder pattern with. */
	public static void handleBlockArc(PacketFXBlockArc payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		mc.particleEngine.add(new FXArc(level, payload.source().x(), payload.source().y(), payload.source().z(),
				payload.target().x(), payload.target().y(), payload.target().z(), payload.red(), payload.green(), payload.blue(), 0.5f));
	}

	/** Real {@link FXBolt} spawn for {@link EntityWisp}'s zap attack, restored 2026-09-15 (see {@link
	 * PacketFXWispZap}'s own doc comment for why this replaces the earlier {@link #handleSlash} reuse).
	 * Resolves both entities fresh client-side (matches {@link #handleSlash}'s own established pattern) and
	 * reads the wisp's own real per-aspect colour directly off it, matching the true original's own real
	 * {@code Aspect.getAspect(wisp.getType()).getColor()} lookup exactly. */
	public static void handleWispZap(PacketFXWispZap payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Entity source = resolveEntity(mc, level, payload.source());
		Entity target = resolveEntity(mc, level, payload.target());
		if (source == null || target == null) {
			return;
		}
		float r = 1.0f, g = 1.0f, b = 1.0f;
		if (source instanceof EntityWisp wisp) {
			Aspect aspect = Aspect.getAspect(wisp.getWispType());
			if (aspect != null) {
				int color = aspect.getColor();
				r = ((color >> 16) & 0xFF) / 255.0f;
				g = ((color >> 8) & 0xFF) / 255.0f;
				b = (color & 0xFF) / 255.0f;
			}
		}
		mc.particleEngine.add(new FXBolt(level, source.getX(), source.getY(), source.getZ(),
				target.getX(), target.getY(), target.getZ(), r, g, b, 0.6f));
	}

	/** Real {@link FXBolt} spawn for {@link thaumcraft.common.items.casters.foci.FocusMediumBolt}'s cast
	 * trail - see {@link PacketFXZap}'s own doc comment. */
	public static void handleZap(PacketFXZap payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		mc.particleEngine.add(new FXBolt(level, payload.source().x(), payload.source().y(), payload.source().z(),
				payload.target().x(), payload.target().y(), payload.target().z(),
				payload.red(), payload.green(), payload.blue(), payload.width()));
	}

	/** Real, purely client-local {@link FXArc} spawn for {@link thaumcraft.common.entities.monster.boss.EntityEldritchGolem}'s
	 * own headless-phase arc-lightning decoration - called directly from that entity's own common-code
	 * {@code aiStep} (safe: only ever reached inside its own {@code level().isClientSide} branch, so this
	 * class never actually loads on a dedicated server - same real safety property {@code Thaumcraft
	 * #registerPayloads}'s own doc comment already establishes for every other real use of this class from
	 * common code). No packet needed at all - matches the true original's own real client-only
	 * {@code onUpdate}/{@code handleStatusUpdate} shape exactly, since the target position is picked
	 * client-locally, never server-authoritative. */
	public static void spawnGolemArc(net.minecraft.world.entity.LivingEntity golem, double tx, double ty, double tz, int arcing) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		mc.particleEngine.add(new FXArc(level, golem.getX(), golem.getY() + golem.getBbHeight() / 2.0, golem.getZ(),
				tx + 0.5, ty + 1.0, tz + 0.5,
				0.65f + level.random.nextFloat() * 0.1f, 1.0f, 1.0f, 1.0f - arcing / 10.0f));
	}

	/** {@link thaumcraft.common.entities.EntityFluxRift}'s collapse "poof" - the original drew a custom
	 * billboard flash ({@code FXDispatcher.drawBamf}) tinted by the packed {@code color} it carries; this
	 * drops that tint (every real call site sends a fixed placeholder colour anyway) in favour of a plain
	 * void/portal-themed vanilla particle burst, with an optional teleport-ish sound standing in for the
	 * original's own bamf sound effect (same "no custom audio subsystem" simplification {@code EntityWisp}
	 * already established). {@code flair} adds a second, larger burst on top of the base one. */
	public static void handleBlockBamf(PacketFXBlockBamf payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		double x = payload.x();
		double y = payload.y();
		double z = payload.z();
		int count = payload.flair() ? 24 : 10;
		for (int i = 0; i < count; i++) {
			double ox = (level.random.nextFloat() - level.random.nextFloat()) * 1.2;
			double oy = (level.random.nextFloat() - level.random.nextFloat()) * 1.2;
			double oz = (level.random.nextFloat() - level.random.nextFloat()) * 1.2;
			level.addParticle(ParticleTypes.REVERSE_PORTAL, x + ox, y + oy, z + oz, 0.0, 0.05, 0.0);
		}
		if (payload.sound()) {
			level.playLocalSound(x, y, z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.NEUTRAL, 1.0f, 0.7f, false);
		}
	}

	/** {@link thaumcraft.common.lib.enchantment.EnumInfusionEnchantment#SOUNDING}'s ore-vein radar - scans a
	 * {@code (4 + size * 4)}-block cube around the source for ore blocks, groups connected ones (matched by
	 * block identity, same-Block neighbours up to 3x3x3 apart - diagonals count, same shape as the original's
	 * own {@code calcGroup}), and marks each group's centre with a real {@link
	 * thaumcraft.client.fx.particles.FXGeneric} marker (restored 2026-09-15, see {@link #markGroup}'s own doc
	 * comment) revealed via a real {@link #addDelayed} distance-proportional delay - matches the true
	 * original's own real "sonar ping" staggering exactly (farther groups reveal later, {@code distance * 3}
	 * real ticks). */
	public static void handleScanSource(PacketFXScanSource payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		BlockPos origin = payload.blockPos();
		int range = 4 + payload.size() * 4;

		Set<BlockPos> remaining = new HashSet<>();
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		for (int xx = -range; xx <= range; xx++) {
			for (int yy = -range; yy <= range; yy++) {
				for (int zz = -range; zz <= range; zz++) {
					cursor.set(origin.getX() + xx, origin.getY() + yy, origin.getZ() + zz);
					if (level.getBlockState(cursor).is(Tags.Blocks.ORES)) {
						remaining.add(cursor.immutable());
					}
				}
			}
		}

		while (!remaining.isEmpty()) {
			BlockPos start = remaining.iterator().next();
			remaining.remove(start);
			Block groupBlock = level.getBlockState(start).getBlock();
			Set<BlockPos> group = new HashSet<>();
			group.add(start);
			ArrayDeque<BlockPos> queue = new ArrayDeque<>();
			queue.add(start);
			while (!queue.isEmpty()) {
				BlockPos current = queue.poll();
				for (int xx = -1; xx <= 1; xx++) {
					for (int yy = -1; yy <= 1; yy++) {
						for (int zz = -1; zz <= 1; zz++) {
							if (xx == 0 && yy == 0 && zz == 0) {
								continue;
							}
							BlockPos next = current.offset(xx, yy, zz);
							if (remaining.remove(next) && level.getBlockState(next).getBlock() == groupBlock) {
								group.add(next);
								queue.add(next);
							}
						}
					}
				}
			}
			markGroup(level, group, groupBlock, origin);
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXGeneric} marker, restored 2026-09-15 (see that class's
	 * own doc comment) - was a plain vanilla dust particle burst before. Matches the true original's own
	 * real config exactly: a 44-tick, 15-frame looping glow sprite (frames 240-254 on the shared particle
	 * sheet) fading in then out ({@code 0 -> 1 -> 0.8 -> 0} over its life), scaled 9x, additively blended,
	 * revealed {@code distance * 3} real ticks after the scan origin via {@link #addDelayed} - the real
	 * "sonar ping" timing, restored alongside the marker itself. */
	private static void markGroup(ClientLevel level, Set<BlockPos> group, Block block, BlockPos origin) {
		double x = 0;
		double y = 0;
		double z = 0;
		for (BlockPos p : group) {
			x += p.getX() + 0.5;
			y += p.getY() + 0.5;
			z += p.getZ() + 0.5;
		}
		x /= group.size();
		y /= group.size();
		z /= group.size();

		double distance = Math.sqrt(origin.distToCenterSqr(x, y, z));

		Vector3f color = oreColor(block);
		thaumcraft.client.fx.particles.FXGeneric fx = new thaumcraft.client.fx.particles.FXGeneric(level, x, y, z, 0.0, 0.0, 0.0);
		fx.setMaxAge(44);
		fx.setRBGColorF(color.x(), color.y(), color.z());
		fx.setAlphaF(0.0f, 1.0f, 0.8f, 0.0f);
		fx.setParticles(240, 15, 1);
		fx.setGridSize(16);
		fx.setLoop(true);
		fx.setScale(9.0f);
		fx.setLayer(1);
		addDelayed(fx, (int) (distance * 3.0));
	}

	private static Vector3f oreColor(Block block) {
		int rgb;
		if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE) {
			rgb = 0xD8AF93;
		} else if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE) {
			rgb = 0xC06A42;
		} else if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE) {
			rgb = 0xFCEE4B;
		} else if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE) {
			rgb = 0x2B2B2B;
		} else if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE) {
			rgb = 0xFF0000;
		} else if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE) {
			rgb = 0x1428AC;
		} else if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE) {
			rgb = 0x5DECF5;
		} else if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE) {
			rgb = 0x17DD62;
		} else if (block == Blocks.NETHER_QUARTZ_ORE || block == BlocksTC.oreQuartz.get()) {
			rgb = 0xE5DCD3;
		} else if (block == BlocksTC.oreCinnabar.get()) {
			rgb = 0x9B0000;
		} else if (block == BlocksTC.oreAmber.get()) {
			rgb = 0xFDB92D;
		} else {
			rgb = 0xC8C8C0;
		}
		return new Vector3f(((rgb >> 16) & 0xFF) / 255.0f, ((rgb >> 8) & 0xFF) / 255.0f, (rgb & 0xFF) / 255.0f);
	}
}
