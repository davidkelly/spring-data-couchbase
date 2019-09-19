package org.springframework.data.couchbase.repository;

import java.util.Collections;
import java.util.List;

import com.couchbase.client.java.Bucket;
import com.couchbase.client.java.Cluster;
import com.couchbase.client.java.Collection;

import com.couchbase.client.java.kv.PersistTo;
import com.couchbase.client.java.kv.ReplicateTo;
import org.springframework.data.couchbase.config.BeanNames;
import org.springframework.data.couchbase.core.RxJavaCouchbaseTemplate;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * @author Subhashni Balakrishnan
 */
public class SimpleReactiveCouchbaseRepositoryListener extends DependencyInjectionTestExecutionListener {

	@Override
	public void beforeTestClass(final TestContext testContext) throws Exception {
		Bucket bucket = (Bucket) testContext.getApplicationContext().getBean(BeanNames.COUCHBASE_BUCKET);
		Cluster cluster = (Cluster) testContext.getApplicationContext().getBean(BeanNames.COUCHBASE_CLUSTER);
		populateTestData(cluster, bucket.defaultCollection());
	}

	private void populateTestData(Cluster cluster, Collection collection) {
		RxJavaCouchbaseTemplate template = new RxJavaCouchbaseTemplate(cluster, collection);

		for (int i = 0; i < 100; i++) {
			ReactiveUser u = new ReactiveUser("reactivetestuser-" + i, "reactiveuname-" + i, i);
			template.save(u, PersistTo.ACTIVE, ReplicateTo.NONE).subscribe();
		}

	}

}
