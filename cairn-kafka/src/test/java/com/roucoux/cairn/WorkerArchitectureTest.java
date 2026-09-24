package com.roucoux.cairn;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The hexagonal rules as they apply to the worker. The application module has its own
 * {@code ArchitectureTest}: neither module sees the other's classes, so each enforces its side.
 */
class WorkerArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.roucoux.cairn");

    @Test
    void theWorkerNeverTouchesTheAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..kafka..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .check(CLASSES);
    }
}
