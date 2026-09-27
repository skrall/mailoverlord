package org.mailoverlord.server.config;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

/**
 * Runtime hints needed to run Hibernate in a GraalVM native image.
 *
 * <p>Hibernate declares its logging interfaces with JBoss Logging, whose annotation
 * processor generates a matching implementation class named {@code <Interface>_$logger} for
 * each one. At runtime {@code Logger.getMessageLogger} finds that implementation by name and
 * instantiates it reflectively, so every one of them needs to be present in the image and
 * reachable through its constructor.
 *
 * <p>A native image without these hints fails at startup, or on first use of a code path, with:
 * <pre>
 * java.lang.IllegalArgumentException: Invalid logger interface
 *     org.hibernate.jpa.internal.JpaLogger (implementation not found)
 * </pre>
 *
 * <p>Registering just the one interface from the error message is not enough. The loggers are
 * initialised lazily, so the next untouched one fails instead, and Hibernate adds new ones
 * between releases. This scans hibernate-core for the generated implementations instead of
 * hardcoding a list, so a Hibernate upgrade does not silently reintroduce the failure.
 *
 * <p>{@link #STRATEGIES} covers the second reflective path: Hibernate's {@code StrategySelector}
 * looks implementations up by name and calls their no-argument constructor. See that field for
 * why those cannot be discovered the same way.
 */
public class HibernateRuntimeHints implements RuntimeHintsRegistrar {

    /**
     * Suffix of the implementation the JBoss Logging processor generates for a
     * message logger interface.
     */
    private static final String LOGGER_SUFFIX = "_$logger";

    private static final String LOGGER_PATTERN = "classpath*:org/hibernate/**/*" + LOGGER_SUFFIX + ".class";

    /**
     * Message bundles the generated loggers read their messages from.
     */
    private static final String MESSAGES_PATTERN = "org/hibernate/**/*i18n.properties";

    /**
     * DTDs and XSDs the JAXB binder resolves while reading mappings, which a native image
     * otherwise drops because nothing references them from code:
     * <pre>
     * Caused by: org.hibernate.boot.jaxb.internal.stax.XmlInfrastructureException:
     *     Unable to locate schema [org/hibernate/hibernate-mapping-3.0.dtd] via classpath
     * </pre>
     *
     * <p>Matched by pattern rather than by name because Hibernate carries a fixed set of them
     * across its mapping, configuration and JPA namespaces, and gains one with each revision
     * of the specs it supports.
     */
    private static final List<String> SCHEMA_PATTERNS =
            List.of("org/hibernate/**/*.xsd", "org/hibernate/**/*.dtd");

    /**
     * Implementations Hibernate resolves by name and instantiates through their no-argument
     * constructor, which a native image otherwise drops:
     * <pre>
     * Caused by: java.lang.NoSuchMethodException:
     *     org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard.&lt;init&gt;()
     * </pre>
     *
     * <p>Taken from the reachability metadata Hibernate publishes for itself, which the
     * GraalVM metadata repository applies per version. There is no entry for 7.4.x yet, so
     * nothing is applied automatically and the image fails to start on the very first one.
     * Re-check the published metadata on each Hibernate upgrade; once it covers the version
     * in use this list can go, and HibernateRuntimeHintsTest will flag any name that has been
     * renamed or removed.
     *
     * <p>Not derived from a class-name convention on purpose. Names like
     * {@code ColumnOrderingStrategyStandard} and {@code PhysicalNamingStrategyStandardImpl}
     * do not follow one, so a suffix rule silently misses the classes that break startup.
     */
    private static final List<String> STRATEGIES = List.of(
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl$JaxbPropertiesImpl",
            "org.hibernate.boot.jaxb.configuration.spi.JaxbPersistenceImpl$JaxbPersistenceUnitImpl$JaxbPropertiesImpl$JaxbPropertyImpl",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy",
            "org.hibernate.boot.model.naming.ImplicitNamingStrategyJpaCompliantImpl",
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "org.hibernate.boot.model.relational.ColumnOrderingStrategyStandard",
            "org.hibernate.dialect.type.OracleArrayJdbcTypeConstructor",
            "org.hibernate.dialect.type.OracleNestedTableJdbcTypeConstructor",
            "org.hibernate.dialect.type.OracleStructJdbcType",
            "org.hibernate.dialect.type.PostgreSQLInetJdbcType",
            "org.hibernate.dialect.type.PostgreSQLIntervalSecondJdbcType",
            "org.hibernate.dialect.type.PostgreSQLJsonArrayPGObjectJsonbJdbcTypeConstructor",
            "org.hibernate.dialect.type.PostgreSQLJsonPGObjectJsonbType",
            "org.hibernate.dialect.type.PostgreSQLStructPGObjectJdbcType",
            "org.hibernate.generator.internal.GeneratedAlwaysGeneration",
            "org.hibernate.id.Assigned",
            "org.hibernate.id.ForeignGenerator",
            "org.hibernate.id.GUIDGenerator",
            "org.hibernate.id.IdentityGenerator",
            "org.hibernate.id.IncrementGenerator",
            "org.hibernate.id.SelectGenerator",
            "org.hibernate.id.UUIDGenerator",
            "org.hibernate.id.UUIDHexGenerator",
            "org.hibernate.id.enhanced.LegacyNamingStrategy",
            "org.hibernate.id.enhanced.SequenceStyleGenerator",
            "org.hibernate.id.enhanced.SingleNamingStrategy",
            "org.hibernate.id.enhanced.StandardNamingStrategy",
            "org.hibernate.id.enhanced.TableGenerator",
            "org.hibernate.resource.transaction.backend.jdbc.internal.JdbcResourceLocalTransactionCoordinatorBuilderImpl");

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        List<String> loggers = ClassPathScanner.findClassNames(classLoader, LOGGER_PATTERN);
        for (String logger : loggers) {
            hints.reflection().registerTypeIfPresent(classLoader, logger, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        for (String strategy : STRATEGIES) {
            hints.reflection().registerTypeIfPresent(classLoader, strategy, MemberCategory.INVOKE_PUBLIC_CONSTRUCTORS);
        }
        hints.resources().registerPattern(MESSAGES_PATTERN);
        for (String schema : SCHEMA_PATTERNS) {
            hints.resources().registerPattern(schema);
        }
    }

    /**
     * The resource patterns that need registering, for verification by the tests.
     */
    static List<String> resourcePatterns() {
        return Stream.concat(Stream.of(MESSAGES_PATTERN), SCHEMA_PATTERNS.stream()).toList();
    }

    /**
     * The strategy implementations that need registering, for verification by the tests.
     */
    static List<String> strategies() {
        return STRATEGIES;
    }
}
