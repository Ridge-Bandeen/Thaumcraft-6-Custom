package thaumcraft.common.lib;

import java.util.Arrays;
import java.util.Collection;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.tree.LiteralCommandNode;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import thaumcraft.Thaumcraft;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.capabilities.IPlayerKnowledge;
import thaumcraft.api.capabilities.IPlayerWarp;
import thaumcraft.api.capabilities.ThaumcraftCapabilities;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchCategory;
import thaumcraft.api.research.ResearchEntry;
import thaumcraft.api.research.ResearchStage;

/**
 * Replaces the original's {@code /thaumcraft} (aliases {@code /thaum}, {@code /tc}) command, {@code CommandThaumcraft} - the tester's
 * tool for handing out and taking back research and warp (the port had no commands at all until 2026-09-26, so nothing could unlock research
 * short of playing to it):
 * <ul>
 * <li>{@code /thaumcraft research list} - every research key that exists</li>
 * <li>{@code /thaumcraft research <player> list|all|reset|<research>} - what a player knows; give everything; reset (keeping the auto-unlocked
 * starters); give one research together with everything it needs</li>
 * <li>{@code /thaumcraft research <player> revoke <research>} - take one back, and everything built on it</li>
 * <li>{@code /thaumcraft warp <player> add|set <amount> [perm|temp]}</li>
 * </ul>
 * Not ported: {@code reload} (re-reading the research JSON - {@code /reload} covers that here).
 */
@EventBusSubscriber(modid = Thaumcraft.MODID)
public final class ThaumcraftCommands {

	private ThaumcraftCommands() {
	}

