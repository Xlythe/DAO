package com.xlythe.dao;

import android.content.Context;
import android.database.Cursor;
import android.os.Looper;

import com.xlythe.dao.remote.JSONResult;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.robolectric.Shadows.shadowOf;

@RunWith(RobolectricTestRunner.class)
public class PersistenceRegressionTest {
    private final Context context = RuntimeEnvironment.getApplication();
    private final MockServer server = new MockServer();

    @After public void tearDown() { RemoteModel.setServer(null); }

    @Test public void nullFiltersComposeWithOtherFieldsAndDeleteSelectively() {
        new MockModel.Query(context).title(null).myInt(1).insert();
        new MockModel.Query(context).title("kept").myInt(1).insert();
        new MockModel.Query(context).title(null).myInt(2).insert();
        MockModel.Query selection = new MockModel.Query(context).title(null).myInt(1);
        assertEquals(1, selection.count());
        assertEquals(1, selection.first().getMyInt());
        selection.delete();
        assertEquals(2, new MockModel.Query(context).count());
        assertNotNull(new MockModel.Query(context).title("kept").first());
    }

    @Test public void binaryFiltersDistinguishNullEmptyAndArbitraryBytes() {
        byte[] value = {(byte) 0xff, 0, 1, 15, 16};
        new MockModel.Query(context).myByteArray(null).insert();
        new MockModel.Query(context).myByteArray(new byte[0]).insert();
        new MockModel.Query(context).myByteArray(value).insert();
        assertEquals(1, new MockModel.Query(context).myByteArray(null).count());
        assertEquals(1, new MockModel.Query(context).myByteArray(new byte[0]).count());
        try (Cursor cursor = new MockModel.Query(context).myByteArray(value).cursor()) {
            assertEquals(1, cursor.getCount());
            assertTrue(cursor.moveToFirst());
            assertArrayEquals(value, cursor.getBlob(cursor.getColumnIndexOrThrow("my_byte_array")));
        }
        assertTrue(new MockModel.Query(context).myByteArray(new byte[]{1}).all().isEmpty());
    }

    @Test public void detachedSaveRetainsTheExistingLocalRowId() {
        MockRemoteModel first = remoteRow(8, "first");
        MockRemoteModel copy = remoteRow(8, "second");
        assertTrue(first.get_Id() > 0);
        assertEquals(first.get_Id(), copy.get_Id());
        assertEquals(first.get_Id(), new MockRemoteModel.Query(context).id(8).first().get_Id());
        assertEquals(1, new MockRemoteModel.Query(context).count());
    }

