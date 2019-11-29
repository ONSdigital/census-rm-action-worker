package uk.gov.ons.census.action.poller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import uk.gov.ons.census.action.model.entity.ActionPlan;
import uk.gov.ons.census.action.model.entity.ActionRule;

public class CaseClassifierTest {
  @Test
  public void testEnqueueCasesForActionRule() {
    // Given
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    CaseClassifier underTest = new CaseClassifier(jdbcTemplate);
    Map<String, List<String>> classifiers = new HashMap<>();
    classifiers.put("treatment_code", List.of("abc", "xyz"));
    ActionPlan actionPlan = new ActionPlan();
    actionPlan.setId(UUID.randomUUID());
    ActionRule actionRule = new ActionRule();
    actionRule.setId(UUID.randomUUID());
    actionRule.setActionPlan(actionPlan);
    actionRule.setClassifiers(classifiers);

    // When
    underTest.enqueueCasesForActionRule(actionRule);

    // Then
    StringBuilder expectedSql = new StringBuilder();
    expectedSql.append("INSERT INTO actionv2.case_to_process (batch_id, batch_quantity, ");
    expectedSql.append("action_rule_id, caze_case_ref) SELECT ?, COUNT(*) OVER (), ?, case_ref ");
    expectedSql.append("FROM actionv2.cases WHERE action_plan_id='");
    expectedSql.append(actionPlan.getId().toString());
    expectedSql.append("' AND receipt_received='f' AND refusal_received='f' AND ");
    expectedSql.append("address_invalid='f' AND case_type != 'HI' AND treatment_code IN ");
    expectedSql.append("('abc','xyz')");
    verify(jdbcTemplate)
        .update(eq(expectedSql.toString()), any(UUID.class), eq(actionRule.getId()));
  }
}
