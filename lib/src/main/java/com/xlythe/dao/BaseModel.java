package com.xlythe.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static com.xlythe.dao.Transcriber.getContentValues;
import static com.xlythe.dao.Transcriber.inflate;
import static com.xlythe.dao.Util.isBoolean;
import static com.xlythe.dao.Util.isByteArray;
import static com.xlythe.dao.Util.isFloat;
import static com.xlythe.dao.Util.isInt;
import static com.xlythe.dao.Util.isLong;
import static com.xlythe.dao.Util.isString;
import static com.xlythe.dao.Util.isUnique;
import static com.xlythe.dao.Util.newInstance;

/**
 * A class that serializes its fields into a database
 *
 * All classes that extend Model must have a constructor that takes a context (and nothing else).
 */
public abstract class BaseModel<T extends BaseModel> implements Serializable {
    private static final String TAG = BaseModel.class.getSimpleName();
    private static final String _ID = "_id";
    private static final boolean DEBUG = false;

    private transient Context mContext;
    private transient Field[] mFields;
    private transient ModelDataSource mDataSource;

    private long _id;

    public BaseModel(Context context) {
        setContext(context);
    }

    public void setContext(Context context) {
        mContext = context;

        ArrayList<Field> fields = new ArrayList<>();
        for (Field field : getModelClass().getDeclaredFields()) {
            field.setAccessible(true);
            if (!Modifier.isTransient(field.getModifiers())
                    && !Modifier.isStatic(field.getModifiers())) {
                fields.add(field);
            }
        }
        try {
            Field field = BaseModel.class.getDeclaredField(_ID);
            field.setAccessible(true);
            fields.add(field);
        } catch (NoSuchFieldException e) {
            Log.e(TAG, "Failed to find field _ID", e);
        }
        mFields = fields.toArray(new Field[fields.size()]);

        mDataSource = new ModelDataSource(getContext());
    }

    public Context getContext() {
        return mContext;
    }

    protected ModelDataSource getDataSource() {
        return mDataSource;
    }

    protected void open() {
        try {
            mDataSource.open();
        } catch(SQLException e) {
            e.printStackTrace();
        }
    }

    protected void close() {
        mDataSource.close();
    }

    @Override
    public boolean equals(Object o) {
        if (o == null) return false;
        if (o.getClass() != getClass()) return false;
        for (Field field : mFields) {
            if (isUnique(field)) {
                try {
                    Object me = field.get(this);
                    Object them = field.get(o);
                    if (me == them) {
                        continue;
                    } else if (me == null) {
                        return false;
                    }

                    if (!me.equals(them)) {
                        return false;
                    }
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Should not happen", e);
                }
            }
        }
        return true;
    }

    Field[] getFields() {
        return mFields;
    }

