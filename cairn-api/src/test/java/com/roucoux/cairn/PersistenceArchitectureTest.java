package com.roucoux.cairn;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The rules that only make sense once the application has a persistence adapter. They live apart
 * from {@link ArchitectureTest} so that an application generated without the schema module drops
 * this file whole: ArchUnit fails a rule whose subject matches nothing, so these two would break
 * a build that has no persistence package at all.
 */
class PersistenceArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.roucoux.cairn");

    @Test
    void persistenceDoesNotDependOnTheClient() {
        noClasses()
                .that()
                .resideInAPackage("..adapter.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.client..")
                .check(CLASSES);
    }

    @Test
    void clientDoesNotDependOnPersistence() {
        noClasses()
                .that()
                .resideInAPackage("..adapter.client..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter.persistence..")
                .check(CLASSES);
    }
}
