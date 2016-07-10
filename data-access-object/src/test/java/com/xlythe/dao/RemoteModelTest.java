package com.xlythe.dao;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

@RunWith(RobolectricGradleTestRunner.class)
@Config(sdk=23, shadows={}, constants = BuildConfig.class)
public class RemoteModelTest {
    private Context mContext;
    private MockServer mMockServer;

    @Before
    public void setup() {
        // Set up the server
        mMockServer = new MockServer();
        MockRemoteModel.setServer(mMockServer);

        // Print out logs to the console
        ShadowLog.stream = System.out;

        // Grab the context
        mContext = RuntimeEnvironment.application;

        // Clear the cache
        new MockRemoteModel(mContext).dropTable();
    }

    @Test
    public void cache() {
        // There should be nothing in the cache by default
        MockRemoteModel result = new MockRemoteModel.Query(mContext).id(1).first();
        assertEquals(null, result);

        // After we talk to the server, there should be a cache.
        mMockServer.setResponse(200, "[{\"id\":1,\"title\":\"\",\"my_long\":0,\"my_bool\":\"false\",\"my_int\":0}]");
        new MockRemoteModel.Query(mContext).id(1).first(new RemoteModel.Callback<MockRemoteModel>() {
            @Override
            public void onSuccess(MockRemoteModel object) {
                MockRemoteModel result = new MockRemoteModel.Query(mContext).id(1).first();
                assertNotNull(result);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void get() {
        mMockServer.setResponse(200, "[{\"id\":1,\"title\":\"Hello World\",\"my_long\":1000,\"my_bool\":\"true\",\"my_int\":1}]");

        // After we talk to the server, there should be a cache.
        new MockRemoteModel.Query(mContext).id(1).all(new RemoteModel.Callback<List<MockRemoteModel>>() {
            @Override
            public void onSuccess(List<MockRemoteModel> object) {
                assertEquals(1, object.size());
                assertEquals("Hello World", object.get(0).title);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void getFirst() {
        mMockServer.setResponse(200, "[{\"id\":1,\"title\":\"Hello World\",\"my_long\":1000,\"my_bool\":\"true\",\"my_int\":1}]");

        // After we talk to the server, there should be a cache.
        new MockRemoteModel.Query(mContext).id(1).first(new RemoteModel.Callback<MockRemoteModel>() {
            @Override
            public void onSuccess(MockRemoteModel object) {
                assertEquals(1, object.id);
                assertEquals("Hello World", object.title);
                assertEquals(1000, object.my_long);
                assertEquals(true, object.my_bool);
                assertEquals(1, object.my_int);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void insert() {
        mMockServer.setResponse(201, "{\"id\":1,\"title\":\"\",\"my_long\":0,\"my_bool\":\"false\",\"my_int\":0}");

        // After we talk to the server, there should be a cache.
        new MockRemoteModel.Query(mContext).id(1).insert(new RemoteModel.Callback<MockRemoteModel>() {
            @Override
            public void onSuccess(MockRemoteModel object) {
                assertEquals(1, object.id);
                assertEquals("", object.title);
                assertEquals(0, object.my_long);
                assertEquals(false, object.my_bool);
                assertEquals(0, object.my_int);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
        Robolectric.flushForegroundThreadScheduler();
    }

    @Test
    public void update() {
        mMockServer.setResponse(201, "{\"id\":1,\"title\":\"\",\"my_long\":0,\"my_bool\":\"false\",\"my_int\":0}");

        MockRemoteModel model = new MockRemoteModel(mContext);
        model.save(new RemoteModel.Callback<MockRemoteModel>() {
            @Override
            public void onSuccess(MockRemoteModel object) {
                assertEquals(1, object.id);
                assertEquals("", object.title);
                assertEquals(0, object.my_long);
                assertEquals(false, object.my_bool);
                assertEquals(0, object.my_int);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
    }

    @Test
    public void delete() {
        mMockServer.setResponse(204, "Success");

        MockRemoteModel model = new MockRemoteModel(mContext);
        model.delete(new RemoteModel.Callback<Void>() {
            @Override
            public void onSuccess(Void object) {
                assertTrue(true);
            }

            @Override
            public void onFailure(Throwable throwable) {
                throw new IllegalStateException(throwable);
            }
        });
        Robolectric.flushForegroundThreadScheduler();
    }

}
