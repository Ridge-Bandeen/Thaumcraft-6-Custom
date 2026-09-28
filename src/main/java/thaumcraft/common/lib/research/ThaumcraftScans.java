package thaumcraft.common.lib.research;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.AbstractHurtingProjectile;
import net.minecraft.world.entity.projectile.LlamaSpit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import thaumcraft.api.blocks.BlocksTC;
import thaumcraft.api.entities.IEldritchMob;
import thaumcraft.api.items.ItemsTC;
import thaumcraft.api.research.ScanBlock;
import thaumcraft.api.research.ScanEntity;
import thaumcraft.api.research.ScanItem;
import thaumcraft.api.research.ScanOreDictionary;
import thaumcraft.api.research.ScanningManager;
import thaumcraft.common.entities.EntityFluxRift;
import thaumcraft.common.entities.construct.EntityOwnedConstruct;
import thaumcraft.common.entities.monster.EntityBrainyZombie;
import thaumcraft.common.entities.monster.EntityEldritchCrab;
import thaumcraft.common.entities.monster.EntityEldritchGuardian;
import thaumcraft.common.entities.monster.EntityFireBat;
import thaumcraft.common.entities.monster.EntityInhabitedZombie;
import thaumcraft.common.entities.monster.EntityPech;
import thaumcraft.common.entities.monster.EntityThaumicSlime;
import thaumcraft.common.entities.monster.EntityWisp;
import thaumcraft.common.entities.monster.boss.EntityThaumcraftBoss;
import thaumcraft.common.entities.monster.cult.EntityCultist;
import thaumcraft.common.entities.monster.tainted.EntityTaintCrawler;
import thaumcraft.common.entities.monster.tainted.EntityTaintSeed;
import thaumcraft.common.entities.monster.tainted.EntityTaintSwarm;
import thaumcraft.common.entities.monster.tainted.EntityTaintacle;
import thaumcraft.common.golems.EntityThaumcraftGolem;

/**
 * The original's own {@code ConfigResearch#initScannables} - every Thaumometer scan target Thaumcraft 6 registers,
 * in the original's own order. Restored 2026-09-27: the port had only the generic/enchantment/potion/sky scans plus
 * three invented stand-ins (a zombie, a pig, and a Runic Matrix scan granting Boots of the Traveller), which left 55
 * of the 148 research entries unreachable because the virtual keys they wait on ({@code "!ORECRYSTAL"},
 * {@code "f_golem"}, {@code "f_TELEPORT"}...) had nothing that could ever grant them.
 *
 * <p>Modern substitutions, each the nearest real equivalent of what the original named:
 * <ul>
 * <li>1.12 {@code EntityGolem} (iron golem, snow golem, shulker) is {@link AbstractGolem}; this port's Thaumcraft
 * golem doesn't extend {@link EntityOwnedConstruct} the way the original's did, so it's listed alongside it.</li>
 * <li>1.12 {@code EntityFireball} (large/small fireballs, dragon fireball, wither skull) is
 * {@link AbstractHurtingProjectile}; 1.12 {@code EntityArrow} is {@link AbstractArrow}.</li>
 * <li>{@code Blocks.PORTAL} is the nether portal; {@code HARDENED_CLAY}/{@code STAINED_HARDENED_CLAY} are
 * terracotta and its 16 colours; {@code ScanMaterial(Material.CLAY)} (the clay block's own material - nothing else
 * used it) is a plain scan of the clay block.</li>
 * <li>Ore-dictionary names became item tags ({@code c:} for the vanilla metals) or Thaumcraft's own items directly.</li>
 * </ul>
 */
public final class ThaumcraftScans {

	private ThaumcraftScans() {
	}

	private static void add(thaumcraft.api.research.IScanThing thing) {
		ScanningManager.addScannableThing(thing);
	}

	private static Block b(net.neoforged.neoforge.registries.DeferredBlock<? extends Block> block) {
		return block.get();
	}

