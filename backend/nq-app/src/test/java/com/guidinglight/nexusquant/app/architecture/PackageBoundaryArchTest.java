package com.guidinglight.nexusquant.app.architecture;

import com.guidinglight.nexusquant.app.architecture.fixture.strategy.InvalidStrategyTradingDependency;
import com.guidinglight.nexusquant.strategy.domain.port.StrategyExecutionGateway;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * PackageBoundaryArchTest 针对结构治理批次新增 package ownership 与 domain purity 护栏。
 */
@AnalyzeClasses(packages = "com.guidinglight.nexusquant")
class PackageBoundaryArchTest {

    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^package\\s+(.+);");
    private static final Pattern GATE_STAGE_PACKAGE_PATTERN =
            Pattern.compile("^(package|import)\\s+.*\\.gatew(?:\\.|;).*");
    private static final Path BACKEND_SOURCE_ROOT = Path.of("..").normalize();

    @ArchTest
    static final ArchRule domain_packages_should_not_depend_on_spring_framework = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework..");

    @ArchTest
    static final ArchRule strategy_should_not_depend_on_trading_application_or_infrastructure = noClasses()
            .that().resideInAPackage("..strategy..")
            .and().resideOutsideOfPackage("..app.architecture.fixture..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..trading.application..",
                    "..trading.infra..",
                    "..trading.infrastructure.."
            );

    @ArchTest
    static final ArchRule validation_should_not_depend_on_trading_owned_audit_port = noClasses()
            .that().resideInAnyPackage("..validation..", "..validationreview..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.trading.domain.port.AuditLogRepository"
            );

    @ArchTest
    static final ArchRule domain_should_not_depend_on_jdbc_infra_or_controller = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "..infra..",
                    "..controller..",
                    "..api.web.."
            );

    @ArchTest
    static final ArchRule only_order_command_adapter_should_depend_on_strategy_port = noClasses()
            .that().resideInAPackage("..trading.application..")
            .and().doNotHaveFullyQualifiedName(
                    "com.guidinglight.nexusquant.trading.application.port.OrderCommandStrategyExecutionGateway"
            )
            .should().dependOnClassesThat().resideInAPackage("..strategy.domain.port..");

    @ArchTest
    static final ArchRule order_command_adapter_should_implement_strategy_port = classes()
            .that().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.trading.application.port.OrderCommandStrategyExecutionGateway"
            )
            .should().implement(StrategyExecutionGateway.class);

    @Test
    void strategy_boundary_rule_should_reject_negative_fixture() {
        JavaClasses fixtureClasses = new ClassFileImporter().importClasses(InvalidStrategyTradingDependency.class);

        assertThrows(AssertionError.class, () -> noClasses()
                .that().resideInAPackage("..strategy..")
                .should().dependOnClassesThat().resideInAnyPackage("..trading.application..")
                .check(fixtureClasses));
    }

    @Test
    void main_source_packages_should_have_single_module_owner() throws IOException {
        Map<String, Set<String>> packageOwners = new LinkedHashMap<>();
        try (var paths = Files.walk(BACKEND_SOURCE_ROOT)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src" + File.separator + "main" + File.separator + "java"))
                    .forEach(path -> {
                        String packageName = readPackageName(path);
                        if (packageName != null) {
                            packageOwners.computeIfAbsent(packageName, ignored -> new TreeSet<>())
                                    .add(path.normalize().getName(1).toString());
                        }
                    });
        }
        List<String> offenders = packageOwners.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(entry -> entry.getKey() + " -> " + entry.getValue())
                .sorted()
                .toList();
        assertTrue(offenders.isEmpty(), () -> "main source split packages detected: " + offenders);
    }

    @Test
    void main_source_should_not_depend_on_gate_stage_packages() throws IOException {
        List<String> offenders;
        try (var paths = Files.walk(BACKEND_SOURCE_ROOT)) {
            offenders = paths
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src" + File.separator + "main" + File.separator + "java"))
                    .flatMap(path -> readLines(path).stream()
                            .map(String::trim)
                            .filter(line -> GATE_STAGE_PACKAGE_PATTERN.matcher(line).matches())
                            .map(line -> path.normalize() + ": " + line))
                    .sorted()
                    .toList();
        }
        assertTrue(offenders.isEmpty(), () -> "main source Gate stage package dependencies detected: " + offenders);
    }

    private String readPackageName(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .map(String::trim)
                    .map(PACKAGE_PATTERN::matcher)
                    .filter(java.util.regex.Matcher::matches)
                    .map(matcher -> matcher.group(1))
                    .findFirst()
                    .orElse(null);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to inspect package declaration: " + path, ex);
        }
    }

    private List<String> readLines(Path path) {
        try {
            return Files.readAllLines(path);
        } catch (IOException ex) {
            throw new IllegalStateException("failed to inspect source file: " + path, ex);
        }
    }
}