	@SubscribeEvent
	static void onRegisterCommands(RegisterCommandsEvent event) {
		LiteralCommandNode<CommandSourceStack> root = event.getDispatcher().register(Commands.literal("thaumcraft")
				.requires(source -> source.hasPermission(2))
				.then(Commands.literal("research")
						.then(Commands.literal("list").executes(ctx -> listResearch(ctx.getSource())))
						.then(Commands.argument("player", EntityArgument.player())
								.then(Commands.literal("list").executes(ctx -> listKnown(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
								.then(Commands.literal("all").executes(ctx -> giveAll(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
								.then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))
								.then(Commands.literal("revoke")
										.then(Commands.argument("research", StringArgumentType.word())
												.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(allKeys(), builder))
												.executes(ctx -> revoke(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "research")))))
								.then(Commands.argument("research", StringArgumentType.word())
										.suggests((ctx, builder) -> SharedSuggestionProvider.suggest(allKeys(), builder))
										.executes(ctx -> give(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"), StringArgumentType.getString(ctx, "research"))))))
				.then(Commands.literal("warp")
						.then(Commands.argument("player", EntityArgument.player())
								.then(warpMode("add")).then(warpMode("set")))));
		event.getDispatcher().register(Commands.literal("thaum").requires(source -> source.hasPermission(2)).redirect(root));
		event.getDispatcher().register(Commands.literal("tc").requires(source -> source.hasPermission(2)).redirect(root));
	}

	private static LiteralArgumentBuilder<CommandSourceStack> warpMode(String mode) {
		return Commands.literal(mode).then(Commands.argument("amount", IntegerArgumentType.integer(mode.equals("add") ? -100 : 0, mode.equals("add") ? 100 : Integer.MAX_VALUE))
				.executes(ctx -> warp(ctx, mode, ""))
				.then(Commands.literal("perm").executes(ctx -> warp(ctx, mode, "perm")))
				.then(Commands.literal("temp").executes(ctx -> warp(ctx, mode, "temp"))));
	}

	private static Collection<String> allKeys() {
		java.util.List<String> keys = new java.util.ArrayList<>();
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			keys.addAll(category.research.keySet());
		}
		return keys;
	}

	private static Component ok() {
		return Component.literal("Success!").withStyle(ChatFormatting.DARK_PURPLE);
	}

	private static Component purple(String text) {
		return Component.literal(text).withStyle(ChatFormatting.DARK_PURPLE);
	}

	private static int listResearch(CommandSourceStack source) {
		int count = 0;
		for (String key : allKeys()) {
			source.sendSuccess(() -> purple(key), false);
			count++;
		}
		return count;
	}

	private static int listKnown(CommandSourceStack source, ServerPlayer player) {
		source.sendSuccess(() -> purple("Research for " + player.getName().getString()), false);
		source.sendSuccess(() -> purple(String.join(", ", ThaumcraftCapabilities.getKnowledge(player).getResearchList())), false);
		return 1;
	}

	private static int give(CommandSourceStack source, ServerPlayer player, String research) {
		if (ResearchCategories.getResearch(research) == null) {
			source.sendFailure(Component.literal("Research does not exist."));
			return 0;
		}
		giveRecursiveResearch(player, research);
		ThaumcraftCapabilities.getKnowledge(player).sync(player);
		player.sendSystemMessage(purple(source.getTextName() + " gave you " + research + " research and its requisites."));
		source.sendSuccess(ThaumcraftCommands::ok, false);
		return 1;
	}

	/** The original's {@code giveRecursiveResearch}: parents first, then the research other stages ask for, then this, then its siblings. */
	public static void giveRecursiveResearch(ServerPlayer player, String research) {
		int at = research.indexOf('@');
		if (at >= 0) research = research.substring(0, at);
		ResearchEntry entry = ResearchCategories.getResearch(research);
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (knowledge.isResearchComplete(research)) return;
		if (entry != null && entry.getParents() != null) {
			for (String parent : entry.getParentsStripped()) {
				giveRecursiveResearch(player, parent);
			}
		}
		if (entry != null && entry.getStages() != null) {
			for (ResearchStage stage : entry.getStages()) {
				if (stage.getResearch() != null) {
					for (String required : stage.getResearch()) {
						ThaumcraftApi.internalMethods.completeResearch(player, required);
					}
				}
			}
		}
		ThaumcraftApi.internalMethods.completeResearch(player, research);
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			for (ResearchEntry other : category.research.values()) {
				if (other.getStages() == null) continue;
				for (ResearchStage stage : other.getStages()) {
					if (stage.getResearch() != null && Arrays.asList(stage.getResearch()).contains(research)) {
						knowledge.setResearchFlag(other.getKey(), IPlayerKnowledge.EnumResearchFlag.PAGE);
						break;
					}
				}
			}
		}
		if (entry != null && entry.getSiblings() != null) {
			for (String sibling : entry.getSiblings()) {
				giveRecursiveResearch(player, sibling);
			}
		}
	}

	private static int revoke(CommandSourceStack source, ServerPlayer player, String research) {
		if (ResearchCategories.getResearch(research) == null) {
			source.sendFailure(Component.literal("Research does not exist."));
			return 0;
		}
		revokeRecursiveResearch(player, research);
		ThaumcraftCapabilities.getKnowledge(player).sync(player);
		player.sendSystemMessage(purple(source.getTextName() + " removed " + research + " research and its children."));
		source.sendSuccess(ThaumcraftCommands::ok, false);
		return 1;
	}

	public static void revokeRecursiveResearch(ServerPlayer player, String research) {
		int at = research.indexOf('@');
		if (at >= 0) research = research.substring(0, at);
		IPlayerKnowledge knowledge = ThaumcraftCapabilities.getKnowledge(player);
		if (!knowledge.isResearchComplete(research)) return;
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			for (ResearchEntry other : category.research.values()) {
				if (other != null && other.getParents() != null && knowledge.isResearchComplete(other.getKey())) {
					for (String parent : other.getParentsStripped()) {
						if (parent.equals(research)) revokeRecursiveResearch(player, other.getKey());
					}
				}
			}
		}
		knowledge.removeResearch(research);
	}

	private static int giveAll(CommandSourceStack source, ServerPlayer player) {
		for (String key : allKeys()) {
			giveRecursiveResearch(player, key);
		}
		ThaumcraftCapabilities.getKnowledge(player).sync(player);
		player.sendSystemMessage(purple(source.getTextName() + " has given you all research."));
		source.sendSuccess(ThaumcraftCommands::ok, false);
		return 1;
	}

	private static int reset(CommandSourceStack source, ServerPlayer player) {
		ThaumcraftCapabilities.getKnowledge(player).clear();
		for (ResearchCategory category : ResearchCategories.researchCategories.values()) {
			for (ResearchEntry entry : category.research.values()) {
				if (entry.hasMeta(ResearchEntry.EnumResearchMeta.AUTOUNLOCK)) {
					ThaumcraftApi.internalMethods.completeResearch(player, entry.getKey());
				}
			}
		}
		player.sendSystemMessage(purple(source.getTextName() + " has reset all your research."));
		source.sendSuccess(ThaumcraftCommands::ok, false);
		ThaumcraftCapabilities.getKnowledge(player).sync(player);
		return 1;
	}

	private static int warp(CommandContext<CommandSourceStack> ctx, String mode, String type) throws CommandSyntaxException {
		ServerPlayer player = EntityArgument.getPlayer(ctx, "player");
		int amount = IntegerArgumentType.getInteger(ctx, "amount");
		IPlayerWarp.EnumWarpType warpType = type.equals("perm") ? IPlayerWarp.EnumWarpType.PERMANENT
				: type.equals("temp") ? IPlayerWarp.EnumWarpType.TEMPORARY : IPlayerWarp.EnumWarpType.NORMAL;
		IPlayerWarp warp = ThaumcraftCapabilities.getWarp(player);
		if (mode.equals("set")) warp.set(warpType, amount);
		else warp.add(warpType, amount);
		warp.sync(player);
		player.sendSystemMessage(purple(ctx.getSource().getTextName() + (mode.equals("set") ? " set your warp to " + amount : " added " + amount + " warp to your total.")));
		ctx.getSource().sendSuccess(ThaumcraftCommands::ok, false);
		return 1;
	}
}
