package uk.gov.ons.census.action.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.action.model.entity.Case;

public interface CaseRepository extends JpaRepository<Case, Long> {}
