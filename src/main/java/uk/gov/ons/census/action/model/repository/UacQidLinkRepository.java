package uk.gov.ons.census.action.model.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import uk.gov.ons.census.action.model.entity.UacQidLink;

public interface UacQidLinkRepository extends JpaRepository<UacQidLink, UUID> {
  List<UacQidLink> findByCaseId(String caseId);

  Optional<UacQidLink> findByQid(String qid);
}