	public static void register() {
		add(new ScanGeneric());
		add(new ScanEnchantment());
		for (Holder<MobEffect> effect : BuiltInRegistries.MOB_EFFECT.asHolderIdMap()) {
			add(new ScanPotion(effect));
		}

		add(new ScanEntity("!Wisp", EntityWisp.class, true));
		add(new ScanEntity("!ThaumSlime", EntityThaumicSlime.class, true));
		add(new ScanEntity("!Firebat", EntityFireBat.class, true));
		add(new ScanEntity("!Pech", EntityPech.class, true));
		add(new ScanEntity("!BrainyZombie", EntityBrainyZombie.class, true));
		add(new ScanEntity("!EldritchCrab", EntityEldritchCrab.class, true));
		add(new ScanEntity("!EldritchCrab", EntityInhabitedZombie.class, true));
		add(new ScanEntity("!CrimsonCultist", EntityCultist.class, true));
		add(new ScanEntity("!EldritchGuardian", EntityEldritchGuardian.class, true));
		add(new ScanEntity("!TaintCrawler", EntityTaintCrawler.class, true));
		add(new ScanEntity("!Taintacle", EntityTaintacle.class, true));
		add(new ScanEntity("!TaintSeed", EntityTaintSeed.class, true));
		add(new ScanEntity("!TaintSwarm", EntityTaintSwarm.class, true));
		add(new ScanEntity("f_toomuchflux", EntityFluxRift.class, true));
		add(new ScanEntity("!FluxRift", EntityFluxRift.class, true));
		add(new ScanEntity("f_golem", AbstractGolem.class, true));
		add(new ScanEntity("f_golem", EntityOwnedConstruct.class, true));
		add(new ScanEntity("f_golem", EntityThaumcraftGolem.class, true));
		add(new ScanEntity("f_SPIDER", Spider.class, true));
		add(new ScanEntity("f_BAT", Bat.class, true));
		add(new ScanEntity("f_BAT", EntityFireBat.class, true));
		add(new ScanEntity("f_FLY", Bat.class, true));
		add(new ScanEntity("f_FLY", Parrot.class, true));
		add(new ScanEntity("f_FLY", EntityFireBat.class, true));
		add(new ScanEntity("f_FLY", EntityTaintSwarm.class, true));
		add(new ScanEntity("f_FLY", EntityWisp.class, true));
		add(new ScanEntity("f_FLY", Ghast.class, true));
		add(new ScanEntity("f_FLY", Blaze.class, true));
		add(new ScanEntity("!ORMOB", IEldritchMob.class, true));
		add(new ScanEntity("!ORBOSS", EntityThaumcraftBoss.class, true));

		add(new ScanBlock("!ORBLOCK1", b(BlocksTC.stoneAncient), b(BlocksTC.stoneAncientTile)));
		add(new ScanBlock("!ORBLOCK2", b(BlocksTC.stoneEldritchTile)));
		add(new ScanBlock("!ORBLOCK3", b(BlocksTC.stoneAncientGlyphed)));

		Block[] crystals = BlocksTC.crystals.values().stream().map(net.neoforged.neoforge.registries.DeferredBlock::get).toArray(Block[]::new);
		Block[] ores = new Block[crystals.length + 2];
		ores[0] = b(BlocksTC.oreAmber);
		ores[1] = b(BlocksTC.oreCinnabar);
		System.arraycopy(crystals, 0, ores, 2, crystals.length);
		add(new ScanBlock("ORE", ores));
		add(new ScanBlock("!OREAMBER", b(BlocksTC.oreAmber)));
		add(new ScanBlock("!ORECINNABAR", b(BlocksTC.oreCinnabar)));
		add(new ScanBlock("!ORECRYSTAL", crystals));

		add(new ScanBlock("PLANTS", b(BlocksTC.logGreatwood), b(BlocksTC.logSilverwood), b(BlocksTC.saplingGreatwood), b(BlocksTC.saplingSilverwood),
				b(BlocksTC.cinderpearl), b(BlocksTC.shimmerleaf), b(BlocksTC.vishroom)));
		add(new ScanBlock("!PLANTWOOD", b(BlocksTC.logGreatwood)));
		add(new ScanBlock("!PLANTWOOD", b(BlocksTC.logSilverwood)));
		add(new ScanBlock("!PLANTWOOD", b(BlocksTC.saplingGreatwood)));
		add(new ScanBlock("!PLANTWOOD", b(BlocksTC.saplingSilverwood)));
		add(new ScanBlock("!PLANTCINDERPEARL", b(BlocksTC.cinderpearl)));
		add(new ScanBlock("!PLANTSHIMMERLEAF", b(BlocksTC.shimmerleaf)));
		add(new ScanBlock("!PLANTVISHROOM", b(BlocksTC.vishroom)));

		add(new ScanItem("PRIMPEARL", new ItemStack(ItemsTC.primordialPearl.get())));
		add(new ScanItem("!DRAGONBREATH", new ItemStack(Items.DRAGON_BREATH)));
		add(new ScanItem("!TOTEMUNDYING", new ItemStack(Items.TOTEM_OF_UNDYING)));
		add(new ScanBlock("f_TELEPORT", Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME));
		add(new ScanItem("f_TELEPORT", new ItemStack(Items.ENDER_PEARL)));
		add(new ScanEntity("f_TELEPORT", EnderMan.class, true));
		add(new ScanEntity("f_BRAIN", EntityBrainyZombie.class, true));
		add(new ScanItem("f_BRAIN", new ItemStack(ItemsTC.brain.get())));
		add(new ScanBlock("f_DISPENSER", Blocks.DISPENSER));
		add(new ScanItem("f_DISPENSER", new ItemStack(Blocks.DISPENSER)));
		add(new ScanItem("f_MATCLAY", new ItemStack(Items.CLAY_BALL)));
		add(new ScanBlock("f_MATCLAY", Blocks.TERRACOTTA, Blocks.WHITE_TERRACOTTA, Blocks.ORANGE_TERRACOTTA, Blocks.MAGENTA_TERRACOTTA,
				Blocks.LIGHT_BLUE_TERRACOTTA, Blocks.YELLOW_TERRACOTTA, Blocks.LIME_TERRACOTTA, Blocks.PINK_TERRACOTTA, Blocks.GRAY_TERRACOTTA,
				Blocks.LIGHT_GRAY_TERRACOTTA, Blocks.CYAN_TERRACOTTA, Blocks.PURPLE_TERRACOTTA, Blocks.BLUE_TERRACOTTA, Blocks.BROWN_TERRACOTTA,
				Blocks.GREEN_TERRACOTTA, Blocks.RED_TERRACOTTA, Blocks.BLACK_TERRACOTTA));
		add(new ScanBlock("f_MATCLAY", Blocks.CLAY));
		add(new ScanOreDictionary("f_MATIRON", Tags.Items.ORES_IRON, Tags.Items.INGOTS_IRON, Tags.Items.STORAGE_BLOCKS_IRON, ItemsTC.plateIron.get()));
		add(new ScanOreDictionary("f_MATBRASS", ItemsTC.ingotBrass.get(), ItemsTC.metalBlockBrass.get(), ItemsTC.plateBrass.get()));
		add(new ScanOreDictionary("f_MATTHAUMIUM", ItemsTC.ingotThaumium.get(), ItemsTC.metalBlockThaumium.get(), ItemsTC.plateThaumium.get()));
		add(new ScanOreDictionary("f_MATVOID", ItemsTC.ingotVoid.get(), ItemsTC.metalBlockVoid.get(), ItemsTC.plateVoid.get()));
		add(new ScanEntity("f_arrow", AbstractArrow.class, true));
		add(new ScanItem("f_arrow", new ItemStack(Items.ARROW)));
		add(new ScanEntity("f_fireball", AbstractHurtingProjectile.class, true));
		add(new ScanEntity("f_spit", LlamaSpit.class, true));
		add(new ScanItem("!Pechwand", new ItemStack(ItemsTC.pechWand.get())));
		add(new ScanItem("f_VOIDSEED", new ItemStack(ItemsTC.voidSeed.get())));
		add(new ScanSky());
	}
}
