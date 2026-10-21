package com.arquitechthor.kopi.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/// Enforces the hexagonal dependency rule (see `docs/adr/001`).
///
/// Maven module dependencies already stop most violations at compile time.
/// These rules also catch what Maven cannot see, such as the domain using
/// `java.sql` (which is part of the JDK) or a package placed in the wrong
/// module.
///
/// `allowEmptyShould(true)` is there only while the modules are still empty.
/// Remove it from each rule once its ring has real classes.
@AnalyzeClasses(packages = "com.arquitechthor.kopi", importOptions = ImportOption.DoNotIncludeTests.class)
class HexagonalArchitectureTest {

    private static final String ROOT = "com.arquitechthor.kopi";
    private static final String DOMAIN = ROOT + ".domain..";
    private static final String APPLICATION = ROOT + ".application..";
    private static final String ADAPTERS = ROOT + ".adapter..";
    private static final String BOOTSTRAP = ROOT + ".bootstrap..";
    private static final String LABS = ROOT + ".labs..";

    @ArchTest
    static final ArchRule domainDependsOnNothingOutsideItself = noClasses()
            .that().resideInAPackage(DOMAIN)
            .should().dependOnClassesThat().resideInAnyPackage(
                    APPLICATION, ADAPTERS, BOOTSTRAP,
                    "org.springframework..", "jakarta..", "java.sql..", "javax.sql..")
            .because("the domain is the innermost ring and must stay plain Java");

    @ArchTest
    static final ArchRule applicationDependsOnlyOnTheDomain = noClasses()
            .that().resideInAPackage(APPLICATION)
            .should().dependOnClassesThat().resideInAnyPackage(
                    ADAPTERS, BOOTSTRAP,
                    "org.springframework..", "jakarta..", "java.sql..", "javax.sql..")
            .because("use cases must not depend on adapters or on Spring (ADR-002)")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule adaptersDoNotKnowEachOther = slices()
            .matching(ROOT + ".adapter.(*)..")
            .should().notDependOnEachOther()
            .because("each adapter talks only to the application ring")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule nothingDependsOnBootstrap = noClasses()
            .that().resideOutsideOfPackage(BOOTSTRAP)
            .should().dependOnClassesThat().resideInAPackage(BOOTSTRAP)
            .because("bootstrap is the outermost ring: it wires everything and nobody uses it")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule nothingDependsOnLabs = noClasses()
            .that().resideOutsideOfPackage(LABS)
            .should().dependOnClassesThat().resideInAPackage(LABS)
            .because("kopi-labs uses preview features and must never leak into the application (ADR-012)");
}
