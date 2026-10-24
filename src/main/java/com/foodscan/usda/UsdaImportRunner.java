package com.foodscan.usda;

import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Runs the USDA import at startup when {@code foodscan.usda.import-dir} (env
 * {@code USDA_IMPORT_DIR}) is set. Leave it unset for normal runs.
 */
@Component
class UsdaImportRunner implements ApplicationRunner {

    private final UsdaImporter importer;
    private final String importDir;

    UsdaImportRunner(UsdaImporter importer, @Value("${foodscan.usda.import-dir:}") String importDir) {
        this.importer = importer;
        this.importDir = importDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (StringUtils.hasText(importDir)) {
            importer.importDirectory(Path.of(importDir));
        }
    }
}
