package uk.gov.ons.census.action.poller;

import java.io.IOException;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.action.model.entity.CaseToProcess;
import uk.gov.ons.census.action.model.entity.FulfilmentsToSend;
import uk.gov.ons.census.action.model.repository.CaseToProcessRepository;
import uk.gov.ons.census.action.model.repository.FulfilmentToSendRepository;

@Component
public class ChunkProcessor {
  private final CaseToProcessRepository caseToProcessRepository;
  private final CaseProcessor caseProcessor;
  private final FulfilmentToSendRepository fulfilmentToSendRepository;
  private final FulfilmentProcessor fulfilmentProcessor;

  @Value("${scheduler.chunksize}")
  private int chunkSize;

  public ChunkProcessor(
      CaseToProcessRepository caseToProcessRepository,
      CaseProcessor caseProcessor,
      FulfilmentToSendRepository fulfilmentToSendRepository,
      FulfilmentProcessor fulfilmentProcessor) {
    this.caseToProcessRepository = caseToProcessRepository;
    this.caseProcessor = caseProcessor;
    this.fulfilmentToSendRepository = fulfilmentToSendRepository;
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
    try (Stream<FulfilmentsToSend> fulfilments =
        fulfilmentToSendRepository.findChunkToProcess(chunkSize)) {
      fulfilments.forEach(
          fulfilmentsToSend -> {
            try {
              fulfilmentProcessor.process(fulfilmentsToSend);
            } catch (IOException e) {
              e.printStackTrace();
            }
            fulfilmentToSendRepository.delete(
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

    return fulfilmentToSendRepository.count() > 0;
  }
}
