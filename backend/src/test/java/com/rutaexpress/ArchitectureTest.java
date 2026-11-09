package com.rutaexpress;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.fields;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/** Reglas de arquitectura: fronteras entre módulos (Spring Modulith) y convenciones (ArchUnit). */
class ArchitectureTest {

    private static final ApplicationModules MODULES = ApplicationModules.of(RutaExpressApplication.class);
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.rutaexpress");

    @Test
    void modulesRespectTheirBoundaries() {
        // Falla si un módulo usa tipos internos de otro o si hay dependencias circulares
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        // Genera diagramas C4/PlantUML de los módulos en target/spring-modulith-docs (para la memoria)
        new Documenter(MODULES).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }

    @Test
    void sharedDoesNotDependOnBusinessModules() {
        noClasses().that().resideInAPackage("com.rutaexpress.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.rutaexpress.auth..", "com.rutaexpress.catalog..", "com.rutaexpress.parcel..",
                        "com.rutaexpress.scanning..", "com.rutaexpress.geo..", "com.rutaexpress.routing..",
                        "com.rutaexpress.delivery..", "com.rutaexpress.tracking..", "com.rutaexpress.mcp..")
                .check(CLASSES);
    }

    @Test
    void controllersNeverExposeEntities() {
        methods().that().areDeclaredInClassesThat().areAnnotatedWith(RestController.class)
                .should().notHaveRawReturnType(com.tngtech.archunit.base.DescribedPredicate.describe(
                        "una entidad JPA", (com.tngtech.archunit.core.domain.JavaClass c) -> c.isAnnotatedWith(Entity.class)))
                .check(CLASSES);
    }

    @Test
    void noFieldInjection() {
        fields().should().notBeAnnotatedWith(Autowired.class)
                .because("se usa inyección por constructor")
                .check(CLASSES);
    }

    @Test
    void optimizersDoNotDependOnPersistence() {
        noClasses().that().resideInAPackage("..routing.optimizer..")
                .should().dependOnClassesThat().resideInAnyPackage("jakarta.persistence..", "org.springframework.data..")
                .because("los algoritmos de ruta son puros: se prueban sin base de datos")
                .check(CLASSES);
    }
}
