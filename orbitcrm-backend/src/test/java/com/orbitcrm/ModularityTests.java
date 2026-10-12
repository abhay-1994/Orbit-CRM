package com.orbitcrm;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Verifies the Spring Modulith module structure of the application.
 *
 * <p>Purpose: keep the modular monolith healthy and splittable later. {@code modulesRespectBoundaries} fails the build
 * when modules form a dependency cycle or when a module uses another module's internal packages (anything not exposed
 * through a {@code @NamedInterface} package such as {@code service}, {@code dto}, {@code model} or {@code event}).
 * {@code writeModuleDocumentation} generates PlantUML diagrams of all modules and their dependencies into
 * {@code target/spring-modulith-docs}. This is a plain unit test; it needs no database.
 *
 * @author Abhay
 */
class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(OrbitCrmApplication.class);

    @Test
    void modulesRespectBoundaries() {
        modules.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(modules).writeModulesAsPlantUml().writeIndividualModulesAsPlantUml();
    }
}
