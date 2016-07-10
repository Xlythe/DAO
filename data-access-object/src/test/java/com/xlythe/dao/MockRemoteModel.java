package com.xlythe.dao;

import android.content.Context;

@Database(version=1, retainDataOnUpgrade=false)
public class MockRemoteModel extends RemoteModel<MockRemoteModel> {
    static final String URL = "https://www.example.com/mock_model";

    public static class Query extends RemoteModel.Query<MockRemoteModel> {
        public Query(Context context) {
            super(MockRemoteModel.class, context);
            url(URL);
        }

        public MockRemoteModel.Query id(int id) {
            where(new Param("id", id));
            return this;
        }

        public MockRemoteModel.Query title(String title) {
            where(new Param("title", title));
            return this;
        }

        public MockRemoteModel.Query myLong(long myLong) {
            where(new Param("my_long", myLong));
            return this;
        }

        public MockRemoteModel.Query myInt(int myInt) {
            where(new Param("my_int", myInt));
            return this;
        }

        public MockRemoteModel.Query myBool(boolean myBool) {
            where(new Param("my_bool", myBool));
            return this;
        }

        public MockRemoteModel.Query myByteArray(byte[] myByteArray) {
            where(new Param("my_byte_array", myByteArray));
            return this;
        }
    }

    @Unique
    private int id;
    private String title;
    private long my_long;
    private boolean my_bool;
    private int my_int;
    private byte[] my_byte_array;

    public MockRemoteModel(Context context) {
        super(context);
        setUrl(URL);
    }
}