    Field getField(String name) throws NoSuchFieldException {
        Field field = getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    
    Class<T> getModelClass() {
        return (Class<T>) getClass();
    }

    public int getDatabaseVersion() {
        return Util.getDatabaseVersion(getClass());
    }

    public boolean retainDataOnUpgrade() {
        return Util.retainDataOnUpgrade(getClass());
    }

    protected class ModelDataSource {
        // Database fields
        private SQLiteDatabase database;
        private final ModelHelper dbHelper;

        // These are the column names (eg. _id)
        private final String[] columns;

        // These are the associated types (eg. int) for each column.
        private final String[] types;

        public ModelDataSource(Context context) {
            Field[] fields = getFields();
            columns = new String[fields.length];
            types = new String[fields.length];
            for (int i = 0; i < fields.length; i++) {
                Field field = fields[i];
                columns[i] = field.getName();
                types[i] = getType(field);
            }

            dbHelper = new ModelHelper(context, getDbName());
        }

        public void open() throws SQLException {
            database = dbHelper.getWritableDatabase();
            dbHelper.onCreate(database);
        }

        public void close() {
            dbHelper.close();
        }

        private Param[] getUniqueParams(T instance) {
            ArrayList<Param> params = new ArrayList<>();
            try {
                for (Field field : getFields()) {
                    if (isUnique(field) && field.get(instance) != null) {
                        params.add(new Param(field.getName(), field.get(instance)));
                    }
                }
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            }

            // If no unique values were set, use the _id value instead.
            if (params.isEmpty()) {
                return new Param[] { new Param(_ID, _id) };
            }

            // Otherwise, ignore _id and use their unique values
            return params.toArray(new Param[params.size()]);
        }

        private String createQuery(Param... params) {
            StringBuilder query = new StringBuilder();
            for (Param param : params) {
                if (query.length() > 0) {
                    query.append(" AND ");
                }
                query.append(param.getKey());
                query.append(" = ");
                query.append(param.getValue());
            }
            return query.toString();
        }

        public String getDbName() {
            return getModelClass().getPackage().getName() + ".db";
        }

        public String getTableName() {
            return getModelClass().getSimpleName();
        }

        private String getType(Field field) {
            if (isInt(field)) {
                return "integer";
            } else if (isLong(field)) {
                return "integer";
            } else if (isFloat(field)) {
                return "float";
            } else if (isBoolean(field)) {
                return "integer";
            } else if (isString(field)) {
                return "text";
            } else if (isByteArray(field)) {
                return "blob";
            } else {
                throw new UnsupportedClassVersionError(field.getType() + " is not supported");
            }
        }

        public String[] getColumns() {
            return columns;
        }

        public Cursor getRows() {
            return database.query(getTableName(), getColumns(), null, null, null, null, null);
        }

        public void create(T instance) {
            ContentValues values = getContentValues(instance);
            values.remove(_ID);
            if (DEBUG) Log.d(TAG, "Creating new entry values{" + values + "}");
            database.insert(getTableName(), null, values);
        }

        public void update(T instance) {
            String query = createQuery(getUniqueParams(instance));
            if (DEBUG) Log.d(TAG, "Updating existing entry. query{" + query + "}");
            ContentValues values = getContentValues(instance);
            database.update(getTableName(), values, query, null);
        }

        public void save(T instance) {
            if (DEBUG) Log.d(TAG, "Saving");
            Param[] params = getUniqueParams(instance);
            long count = count(params);
            if (count == 0) {
                create(instance);
            } else {
                update(instance);
            }
        }

        public void delete(T instance) {
            String query = createQuery(getUniqueParams(instance));
            if (DEBUG) Log.d(TAG, "Deleting. query{" + query + "}");
            int rowsDeleted = database.delete(getTableName(), query, null);
            Log.i(TAG, "Removed " + rowsDeleted + " rows");
        }

        public long count(Param... params) {
            String query = createQuery(params);
            if (DEBUG) Log.d(TAG, "Counting. query{" + query + "}");
            return DatabaseUtils.queryNumEntries(database, getTableName(), query);
        }

        public List<T> query(String orderBy, Param... params) {
            List<T> list = new ArrayList<T>();
            Cursor cursor = database.query(getTableName(), getColumns(), createQuery(params), null, null, null, orderBy);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                list.add((T) inflate(newInstance(getModelClass(), mContext), cursor));
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        public T first(String orderBy, Param... params) {
            T instance = null;
            Cursor cursor = database.query(getTableName(), getColumns(), createQuery(params), null, null, null, orderBy);
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                instance = (T) inflate(newInstance(getModelClass(), mContext), cursor);
            }
            cursor.close();
            return instance;
        }

        public Cursor cursor(String orderBy, Param... params) {
            return database.query(getTableName(), getColumns(), createQuery(params), null, null, null, orderBy);
        }

        public List<T> getAll() {
            List<T> list = new ArrayList<T>();
            Cursor cursor = database.query(getTableName(), getColumns(), null, null, null, null, null);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                list.add((T) inflate(newInstance(getModelClass(), mContext), cursor));
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        private class ModelHelper extends SQLiteOpenHelper {
            // Database creation sql statement
            private final String DATABASE_CREATE;

            public ModelHelper(Context context, String databaseName) {
                super(context, databaseName, null, getDatabaseVersion());

                StringBuilder builder = new StringBuilder();
                builder.append("create table if not exists ");
                builder.append(getTableName());
                builder.append("(");
                builder.append(_ID);
                builder.append(" integer primary key autoincrement");

                for (int i = 0; i < columns.length; i++) {
                    if (_ID.equals(columns[i])) {
                        continue;
                    }
                    builder.append(", ");
                    builder.append(columns[i]);
                    builder.append(" ");
                    builder.append(types[i]);
                }

                builder.append(");");
                DATABASE_CREATE = builder.toString();
            }

            @Override
            public void onCreate(SQLiteDatabase database) {
                database.execSQL(DATABASE_CREATE);
            }

            @Override
            public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
                if (retainDataOnUpgrade()) {
                    Field[] fields = getFields();
                    for (int i = oldVersion + 1; i <= newVersion; i++) {
                        for (Field field : fields) {
                            if (field.isAnnotationPresent(Version.class)) {
                                int value = field.getAnnotation(Version.class).value();
                                if (value == i) {
                                    String type = getType(field);
                                    database.execSQL("ALTER TABLE " + getTableName() + " ADD COLUMN " + field.getName() + " " + type);
                                }
                            }
                        }
                    }
                } else {
                    // Drop all existing tables
                    List<String> tablesToDrop = new LinkedList<>();
                    Cursor cursor = database.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
                    cursor.moveToFirst();
                    while (!cursor.isAfterLast()) {
                        String table = cursor.getString(0);
                        cursor.moveToNext();
                        if ("android_metadata".equals(table)) {
                            continue;
                        }
                        if ("sqlite_sequence".equals(table)) {
                            continue;
                        }
                        tablesToDrop.add(table);
                    }
                    for (String table : tablesToDrop) {
                        String sql = "drop table if exists " + table;
                        database.execSQL(sql);
                    }

                    // Recreate this table (other tables will be created as needed)
                    onCreate(database);
                }
            }
        }
    }
}