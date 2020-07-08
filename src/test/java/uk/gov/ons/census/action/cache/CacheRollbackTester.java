package uk.gov.ons.census.action.cache;

import org.springframework.stereotype.Component;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.action.model.dto.UacQidDTO;

@ActiveProfiles("test")
@Component
public class CacheRollbackTester {
  private final UacQidCache uacQidCache;

  public CacheRollbackTester(UacQidCache uacQidCache) {
    this.uacQidCache = uacQidCache;
  }

  @Transactional
  public UacQidDTO getUacQidAndThrowExceptionIfRequired(
      int questionnaireType, boolean throwException) {
    UacQidDTO uacQidDTO = uacQidCache.getUacQidPair(questionnaireType);

    if (throwException) {
      throw new RuntimeException();
    }

    return uacQidDTO;
  }
}
