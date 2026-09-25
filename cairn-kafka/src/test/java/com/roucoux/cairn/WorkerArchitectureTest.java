package com.roucoux.cairn;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

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
