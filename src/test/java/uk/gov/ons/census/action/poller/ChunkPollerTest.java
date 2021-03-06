package uk.gov.ons.census.action.poller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.Test;

public class ChunkPollerTest {

  @Test
  public void testProcessQueuedCases() {
    // Given
    ChunkProcessor chunkProcessor = mock(ChunkProcessor.class);
    ChunkPoller underTest = new ChunkPoller(chunkProcessor);

    // When
    underTest.processQueuedCases();

    // Then
    verify(chunkProcessor).processChunk();
    verify(chunkProcessor).isThereWorkToDo();
  }

  @Test
  public void testProcessQueuedFulfilments() {
    // Given
    ChunkProcessor chunkProcessor = mock(ChunkProcessor.class);
    ChunkPoller underTest = new ChunkPoller(chunkProcessor);

    // When
    underTest.processFulfilments();

    // Then
    verify(chunkProcessor).processFulfilmentChunk();
    verify(chunkProcessor).isThereFulfilmentWorkToDo();
  }

  @Test
  public void testProcessQueuedCasesMultipleChunks() {
    // Given
    ChunkProcessor chunkProcessor = mock(ChunkProcessor.class);
    ChunkPoller underTest = new ChunkPoller(chunkProcessor);
    when(chunkProcessor.isThereWorkToDo()).thenReturn(true).thenReturn(true).thenReturn(false);

    // When
    underTest.processQueuedCases();

    // Then
    verify(chunkProcessor, times(3)).processChunk();
    verify(chunkProcessor, times(3)).isThereWorkToDo();
  }

  @Test
  public void testProcessQueuedFulfilmentsMultipleChunks() {
    // Given
    ChunkProcessor chunkProcessor = mock(ChunkProcessor.class);
    ChunkPoller underTest = new ChunkPoller(chunkProcessor);
    when(chunkProcessor.isThereFulfilmentWorkToDo())
        .thenReturn(true)
        .thenReturn(true)
        .thenReturn(false);

    // When
    underTest.processFulfilments();

    // Then
    verify(chunkProcessor, times(3)).processFulfilmentChunk();
    verify(chunkProcessor, times(3)).isThereFulfilmentWorkToDo();
  }
}
