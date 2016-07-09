package com.xlythe.dao;

import android.content.Context;
import android.database.CursorWrapper;

@Database(version=1, retainDataOnUpgrade=false)
public class MockModel extends Model<MockModel> {
    public static void registerObserver(Observer observer) {
        registerObserver(MockModel.class, observer);
    }

    public static void unregisterObserver(Observer observer) {
        unregisterObserver(MockModel.class, observer);
    }

    public static class Cursor extends CursorWrapper {
        private final Context mContext;

        private Cursor(Context context, android.database.Cursor cursor) {
            super(cursor);
            mContext = context;
        }

        public MockModel getMockModel() {
            return new MockModel(mContext, this);
        }
    }

    public static class Query extends Model.Query<MockModel> {
        public Query(Context context) {
            super(MockModel.class, context);
        }

        public MockModel.Query title(String title) {
            where(new Param("title", title));
            return this;
        }

        public MockModel.Query myLong(long myLong) {
            where(new Param("my_long", myLong));
            return this;
        }

        public MockModel.Query myInt(int myInt) {
            where(new Param("my_int", myInt));
            return this;
        }

        public MockModel.Query myBool(boolean myBool) {
            where(new Param("my_bool", myBool));
            return this;
        }

        public MockModel.Query myByteArray(byte[] myByteArray) {
            where(new Param("my_byte_array", myByteArray));
            return this;
        }

        public MockModel.Query orderByMyInt() {
            orderBy("my_int DESC");
            return this;
        }

        public MockModel.Cursor cursor() {
            return new MockModel.Cursor(getContext(), super.cursor());
        }
    }

    private String title;
    private long my_long;
    private boolean my_bool;
    private int my_int;
    private byte[] my_byte_array;
    private transient Object my_transient_object;

    public MockModel(Context context) {
        super(context);
    }

    public MockModel(Context context, android.database.Cursor cursor) {
        super(context, cursor);
    }

    @Override
    public void save() {
        super.save();
    }

    @Override
    public void delete() {
        super.delete();
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public long getMyLong() {
        return my_long;
    }

    public int getMyInt() {
        return my_int;
    }

    public boolean getMyBool() {
        return my_bool;
    }

    public byte[] getMyByteArray() {
        return my_byte_array;
    }
}
