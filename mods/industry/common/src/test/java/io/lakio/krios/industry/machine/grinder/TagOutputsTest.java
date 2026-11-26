package io.lakio.krios.industry.machine.grinder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class TagOutputsTest {

    private static ResourceLocation id(String id) {
        return ResourceLocation.parse(id);
    }

    @Test
    void anEmptyTagGivesNothing() {
        assertTrue(TagOutputs.pick(List.of()).isEmpty());
    }

    @Test
    void aKriosItemWinsOverEveryOtherMod() {
        assertEquals(Optional.of(id("krios_core:iron_dust")), TagOutputs.pick(List.of(
                id("alltheores:iron_dust"), id("minecraft:iron_dust"), id("krios_core:iron_dust"),
                id("mekanism:dust_iron"))));
    }

    @Test
    void vanillaWinsOverAMod() {
        assertEquals(Optional.of(id("minecraft:raw_iron")), TagOutputs.pick(List.of(
                id("aaa:raw_iron"), id("minecraft:raw_iron"))));
    }

    @Test
    void betweenModsTheNamespaceDecidesThenThePath() {
        assertEquals(Optional.of(id("mekanism:dust_tin")), TagOutputs.pick(List.of(
                id("thermal:tin_dust"), id("mekanism:dust_tin"), id("techreborn:tin_dust"))));
        assertEquals(Optional.of(id("mekanism:a_tin")), TagOutputs.pick(List.of(
                id("mekanism:dust_tin"), id("mekanism:a_tin"))));
    }

    @Test
    void theOrderOfTheTagDoesNotMatter() {
        List<ResourceLocation> ids = List.of(id("thermal:tin_dust"), id("mekanism:dust_tin"),
                id("immersiveengineering:dust_tin"));
        assertEquals(TagOutputs.pick(ids), TagOutputs.pick(ids.reversed()));
    }
}