    @Test public void concurrentSavesOfOneUniqueKeyProduceOneRow() throws Exception {
        remoteRow(1, "seed");
        CountDownLatch ready = new CountDownLatch(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.Callable<Long> write = () -> {
                ready.countDown();
                assertTrue(ready.await(10, TimeUnit.SECONDS));
                return remoteRow(2, "concurrent").get_Id();
            };
            Future<Long> first = pool.submit(write);
            Future<Long> second = pool.submit(write);
            assertEquals(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
            assertEquals(1, new MockRemoteModel.Query(context).id(2).count());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test public void observersMayUnregisterDuringNotification() {
        AtomicInteger calls = new AtomicInteger();
        Model.Observer singleUse = new Model.Observer() {
            @Override public void onChange() {
                calls.incrementAndGet();
                MockModel.unregisterObserver(this);
            }
        };
        Model.Observer permanent = calls::incrementAndGet;
        MockModel.registerObserver(singleUse);
        MockModel.registerObserver(permanent);
        try {
            new MockModel.Query(context).insert();
            new MockModel.Query(context).insert();
            assertEquals(3, calls.get());
        } finally {
            MockModel.unregisterObserver(singleUse);
            MockModel.unregisterObserver(permanent);
        }
    }

    @Test public void bulkDeletionNotifiesOnceOnlyWhenRowsWereRemoved() {
        new MockModel.Query(context).myInt(2).insert();
        new MockModel.Query(context).myInt(2).insert();
        AtomicInteger calls = new AtomicInteger();
        Model.Observer observer = () -> {
            calls.incrementAndGet();
            assertEquals(0, new MockModel.Query(context).count());
        };
        MockModel.registerObserver(observer);
        try {
            new MockModel.Query(context).myInt(1).delete();
            assertEquals(0, calls.get());
            new MockModel.Query(context).myInt(2).delete();
            assertEquals(1, calls.get());
        } finally {
            MockModel.unregisterObserver(observer);
        }
    }

    @Test public void remotePageRefreshPreservesRowsOutsideThatPage() {
        remoteRow(1, "One");
        long oldId = remoteRow(2, "Old").get_Id();
        remoteRow(3, "Three");
        respond("[{\"id\":2,\"title\":\"New\"}]");
        Result<List<MockRemoteModel>> result = new Result<>();
        new MockRemoteModel.Query(context).limit(1, 1, result);
        drain();
        assertEquals("New", result.success().get(0).title);
        assertEquals(3, new MockRemoteModel.Query(context).count());
        assertEquals(oldId, new MockRemoteModel.Query(context).id(2).first().get_Id());
    }

    @Test public void malformedRemoteRowsLeaveTheEntireCacheIntact() {
        remoteRow(1, "Original");
        for (String invalid : Arrays.asList("{}", "[{\"id\":2},false]", "[{\"id\":\"invalid\"}]")) {
            respond(invalid);
            Result<List<MockRemoteModel>> result = new Result<>();
            new MockRemoteModel.Query(context).all(result);
            drain();
            assertEquals(1, result.calls);
            assertNotNull(result.failure);
            assertEquals("Original", new MockRemoteModel.Query(context).first().title);
            assertEquals(1, new MockRemoteModel.Query(context).count());
        }
    }

    @Test public void completeRefreshReplacesOnlyMatchingRowsEvenIfTheResponseIsEmpty() {
        remoteRow(1, "A");
        remoteRow(2, "B");
        respond("[]");
        Result<List<MockRemoteModel>> result = new Result<>();
        new MockRemoteModel.Query(context).id(1).all(result);
        drain();
        assertTrue(result.success().isEmpty());
        assertNull(new MockRemoteModel.Query(context).id(1).first());
        assertEquals("B", new MockRemoteModel.Query(context).first().title);
    }

    @Test public void firstRefreshDoesNotDeleteOtherRowsAndHandlesAnEmptyArray() {
        remoteRow(1, "One");
        remoteRow(2, "Two");
        respond("[{\"id\":1,\"title\":\"Changed\"}]");
        Result<MockRemoteModel> result = new Result<>();
        new MockRemoteModel.Query(context).first(result);
        drain();
        assertEquals("Changed", result.success().title);
        assertEquals(2, new MockRemoteModel.Query(context).count());
        respond("[]");
        Result<MockRemoteModel> empty = new Result<>();
        new MockRemoteModel.Query(context).id(3).first(empty);
        drain();
        assertNull(empty.success());
    }

    @Test public void serverAssignedKeyReplacesOnlyTheProvisionalInsert() {
        remoteRow(1, "Keep");
        respond("{\"id\":9}");
        Result<MockRemoteModel> result = new Result<>();
        new MockRemoteModel.Query(context).title("Draft").insert(result);
        drain();
        assertEquals(9, result.success().id);
        assertEquals("Draft", new MockRemoteModel.Query(context).id(9).first().title);
        assertEquals(2, new MockRemoteModel.Query(context).count());
    }

    private MockRemoteModel remoteRow(int id, String title) {
        MockRemoteModel row = new MockRemoteModel(context);
        row.id = id;
        row.title = title;
        row.save();
        return row;
    }

    private void respond(String json) {
        RemoteModel.setServer(server);
        server.setResponse(200, new JSONResult(json));
    }

    private static void drain() { shadowOf(Looper.getMainLooper()).idle(); }

    private static class Result<T> implements Callback<T> {
        T value;
        Throwable failure;
        int calls;
        @Override public void onSuccess(T value) { calls++; this.value = value; }
        @Override public void onFailure(Throwable failure) { calls++; this.failure = failure; }
        T success() {
            assertEquals(1, calls);
            assertNull(failure);
            return value;
        }
    }
}
