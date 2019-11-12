/*
 * Copyright 2013-2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.data.couchbase.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.fail;
import static org.springframework.data.couchbase.CouchbaseTestHelper.getRepositoryWithRetry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataRetrievalFailureException;
;
import org.springframework.data.couchbase.ContainerResourceRunner;
import org.springframework.data.couchbase.IntegrationTestApplicationConfig;
import org.springframework.data.couchbase.repository.config.RepositoryOperationsMapping;
import org.springframework.data.couchbase.repository.support.CouchbaseRepositoryFactory;
import org.springframework.data.couchbase.repository.support.IndexManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.model.MappingInstantiationException;
import org.springframework.data.repository.core.support.RepositoryFactorySupport;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestExecutionListeners;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * This tests PaginAndSortingRepository features in the Couchbase connector.
 *
 * @author Simon Baslé
 * @author Subhashni Balakrishnan
 */
@RunWith(ContainerResourceRunner.class)
@ContextConfiguration(classes = IntegrationTestApplicationConfig.class)
@TestExecutionListeners(PartyPopulatorListener.class)
public class N1qlCouchbaseRepositoryIntegrationTests {

  @Autowired
  private RepositoryOperationsMapping operationsMapping;

  @Autowired
  private IndexManager indexManager;

  private PartyPagingRepository repository;

  private PartyRepository partyRepository;

  private ItemRepository itemRepository;

  private final String KEY_PARTY = "Party1";
  private final String KEY_ITEM = "Item1";


  @Before
  public void setup() throws Exception {
    RepositoryFactorySupport factory = new CouchbaseRepositoryFactory(operationsMapping, indexManager);
    repository = getRepositoryWithRetry(factory, PartyPagingRepository.class);
    partyRepository = getRepositoryWithRetry(factory, PartyRepository.class);
    itemRepository = getRepositoryWithRetry(factory, ItemRepository.class);

    partyRepository.save(new Party(KEY_PARTY, "partyName", "MatchingDescription", null, 1, null));
    itemRepository.save(new Item(KEY_ITEM, "MatchingDescription"));
  }

  @After
  public void cleanUp() {
    try { itemRepository.deleteById(KEY_ITEM); } catch (DataRetrievalFailureException e) {}
    try { partyRepository.deleteById(KEY_PARTY); } catch (DataRetrievalFailureException e) {}
  }

  @Test
  public void shouldBeThreadsafe() {
    // This doesn't guarantee it, but we should catch most thread issues without
    // taking too long here...
    int runs = 50;
    for (int i=0; i<runs; i++) {
      doShouldBeThreadsafe();
    }
  }
  public void doShouldBeThreadsafe() {
    int threads = 50;
    ExecutorService service = Executors.newFixedThreadPool(threads);
    List<Callable<Boolean>> callables = new ArrayList<>();
    for (int thread = 0; thread < threads; ++thread) {
      final int counter = thread;
      Callable<Boolean> booleanSupplier = () -> {
        String expectedName = "party like it's 199" + counter%12;
        String foundName = partyRepository.findByName(expectedName).get(0).getName();
        return expectedName.equals(foundName); //should never get false
      };
      callables.add(booleanSupplier);
    }
    try {
      List<Future<Boolean>> futures = service.invokeAll(callables);
      service.shutdown();
      service.awaitTermination(5, TimeUnit.SECONDS);
      for (Future<Boolean> future: futures) {
        assertThat(future.get()).isTrue();
      }
    } catch (InterruptedException | ExecutionException e) {
      fail("Threads failed to run " + e.getMessage());
    }
  }

  @Test
  public void shouldFindAllWithSort() {
    Iterable<Party> allByAttendanceDesc = repository.findAll(Sort.by(Sort.Direction.DESC, "attendees"));
    long previousAttendance = Long.MAX_VALUE;
    for (Party party : allByAttendanceDesc) {
      assertThat(party.getAttendees() <= previousAttendance).isTrue();
      previousAttendance = party.getAttendees();
    }
    assertThat(previousAttendance == Long.MAX_VALUE)
			.as("Expected to find several parties").isFalse();
  }

  @Test
  public void shouldSortOnRenamedFieldIfJsonNameIsProvidedInSort() {
    Iterable<Party> parties = repository.findAll(Sort.by(Sort.Direction.DESC, "desc"));
    String previousDesc = null;
    for (Party party : parties) {
      if (previousDesc != null) {
        assertThat(party.getDescription().compareTo(previousDesc) <= 0).isTrue();
      }
      previousDesc = party.getDescription();
    }
    assertThat(previousDesc).as("Expected to find several parties").isNotNull();
  }

