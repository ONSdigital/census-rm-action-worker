package uk.gov.ons.census.action.cache;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import lombok.Data;
import org.jeasy.random.EasyRandom;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import uk.gov.ons.census.action.model.dto.UacQidDTO;

@ContextConfiguration
@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringJUnit4ClassRunner.class)
public class UacQidCacheIT {
  private static final String MULTIPLE_QIDS_URL = "/multiple_qids";
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private static final EasyRandom easyRandom = new EasyRandom();
  private static final Random random = new Random();
  private static final ExecutorService executorService = Executors.newFixedThreadPool(16);

  @Autowired private UacQidCache underTest;

  @Autowired CacheRollbackTester cacheRollbackTester;

  @Value("${uacservice.uacqid-fetch-count}")
  private int cacheFetch;

  @Rule public WireMockRule mockUacQidService = new WireMockRule(wireMockConfig().port(8089));

  /**
   * This test checks the nightmare scenario of something causing an endless loop of death
   * exhausting our finite pool of QIDs. The cache should participate in the transaction rollback
   * and as such, the UAC-QID pairs should be returned to the cache for re-use. However, we need to
   * be very certain that the UAC-QID pairs are never used more than once.
   *
   * <p>The 'magic' numbers in the test are very important - because of the limitations of WireMock
   * it will always return an identical chunk of UAC-QIDs to the cache when it tops up. It's
   * important that the test therefore never tops up, or otherwise it will see duplicates and get a
   * false test failure.
   *
   * <p>The 5,000 rollbacks at the start ensure that the UAC-QIDs are definitely being recycled by
   * the cache. The subsequent checks are of sufficient number to be certain that no dupes are being
   * issued by the cache.
   */
  @Test
  public void testRollbacksDontCauseDupesInCache()
      throws JsonProcessingException, InterruptedException, ExecutionException {
    stubCreateUacQid(1);

    // GIVEN a lot of calls which cause rollbacks
    List<Callable<RollbackResult>> rollbacks = new LinkedList<>();
    for (int i = 0; i < 5000; i++) {
      rollbacks.add(() -> doRollback(true));
    }
    executorService.invokeAll(rollbacks);

    // AND some unpredictable flakiness which sometimes causes rollbacks
    rollbacks.clear();
    for (int i = 0; i < 200; i++) {
      rollbacks.add(() -> doRollback(random.nextBoolean()));
    }

    // WHEN the rollbacks stop and normal processing resumes
    for (int i = 0; i < 350; i++) {
      rollbacks.add(() -> doRollback(false));
    }
    List<Future<RollbackResult>> futures = executorService.invokeAll(rollbacks);

    // THEN there shouldn't be any dupes
    Set<UacQidDTO> uacQidsUsedFromCache = new HashSet<>();
    for (Future<RollbackResult> resultFuture : futures) {
      if (!resultFuture.isDone()) {
        fail();
      }

      RollbackResult rollbackResult = resultFuture.get();

      if (rollbackResult.getUacQidDtoUsedSuccessfully() != null) {
        if (uacQidsUsedFromCache.contains(rollbackResult.getUacQidDtoUsedSuccessfully())) {
          fail();
        }

        uacQidsUsedFromCache.add(rollbackResult.getUacQidDtoUsedSuccessfully());
      }
    }

    // AND there shouldn't be any dupes in the cache
    Set<UacQidDTO> uacQidsUniqueList = new HashSet<>();
    for (int i = 0; i < 250; i++) {
      UacQidDTO uacQidDTO = underTest.getUacQidPair(1);
      if (uacQidsUniqueList.contains(uacQidDTO)) {
        fail();
      }

      uacQidsUniqueList.add(uacQidDTO);
    }
  }

  @Data
  private class RollbackResult {
    private UacQidDTO uacQidDtoUsedSuccessfully;
  }

  private RollbackResult doRollback(boolean rollback) {
    RollbackResult rollbackResult = new RollbackResult();

    try {
      UacQidDTO uacQidDto = cacheRollbackTester.getUacQidAndThrowExceptionIfRequired(1, rollback);

      rollbackResult.setUacQidDtoUsedSuccessfully(uacQidDto);
    } catch (RuntimeException possiblyExpectedException) {
      // Ignored
    }

    return rollbackResult;
  }

  private void stubCreateUacQid(int questionnaireType) throws JsonProcessingException {
    UacQidDTO[] uacQidDTOList = new UacQidDTO[cacheFetch];

    for (int i = 0; i < cacheFetch; i++) {
      UacQidDTO uacQidDTO = easyRandom.nextObject(UacQidDTO.class);
      uacQidDTOList[i] = uacQidDTO;
    }

    String uacQidDtoJson = objectMapper.writeValueAsString(uacQidDTOList);
    stubFor(
        get(urlPathEqualTo(MULTIPLE_QIDS_URL))
            .withQueryParam("questionnaireType", equalTo(Integer.toString(questionnaireType)))
            .willReturn(
                aResponse()
                    .withStatus(HttpStatus.OK.value())
                    .withHeader("Content-Type", "application/json")
                    .withBody(uacQidDtoJson)));
  }
}
