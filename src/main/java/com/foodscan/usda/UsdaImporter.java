package com.foodscan.usda;

import com.foodscan.food.Food;
import com.foodscan.food.FoodPortion;
import com.foodscan.food.FoodRepository;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ObjectReader;

/**
 * Imports FoodData Central JSON downloads (the {@code *_food_json_*.zip} files
 * from https://fdc.nal.usda.gov/download-datasets) into the {@code foods} and
 * {@code food_portions} tables. Re-running is safe: foods are matched by
 * fdcId and updated, and their portions replaced.
 */
@Service
public class UsdaImporter {

    private static final Logger log = LoggerFactory.getLogger(UsdaImporter.class);
    private static final String SOURCE = "usda";

    // One transaction per chunk keeps the persistence context small; should be
    // a multiple of hibernate.jdbc.batch_size.
    private static final int CHUNK_SIZE = 500;

    private final ObjectMapper mapper;
    private final ObjectReader elementReader;
    private final FoodRepository foods;
    private final TransactionTemplate tx;

    public UsdaImporter(ObjectMapper mapper, FoodRepository foods, TransactionTemplate tx) {
        this.mapper = mapper;
        // Reads one array element at a time; the rest of the array is still
        // pending, so trailing tokens are expected.
        this.elementReader = mapper.readerFor(JsonNode.class)
                .without(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        this.foods = foods;
        this.tx = tx;
    }

    /** Imports every FoodData Central JSON zip in {@code dir}. */
    public void importDirectory(Path dir) throws IOException {
        List<Path> zips;
        try (Stream<Path> files = Files.list(dir)) {
            zips = files.filter(p -> p.getFileName().toString().matches("(?i).*_json_.*\\.zip"))
                    .sorted()
                    .toList();
        }
        if (zips.isEmpty()) {
            throw new IOException("No FoodData Central *_json_*.zip files in " + dir.toAbsolutePath());
        }
        for (Path zip : zips) {
            importZip(zip);
        }
    }

    public void importZip(Path zip) throws IOException {
        long start = System.currentTimeMillis();
        List<UsdaFood> parsed = new ArrayList<>();
        int skipped = 0;
        try (ZipFile zipFile = new ZipFile(zip.toFile())) {
            ZipEntry entry = zipFile.stream()
                    .filter(e -> e.getName().endsWith(".json"))
                    .findFirst()
                    .orElseThrow(() -> new IOException("No .json entry in " + zip));
            try (InputStream in = zipFile.getInputStream(entry)) {
                for (JsonNode node : readFoods(in)) {
                    UsdaFood food = UsdaFoodMapper.map(node);
                    if (food.hasMacros() && !food.name().isEmpty()) {
                        parsed.add(food);
                    } else {
                        skipped++;
                    }
                }
            }
        }

        for (int i = 0; i < parsed.size(); i += CHUNK_SIZE) {
            List<UsdaFood> chunk = parsed.subList(i, Math.min(i + CHUNK_SIZE, parsed.size()));
            tx.executeWithoutResult(_ -> saveChunk(chunk));
        }
        log.info("Imported {} foods from {} ({} skipped without nutrients) in {} ms",
                parsed.size(), zip.getFileName(), skipped, System.currentTimeMillis() - start);
    }

    /**
     * The files are {@code {"FoundationFoods": [ {...}, null, {...} ]}}. We
     * stream the array rather than binding the whole document, and skip the
     * null entries that occur in the Foundation download.
     */
    private List<JsonNode> readFoods(InputStream in) {
        List<JsonNode> nodes = new ArrayList<>();
        try (JsonParser parser = mapper.createParser(in)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new IllegalStateException("Expected a JSON object at the top level");
            }
            while (parser.nextToken() == JsonToken.PROPERTY_NAME) {
                if (parser.nextToken() != JsonToken.START_ARRAY) {
                    parser.skipChildren();
                    continue;
                }
                while (parser.nextToken() != JsonToken.END_ARRAY) {
                    if (parser.currentToken() == JsonToken.START_OBJECT) {
                        nodes.add(elementReader.readValue(parser));
                    } else {
                        parser.skipChildren();
                    }
                }
            }
        }
        return nodes;
    }

    private void saveChunk(List<UsdaFood> chunk) {
        Map<String, Food> existing = foods
                .findBySourceAndSourceRefIn(SOURCE, chunk.stream().map(UsdaFood::fdcId).toList())
                .stream()
                .collect(Collectors.toMap(Food::getSourceRef, Function.identity()));

        List<Food> toSave = new ArrayList<>(chunk.size());
        for (UsdaFood usda : chunk) {
            Food food = existing.getOrDefault(usda.fdcId(), new Food(SOURCE, usda.fdcId()));
            food.setDataType(usda.dataType());
            food.setName(usda.name());
            food.setCategory(usda.category());
            food.setKcal100g(decimal(usda.kcal()));
            food.setProtein100g(decimal(usda.proteinG()));
            food.setCarbs100g(decimal(usda.carbsG()));
            food.setFat100g(decimal(usda.fatG()));
            food.setFiber100g(decimal(usda.fiberG()));
            food.setSugar100g(decimal(usda.sugarG()));
            food.setSodiumMg100g(decimal(usda.sodiumMg()));
            food.setEnergyBasis(usda.energyBasis());
            food.replacePortions(usda.portions().stream()
                    .map(p -> new FoodPortion.Measure(p.label(), BigDecimal.valueOf(p.grams())))
                    .toList());
            toSave.add(food);
        }
        foods.saveAll(toSave);
    }

    private static BigDecimal decimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value);
    }
}
