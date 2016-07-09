package com.xlythe.dao;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricGradleTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.nio.ByteBuffer;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;

@RunWith(RobolectricGradleTestRunner.class)
@Config(sdk=23, shadows={}, constants = BuildConfig.class)
public class ModelTest {
    private Context mContext;

    @Before
    public void setup() {
        ShadowLog.stream = System.out;
        mContext = RuntimeEnvironment.application;
        new MockModel(mContext).dropTable();
    }

    @Test
    public void insert() {
        new MockModel.Query(mContext).myLong(1).myInt(Integer.MAX_VALUE).insert();
        assertEquals(Integer.MAX_VALUE, new MockModel.Query(mContext).myLong(1).first().getMyInt());

        new MockModel.Query(mContext).myLong(2).myBool(true).insert();
        assertEquals(true, new MockModel.Query(mContext).myLong(2).first().getMyBool());

        new MockModel.Query(mContext).myLong(3).myBool(false).insert();
        assertEquals(false, new MockModel.Query(mContext).myLong(3).first().getMyBool());

        new MockModel.Query(mContext).myLong(Long.MAX_VALUE).insert();
        assertEquals(1, new MockModel.Query(mContext).myLong(Long.MAX_VALUE).count());

        new MockModel.Query(mContext).myLong(Long.MIN_VALUE).insert();
        assertEquals(1, new MockModel.Query(mContext).myLong(Long.MIN_VALUE).count());

        new MockModel.Query(mContext).myLong(4).myByteArray(new byte[]{ 0, 0, 0, 1, 1, 1}).insert();
        assertEquals(ByteBuffer.wrap(new byte[]{ 0, 0, 0, 1, 1, 1}),
                ByteBuffer.wrap(new MockModel.Query(mContext).myLong(4).first().getMyByteArray()));
    }

    @Test
    public void query() {
        new MockModel.Query(mContext).title("Hello World").myInt(1).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(2).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(3).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(4).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(5).myLong(2).myBool(false).insert();

        assertEquals(5, new MockModel.Query(mContext).count());
        assertEquals(1, new MockModel.Query(mContext).myInt(1).count());
        assertEquals(2, new MockModel.Query(mContext).myLong(1).count());
        assertEquals(3, new MockModel.Query(mContext).myLong(2).count());
        assertEquals(4, new MockModel.Query(mContext).myBool(true).count());
    }

    @Test
    public void save() {
        MockModel mockModel = new MockModel(mContext);
        mockModel.setTitle("Hello World");
        mockModel.save();

        assertEquals(1, new MockModel.Query(mContext).count());
        assertEquals("Hello World", new MockModel.Query(mContext).title("Hello World").first().getTitle());
    }

    @Test
    public void delete() {
        MockModel mockModel = new MockModel(mContext);
        mockModel.setTitle("Hello World");
        mockModel.save();

        assertEquals(1, new MockModel.Query(mContext).count());
        assertEquals("Hello World", new MockModel.Query(mContext).title("Hello World").first().getTitle());

        mockModel.delete();

        assertEquals(0, new MockModel.Query(mContext).count());
    }
}
