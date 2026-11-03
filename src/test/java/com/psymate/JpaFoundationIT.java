package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.testsupport.SharedPostgres;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * M8-01 (G Q32) : JPA est active a cote de JdbcClient, sur la meme DataSource et la meme
 * transaction. Aucune entite n'existe encore : {@code ddl-auto=validate} doit donc passer a vide.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class JpaFoundationIT {

  @Autowired private EntityManagerFactory entityManagerFactory;
  @Autowired private PlatformTransactionManager transactionManager;
  @Autowired private Environment environment;
  @Autowired private JdbcClient jdbc;
  @PersistenceContext private EntityManager entityManager;

  @Test
  void jpaIsActiveWithTheExpectedSettings() {
    assertThat(entityManagerFactory).isNotNull();
    assertThat(entityManagerFactory.isOpen()).isTrue();
    assertThat(transactionManager).isInstanceOf(JpaTransactionManager.class);
    // open-in-view est un reglage Spring MVC, pas Hibernate : il n'apparait que dans l'Environment.
    assertThat(environment.getProperty("spring.jpa.open-in-view", Boolean.class)).isFalse();

    // Reglages effectivement appliques a l'EntityManagerFactory, pas seulement declares.
    Map<String, Object> hibernate = entityManagerFactory.getProperties();
    assertThat(hibernate)
        .as("ddl-auto")
        .hasEntrySatisfying("hibernate.hbm2ddl.auto", v -> assertThat(v).hasToString("validate"));
    assertThat(hibernate)
        .as("time zone")
        .hasEntrySatisfying("hibernate.jdbc.time_zone", v -> assertThat(v).hasToString("UTC"));
    assertThat(hibernate)
        .as("second level cache")
        .hasEntrySatisfying(
            "hibernate.cache.use_second_level_cache", v -> assertThat(v).hasToString("false"));
    assertThat(hibernate)
        .as("query cache")
        .hasEntrySatisfying(
            "hibernate.cache.use_query_cache", v -> assertThat(v).hasToString("false"));
    // show-sql : Boot ne pousse hibernate.show_sql vers Hibernate que s'il vaut true.
    assertThat(hibernate).doesNotContainKey("hibernate.show_sql");
    assertThat(environment.getProperty("spring.jpa.show-sql", Boolean.class)).isFalse();
    assertThat(hibernate)
        .as("physical naming")
        .hasEntrySatisfying(
            "hibernate.physical_naming_strategy",
            v -> assertThat(v.toString()).contains("PhysicalNamingStrategySnakeCaseImpl"));
    assertThat(hibernate)
        .as("implicit naming")
        .hasEntrySatisfying(
            "hibernate.implicit_naming_strategy",
            v -> assertThat(v.toString()).contains("SpringImplicitNamingStrategy"));
  }

  @Test
  void jdbcWriteIsVisibleToEntityManagerBeforeCommitAndRolledBackTogether() {
    UUID id = UUID.randomUUID();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              insertWithJdbc(id);
              assertThat(countWithEntityManager(id)).isEqualTo(1);
              status.setRollbackOnly();
            });
    assertNoRow(id);
  }

  @Test
  void entityManagerWriteIsVisibleToJdbcBeforeCommitAndRolledBackTogether() {
    UUID id = UUID.randomUUID();
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              insertWithEntityManager(id);
              assertThat(countWithJdbc(id)).isEqualTo(1);
              status.setRollbackOnly();
            });
    assertNoRow(id);
  }

  @Test
  void bothWritesShareOneTransactionAndCommitTogether() {
    UUID viaJdbc = UUID.randomUUID();
    UUID viaEntityManager = UUID.randomUUID();
    try {
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                insertWithJdbc(viaJdbc);
                insertWithEntityManager(viaEntityManager);
              });
      new TransactionTemplate(transactionManager)
          .executeWithoutResult(
              status -> {
                assertThat(countWithJdbc(viaJdbc)).isEqualTo(1);
                assertThat(countWithEntityManager(viaJdbc)).isEqualTo(1);
                assertThat(countWithJdbc(viaEntityManager)).isEqualTo(1);
                assertThat(countWithEntityManager(viaEntityManager)).isEqualTo(1);
              });
    } finally {
      jdbc.sql("delete from tenant where id in (:a, :b)")
          .param("a", viaJdbc)
          .param("b", viaEntityManager)
          .update();
    }
  }

  private void insertWithJdbc(UUID id) {
    jdbc.sql("insert into tenant (id, name) values (:id, :name)")
        .param("id", id)
        .param("name", "jpa-foundation-jdbc")
        .update();
  }

  private void insertWithEntityManager(UUID id) {
    entityManager
        .createNativeQuery("insert into tenant (id, name) values (:id, :name)")
        .setParameter("id", id)
        .setParameter("name", "jpa-foundation-em")
        .executeUpdate();
  }

  private long countWithJdbc(UUID id) {
    return jdbc.sql("select count(*) from tenant where id = :id")
        .param("id", id)
        .query(Long.class)
        .single();
  }

  private long countWithEntityManager(UUID id) {
    Object count =
        entityManager
            .createNativeQuery("select count(*) from tenant where id = :id")
            .setParameter("id", id)
            .getSingleResult();
    return ((Number) count).longValue();
  }

  /** Depuis une nouvelle transaction, ni JDBC ni l'EntityManager ne voient la ligne annulee. */
  private void assertNoRow(UUID id) {
    new TransactionTemplate(transactionManager)
        .executeWithoutResult(
            status -> {
              assertThat(countWithJdbc(id)).isZero();
              assertThat(countWithEntityManager(id)).isZero();
            });
  }
}
