package thaumcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.cult.EntityCultistKnight;

/**
 * Renders {@link EntityCultistKnight} - the original's own {@code RenderCultist} used a plain vanilla
 * {@code ModelBiped} (no custom geometry) with a {@code LayerBipedArmor} also built from plain {@code
 * ModelBiped}s, i.e. a completely ordinary humanoid renderer. The modern equivalent of "plain humanoid,
 * wide arms" is {@link ModelLayers#PLAYER} (not {@code PLAYER_SLIM} - that's the thin-armed "Alex" variant)
 * for both the body and the {@link HumanoidArmorLayer}'s inner/outer models, plus a real {@link
 * ItemInHandLayer} for the held sword. {@link HumanoidArmorLayer} auto-derives the worn texture from
 * whatever {@code ArmorMaterial} the equipped {@code ArmorItem} declares (see {@code
 * ThaumcraftArmorMaterials#CULTIST_PLATE}/{@code #CULTIST_BOOTS}) - no per-item texture override needed,
 * unlike the original's own {@code getArmorTexture}.
 *
 * Real, documented cut: the original's own ritualist floaty-line effect (a cleric-only visual, drawn in
 * this same renderer class in the original) isn't ported - that belongs to {@code EntityCultistCleric},
 * a later sub-chunk of this arc, not this knight-only renderer.
 */
public class RenderCultistKnight extends HumanoidMobRenderer<EntityCultistKnight, HumanoidModel<EntityCultistKnight>> {

	private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/cultist.png");

	public RenderCultistKnight(EntityRendererProvider.Context context) {
		super(context, new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)), 0.5f);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(EntityCultistKnight entity) {
		return TEXTURE;
	}
}
