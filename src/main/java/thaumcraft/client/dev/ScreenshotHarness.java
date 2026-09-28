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
 * {@code entry:KEY}, {@code page:N}, {@code recipe:KEY}, {@code aspects:}, {@code back}, {@code shot:name}, {@code wait:ticks}.
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
