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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
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
import thaumcraft.client.fx.particles.FXBoreParticles;
import thaumcraft.client.fx.particles.FXBoreSparkle;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.lib.network.fx.PacketFXBlockArc;
import thaumcraft.common.lib.network.fx.PacketFXBlockBamf;
import thaumcraft.common.lib.network.fx.PacketFXBoreDig;
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
		if (!DELAYED.isEmpty()) {
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
		if (!SWARMS.isEmpty()) {
			SWARMS.values().removeIf(list -> {
				list.removeIf(fx -> !fx.isAlive());
				return list.isEmpty();
			});
		}
	}

	private static final java.util.Map<Integer, List<thaumcraft.client.fx.particles.FXSwarm>> SWARMS = new java.util.HashMap<>();

	/** Real "up to 30 buzzing particles" maintenance loop for {@link
	 * thaumcraft.common.entities.monster.tainted.EntityTaintSwarm} - see that entity's own doc comment. Real,
	 * faithful reproduction of the true original's own real per-tick shape: prune the first dead/removed
	 * particle found (matches the true original's own real single-removal-per-tick quirk, not a full sweep),
	 * then top up by one if still under the real cap. Only ever called while the owning entity is still
	 * really ticking - once it's removed, {@link #onClientTick} itself sweeps the now-orphaned entry once its
	 * own particles have all finished their real fade-out (see that method's own doc comment), since a
	 * removed entity's own {@code aiStep} obviously never calls this again to clean up after itself. */
	public static void maintainSwarm(Entity target) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		List<thaumcraft.client.fx.particles.FXSwarm> list = SWARMS.computeIfAbsent(target.getId(), id -> new ArrayList<>());
		for (int i = 0; i < list.size(); i++) {
			if (!list.get(i).isAlive()) {
				list.remove(i);
				break;
			}
		}
		if (list.size() < 30) {
			double x = target.getX() + (level.random.nextFloat() - level.random.nextFloat()) * 2.0f;
			double y = target.getY() + (level.random.nextFloat() - level.random.nextFloat()) * 2.0f;
			double z = target.getZ() + (level.random.nextFloat() - level.random.nextFloat()) * 2.0f;
			float r = 0.8f + level.random.nextFloat() * 0.2f;
			float g = level.random.nextFloat() * 0.4f;
			float b = 1.0f - level.random.nextFloat() * 0.2f;
			thaumcraft.client.fx.particles.FXSwarm fx = new thaumcraft.client.fx.particles.FXSwarm(level, x, y, z, target, r, g, b, 0.22f, 15.0f, 0.08f);
			list.add(fx);
			mc.particleEngine.add(fx);
		}
	}

	private static final Set<Integer> WISPS = new HashSet<>();

	/** Real {@link thaumcraft.client.fx.particles.FXWispGlow} spawn for {@link
	 * thaumcraft.common.entities.monster.EntityWisp} - see that class's own doc comment. Fire-and-forget, one
	 * real time per real wisp id (guarded by {@link #WISPS}, never cleared - a stale entity id can never be
	 * reused by a live entity again, so this is safe to leave unbounded for a session's worth of real wisps):
	 * spawns the 3 real tracking particles once, each of which then self-removes the instant its own target
	 * dies, matching the true original's own real per-frame "stop drawing the moment health hits 0" behaviour
	 * with no fade. */
	public static void maintainWisp(Entity wisp) {
		if (!WISPS.add(wisp.getId())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			WISPS.remove(wisp.getId());
			return;
		}
		ResourceLocation particles = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/particles.png");
		ResourceLocation nodes = ResourceLocation.fromNamespaceAndPath(Thaumcraft.MODID, "textures/misc/auranodes.png");
		int color = 0xFFFFFF;
		if (wisp instanceof EntityWisp entityWisp) {
			Aspect aspect = Aspect.getAspect(entityWisp.getWispType());
			if (aspect != null) {
				color = aspect.getColor();
			}
		}
		float ar = ((color >> 16) & 0xFF) / 255.0f;
		float ag = ((color >> 8) & 0xFF) / 255.0f;
		float ab = (color & 0xFF) / 255.0f;
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXWispGlow(level, wisp, particles, 64, 512, 0.4f, 1.0f, 1.0f, 1.0f, 1.0f));
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXWispGlow(level, wisp, particles, 64, 320, 0.75f, 1.0f, 1.0f, 1.0f, 0.25f));
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXWispGlow(level, wisp, nodes, 32, 800, 0.75f, ar, ag, ab, 0.5f));
	}

	private static final Set<Integer> GOLEM_ORBS = new HashSet<>();

	/** Real {@link thaumcraft.client.fx.particles.FXGolemOrb} spawn for {@link
	 * thaumcraft.common.entities.projectile.EntityGolemOrb} - see that particle's own doc comment.
	 * Fire-and-forget, one real time per real orb id, same shape as {@link #maintainWisp}. */
	public static void maintainGolemOrb(Entity orb, boolean red) {
		if (!GOLEM_ORBS.add(orb.getId())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			GOLEM_ORBS.remove(orb.getId());
			return;
		}
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXGolemOrb(level, orb, red));
	}

	private static final Set<Integer> ELDRITCH_ORBS = new HashSet<>();

	/** Real {@link thaumcraft.client.fx.particles.FXEldritchOrbSpikes}/{@link
	 * thaumcraft.client.fx.particles.FXEldritchOrbGlow} spawn for {@link
	 * thaumcraft.common.entities.projectile.EntityEldritchOrb} - see those particles' own doc comments.
	 * Fire-and-forget, one real time per real orb id, same shape as {@link #maintainWisp}/{@link
	 * #maintainGolemOrb} - 2 real particles this time (the untextured spike geometry and the textured glow
	 * quad are 2 separate real render passes in the true original, and can't share one particle instance). */
	public static void maintainEldritchOrb(Entity orb) {
		if (!ELDRITCH_ORBS.add(orb.getId())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			ELDRITCH_ORBS.remove(orb.getId());
			return;
		}
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXEldritchOrbSpikes(level, orb));
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXEldritchOrbGlow(level, orb));
	}

	private static final Set<Integer> CULTIST_PORTALS = new HashSet<>();

	/** Real {@link thaumcraft.client.fx.particles.FXCultistPortalLesser} spawn for {@link
	 * thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser} - see that particle's own doc
	 * comment. Fire-and-forget, one real time per real portal id, same shape as {@link #maintainWisp}. Shares
	 * {@link #CULTIST_PORTALS} with {@link #maintainCultistPortalGreater} since entity ids are unique across
	 * every real entity type, not just within one. */
	public static void maintainCultistPortalLesser(thaumcraft.common.entities.monster.cult.EntityCultistPortalLesser portal) {
		if (!CULTIST_PORTALS.add(portal.getId())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			CULTIST_PORTALS.remove(portal.getId());
			return;
		}
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXCultistPortalLesser(level, portal));
	}

	/** Real {@link thaumcraft.client.fx.particles.FXCultistPortalGreater} spawn for {@link
	 * thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater} - see {@link #maintainCultistPortalLesser}. */
	public static void maintainCultistPortalGreater(thaumcraft.common.entities.monster.boss.EntityCultistPortalGreater portal) {
		if (!CULTIST_PORTALS.add(portal.getId())) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			CULTIST_PORTALS.remove(portal.getId());
			return;
		}
		mc.particleEngine.add(new thaumcraft.client.fx.particles.FXCultistPortalGreater(level, portal));
	}

	/** Real {@link thaumcraft.client.fx.particles.FXVent} burst - see that class's own doc comment. Real
	 * common-safe signature (a {@link net.minecraft.world.level.Level}, not a {@link ClientLevel}) so common
	 * callers like {@link thaumcraft.common.tiles.crafting.TileGolemBuilder#tickPress} can call this directly
	 * without ever referencing a client-only type themselves - see {@code FocusFxHelper}'s own doc comment for
	 * why that distinction matters (a real dedicated-server crash this port already hit once). */
	public static void spawnVentBurst(net.minecraft.world.level.Level level, double x, double y, double z, int count, int color) {
		if (!(level instanceof ClientLevel clientLevel)) {
			return;
		}
		var particleEngine = Minecraft.getInstance().particleEngine;
		for (int i = 0; i < count; i++) {
			particleEngine.add(new thaumcraft.client.fx.particles.FXVent(clientLevel, x, y, z,
					clientLevel.random.nextGaussian() * 0.1, 0.0, clientLevel.random.nextGaussian() * 0.1, color));
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXVent2} burst - see {@link #spawnVentBurst}'s own doc
	 * comment for why this common-safe signature matters. */
	public static void spawnVentBurst2(net.minecraft.world.level.Level level, double x, double y, double z,
			double mx, double my, double mz, int color, float scale) {
		if (!(level instanceof ClientLevel clientLevel)) {
			return;
		}
		Minecraft.getInstance().particleEngine.add(new thaumcraft.client.fx.particles.FXVent2(clientLevel, x, y, z, mx, my, mz, color).setScale(scale));
	}

	/** Real {@link thaumcraft.client.fx.particles.FXFireMote} spawn - shared by the true original's own real
	 * {@code drawFireMote}/{@code drawAlumentum} wrapper methods (same particle class, different default
	 * colour/scale at each real call site). */
	public static void spawnFireMote(net.minecraft.world.level.Level level, double x, double y, double z,
			double mx, double my, double mz, float r, float g, float b, float scale) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		Minecraft.getInstance().particleEngine.add(new thaumcraft.client.fx.particles.FXFireMote(clientLevel, x, y, z, mx, my, mz, r, g, b, scale));
	}

	/** The original's {@code FXDispatcher#crucibleFroth}: short-lived pale froth on a hot crucible's surface. */
	public static void spawnCrucibleFroth(net.minecraft.world.level.Level level, double x, double y, double z) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, 0.0, 0.0, 0.0);
		fb.setMaxAge(4 + level.random.nextInt(3));
		fb.setScale(level.random.nextFloat() * 0.2f + 0.2f);
		fb.setRBGColorF(0.5f, 0.5f, 0.7f);
		fb.setRandomMovementScale(0.001f, 0.001f, 0.001f);
		fb.setGravity(0.1f);
		fb.setParticle(64);
		fb.setFinalFrames(65, 66);
		addDelayed(fb, 0);
	}

	/** The original's {@code FXDispatcher#crucibleFrothDown}: purple froth running down the side of an over-full crucible. */
	public static void spawnCrucibleFrothDown(net.minecraft.world.level.Level level, double x, double y, double z) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, 0.0, 0.0, 0.0);
		fb.setMaxAge(12 + level.random.nextInt(12));
		fb.setScale(level.random.nextFloat() * 0.2f + 0.4f);
		fb.setRBGColorF(0.25f, 0.0f, 0.75f);
		fb.setAlphaF(0.8f);
		fb.setRandomMovementScale(0.001f, 0.001f, 0.001f);
		fb.setGravity(0.05f);
		fb.setNoClip(false);
		fb.setParticle(73);
		fb.setFinalFrames(65, 66);
		fb.setLayer(1);
		addDelayed(fb, 0);
	}

	/** The original's {@code FXDispatcher#crucibleBubble}: a bubble coloured by a dissolved aspect, drifting up. */
	public static void spawnCrucibleBubble(net.minecraft.world.level.Level level, double x, double y, double z, float r, float g, float b) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, 0.0, 0.0, 0.0);
		fb.setMaxAge(15 + level.random.nextInt(10));
		fb.setScale(level.random.nextFloat() * 0.3f + 0.3f);
		fb.setRBGColorF(r, g, b);
		fb.setRandomMovementScale(0.002f, 0.002f, 0.002f);
		fb.setGravity(-0.001f);
		fb.setParticle(64);
		fb.setFinalFrames(65, 66, 66);
		addDelayed(fb, 0);
	}

	/** The original's {@code FXDispatcher#drawNitorFlames}: a translucent, looping 8-frame flame tinted by the nitor's dye. */
	public static void spawnNitorFlame(net.minecraft.world.level.Level level, double x, double y, double z, double mx, double my, double mz, int color) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, mx, my, mz);
		fb.setMaxAge(10 + level.random.nextInt(5));
		fb.setRBGColorF(((color >> 16) & 255) / 255.0f, ((color >> 8) & 255) / 255.0f, (color & 255) / 255.0f);
		fb.setAlphaF(0.66f);
		fb.setLoop(true);
		fb.setGridSize(64);
		fb.setParticles(264, 8, 1);
		fb.setScale(3.0f + level.random.nextFloat(), 0.05f);
		fb.setGravity(0.0f); // the original's nitor particles hover; FXGeneric's port default (1.0) made the core fall
		fb.setRandomMovementScale(0.0025f, 0.0f, 0.0025f);
		addDelayed(fb, 0);
	}

	/** The original's {@code FXDispatcher#drawNitorCore}: the small white core (particle 457), normal-blended. */
	public static void spawnNitorCore(net.minecraft.world.level.Level level, double x, double y, double z) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, 0.0, 0.0, 0.0);
		fb.setMaxAge(10);
		fb.setRBGColorF(1.0f, 1.0f, 1.0f);
		fb.setAlphaF(1.0f);
		fb.setGridSize(64);
		fb.setParticles(457, 1, 1);
		fb.setGravity(0.0f);
		fb.setScale(1.0f, 1.0f + (float) level.random.nextGaussian() * 0.1f, 1.0f);
		fb.setLayer(1);
		fb.setRandomMovementScale(2.0E-4f, 2.0E-4f, 2.0E-4f);
		addDelayed(fb, 0);
	}

	/** Real {@link thaumcraft.client.fx.particles.FXVisSparkle} spawn. */
	public static void spawnVisSparkle(net.minecraft.world.level.Level level, double x, double y, double z, double tx, double ty, double tz, int color) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXVisSparkle(clientLevel, x, y, z, tx, ty, tz);
		java.awt.Color c = new java.awt.Color(color);
		fb.setColor(c.getRed() / 255.0f, c.getGreen() / 255.0f, c.getBlue() / 255.0f);
		Minecraft.getInstance().particleEngine.add(fb);
	}

	/** Real port of the true original's own {@code FXDispatcher#spark}: a short-lived (5-9 tick) glowing spark from
	 * the 16-grid particle sheet - a random row of the 8-frame animation starting at column 8. Used by the
	 * Warden's sapping fields ({@code BlockEffectSap}). The original's random horizontal mirroring is dropped. */
	public static void spawnSpark(net.minecraft.world.level.Level level, double x, double y, double z, float size, float r, float g, float b, float a) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, 0.0, 0.0, 0.0);
		fb.setMaxAge(5 + level.random.nextInt(5));
		fb.setAlphaF(a);
		fb.setRBGColorF(r, g, b);
		fb.setGridSize(16);
		fb.setParticles(8 + level.random.nextInt(3) * 16, 8, 1);
		fb.setScale(size);
		addDelayed(fb, 0);
	}

	/** The original's {@code FXDispatcher#drawSimpleSparkle}: a glowing, twinkling grid-sheet sparkle that drifts and slows. */
	public static void spawnSimpleSparkle(net.minecraft.world.level.Level level, double x, double y, double z, double xd, double yd, double zd,
			float scale, float r, float g, float b, int delay, float decay, float grav, int baseAge) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		boolean sp = level.random.nextFloat() < 0.2;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, xd, yd, zd);
		int age = baseAge * 4 + level.random.nextInt(baseAge);
		fb.setMaxAge(age);
		fb.setRBGColorF(r, g, b);
		float[] alphas = new float[6 + level.random.nextInt(Math.max(1, age / 3))];
		for (int a = 1; a < alphas.length - 1; ++a) {
			alphas[a] = level.random.nextFloat();
		}
		fb.setAlphaF(alphas);
		fb.setParticles(sp ? 320 : 512, 16, 1);
		fb.setLoop(true);
		fb.setGravity(grav);
		fb.setScale(scale, scale * 2.0f);
		fb.setLayer(0);
		fb.setSlowDown(decay);
		fb.setRandomMovementScale(5.0E-4f, 0.001f, 5.0E-4f);
		fb.setWind(5.0E-4);
		addDelayed(fb, delay);
	}

	/** The original's {@code FXDispatcher#drawBlockSparkles}: dust sparkles across the exposed faces of a block, starting near
	 * {@code start} and spreading out (delay grows with distance from it). */
	public static void spawnBlockSparkles(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos p, net.minecraft.world.phys.Vec3 start) {
		if (!(level instanceof ClientLevel)) return;
		net.minecraft.world.phys.AABB bs = level.getBlockState(p).getShape(level, p).isEmpty()
				? new net.minecraft.world.phys.AABB(0, 0, 0, 1, 1, 1) : level.getBlockState(p).getShape(level, p).bounds();
		int num = (int) (((bs.getXsize() + bs.getYsize() + bs.getZsize()) / 3.0) * 20.0);
		for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
			net.minecraft.core.BlockPos np = p.relative(face);
			net.minecraft.world.level.block.state.BlockState state = level.getBlockState(np);
			if (state.isSolidRender(level, np) || state.isFaceSturdy(level, np, face.getOpposite())) continue;
			boolean rx = face.getStepX() == 0;
			boolean ry = face.getStepY() == 0;
			boolean rz = face.getStepZ() == 0;
			double mx = 0.5 + face.getStepX() * 0.51;
			double my = 0.5 + face.getStepY() * 0.51;
			double mz = 0.5 + face.getStepZ() * 0.51;
			for (int a = 0; a < num * 2; ++a) {
				double x = mx;
				double y = my;
				double z = mz;
				if (rx) x += level.random.nextGaussian() * 0.6;
				if (ry) y += level.random.nextGaussian() * 0.6;
				if (rz) z += level.random.nextGaussian() * 0.6;
				x = net.minecraft.util.Mth.clamp(x, bs.minX, bs.maxX);
				y = net.minecraft.util.Mth.clamp(y, bs.minY, bs.maxY);
				z = net.minecraft.util.Mth.clamp(z, bs.minZ, bs.maxZ);
				float g = net.minecraft.util.Mth.randomBetweenInclusive(level.random, 189, 255) / 255.0f;
				float b = net.minecraft.util.Mth.randomBetweenInclusive(level.random, 64, 255) / 255.0f;
				net.minecraft.world.phys.Vec3 v1 = new net.minecraft.world.phys.Vec3(p.getX() + x, p.getY() + y, p.getZ() + z);
				double delay = level.random.nextInt(5) + v1.distanceTo(start) * 16.0;
				spawnSimpleSparkle(level, p.getX() + x, p.getY() + y, p.getZ() + z, 0.0, 0.0025, 0.0,
						0.4f + (float) level.random.nextGaussian() * 0.1f, 1.0f, g, b, (int) delay, 1.0f, 0.01f, 16);
			}
		}
	}

	/** Salis Mundus client-side flourish ({@code ItemMagicDust#doSparkles}): fifty sparkles thrown from the player's hand
	 * toward the target (a third of them floaty), then the trigger's own block sparkles where it will act. */
	public static void spawnDustSparkles(net.minecraft.world.level.Level level, net.minecraft.world.entity.player.Player player,
			net.minecraft.world.InteractionHand hand, net.minecraft.core.BlockPos pos, net.minecraft.world.phys.Vec3 hit,
			thaumcraft.api.crafting.IDustTrigger trigger, thaumcraft.api.crafting.IDustTrigger.Placement place) {
		if (!(level instanceof ClientLevel)) return;
		double yawRad = (player.getYRot() + (hand == net.minecraft.world.InteractionHand.MAIN_HAND ? 0.0f : 180.0f)) / 180.0 * Math.PI;
		net.minecraft.world.phys.Vec3 look = player.getLookAngle();
		net.minecraft.world.phys.Vec3 v1 = new net.minecraft.world.phys.Vec3(
				player.getX() - Math.cos(yawRad) * 0.3 + look.x * 0.3,
				player.getBoundingBox().minY + player.getBbHeight() / 2.0 + 0.25 + look.y * 0.3,
				player.getZ() - Math.sin(yawRad) * 0.3 + look.z * 0.3);
		net.minecraft.world.phys.Vec3 v2 = new net.minecraft.world.phys.Vec3(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5).subtract(v1);
		int count = 50;
		for (int a = 0; a < count; ++a) {
			boolean floaty = a < count / 3;
			float g = net.minecraft.util.Mth.randomBetweenInclusive(level.random, 189, 255) / 255.0f;
			float b = net.minecraft.util.Mth.randomBetweenInclusive(level.random, 64, 255) / 255.0f;
			spawnSimpleSparkle(level, v1.x, v1.y, v1.z,
					v2.x / 6.0 + level.random.nextGaussian() * 0.05,
					v2.y / 6.0 + level.random.nextGaussian() * 0.05 + (floaty ? 0.05 : 0.15),
					v2.z / 6.0 + level.random.nextGaussian() * 0.05,
					0.5f, 1.0f, g, b, level.random.nextInt(5), floaty ? (0.3f + level.random.nextFloat() * 0.5f) : 0.85f, floaty ? 0.2f : 0.5f, 16);
		}
		java.util.List<net.minecraft.core.BlockPos> sparkles = trigger.sparkle(level, player, pos, place);
		if (sparkles != null) {
			for (net.minecraft.core.BlockPos p : sparkles) {
				spawnBlockSparkles(level, p, hit);
			}
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXSmokeSpiral} spawn. */
	public static void spawnSmokeSpiral(net.minecraft.world.level.Level level, double x, double y, double z, float radius, int start, int minY, int color) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		Minecraft.getInstance().particleEngine.add(new thaumcraft.client.fx.particles.FXSmokeSpiral(clientLevel, x, y, z, radius, start, minY, color));
	}

	/** Real {@link thaumcraft.client.fx.particles.FXSlimyBubble} spawn. */
	public static void spawnSlimyBubble(net.minecraft.world.level.Level level, double x, double y, double z, float scale) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		Minecraft.getInstance().particleEngine.add(new thaumcraft.client.fx.particles.FXSlimyBubble(clientLevel, x, y, z, scale));
	}

	/** Real {@link thaumcraft.client.fx.particles.FXWispEG} spawn - {@code target} is a plain, dist-agnostic
	 * vanilla {@link Entity}, safe to reference directly from a common-side method signature. */
	public static void spawnWispEG(net.minecraft.world.level.Level level, double x, double y, double z, Entity target) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var particleEngine = Minecraft.getInstance().particleEngine;
		for (int i = 0; i < 2; i++) {
			particleEngine.add(new thaumcraft.client.fx.particles.FXWispEG(clientLevel, x, y, z, target));
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXBlockRunes} spawn - the plain {@code blockRunes} variant
	 * (real random X-jitter, real default scale). */
	public static void spawnBlockRunes(net.minecraft.world.level.Level level, double x, double y, double z,
			float r, float g, float b, int duration, float gravity) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXBlockRunes(clientLevel, x + 0.5, y + 0.5, z + 0.5, r, g, b, duration);
		fb.setGravity(gravity);
		Minecraft.getInstance().particleEngine.add(fb);
	}

	/** Real {@link thaumcraft.client.fx.particles.FXBlockRunes} spawn - the {@code blockRunes2} variant (real
	 * fixed scale, zeroed X-jitter). */
	public static void spawnBlockRunes2(net.minecraft.world.level.Level level, double x, double y, double z,
			float r, float g, float b, int duration, float gravity) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXBlockRunes(clientLevel, x + 0.5, y + 0.5, z + 0.5, r, g, b, duration);
		fb.setGravity(gravity);
		fb.setScale((float) (0.5 + clientLevel.random.nextGaussian() * 0.1));
		fb.setOffsetX(0.0);
		Minecraft.getInstance().particleEngine.add(fb);
	}

	/** Real {@link thaumcraft.client.fx.particles.FXBreakingFade} "arising from the ground" burst - see the
	 * true original's own real {@code FXDispatcher.tentacleAriseFX}. {@code target} is a plain, dist-agnostic
	 * vanilla {@link Entity}, safe to reference directly from a common-side method signature. */
	public static void spawnTentacleArise(net.minecraft.world.level.Level level, Entity target) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var particleEngine = Minecraft.getInstance().particleEngine;
		float height = target.getBbHeight();
		for (int j = 0; j < 2.0f * height; j++) {
			float f = clientLevel.random.nextFloat() * (float) Math.PI * height;
			float f2 = clientLevel.random.nextFloat() * 0.5f + 0.5f;
			float f3 = Mth.sin(f) * height * 0.25f * f2;
			float f4 = Mth.cos(f) * height * 0.25f * f2;
			var fx = new thaumcraft.client.fx.particles.FXBreakingFade(clientLevel, target.getX() + f3, target.getY(), target.getZ() + f4,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL));
			fx.setColor(0.4f, 0.0f, 0.4f);
			fx.setStartAlpha(0.5f);
			fx.setParticleMaxAge((int) (66.0f / (clientLevel.random.nextFloat() * 0.9f + 0.1f)));
			particleEngine.add(fx);
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXBreakingFade} landing-splat burst - see the true
	 * original's own real {@code FXDispatcher.slimeJumpFX}. */
	public static void spawnSlimeJump(net.minecraft.world.level.Level level, Entity target, int size) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		float f = clientLevel.random.nextFloat() * (float) Math.PI * 2.0f;
		float f2 = clientLevel.random.nextFloat() * 0.5f + 0.5f;
		float f3 = Mth.sin(f) * size * 0.5f * f2;
		float f4 = Mth.cos(f) * size * 0.5f * f2;
		double midY = (target.getBoundingBox().minY + target.getBoundingBox().maxY) / 2.0;
		var fx = new thaumcraft.client.fx.particles.FXBreakingFade(clientLevel, target.getX() + f3, midY, target.getZ() + f4,
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL));
		fx.setColor(0.7f, 0.0f, 1.0f);
		fx.setStartAlpha(0.4f);
		fx.setParticleMaxAge((int) (66.0f / (clientLevel.random.nextFloat() * 0.9f + 0.1f)));
		Minecraft.getInstance().particleEngine.add(fx);
	}

	/** Real client-bound handler for {@link thaumcraft.common.lib.network.fx.PacketFXTaintLand} - see that
	 * packet's own doc comment and {@code BlockTaint#onLand}'s own doc comment for the real modern hook this
	 * replaces the true original's own real {@code EntityFallingTaint}/{@code taintLandFX} with. Real 10x
	 * burst (matches the true original's own real {@code for (j<10) taintLandFX(this)} loop), same real
	 * {@link thaumcraft.client.fx.particles.FXBreakingFade} scatter shape {@link #spawnSlimeJump} already
	 * established, dark taint tint instead of slime's own bright purple. */
	public static void handleTaintLand(thaumcraft.common.lib.network.fx.PacketFXTaintLand payload) {
		var mc = Minecraft.getInstance();
		if (!(mc.level instanceof ClientLevel clientLevel)) return;
		var particleEngine = mc.particleEngine;
		for (int j = 0; j < 10; j++) {
			float f = clientLevel.random.nextFloat() * (float) Math.PI * 2.0f;
			float f2 = clientLevel.random.nextFloat() * 0.5f + 0.5f;
			float f3 = Mth.sin(f) * 2.0f * 0.5f * f2;
			float f4 = Mth.cos(f) * 2.0f * 0.5f * f2;
			var fx = new thaumcraft.client.fx.particles.FXBreakingFade(clientLevel, payload.x() + f3, payload.y() + 0.5, payload.z() + f4,
					new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL));
			fx.setColor(0.1f, 0.0f, 0.1f);
			fx.setStartAlpha(0.4f);
			fx.setParticleMaxAge((int) (66.0f / (clientLevel.random.nextFloat() * 0.9f + 0.1f)));
			particleEngine.add(fx);
		}
	}

	/** Real {@link thaumcraft.client.fx.particles.FXBreakingFade} shatter burst - see the true original's own
	 * real {@code FXDispatcher.taintsplosionFX}. */
	public static void spawnTaintsplosion(net.minecraft.world.level.Level level, Entity target) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var random = clientLevel.random;
		var fx = new thaumcraft.client.fx.particles.FXBreakingFade(clientLevel, target.getX(), target.getY() + random.nextFloat() * target.getBbHeight(), target.getZ(),
				new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SLIME_BALL));
		if (random.nextBoolean()) {
			fx.setColor(0.6f, 0.0f, 0.3f);
			fx.setStartAlpha(0.4f);
		} else {
			fx.setColor(0.3f, 0.0f, 0.3f);
			fx.setStartAlpha(0.6f);
		}
		fx.setSpeed(Math.random() * 2.0 - 1.0, Math.random() * 2.0 - 1.0, Math.random() * 2.0 - 1.0);
		fx.boom();
		fx.setParticleMaxAge((int) (66.0f / (random.nextFloat() * 0.9f + 0.1f)));
		Minecraft.getInstance().particleEngine.add(fx);
	}

	/** Real client-bound handler for {@link thaumcraft.common.lib.network.fx.PacketFXEssentiaSource} - spawns
	 * (or extends an already-active) real {@link thaumcraft.client.fx.particles.FXEssentiaStream}, matching
	 * the true original's own real {@code PacketFXEssentiaSource#onMessage} de-duplication exactly (the
	 * particle's own constructor does the actual key-lookup/extend-vs-spawn decision). The true original's own
	 * real "count" seed ({@code RenderEventHandler.tickCount}, a real shared, ever-incrementing client tick
	 * counter) is replaced with the real client level's own real game time - same real purpose (vary the
	 * initial-kick wobble phase between concurrently-triggered streams), no new counter needed. */
	public static void handleEssentiaSource(thaumcraft.common.lib.network.fx.PacketFXEssentiaSource payload) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		var source = payload.source();
		var target = payload.target();
		int tickSeed = (int) level.getGameTime();
		var fx = new thaumcraft.client.fx.particles.FXEssentiaStream(level, source.x(), source.y(), source.z(),
				target.x(), target.y(), target.z(), tickSeed, payload.color(), 1.0f, payload.ext(), 0.0);
		Minecraft.getInstance().particleEngine.add(fx);
	}

	/** Real {@link thaumcraft.client.fx.particles.FXGeneric} "essentia drip" spawn - see the true original's
	 * own real {@code FXDispatcher.essentiaDropFx}, reused by {@link
	 * thaumcraft.client.fx.particles.FXEssentiaStream} itself for its own real target/mid-trail drip cues. */
	public static void spawnEssentiaDrop(net.minecraft.world.level.Level level, double x, double y, double z, float r, float g, float b, float alpha) {
		if (!(level instanceof ClientLevel clientLevel)) return;
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z,
				clientLevel.random.nextGaussian() * 0.005, clientLevel.random.nextGaussian() * 0.005, clientLevel.random.nextGaussian() * 0.005);
		fb.setMaxAge(20 + clientLevel.random.nextInt(10));
		fb.setRBGColorF(r, g, b);
		fb.setAlphaF(alpha);
		fb.setLoop(false);
		fb.setParticles(25, 1, 1);
		fb.setScale(0.4f + clientLevel.random.nextFloat() * 0.2f, 0.2f);
		fb.setLayer(1);
		fb.setGravity(0.01f);
		fb.setRotationSpeed(0.0f);
		Minecraft.getInstance().particleEngine.add(fb);
	}

	/** Real client-bound handler for {@link thaumcraft.common.lib.network.fx.PacketFXInfusionSource} - a real
	 * short burst (4 particles) of whichever real variant matches the consumed item, plus a real 1-in-3 chance
	 * of a real {@link thaumcraft.client.fx.particles.FXBoreSparkle} instead - matching the true original's
	 * own real dispatch and weighting exactly (see that packet's own doc comment for the real "one burst at
	 * consumption" vs. the true original's own real multi-tick trickle). */
	public static void handleInfusionSource(thaumcraft.common.lib.network.fx.PacketFXInfusionSource payload) {
		var level = Minecraft.getInstance().level;
		if (level == null) return;
		BlockPos source = payload.source();
		BlockPos target = payload.target();
		net.minecraft.world.item.ItemStack stack = payload.stack();
		var particleEngine = Minecraft.getInstance().particleEngine;
		var random = level.random;

		if (random.nextInt(3) == 0) {
			var sparkle = new thaumcraft.client.fx.particles.FXBoreSparkle(level,
					source.getX() + random.nextFloat(), source.getY() + 1 + random.nextFloat(), source.getZ() + random.nextFloat(),
					target.getX() + 0.5, target.getY() - 0.5, target.getZ() + 0.5);
			sparkle.setRBGColorF(0.4f + random.nextFloat() * 0.2f, 0.2f, 0.6f + random.nextFloat() * 0.3f);
			particleEngine.add(sparkle);
		} else if (stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem) {
			var state = blockItem.getBlock().defaultBlockState();
			for (int a = 0; a < 4; a++) {
				var fb = new thaumcraft.client.fx.particles.FXBoreParticles(level,
						source.getX() + random.nextFloat(), source.getY() + 1 + random.nextFloat(), source.getZ() + random.nextFloat(),
						target.getX() + 0.5, target.getY() - 0.5, target.getZ() + 0.5, state);
				fb.setAlphaF(0.3f);
				particleEngine.add(fb);
			}
		} else {
			for (int a = 0; a < 4; a++) {
				var fb = new thaumcraft.client.fx.particles.FXBoreParticlesItem(level,
						source.getX() + 0.4 + random.nextFloat() * 0.2, source.getY() + 1.23 + random.nextFloat() * 0.2, source.getZ() + 0.4 + random.nextFloat() * 0.2,
						target.getX() + 0.5, target.getY() - 0.5, target.getZ() + 0.5, stack);
				particleEngine.add(fb);
			}
		}
	}

	/** Real client-bound handler for {@link thaumcraft.common.lib.network.misc.PacketKnowledgeGain} - resolves
	 * the real category key back to a {@link thaumcraft.api.research.ResearchCategory} (the empty-string
	 * sentinel means "no category", matching the true original's own real convention), hands the gain off to
	 * {@link thaumcraft.client.gui.KnowledgeGainOverlay}, and plays the real {@code learn} sound at the
	 * player's own position (matches the true original's own real ambient-category playback exactly). */
	public static void handleKnowledgeGain(thaumcraft.common.lib.network.misc.PacketKnowledgeGain payload) {
		var mc = Minecraft.getInstance();
		var player = mc.player;
		var level = mc.level;
		if (player == null || level == null) return;
		var type = thaumcraft.api.capabilities.IPlayerKnowledge.EnumKnowledgeType.values()[payload.knowledgeType()];
		var category = payload.category().isEmpty() ? null : thaumcraft.api.research.ResearchCategories.getResearchCategory(payload.category());
		thaumcraft.client.gui.KnowledgeGainOverlay.add(type, category, level.random);
		level.playLocalSound(player.getX(), player.getY(), player.getZ(),
				thaumcraft.common.registry.ThaumcraftSounds.LEARN.get(), SoundSource.AMBIENT, 1.0f, 1.0f, false);
	}

	private ClientFxHandlers() {
	}

	/** Real {@link thaumcraft.client.fx.particles.FXGeneric} construction for every real {@code
	 * FocusEffectXxx#renderParticleFX} override, moved here 2026-09-16 after a real, previously-unseen crash:
	 * having a common-side {@code FocusEffect} subclass (loaded/bytecode-verified on BOTH sides, since its
	 * gameplay half runs server-side too) directly reference {@code FXGeneric}/{@code ClientLevel} inside its
	 * own {@code renderParticleFX} body made the JVM's own verifier resolve those real {@code
	 * @OnlyIn(Dist.CLIENT)} types the moment ANY method on that class got touched - NeoForge's own dist-check
	 * then threw {@code "Attempted to load class ... for invalid dist DEDICATED_SERVER"} even though {@code
	 * renderParticleFX} itself is only ever really invoked client-side (29 real GameTests failed on this
	 * before the fix). Every real {@code FocusEffectXxx#renderParticleFX} body now only ever builds a plain,
	 * common-safe {@link thaumcraft.common.items.casters.foci.FocusGenericFx} (primitives/arrays only, no
	 * client type anywhere) and hands it here - matching the same real "common code only ever calls a
	 * client-only class from a method whose own signature carries no client type" boundary {@link
	 * #spawnGolemArc} already established, just generalized to cover every {@code FXGeneric}-shaped effect at
	 * once instead of one dedicated method per caller. */
	public static void spawnGeneric(net.minecraft.world.level.Level level, double x, double y, double z,
			double mx, double my, double mz, thaumcraft.common.items.casters.foci.FocusGenericFx cfg) {
		if (!(level instanceof ClientLevel clientLevel)) {
			return;
		}
		var fb = new thaumcraft.client.fx.particles.FXGeneric(clientLevel, x, y, z, mx, my, mz);
		fb.setMaxAge(cfg.age);
		fb.setGridSize(cfg.grid);
		fb.setRBGColorF(cfg.redStart, cfg.greenStart, cfg.blueStart, cfg.redEnd, cfg.greenEnd, cfg.blueEnd);
		fb.setAlphaF(cfg.alpha);
		fb.setParticles(cfg.partStart, cfg.partNum, cfg.partInc);
		fb.setScale(cfg.scale);
		fb.setLoop(cfg.loop);
		fb.setLayer(cfg.layer);
		fb.setSlowDown(cfg.slowDown);
		fb.setGravity(cfg.grav);
		fb.setRandomMovementScale(cfg.randomX, cfg.randomY, cfg.randomZ);
		fb.setRotationSpeed(cfg.rotstart, cfg.rot);
		addDelayed(fb, cfg.delay);
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

	/** Real {@link FXBoreSparkle}/{@link FXBoreParticles} digging burst for {@link
	 * thaumcraft.common.entities.construct.EntityArcaneBore}, restored 2026-09-15 - see {@link
	 * PacketFXBoreDig}'s own doc comment. Matches the true original's own real {@code boreDigFx} exactly:
	 * 50 real particles total, spread evenly across the real {@code delay} ticks between digs (a real 1-in-4
	 * chance per particle of a {@link FXBoreSparkle}, otherwise a real {@link FXBoreParticles} chunk of the
	 * actual block being mined), each homing toward the bore itself. */
	public static void handleBoreDig(PacketFXBoreDig payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		Entity bore = resolveEntity(mc, level, payload.boreId());
		if (bore == null) {
			return;
		}
		BlockPos pos = payload.pos();
		var state = level.getBlockState(pos);
		if (state.isAir()) {
			return;
		}
		int delay = Math.max(1, payload.delay());
		int perTick = Math.max(1, 50 / delay);
		for (int tick = 0; tick < delay; tick++) {
			for (int i = 0; i < perTick; i++) {
				double px = pos.getX() + level.random.nextFloat();
				double py = pos.getY() + level.random.nextFloat();
				double pz = pos.getZ() + level.random.nextFloat();
				if (level.random.nextInt(4) == 0) {
					addDelayed(new FXBoreSparkle(level, px, py, pz, bore), tick);
				} else {
					FXBoreParticles fx = new FXBoreParticles(level, px, py, pz, bore.getX(), bore.getY(), bore.getZ(), state);
					fx.setTarget(bore);
					addDelayed(fx, tick);
				}
			}
		}
	}

	/** Real "render every real effect in the cast package" dispatch for {@link
	 * thaumcraft.common.items.casters.foci.FocusMediumTouch}'s own cast-impact, restored 2026-09-16 - see
	 * {@link thaumcraft.common.lib.network.fx.PacketFXFocusEffect}'s own doc comment. Matches the true
	 * original's own real {@code amt = max(1, 10/partKeys.length)} spread and real per-particle gaussian
	 * motion jitter exactly. */
	public static void handleFocusEffect(thaumcraft.common.lib.network.fx.PacketFXFocusEffect payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		List<String> keys = payload.parts();
		int amt = Math.max(1, 10 / keys.size());
		for (String key : keys) {
			if (thaumcraft.api.casters.FocusEngine.getElement(key) instanceof thaumcraft.api.casters.FocusEffect effect) {
				for (int i = 0; i < amt; i++) {
					effect.renderParticleFX(level, payload.pos().x(), payload.pos().y(), payload.pos().z(),
							payload.motion().x() + level.random.nextGaussian() / 20.0,
							payload.motion().y() + level.random.nextGaussian() / 20.0,
							payload.motion().z() + level.random.nextGaussian() / 20.0);
				}
			}
		}
	}

	/** Real "this one real effect just hit something" dispatch, restored 2026-09-16 - see {@link
	 * thaumcraft.common.lib.network.fx.PacketFXFocusPartImpact}'s own doc comment. Matches the true
	 * original's own real {@code amt = max(1, 15/partKeys.length)} spread and real fresh-on-receipt gaussian
	 * motion (scale 0.15) exactly. */
	public static void handleFocusPartImpact(thaumcraft.common.lib.network.fx.PacketFXFocusPartImpact payload) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		List<String> keys = payload.parts();
		int amt = Math.max(1, 15 / keys.size());
		for (String key : keys) {
			if (thaumcraft.api.casters.FocusEngine.getElement(key) instanceof thaumcraft.api.casters.FocusEffect effect) {
				for (int i = 0; i < amt; i++) {
					effect.renderParticleFX(level, payload.pos().x(), payload.pos().y(), payload.pos().z(),
							level.random.nextGaussian() * 0.15, level.random.nextGaussian() * 0.15, level.random.nextGaussian() * 0.15);
				}
			}
		}
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
