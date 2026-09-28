package io.lakio.krios.industry.client.render;

import io.lakio.krios.industry.client.EmissiveOverlays;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.QuadTransformers;
import net.neoforged.neoforge.client.model.data.ModelData;

/**
 * NeoForge side of the emissive ore overlay (the Fabric side is a FRAPI
 * quad transform): rebakes every quad whose sprite ends in {@code _glow}
 * with a fullbright lightmap, which the patched vanilla light pipeline
 * maxes against world light.
 */
public class EmissiveOverlayModel extends BakedModelWrapper<BakedModel> {

    private static final IQuadTransformer FULLBRIGHT =
            QuadTransformers.applyingLightmap(LightTexture.FULL_BRIGHT);

    public EmissiveOverlayModel(BakedModel wrapped) {
        super(wrapped);
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand) {
        return glowUp(super.getQuads(state, side, rand));
    }

    @Override
    public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource rand,
            ModelData data, RenderType renderType) {
        return glowUp(super.getQuads(state, side, rand, data, renderType));
    }

    private static List<BakedQuad> glowUp(List<BakedQuad> quads) {
        List<BakedQuad> out = null;
        for (int i = 0; i < quads.size(); i++) {
            BakedQuad quad = quads.get(i);
            if (quad.getSprite().contents().name().getPath().endsWith(EmissiveOverlays.GLOW_SUFFIX)) {
                if (out == null) {
                    out = new ArrayList<>(quads);
                }
                out.set(i, FULLBRIGHT.process(quad));
            }
        }
        return out != null ? out : quads;
    }
}
