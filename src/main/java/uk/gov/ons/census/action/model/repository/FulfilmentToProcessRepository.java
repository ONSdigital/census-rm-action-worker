package uk.gov.ons.census.action.model.repository;

import java.util.UUID;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;

public interface FulfilmentToProcessRepository extends JpaRepository<FulfilmentToProcess, UUID> {

  long countByBatchIdNotNull();

  @Query(
      value =
          "SELECT * FROM actionv2.fulfilment_to_process where batch_id is not null and quantity is not null LIMIT :limit FOR UPDATE SKIP LOCKED",
      nativeQuery = true)
  Stream<FulfilmentToProcess> findChunkToProcess(@Param("limit") int limit);
}
