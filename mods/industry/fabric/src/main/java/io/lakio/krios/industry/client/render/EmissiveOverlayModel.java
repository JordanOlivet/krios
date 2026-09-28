package io.lakio.krios.industry.client.render;

import io.lakio.krios.industry.Constants;
import io.lakio.krios.industry.client.EmissiveOverlays;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import net.fabricmc.fabric.api.renderer.v1.RendererAccess;
import net.fabricmc.fabric.api.renderer.v1.material.BlendMode;
import net.fabricmc.fabric.api.renderer.v1.material.RenderMaterial;
import net.fabricmc.fabric.api.renderer.v1.model.ForwardingBakedModel;
import net.fabricmc.fabric.api.renderer.v1.model.SpriteFinder;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fabric side of the emissive ore overlay (the NeoForge side is pure model
 * JSON, {@code neoforge:face_data}): wraps the baked model and re-emits
 * every quad whose sprite ends in {@code _glow} as fullbright cutout.
 * Generic on purpose — any future model can opt in by using a {@code _glow}
 * sprite and adding its id to {@link #TARGETS}.
 */
public class EmissiveOverlayModel extends ForwardingBakedModel {

    /** Block model files whose baked form gets wrapped. */
    public static final Set<ResourceLocation> TARGETS = EmissiveOverlays.BLOCKS.stream()
            .map(name -> ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "block/" + name))
            .collect(Collectors.toUnmodifiableSet());

    public EmissiveOverlayModel(BakedModel wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public boolean isVanillaAdapter() {
        // Force the FRAPI path so emitBlockQuads runs even though the
        // wrapped model is a plain JSON one.
        return false;
    }

    @Override
    public void emitBlockQuads(BlockAndTintGetter blockView, BlockState state, BlockPos pos,
            Supplier<RandomSource> randomSupplier, RenderContext context) {
        SpriteFinder finder = SpriteFinder.get(
                Minecraft.getInstance().getModelManager().getAtlas(TextureAtlas.LOCATION_BLOCKS));
        RenderMaterial glow = RendererAccess.INSTANCE.getRenderer().materialFinder()
                .blendMode(BlendMode.CUTOUT)
                .emissive(true)
                .disableDiffuse(true)
                .ambientOcclusion(TriState.FALSE)
                .find();
        context.pushTransform(quad -> {
            TextureAtlasSprite sprite = finder.find(quad);
            if (sprite != null && sprite.contents().name().getPath().endsWith(EmissiveOverlays.GLOW_SUFFIX)) {
                quad.material(glow);
            }
            return true;
        });
        super.emitBlockQuads(blockView, state, pos, randomSupplier, context);
        context.popTransform();
    }
}
