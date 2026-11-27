package io.lakio.krios.industry.machine.grinder;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.lakio.krios.core.koios.KoiosBalance;
import io.lakio.krios.industry.IndustryBalance;
import io.lakio.krios.industry.machine.MachineTiers;
import org.junit.jupiter.api.Test;

/**
 * The shipped grinding recipes against the yield rule (ADR-038): at the last
 * tier with every Yield point counted, one operation still fits a stack, so
 * the grinder needs no overflow of its own.
 */
class GrindingRecipeFilesTest {

    private static final Path RECIPES = Path.of("src/main/resources/data/krios_industry/recipe");

    private static List<Path> grindingFiles() throws IOException {
        assertTrue(Files.isDirectory(RECIPES), "no recipe folder at " + RECIPES.toAbsolutePath());
        try (Stream<Path> files = Files.list(RECIPES)) {
            List<Path> grinding = files.filter(path -> path.getFileName().toString().startsWith("grinding_"))
                    .sorted().toList();
            assertTrue(grinding.size() > 40, "only " + grinding.size() + " grinding recipes found");
            return grinding;
        }
    }

    /** Every result entry of a recipe file: one object or a list of them. */
    private static List<JsonObject> results(Path file) throws IOException {
        JsonObject recipe = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
        JsonElement result = recipe.get("result");
        List<JsonObject> entries = new ArrayList<>();
        if (result.isJsonArray()) {
            result.getAsJsonArray().forEach(entry -> entries.add(entry.getAsJsonObject()));
        } else {
            entries.add(result.getAsJsonObject());
        }
        return entries;
    }

    @Test
    void everyOperationFitsAStackAtTheLastTier() throws IOException {
        int cap = MachineTiers.yieldPointCap(KoiosBalance.MAX_TIER);
        for (Path file : grindingFiles()) {
            for (JsonObject entry : results(file)) {
                int count = entry.has("count") ? entry.get("count").getAsInt() : 1;
                int items = IndustryBalance.grindQuarters(count, true, cap) / 4;
                assertTrue(items <= 64, file.getFileName() + ": " + count + " gives " + items
                        + " items an operation at the last tier");
            }
        }
    }

    @Test
    void aModdedOreNamesItsDustThenItsRawMetal() throws IOException {
        for (Path file : grindingFiles()) {
            String name = file.getFileName().toString();
            List<JsonObject> entries = results(file);
            if (entries.size() > 1) {
                String metal = name.substring("grinding_".length(), name.length() - "_ore.json".length());
                assertTrue(entries.get(0).get("tag").getAsString().equals("c:dusts/" + metal)
                                && entries.get(1).get("tag").getAsString().equals("c:raw_materials/" + metal),
                        name + " does not name its dust, then its raw metal");
            }
        }
    }
}
