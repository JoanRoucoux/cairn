package com.roucoux.cairn;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The hexagonal rules as they apply to the batch. The application module has its own
 * {@code ArchitectureTest}: neither module sees the other's classes, so each enforces its side.
 */
class BatchArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.roucoux.cairn");

    @Test
    void theJobNeverTouchesTheAdapters() {
        noClasses()
                .that()
                .resideInAPackage("..batch..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .check(CLASSES);
    }

    @Test
    void jobStepsDependOnUseCasesNotOnTheirImplementations() {
        noClasses()
                .that()
                .resideInAPackage("..batch.job..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..domain.service..")
                .check(CLASSES);
    }
}
