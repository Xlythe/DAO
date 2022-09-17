package com.xlythe.dao;

import android.content.Context;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLog;

import java.nio.ByteBuffer;
import java.util.List;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;
import static junit.framework.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(minSdk=23)
public class ModelTest {
    private Context mContext;

    @Before
    public void setup() {
        ShadowLog.stream = System.out;
        mContext = RuntimeEnvironment.getApplication();
        new MockModel(mContext).dropTable();
    }

    @Test
    public void orderBy() {
        new MockModel.Query(mContext).myInt(1).insert();
        new MockModel.Query(mContext).myInt(2).insert();
        new MockModel.Query(mContext).myInt(3).insert();
        new MockModel.Query(mContext).myInt(4).insert();
        new MockModel.Query(mContext).myInt(5).insert();

        List<MockModel> results = new MockModel.Query(mContext).orderByMyIntAsc().all();
        for (int i = 0; i < 5; i++) {
            assertEquals(i + 1, results.get(i).getMyInt());
        }

        results = new MockModel.Query(mContext).orderByMyIntDesc().all();
        for (int i = 0; i < 5; i++) {
            assertEquals(5 - i, results.get(i).getMyInt());
        }
    }

    @Test
    public void insert() {
        new MockModel.Query(mContext).myLong(1).myInt(Integer.MAX_VALUE).insert();
        assertEquals(Integer.MAX_VALUE, new MockModel.Query(mContext).myLong(1).first().getMyInt());

        new MockModel.Query(mContext).myLong(2).myBool(true).insert();
        assertTrue(new MockModel.Query(mContext).myLong(2).first().getMyBool());

        new MockModel.Query(mContext).myLong(3).myBool(false).insert();
        assertFalse(new MockModel.Query(mContext).myLong(3).first().getMyBool());

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
    public void limit() {
        new MockModel.Query(mContext).title("Hello World").myInt(1).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(2).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(3).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(4).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(5).myLong(2).myBool(false).insert();

        assertEquals(5, new MockModel.Query(mContext).count());
        assertEquals(0, new MockModel.Query(mContext).limit(0).size());
        assertEquals(1, new MockModel.Query(mContext).limit(1).size());
        assertEquals(2, new MockModel.Query(mContext).limit(2).size());
        assertEquals(3, new MockModel.Query(mContext).limit(3).size());
        assertEquals(4, new MockModel.Query(mContext).limit(4).size());
        assertEquals(5, new MockModel.Query(mContext).limit(5).size());
        assertEquals(5, new MockModel.Query(mContext).limit(6).size());
        assertEquals(5, new MockModel.Query(mContext).limit(Integer.MAX_VALUE).size());

        assertEquals(1, new MockModel.Query(mContext).myInt(3).limit(10).size());

        assertEquals(2, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 1).get(0).getMyInt());

        try {
            new MockModel.Query(mContext).orderByMyIntAsc().limit(-1);
            fail();
        } catch (IllegalArgumentException e) {
            // ignored
        }
    }

    @Test
    public void offset() {
        new MockModel.Query(mContext).title("Hello World").myInt(1).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(2).myLong(1).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(3).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(4).myLong(2).myBool(true).insert();
        new MockModel.Query(mContext).title("Hello World").myInt(5).myLong(2).myBool(false).insert();

        assertEquals(5, new MockModel.Query(mContext).count());
        assertEquals(1, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 0).get(0).getMyInt());
        assertEquals(2, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 1).get(0).getMyInt());
        assertEquals(3, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 2).get(0).getMyInt());
        assertEquals(4, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 3).get(0).getMyInt());
        assertEquals(5, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 4).get(0).getMyInt());
        assertEquals(0, new MockModel.Query(mContext).orderByMyIntAsc().limit(1, 5).size());

        try {
            new MockModel.Query(mContext).orderByMyIntAsc().limit(1, -1);
            fail();
        } catch (IllegalArgumentException e) {
            // ignored
        }
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
