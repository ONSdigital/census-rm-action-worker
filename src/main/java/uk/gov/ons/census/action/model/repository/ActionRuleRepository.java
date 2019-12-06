package uk.gov.ons.census.action.model.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.action.model.entity.ActionRule;

public interface ActionRuleRepository extends JpaRepository<ActionRule, UUID> {}
