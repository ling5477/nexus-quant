package com.guidinglight.nexusquant.app.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

@AnalyzeClasses(packages = "com.guidinglight.nexusquant")
class ModuleBoundaryArchTest {

    private static final Pattern SQL_KEYWORD_PATTERN = Pattern.compile("\\b(SELECT|INSERT|UPDATE|DELETE)\\b");
    private static final Path API_SOURCE_ROOT = Path.of("..", "nq-api", "src", "main", "java", "com", "guidinglight", "nexusquant", "api");

    @ArchTest
    static final ArchRule api_should_not_depend_on_jdbc = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("org.springframework.jdbc..", "..infra..jdbc..");

    @ArchTest
    static final ArchRule core_should_not_depend_on_infra_or_spring_jdbc = noClasses()
            .that().resideInAPackage("..core..")
            .should().dependOnClassesThat().resideInAnyPackage("..infra..", "org.springframework.jdbc..");

    @ArchTest
    static final ArchRule api_should_not_depend_on_scheduler_concrete = noClasses()
            .that().resideInAPackage("..api..")
            .should().dependOnClassesThat().resideInAnyPackage("..scheduler.service..");

    @ArchTest
    static final ArchRule core_should_not_contain_jdbc_implementations = noClasses()
            .that().resideInAPackage("..core..")
            .should().haveSimpleNameStartingWith("Jdbc");

    @ArchTest
    static final ArchRule jdbc_repositories_and_query_adapters_should_reside_in_infra = classes()
            .that().haveSimpleNameStartingWith("Jdbc")
            .and().resideOutsideOfPackage("..app.architecture..")
            .and().doNotHaveSimpleName("JdbcTradingQueryFacade")
            .should().resideInAPackage("..infra..");

    @ArchTest
    static final ArchRule fallback_components_should_only_be_wired_from_local_test_fallback_configuration = noClasses()
            .that().resideInAPackage("..app.config..")
            .and().doNotHaveSimpleName("LocalTestFallbackConfiguration")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.adapter.api.service.NoopAccountAdapter"
            )
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.adapter.api.service.NoopMarketDataAdapter"
            )
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.ledger.service.NoopLedgerService"
            )
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.config.service.InMemoryConfigSnapshotService"
            )
            .orShould().dependOnClassesThat().haveFullyQualifiedName(
                    "com.guidinglight.nexusquant.scheduler.service.PaperTradingAdapter"
            );

    @Test
    void api_source_should_not_contain_sql_literals() throws IOException {
        List<String> offenders = Files.walk(API_SOURCE_ROOT)
                .filter(path -> path.toString().endsWith(".java"))
                .filter(this::containsSqlKeyword)
                .map(API_SOURCE_ROOT::relativize)
                .map(Path::toString)
                .sorted()
                .toList();
        assertTrue(offenders.isEmpty(), () -> "nq-api source contains SQL keywords: " + offenders);
    }

    private boolean containsSqlKeyword(Path path) {
        try {
            return Files.readAllLines(path).stream()
                    .filter(line -> !line.stripLeading().startsWith("//"))
                    .anyMatch(line -> SQL_KEYWORD_PATTERN.matcher(line).find());
        } catch (IOException ex) {
            throw new IllegalStateException("failed to inspect api source: " + path, ex);
        }
    }
}
