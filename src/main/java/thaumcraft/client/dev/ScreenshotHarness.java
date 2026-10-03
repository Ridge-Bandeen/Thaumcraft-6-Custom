package thaumcraft.client.dev;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.client.gui.ThaumonomiconScreen;
import thaumcraft.common.container.ThaumonomiconMenu;

/**
 * Dev-only visual check harness (2026-09-27): with {@code -Dthaumcraft.screenshots=<plan>} set (see the
 * {@code screenshotClient} run in build.gradle), once a world has loaded it walks a scripted plan - open the
 * Thaumonomicon for real (through the server, like the item does), optionally grant research, switch categories, open
 * entries in the reader, turn pages - saving a screenshot after each step to {@code run/screenshots/}, then quits. It
 * exists so the port's rendering can be checked against the original without a human driving the client. Does nothing
 * at all unless that property is set.
 *
 * <p>Plan: comma-separated steps - {@code book}, {@code all} (grant all research), {@code cat:KEY},
 * {@code entry:KEY}, {@code page:N}, {@code press} (open a Golem Press), {@code recipe:KEY}, {@code aspects:}, {@code back}, {@code shot:name}, {@code wait:ticks},
 * {@code config} (the mod's config screen), {@code close}, {@code hold:ITEM}/{@code offhand:ITEM}, {@code sneak:true|false},
 * {@code cmd:COMMAND} (a command, {@code +} for spaces).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID, value = Dist.CLIENT)
public final class ScreenshotHarness {

	private static final String PLAN = System.getProperty("thaumcraft.screenshots");
	private static List<String> steps;
	private static int wait = 200;

	private ScreenshotHarness() {
	}

	@SubscribeEvent
	static void onTick(ClientTickEvent.Post event) {
		if (PLAN == null) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.level == null || mc.getSingleplayerServer() == null) return;
		if (steps == null) steps = new ArrayList<>(List.of(PLAN.split(",")));
		if (wait-- > 0) return;
		if (steps.isEmpty()) {
			mc.stop();
			return;
		}
		String step = steps.remove(0).trim();
		try {
			run(mc, step);
		} catch (Exception e) {
			Thaumcraft.LOGGER.error("Screenshot harness step {} failed", step, e);
		}
	}

	private static void run(Minecraft mc, String step) throws Exception {
		String arg = step.contains(":") ? step.substring(step.indexOf(':') + 1) : "";
		wait = 20;
		switch (step.contains(":") ? step.substring(0, step.indexOf(':')) : step) {
			case "book" -> mc.getSingleplayerServer().execute(() -> {
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp != null) sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new ThaumonomiconMenu(id, inv),
						Component.translatable("item.thaumcraft.thaumonomicon")));
			});
			case "all" -> mc.player.connection.sendCommand("thaumcraft research @s all");
			case "reset" -> mc.player.connection.sendCommand("thaumcraft research @s reset");
			case "table" -> mc.getSingleplayerServer().execute(() -> {
				// a Research Table two blocks ahead with ink and paper, a bookshelf beside it (an aid), then open it
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp == null) return;
				net.minecraft.core.BlockPos pos = sp.blockPosition().relative(sp.getDirection(), 2);
				sp.serverLevel().setBlockAndUpdate(pos, thaumcraft.api.blocks.BlocksTC.researchTable.get().defaultBlockState());
				sp.serverLevel().setBlockAndUpdate(pos.relative(sp.getDirection().getClockWise()), net.minecraft.world.level.block.Blocks.BOOKSHELF.defaultBlockState());
				if (sp.serverLevel().getBlockEntity(pos) instanceof thaumcraft.common.tiles.crafting.TileResearchTable tile) {
					tile.getItemHandler().insertItem(0, new net.minecraft.world.item.ItemStack(thaumcraft.api.items.ItemsTC.scribingTools.get()), false);
					tile.getItemHandler().insertItem(1, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.PAPER, 16), false);
					sp.openMenu(tile);
				}
			});
			case "fx" -> {
				// a client effect 3 blocks ahead at eye height: fx:bamf (the infusion puff) or fx:shield (the pedestal shield)
				var look = mc.player.getLookAngle();
				double x = mc.player.getX() + look.x * 3, y = mc.player.getEyeY() + look.y * 3, z = mc.player.getZ() + look.z * 3;
				if (arg.equals("bamf")) {
					thaumcraft.client.fx.ClientFxHandlers.drawBamf(mc.level, x, y, z, 0.5f, 0.1f, 0.6f, true, true, null);
				} else {
					thaumcraft.client.render.ShieldRunesFx.spawn(x, y - 1, z, null, 40, 0.0f, 90.0f);
				}
			}
			case "press" -> mc.getSingleplayerServer().execute(() -> {
				// a Golem Press two blocks ahead, a chest beside it with some iron golem parts, then open it
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp == null) return;
				net.minecraft.core.BlockPos pos = sp.blockPosition().relative(sp.getDirection(), 2);
				sp.serverLevel().setBlockAndUpdate(pos, thaumcraft.api.blocks.BlocksTC.golemBuilder.get().defaultBlockState());
				net.minecraft.core.BlockPos chestPos = pos.relative(sp.getDirection().getClockWise());
				sp.serverLevel().setBlockAndUpdate(chestPos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
				if (sp.serverLevel().getBlockEntity(chestPos) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity chest) {
					chest.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.IRON_INGOT, 16));
				}
				if (sp.serverLevel().getBlockEntity(pos) instanceof thaumcraft.common.tiles.crafting.TileGolemBuilder tile) {
					sp.openMenu(tile);
				}
			});
			case "click" -> {
				if (mc.player.containerMenu != null) mc.gameMode.handleInventoryButtonClick(mc.player.containerMenu.containerId, Integer.parseInt(arg));
			}
			case "cat" -> invoke(mc, "selectCategory", String.class, arg);
			case "entry" -> {
				if (mc.screen instanceof ThaumonomiconScreen s) {
					Method m = ThaumonomiconScreen.class.getDeclaredMethod("openPage", ResearchEntry.class, net.minecraft.resources.ResourceLocation.class);
					m.setAccessible(true);
					m.invoke(s, ResearchCategories.getResearch(arg), null);
				}
			}
			case "back" -> invoke(mc, "closePage", null, null);
			case "page", "recipe", "aspects" -> {
				if (mc.screen instanceof ThaumonomiconScreen s) {
					Field pf = ThaumonomiconScreen.class.getDeclaredField("page");
					pf.setAccessible(true);
					Object view = pf.get(s);
					if (view == null) return;
					Class<?> vc = view.getClass();
					switch (step.substring(0, step.indexOf(':') < 0 ? step.length() : step.indexOf(':'))) {
						case "page" -> {
							Field f = vc.getDeclaredField("page");
							f.setAccessible(true);
							f.setInt(view, Integer.parseInt(arg));
						}
						case "recipe" -> {
							Field f = vc.getDeclaredField("shownRecipe");
							f.setAccessible(true);
							f.set(null, net.minecraft.resources.ResourceLocation.parse(arg.toLowerCase(java.util.Locale.ROOT)));
						}
						default -> {
							Field f = vc.getDeclaredField("showingAspects");
							f.setAccessible(true);
							f.setBoolean(view, true);
						}
					}
				}
			}
			case "config" -> mc.setScreen(new net.neoforged.neoforge.client.gui.ConfigurationScreen(
					net.neoforged.fml.ModList.get().getModContainerById(Thaumcraft.MODID).orElseThrow(), null));
			case "close" -> mc.setScreen(null);
			case "hold", "offhand" -> mc.getSingleplayerServer().execute(() -> {
				// hold:thaumcraft:thaumometer - put an item in the main (or off) hand
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp == null) return;
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(arg));
				sp.setItemInHand(step.startsWith("hold") ? net.minecraft.world.InteractionHand.MAIN_HAND : net.minecraft.world.InteractionHand.OFF_HAND,
						new net.minecraft.world.item.ItemStack(item));
			});
			case "cmd" -> mc.player.connection.sendCommand(arg.replace('+', ' ')); // cmd:effect+give+@s+...
			case "sneak" -> mc.options.keyShift.setDown(Boolean.parseBoolean(arg));
			case "face" -> { // square up to the nearest cardinal direction, level
				mc.player.setYRot(mc.player.getDirection().toYRot());
				mc.player.setXRot(Float.parseFloat(arg.isEmpty() ? "0" : arg));
			}
			case "turn" -> {
				// turn the player to a yaw (degrees), client and server, so later steps face that way
				float yaw = Float.parseFloat(arg.isEmpty() ? "0" : arg);
				mc.player.setYRot(yaw);
				mc.getSingleplayerServer().execute(() -> {
					ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
					if (sp != null) sp.teleportTo(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(), yaw, sp.getXRot());
				});
			}
			case "golem", "golemopen" -> mc.getSingleplayerServer().execute(() -> {
				// a golem N blocks ahead (behind whatever is there), plus a stone wall 2 blocks in front of it ("golemopen": no wall)
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp == null) return;
				int dist = arg.isEmpty() ? 6 : Integer.parseInt(arg);
				net.minecraft.core.BlockPos at = sp.blockPosition().relative(sp.getDirection(), dist);
				boolean open = step.startsWith("golemopen");
				for (int dx = -2; dx <= 2; dx++) for (int dy = 0; dy <= 3; dy++) {
					net.minecraft.core.BlockPos w = sp.blockPosition().relative(sp.getDirection(), dist - 2).relative(sp.getDirection().getClockWise(), dx).above(dy);
					sp.serverLevel().setBlockAndUpdate(w, (open ? net.minecraft.world.level.block.Blocks.AIR : net.minecraft.world.level.block.Blocks.STONE).defaultBlockState());
				}
				thaumcraft.common.golems.EntityThaumcraftGolem golem = thaumcraft.common.registry.ThaumcraftEntities.GOLEM.get().create(sp.serverLevel());
				thaumcraft.common.golems.GolemProperties props = new thaumcraft.common.golems.GolemProperties();
				props.setMaterial(thaumcraft.api.golems.parts.GolemMaterial.getMaterials()[0]);
				props.setHead(thaumcraft.api.golems.parts.GolemHead.getHeads()[0]);
				props.setArms(thaumcraft.api.golems.parts.GolemArm.getArms()[0]);
				props.setLegs(thaumcraft.api.golems.parts.GolemLeg.getLegs()[0]);
				props.setAddon(thaumcraft.api.golems.parts.GolemAddon.getAddons()[0]);
				golem.moveTo(at.getX() + 0.5, at.getY() + (open ? 2 : 0), at.getZ() + 0.5, sp.getYRot() + 180, 0);
				golem.initializeGolem(props, 0);
				golem.setNoAi(true);
				sp.serverLevel().addFreshEntity(golem);
			});
			case "seal" -> mc.getSingleplayerServer().execute(() -> {
				// a stone block 3 ahead at eye level with a Breaker seal (it has a work area) on the side facing the player
				ServerPlayer sp = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
				if (sp == null) return;
				net.minecraft.core.BlockPos pos = sp.blockPosition().above().relative(sp.getDirection(), arg.isEmpty() ? 3 : Integer.parseInt(arg));
				sp.serverLevel().setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
				thaumcraft.common.golems.seals.SealHandler.addSealEntity(sp.serverLevel(), pos, sp.getDirection().getOpposite(),
						new thaumcraft.common.golems.seals.SealBreaker(), sp);
			});
			case "wait" -> wait = Integer.parseInt(arg);
			case "shot" -> {
				File dir = new File(mc.gameDirectory, "screenshots");
				Screenshot.grab(mc.gameDirectory, "harness_" + arg + ".png", mc.getMainRenderTarget(), msg -> {
				});
				Thaumcraft.LOGGER.info("Screenshot harness saved {}", new File(dir, "harness_" + arg + ".png"));
			}
			default -> Thaumcraft.LOGGER.warn("Screenshot harness: unknown step {}", step);
		}
	}

	private static void invoke(Minecraft mc, String name, Class<?> type, Object arg) throws Exception {
		if (!(mc.screen instanceof ThaumonomiconScreen s)) {
			Thaumcraft.LOGGER.warn("Screenshot harness: {} needs the Thaumonomicon open (screen is {})", name, mc.screen);
			return;
		}
		Method m = type == null ? ThaumonomiconScreen.class.getDeclaredMethod(name) : ThaumonomiconScreen.class.getDeclaredMethod(name, type);
		m.setAccessible(true);
		if (type == null) m.invoke(s);
		else m.invoke(s, arg);
	}
}
