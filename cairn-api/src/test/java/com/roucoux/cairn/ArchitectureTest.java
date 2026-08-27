package com.roucoux.cairn;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.roucoux.cairn.domain.exception.business.BusinessException;
import com.roucoux.cairn.domain.exception.technical.TechnicalException;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * The hexagonal rules, enforced at build time. This lives in the application module because it is
 * the only one that sees every class of the hexagon on its (test) classpath.
 */
class ArchitectureTest {

    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.roucoux.cairn");

    @Test
    void domainDependsOnlyOnItselfAndTheJdk() {
        classes()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .onlyDependOnClassesThat()
                .resideInAnyPackage("..domain..", "java..")
                .check(CLASSES);
    }

    @Test
    void inboundSideNeverTouchesTheAdapters() {
        noClasses()
                .that()
                .resideInAnyPackage("..application..", "..infrastructure..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..adapter..")
                .check(CLASSES);
    }

    @Test
    void adaptersUseTheDomainThroughItsPortsModelAndExceptionsOnly() {
        noClasses()
                .that()
                .resideInAPackage("..adapter..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..domain.service..")
                .check(CLASSES);
    }

    @Test
    void controllersDependOnUseCasesNotOnTheirImplementations() {
        noClasses()
                .that()
                .resideInAPackage("..application.controller..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..domain.service..")
                .check(CLASSES);
    }

    @Test
    void useCasesAreImplementedByDomainServicesOnly() {
        classes()
                .that()
                .implement(resideInAPackage("..domain.port.in.."))
                .should()
                .resideInAPackage("..domain.service..")
                .check(CLASSES);
    }

    @Test
    void outboundPortsAreImplementedByAdaptersOnly() {
        classes()
                .that()
                .implement(resideInAPackage("..domain.port.out.."))
                .should()
                .resideInAPackage("..adapter..")
                .check(CLASSES);
    }

    @Test
    void onlyControllersMappersAndCsvWritersDependOnGeneratedCode() {
        noClasses()
                .that()
                .resideOutsideOfPackages(
                        "..application.controller..", "..application.mapper..", "..application.csv..", "..generated..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..generated..")
                .check(CLASSES);
    }

    @Test
    void topLevelPackagesAreFreeOfCycles() {
        slices().matching("com.roucoux.cairn.(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void businessExceptionsExtendTheBusinessBaseType() {
        classes()
                .that()
                .resideInAPackage("..domain.exception.business..")
                .should()
                .beAssignableTo(BusinessException.class)
                .check(CLASSES);
    }

    @Test
    void technicalExceptionsExtendTheTechnicalBaseType() {
        classes()
                .that()
                .resideInAPackage("..domain.exception.technical..")
                .should()
                .beAssignableTo(TechnicalException.class)
                .check(CLASSES);
    }
}
