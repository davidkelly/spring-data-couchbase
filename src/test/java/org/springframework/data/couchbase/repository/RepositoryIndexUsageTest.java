package org.springframework.data.couchbase.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.document.json.JsonObject;
import com.couchbase.client.java.query.N1qlQuery;
import com.couchbase.client.java.view.ViewQuery;
import com.couchbase.client.java.view.ViewResult;
import com.couchbase.client.java.view.ViewRow;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.data.couchbase.core.CouchbaseOperations;
import org.springframework.data.couchbase.core.convert.CouchbaseConverter;
import org.springframework.data.couchbase.core.query.Consistency;
import org.springframework.data.couchbase.repository.query.CouchbaseEntityInformation;
import org.springframework.data.couchbase.repository.support.N1qlCouchbaseRepository;
import org.springframework.data.couchbase.repository.support.ViewMetadataProvider;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

public class RepositoryIndexUsageTest {

  private static final org.springframework.data.couchbase.core.query.Consistency CONSISTENCY = Consistency.STRONGLY_CONSISTENT;

  private CouchbaseOperations couchbaseOperations;
  private N1qlCouchbaseRepository<String, String> repository;

  @Before
  public void initMocks() {
    ViewRow mockCountRow1 = mock(ViewRow.class);
    when(mockCountRow1.value()).thenReturn("100");
    ViewRow mockCountRow2 = mock(ViewRow.class);
    when(mockCountRow2.value()).thenReturn("200");
    List<ViewRow> allCountRows = Arrays.asList(mockCountRow1, mockCountRow2);

    ViewResult mockCountResult = mock(ViewResult.class);
    when(mockCountResult.iterator()).thenReturn(allCountRows.iterator());

    Bucket mockBucket = mock(Bucket.class);
    when(mockBucket.name()).thenReturn("mockBucket");

    CouchbaseConverter mockConverter = mock(CouchbaseConverter.class);
    when(mockConverter.getTypeKey()).thenReturn("mockType");

    couchbaseOperations = mock(CouchbaseOperations.class);
    when(couchbaseOperations.getDefaultConsistency()).thenReturn(CONSISTENCY);
    when(couchbaseOperations.getCouchbaseBucket()).thenReturn(mockBucket);
    when(couchbaseOperations.getConverter()).thenReturn(mockConverter);
    when(couchbaseOperations.findByView(any(ViewQuery.class), any(Class.class))).thenReturn(allCountRows);
    when(couchbaseOperations.findByN1QL(any(N1qlQuery.class), any(Class.class))).thenReturn(Collections.emptyList());
    when(couchbaseOperations.queryView(any(ViewQuery.class))).thenReturn(mockCountResult);
    when(couchbaseOperations.queryN1QL(any(N1qlQuery.class))).thenReturn(null);

    CouchbaseEntityInformation metadata = mock(CouchbaseEntityInformation.class);
    when(metadata.getJavaType()).thenReturn(String.class);

    repository = new N1qlCouchbaseRepository<String, String>(metadata, couchbaseOperations);
    repository.setViewMetadataProvider(mock(ViewMetadataProvider.class));
  }

  @Test
  public void testFindAllUsesViewWithConfiguredConsistency() {
    String expectedQueryParams = "reduce=false&stale=false";
    repository.findAll();

    verify(couchbaseOperations, never()).queryView(any(ViewQuery.class));
    verify(couchbaseOperations, never()).findByN1QL(any(N1qlQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<ViewQuery> queryCaptor = ArgumentCaptor.forClass(ViewQuery.class);
    verify(couchbaseOperations).findByView(queryCaptor.capture(), any(Class.class));
    String sQuery = queryCaptor.getValue().toString();
    assertEquals(expectedQueryParams, sQuery);
  }

  @Test
  public void testFindAllKeysUsesViewWithConfiguredConsistency() {
    String expectedQueryParams = "reduce=false&stale=false";
    repository.findAll(Collections.singleton("someKey"));

    verify(couchbaseOperations, never()).queryView(any(ViewQuery.class));
    verify(couchbaseOperations, never()).findByN1QL(any(N1qlQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<ViewQuery> queryCaptor = ArgumentCaptor.forClass(ViewQuery.class);
    verify(couchbaseOperations).findByView(queryCaptor.capture(), any(Class.class));
    String sQuery = queryCaptor.getValue().toString();
    assertEquals(expectedQueryParams, sQuery);
  }

  @Test
  public void testCountUsesViewWithConfiguredConsistencyAndReduces() {
    String expectedQueryParams = "reduce=true&stale=false";
    repository.count();

    verify(couchbaseOperations, never()).findByView(any(ViewQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).findByN1QL(any(N1qlQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<ViewQuery> queryCaptor = ArgumentCaptor.forClass(ViewQuery.class);
    verify(couchbaseOperations).queryView(queryCaptor.capture());
    String sQuery = queryCaptor.getValue().toString();
    assertEquals(expectedQueryParams, sQuery);
  }

  @Test
  public void testCountParsesAndAddsLongValuesFromRows() {
    long count = repository.count();
    assertEquals(300L, count);
  }

  @Test
  public void testDeleteAllUsesViewWithConfiguredConsistency() {
    String expectedQueryParams = "reduce=false&stale=false";
    repository.deleteAll();

    verify(couchbaseOperations, never()).findByView(any(ViewQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).findByN1QL(any(N1qlQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<ViewQuery> queryCaptor = ArgumentCaptor.forClass(ViewQuery.class);
    verify(couchbaseOperations).queryView(queryCaptor.capture());
    String sQuery = queryCaptor.getValue().toString();
    assertEquals(expectedQueryParams, sQuery);
  }

  @Test
  public void testFindAllSortedUsesN1qlWithConfiguredConsistencyAndOrderBy() {
    String expectedOrderClause = "ORDER BY `length` ASC";
    Sort sort = new Sort(Sort.Direction.ASC, "length");
    repository.findAll(sort);

    verify(couchbaseOperations, never()).findByView(any(ViewQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryView(any(ViewQuery.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<N1qlQuery> queryCaptor = ArgumentCaptor.forClass(N1qlQuery.class);
    verify(couchbaseOperations).findByN1QL(queryCaptor.capture(), any(Class.class));

    JsonObject query = queryCaptor.getValue().n1ql();
    assertEquals(CONSISTENCY.n1qlConsistency().n1ql(), query.getString("scan_consistency"));
    String statement = query.getString("statement");
    assertTrue("Expected " + expectedOrderClause + " in " + statement, statement.contains(expectedOrderClause));
  }

  @Test
  public void testFindAllPagedUsesUsesN1qlConfiguredConsistencyAndLimitOffset() {
    String expectedLimitClause = "LIMIT 10 OFFSET 0";
    repository.findAll(new PageRequest(0, 10));

    verify(couchbaseOperations, never()).findByView(any(ViewQuery.class), any(Class.class));
    verify(couchbaseOperations, never()).queryView(any(ViewQuery.class));
    verify(couchbaseOperations, never()).queryN1QL(any(N1qlQuery.class));
    ArgumentCaptor<N1qlQuery> queryCaptor = ArgumentCaptor.forClass(N1qlQuery.class);
    verify(couchbaseOperations).findByN1QL(queryCaptor.capture(), any(Class.class));

    JsonObject query = queryCaptor.getValue().n1ql();
    assertEquals(CONSISTENCY.n1qlConsistency().n1ql(), query.getString("scan_consistency"));
    String statement = query.getString("statement");
    assertTrue("Expected " + expectedLimitClause + " in " + statement, statement.contains(expectedLimitClause));
  }
}