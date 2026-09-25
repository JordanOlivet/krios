package io.lakio.krios.industry.koios;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Item data component carrying a core's learning state, so a broken core
 * keeps its XP in item form ("XP is never lost", design). Copied onto the
 * drop by the block loot table and reapplied on placement.
 */
public record KoiosData(int xp, List<String> window) {

    public static final Codec<KoiosData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("xp").forGetter(KoiosData::xp),
            Codec.STRING.listOf().fieldOf("window").forGetter(KoiosData::window)
    ).apply(instance, KoiosData::new));
}
