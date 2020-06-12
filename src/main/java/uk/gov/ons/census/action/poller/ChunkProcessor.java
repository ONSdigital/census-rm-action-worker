package uk.gov.ons.census.action.poller;

import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.action.model.entity.CaseToProcess;
import uk.gov.ons.census.action.model.entity.FulfilmentToProcess;
import uk.gov.ons.census.action.model.repository.CaseToProcessRepository;
import uk.gov.ons.census.action.model.repository.FulfilmentToProcessRepository;

@Component
public class ChunkProcessor {
  private final CaseToProcessRepository caseToProcessRepository;
  private final CaseProcessor caseProcessor;
  private final FulfilmentToProcessRepository fulfilmentToProcessRepository;
  private final FulfilmentProcessor fulfilmentProcessor;

  @Value("${scheduler.chunksize}")
  private int chunkSize;

  public ChunkProcessor(
      CaseToProcessRepository caseToProcessRepository,
      CaseProcessor caseProcessor,
      FulfilmentToProcessRepository fulfilmentToProcessRepository,
      FulfilmentProcessor fulfilmentProcessor) {
    this.caseToProcessRepository = caseToProcessRepository;
    this.caseProcessor = caseProcessor;
    this.fulfilmentToProcessRepository = fulfilmentToProcessRepository;
    this.fulfilmentProcessor = fulfilmentProcessor;
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW) // Start a new transaction for every chunk
  public void processChunk() {
    try (Stream<CaseToProcess> cases = caseToProcessRepository.findChunkToProcess(chunkSize)) {
      cases.forEach(
          caseToProcess -> {
            caseProcessor.process(caseToProcess);
            caseToProcessRepository.delete(caseToProcess); // Delete the case from the 'queue'
          });
    }
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW) // Start a new transaction for every chunk
  public void processFulfilmentChunk() {
    try (Stream<FulfilmentToProcess> fulfilments =
        fulfilmentToProcessRepository.findChunkToProcess(chunkSize)) {
      fulfilments.forEach(
          fulfilmentsToSend -> {
            fulfilmentProcessor.process(fulfilmentsToSend);
            fulfilmentToProcessRepository.delete(
                fulfilmentsToSend); // Delete the fulfilment from the 'queue'
          });
    }
  }

  @Transactional
  public boolean isThereWorkToDo() {
    return caseToProcessRepository.count() > 0;
  }

  @Transactional
  public boolean isThereFulfilmentWorkToDo() {
    return fulfilmentToProcessRepository.count() > 0;
  }
}