  @Test
  public void shouldSortWithoutCaseSensitivity() {
    Iterable<Party> parties = repository.findAll(Sort.by(new Sort.Order(Sort.Direction.DESC, "desc").ignoreCase()));
    String previousDesc = null;
    for (Party party : parties) {
      if (previousDesc != null) {
        assertThat(party.getDescription().compareToIgnoreCase(previousDesc) <= 0)
				.isTrue();
      }
      previousDesc = party.getDescription();
    }
    assertThat(previousDesc).as("Expected to find several parties").isNotNull();
  }

  @Test
  public void shouldPageThroughEntities() {
    Pageable pageable = PageRequest.of(0, 8);

    Page<Party> page1 = repository.findAll(pageable);
    assertThat(page1.getTotalElements() >= 12)
			.as("Query for parties should be atleast 12").isTrue();
    assertThat(page1.getNumberOfElements()).isEqualTo(8);
  }

  @Test
  public void shouldPageThroughSortedEntities() {
    Pageable pageable = PageRequest.of(0, 8, Sort.Direction.DESC, "attendees");

    Page<Party> page1 = repository.findAll(pageable);
    assertThat(page1.getTotalElements() >= 12)
			.as("Query for parties should be atleast 12").isTrue();
    assertThat(page1.getNumberOfElements()).isEqualTo(8);

    List<Party> parties = page1.getContent();
    Long previousAttendees = null;
    for (Party party : parties) {
      if (previousAttendees != null) {
        assertThat(party.getAttendees() <= previousAttendees).isTrue();
      }
      previousAttendees = party.getAttendees();
    }
  }

  @Test
  public void testWrapWhereCriteria() {
    List<Party> partyList = partyRepository.findByDescriptionOrName("MatchingDescription", "partyName");
    assertThat(partyList.size() == 1).isTrue();
  }

  @Test
  public void shouldPageWithStringBasedQuery() {
    Pageable pageable = PageRequest.of(0, 8, Sort.Direction.DESC, "attendees");
    Page<Party> page1 = partyRepository.findPartiesWithAttendee(1, pageable);
    assertThat(page1.getTotalElements() >= 12)
			.as("Query for parties with attendees should be atleast 12").isTrue();
    assertThat(page1.getNumberOfElements()).isEqualTo(8);

    List<Party> parties = page1.getContent();
    Long previousAttendees = null;
    for (Party party : parties) {
      if (previousAttendees != null) {
        assertThat(party.getAttendees() <= previousAttendees).isTrue();
      }
      previousAttendees = party.getAttendees();
    }
    Page<Party> page2 = partyRepository.findPartiesWithAttendee(1, page1.nextPageable());
    assertThat(page2.getNumberOfElements()).isEqualTo(8);
    parties = page2.getContent();
    for (Party party : parties) {
      if (previousAttendees != null) {
        assertThat(party.getAttendees() <= previousAttendees).isTrue();
      }
      previousAttendees = party.getAttendees();
    }
  }

  //Fails on deserialization as a different entity item is also present
  @Test(expected = MappingInstantiationException.class)
  public void shouldFailWithMissingFilterStringBasedQuery() {
    Sort sort = Sort.by(Sort.Direction.DESC, "attendees");
    partyRepository.findParties(sort);
  }

  @Test
  public void testDeleteQuery() {
    partyRepository.save(new Party("testDeleteQuery", "delete", "delete", null, 0, null));
    List<Party> partyList = partyRepository.removeByDescriptionOrName("delete", "delete");
    assertThat(partyList.size() == 1).isTrue();
  }

  @Test
  public void testSpelDateConvertion() {
    final String key = "testSpelDateConvertion";
    Calendar cal = Calendar.getInstance();
    cal.clear();
    cal.set(2018, Calendar.SEPTEMBER, 10);
    Date date = cal.getTime();
    partyRepository.save(new Party(key, "", "", date, 0, null));
    List<Party> partyList = partyRepository.getByEventDate(date);
    assertThat(partyList.size() == 1).isTrue();
    assertThat(key).as("Key mismatch").isEqualTo(partyList.get(0).getKey());
  }

  @Test
  public void testN1qlQueryWithInvalidValue() {
    partyRepository.save(new Party("testN1qlQueryWithInvalidValue", "", "testN1qlQueryWithInvalidValue", null, 0, null));
    final String description = "testN1qlQueryWithInvalidValue* OR `description` LIKE \"\"";
    List<Party> partyList = partyRepository.findByDescriptionStartingWith(description);
    assertThat(partyList.size() == 0).isTrue();
  }
}
