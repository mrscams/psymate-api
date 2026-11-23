package com.psymate;

import static org.assertj.core.api.Assertions.assertThat;

import com.psymate.testsupport.SharedPostgres;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * V17 (M16-03, G Q98) : {@code label_ar} nul par défaut sur les quatre catalogues, {@code
 * inverse_label_ar} sur les types de lien ; les lignes globales du seed reçoivent le libellé du
 * glossaire du web, les émotions personnalisées aucun.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SharedPostgres.class)
class CatalogLabelArMigrationIT {

  @Autowired private JdbcTemplate jdbc;

  private int count(String sql) {
    return jdbc.queryForObject(sql, Integer.class);
  }

  private String one(String sql, Object... args) {
    return jdbc.queryForObject(sql, String.class, args);
  }

  @Test
  void v17IsApplied() {
    assertThat(
            jdbc.queryForList(
                "select version from flyway_schema_history where success order by installed_rank",
                String.class))
        .contains("17");
  }

  @Test
  void arabicColumnsAreNullableText() {
    List<Map<String, Object>> columns =
        jdbc.queryForList(
            "select table_name, column_name, data_type, is_nullable, column_default"
                + " from information_schema.columns where table_schema = 'public'"
                + " and column_name in ('label_ar', 'inverse_label_ar') order by table_name,"
                + " column_name");
    assertThat(columns)
        .extracting(c -> c.get("table_name") + "." + c.get("column_name"))
        .containsExactlyInAnyOrder(
            "directory_vocabulary.label_ar",
            "emotion.label_ar",
            "entry_type.label_ar",
            "link_type.inverse_label_ar",
            "link_type.label_ar");
    for (Map<String, Object> c : columns) {
      assertThat(c.get("data_type")).as(c.toString()).isEqualTo("text");
      assertThat(c.get("is_nullable")).as(c.toString()).isEqualTo("YES");
      assertThat(c.get("column_default")).as(c.toString()).isNull();
    }
  }

  @Test
  void everyGlobalRowOfTheSeedHasItsArabicLabel() {
    assertThat(count("select count(*) from entry_type where label_ar is not null")).isEqualTo(6);
    assertThat(
            count("select count(*) from emotion where scope = 'global' and label_ar is not null"))
        .isEqualTo(29);
    assertThat(count("select count(*) from link_type where label_ar is not null")).isEqualTo(8);
    assertThat(count("select count(*) from link_type where inverse_label_ar is not null"))
        .isEqualTo(7);
    assertThat(
            count(
                "select count(*) from directory_vocabulary where label_ar is not null"
                    + " and kind = 'specialty' and code in ('anxiety', 'depression', 'cbt',"
                    + " 'emdr', 'couple_therapy', 'burnout', 'adhd', 'addictions', 'trauma_ptsd',"
                    + " 'phobias', 'ocd', 'eating_disorders', 'bipolar', 'grief', 'self_esteem',"
                    + " 'stress_management', 'sleep_disorders', 'autism_spectrum', 'parenting',"
                    + " 'career_coaching', 'mindfulness', 'psychodynamic')"))
        .isEqualTo(22);
    assertThat(
            count(
                "select count(*) from directory_vocabulary where label_ar is not null"
                    + " and kind = 'audience' and code in ('adults', 'young_adults',"
                    + " 'adolescents', 'children', 'couples_families')"))
        .isEqualTo(5);
  }

  @Test
  void labelsAreTheGlossaryTerms() {
    assertThat(one("select label_ar from entry_type where code = 'feeling'")).isEqualTo("شعور");
    assertThat(one("select label_ar from emotion where code = 'anxiety'")).isEqualTo("قلق");
    assertThat(one("select label_ar from emotion where code = 'guilt'")).isEqualTo("شعور بالذنب");
    assertThat(one("select label_ar from link_type where code = 'triggers'")).isEqualTo("يُثير");
    assertThat(one("select inverse_label_ar from link_type where code = 'triggers'"))
        .isEqualTo("يُثيره");
    assertThat(one("select inverse_label_ar from link_type where code = 'treats'"))
        .isEqualTo("يُعالَج بـ");
    assertThat(
            one(
                "select label_ar from directory_vocabulary where kind = 'specialty'"
                    + " and code = 'anxiety'"))
        .isEqualTo("القلق");
    assertThat(
            one(
                "select label_ar from directory_vocabulary where kind = 'audience'"
                    + " and code = 'couples_families'"))
        .isEqualTo("الأزواج والعائلات");
  }

  /** co_occurs n'est pas orienté : ni inverse français, ni inverse arabe. */
  @Test
  void theUndirectedTypeHasNoArabicInverse() {
    Map<String, Object> row =
        jdbc.queryForMap(
            "select directed, inverse_label_fr, inverse_label_ar from link_type"
                + " where code = 'co_occurs'");
    assertThat(row.get("directed")).isEqualTo(false);
    assertThat(row.get("inverse_label_fr")).isNull();
    assertThat(row.get("inverse_label_ar")).isNull();
  }

  /** Aucune émotion personnalisée n'a de libellé arabe : elle garde celui du praticien. */
  @Test
  void customEmotionsHaveNoArabicLabel() {
    assertThat(
            count("select count(*) from emotion where scope = 'custom' and label_ar is not null"))
        .isZero();
  }
}
