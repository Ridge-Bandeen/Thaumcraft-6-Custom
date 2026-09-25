package thaumcraft.client.render;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;
import thaumcraft.common.entities.monster.EntityPech;

/**
 * Replaces the old (1.12.2) {@code RenderPech} - see {@link EntityPech}'s own doc comment for why this
 * reuses vanilla's own {@link HumanoidModel} (baked from {@link ModelLayers#PLAYER}) rather than porting
 * the original's custom {@code ModelPech} geometry, the same technique {@code RenderCultistKnight} already
 * established. Three real bundled textures, one per {@link EntityPech#getPechType()} (0 = forage/melee,
 * 1 = thaum/wand, 2 = stalker/bow) - matching the original's own three skins exactly.
 */
public class RenderPech extends HumanoidMobRenderer<EntityPech, ModelPech> {

	private static final ResourceLocation FORAGE = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_forage.png");
	private static final ResourceLocation THAUM = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_thaum.png");
	private static final ResourceLocation STALKER = ResourceLocation.fromNamespaceAndPath("thaumcraft", "textures/entity/pech_stalker.png");

	public RenderPech(EntityRendererProvider.Context context) {
		super(context, new ModelPech(ModelPech.createBodyLayer().bakeRoot()), 0.4f);
		addLayer(new HumanoidArmorLayer<>(this,
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
				new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
				context.getModelManager()));
		addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
	}

	@Override
	public ResourceLocation getTextureLocation(EntityPech entity) {
		return switch (entity.getPechType()) {
			case 1 -> THAUM;
			case 2 -> STALKER;
			default -> FORAGE;
		};
	}
}
