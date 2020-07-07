package uk.gov.ons.census.action.model.entity;

import java.time.OffsetDateTime;
import java.util.UUID;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.ManyToOne;
import lombok.Data;

@Entity
@Data
public class ActionRule {

  @Id private UUID id;

  @ManyToOne private ActionPlan actionPlan;

  @Column
  @Enumerated(EnumType.STRING)
  private ActionType actionType;

  @Column private OffsetDateTime triggerDateTime;

  @Column private Boolean hasTriggered;

  @Lob
  @Column(nullable = false)
  private String classifiersClause;
}
