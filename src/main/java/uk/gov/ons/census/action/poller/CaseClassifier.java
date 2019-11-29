package uk.gov.ons.census.action.poller;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import uk.gov.ons.census.action.model.entity.ActionRule;

@Component
public class CaseClassifier {

  private final JdbcTemplate jdbcTemplate;

  public CaseClassifier(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public void enqueueCasesForActionRule(ActionRule actionRule) {
    UUID batchId = UUID.randomUUID();

    jdbcTemplate.update(
        "INSERT INTO actionv2.case_to_process (batch_id, batch_quantity, action_rule_id, "
            + "caze_case_ref) SELECT ?, COUNT(*) OVER (), ?, case_ref FROM "
            + "actionv2.cases "
            + buildWhereClause(actionRule.getActionPlan().getId(), actionRule.getClassifiers()),
        batchId,
        actionRule.getId());
  }

  private String buildWhereClause(UUID actionPlanId, Map<String, List<String>> classifiers) {
    StringBuilder whereClause = new StringBuilder();
    whereClause.append(String.format("WHERE action_plan_id='%s'", actionPlanId.toString()));
    whereClause.append(" AND receipt_received='f'");
    whereClause.append(" AND refusal_received='f'");
    whereClause.append(" AND address_invalid='f'");
    whereClause.append(" AND case_type != 'HI'");

    for (Map.Entry<String, List<String>> classifier : classifiers.entrySet()) {
      String inClauseValues =
          String.join(
              ",",
              classifier.getValue().stream()
                  .map(value -> ("'" + value + "'"))
                  .collect(Collectors.toList()));

      whereClause.append(String.format(" AND %s IN (%s)", classifier.getKey(), inClauseValues));
    }

    return whereClause.toString();
  }
}
