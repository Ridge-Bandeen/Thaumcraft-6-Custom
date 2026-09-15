package thaumcraft.common.entities.monster.cult;

import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsRestrictionGoal;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.AbstractIllager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import thaumcraft.api.items.ItemsTC;

/**
 * Replaces the old (1.12.2) {@code EntityCultistKnight} - the Crimson Cult's melee-tier rank. Real vanilla
 * Goal reuse throughout (see {@link EntityCultist}'s own doc comment for why there's no custom AI class
 * needed at all): {@link HurtByTargetGoal#setAlertOthers} now covers the original's own hand-rolled {@code
 * AICultistHurtByTarget} (which existed only because 1.12's vanilla {@code EntityAIHurtByTarget} didn't
 * support alerting nearby same-species mobs yet - modern vanilla's own version does, natively, so the
 * custom class is genuinely redundant now, not just simplified away).
 *
 * Real equipment: always wears the Crimson Plate set (helm/chest/legs, see {@link
 * thaumcraft.api.ThaumcraftArmorMaterials#CULTIST_PLATE}), a real 5%(hard)/1%(normal) chance of Crimson
 * Boots, and a real 5%(hard)/1%(normal) chance of a rare weapon swap (Void Sword or Thaumium Sword) instead
 * of the default Iron Sword - matching the original's own {@code setLoot} odds exactly. One simplification:
 * the original also swapped the plate helm for a Crimson Robe Helm when the Void Sword roll hit - that item
 * belongs to the Cleric-tier Robe set, a later sub-chunk of this same arc (see {@link EntityCultist}'s own
 * doc comment), so this knight just keeps its plate helm in that case for now.
 *
 * Real targeting: also engages a nearby real {@link thaumcraft.common.entities.monster.EntityEldritchGuardian}
 * (restored 2026-09-15 - that mob is fully real and shipped now, this class's own targeting entry just hadn't
 * caught up), on top of the base {@link EntityCultist}/{@code Player}/{@code AbstractIllager} entries every
 * cultist rank shares. Real rare "curio" bonus drop, gated on the killer's own known research, comes free
 * from {@link thaumcraft.common.lib.events.MobDeathDropEvents#onLivingDrops} - that real handler already
 * covers every {@link EntityCultist} subtype (this one included), so no per-subclass wiring is needed here.
 *
 * Real, documented cuts vs the original: no random-enchantment roll on the rare weapon drop (the original's
 * own {@code setEnchantmentBasedOnDifficulty} via {@code EnchantmentHelper.addRandomEnchantment} - modern
 * MC's registry-driven enchantment selection needs substantially more plumbing than this one cosmetic bonus
 * on an already-rare drop justifies), and the original's own data-driven {@code loot_table/cultist.json}
 * (1.12-format, unusable as-is under 1.21.1's schema - see this class's own {@link #dropCustomDeathLoot}
 * for the hand-rolled gold-nugget replacement, matching the {@code EntityEldritchCrab} precedent of a Java
 * override over a data-driven table for a single extra drop).
 */
public class EntityCultistKnight extends EntityCultist {

	public EntityCultistKnight(EntityType<? extends EntityCultistKnight> type, Level level) {
		super(type, level);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return EntityCultist.createAttributes().add(Attributes.MAX_HEALTH, 30.0);
	}

	@Override
	protected void registerGoals() {
		goalSelector.addGoal(0, new FloatGoal(this));
		goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0, false));
		goalSelector.addGoal(4, new OpenDoorGoal(this, true));
		goalSelector.addGoal(6, new MoveTowardsRestrictionGoal(this, 0.8));
		goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8));
		goalSelector.addGoal(8, new RandomLookAroundGoal(this));
		targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
		targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
		targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, thaumcraft.common.entities.monster.EntityEldritchGuardian.class, true));
		targetSelector.addGoal(4, new NearestAttackableTargetGoal<>(this, AbstractIllager.class, true));
	}

	@Override
	public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType spawnType, SpawnGroupData spawnGroupData) {
		setLoot(difficulty);
		return super.finalizeSpawn(level, difficulty, spawnType, spawnGroupData);
	}

	private void setLoot(DifficultyInstance diff) {
		RandomSource rand = random;
		boolean hard = diff.getDifficulty() == Difficulty.HARD;
		setItemSlot(EquipmentSlot.HEAD, new ItemStack(ItemsTC.crimsonPlateHelm.get()));
		setItemSlot(EquipmentSlot.CHEST, new ItemStack(ItemsTC.crimsonPlateChest.get()));
		setItemSlot(EquipmentSlot.LEGS, new ItemStack(ItemsTC.crimsonPlateLegs.get()));
		if (rand.nextFloat() < (hard ? 0.05f : 0.01f)) {
			setItemSlot(EquipmentSlot.FEET, new ItemStack(ItemsTC.crimsonBoots.get()));
		}
		if (rand.nextFloat() < (hard ? 0.05f : 0.01f)) {
			if (rand.nextInt(5) == 0) {
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemsTC.voidSword.get()));
			}
			else {
				setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ItemsTC.thaumiumSword.get()));
				if (rand.nextBoolean()) {
					setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
				}
			}
		}
		else {
			setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
		}
	}

	@Override
	protected void dropCustomDeathLoot(net.minecraft.server.level.ServerLevel level, DamageSource damageSource, boolean recentlyHit) {
		spawnAtLocation(new ItemStack(Items.GOLD_NUGGET, random.nextInt(3)));
	}
}
