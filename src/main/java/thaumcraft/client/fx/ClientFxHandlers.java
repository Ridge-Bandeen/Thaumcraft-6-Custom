package thaumcraft.client.fx;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Set;

import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.common.lib.network.fx.PacketFXBlockBamf;
import thaumcraft.common.lib.network.fx.PacketFXScanSource;
import thaumcraft.common.lib.network.fx.PacketFXSlash;

/**
 * Client-side handlers for {@code thaumcraft.common.lib.network.fx}'s payloads - referenced only by method
 * reference from {@code Thaumcraft#registerPayloads} (common code, runs on both sides), never called
 * directly from there. Safe on a dedicated server because a {@code playToClient}-registered handler is only
 * ever invoked on the receiving (client) side - registering it is a common concern (both sides need the
 * payload's codec to en/decode it), actually running it isn't. See that method's own doc comment.
 *
 * Every effect here is built from plain vanilla particles/sounds, not the original's custom
 * {@code FXDispatcher}/{@code ParticleEngine} renderer pipeline - the same boundary this whole port has kept
 * since Cinderpearl's own client FX, now extended to cover networked effects too.
 */
public final class ClientFxHandlers {

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
	 * own {@code calcGroup}), and marks each group's centre with a small burst of vanilla's own colored-dust
	 * particle. Replaces the original's custom {@code FXGeneric} billboard particle (with its own grid
	 * animation, looping, layered alpha) - one real simplification along with it: no distance-proportional
	 * delay staggering the reveal outward like a sonar ping, every group appears at once instead. */
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
			markGroup(level, group, groupBlock);
		}
	}

	private static void markGroup(ClientLevel level, Set<BlockPos> group, Block block) {
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

		DustParticleOptions dust = new DustParticleOptions(oreColor(block), 1.5f);
		int count = Math.min(10, 4 + group.size());
		for (int i = 0; i < count; i++) {
			double ox = (level.random.nextFloat() - level.random.nextFloat()) * 0.6;
			double oy = (level.random.nextFloat() - level.random.nextFloat()) * 0.6;
			double oz = (level.random.nextFloat() - level.random.nextFloat()) * 0.6;
			level.addParticle(dust, x + ox, y + oy, z + oz, 0.0, 0.02, 0.0);
		}
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
