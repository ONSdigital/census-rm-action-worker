package uk.gov.ons.census.action.model.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import uk.gov.ons.census.action.model.entity.Case;

public interface CaseRepository
    extends JpaRepository<Case, Integer>, JpaSpecificationExecutor<Case> {}
