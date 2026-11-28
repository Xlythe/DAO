package com.xlythe.dao;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.io.Serializable;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.xlythe.dao.Transcriber.getContentValues;
import static com.xlythe.dao.Transcriber.getName;
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
public abstract class BaseModel<T extends BaseModel<T>> implements Serializable {
    private static final String TAG = BaseModel.class.getSimpleName();
    static final boolean DEBUG = false;
    static final String _ID = "_id";

    private transient Context mContext;
    private transient Field[] mFields;
    private transient ModelDataSource mDataSource;

    long _id;

    public BaseModel(Context context) {
        setContext(context);
    }

    public long get_Id() {
        return _id;
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
        mFields = fields.toArray(new Field[0]);

        mDataSource = new ModelDataSource(getContext());
    }

    public Context getContext() {
        return mContext;
    }

    protected ModelDataSource getDataSource() {
        return mDataSource;
    }

    protected Object getUniqueKey() {
        for (Field field : mFields) {
            if (isUnique(field)) {
                try {
                    return field.get(this);
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Failed to access field " + getName(field), e);
                }
            }
        }
        return null;
    }

    protected void open() {
        try {
            mDataSource.open();
        } catch(SQLException e) {
            throw new IllegalStateException("Failed to open database", e);
        }
    }

    protected void close() {
        mDataSource.close();
    }

    @Override
    public int hashCode() {
        int result = 17;
        boolean hasKey = false;
        try {
            for (Field field : mFields) {
                if (!isUnique(field)) continue;
                Object key = field.get(this);
                hasKey |= key != null;
                result = 31 * result + (key instanceof byte[] ? Arrays.hashCode((byte[]) key)
                        : key == null ? 0 : key.hashCode());
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        if (hasKey) return result;
        return _id == 0 ? System.identityHashCode(this) : (int) (_id ^ (_id >>> 32));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || other.getClass() != getClass()) return false;
        boolean hasKey = false;
        try {
            for (Field field : mFields) {
                if (!isUnique(field)) continue;
                Object key = field.get(this);
                Object theirs = field.get(other);
                hasKey |= key != null;
                if (key instanceof byte[]) {
                    if (!Arrays.equals((byte[]) key, (byte[]) theirs)) return false;
                } else if (!java.util.Objects.equals(key, theirs)) {
                    return false;
                }
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        return hasKey || (_id != 0 && _id == ((BaseModel<?>) other)._id);
    }

    Field[] getFields() {
        return mFields;
    }

    Field getField(String name) throws NoSuchFieldException {
        for (Field field : mFields) {
            if (getName(field).equals(name)) {
                field.setAccessible(true);
                return field;
            }
        }

        throw new NoSuchFieldException("No field " + name + " in class " + getModelClass());
    }
    
    @SuppressWarnings("unchecked")
    Class<T> getModelClass() {
        return (Class<T>) getClass();
    }

    public int getDatabaseVersion() {
        return Util.getDatabaseVersion(getClass());
    }

    public boolean retainDataOnUpgrade() {
        return Util.retainDataOnUpgrade(getClass());
    }

    public void dropTable() {
        try {
            mDataSource.open();
            mDataSource.dropTable();
        } catch (SQLException e) {
            Log.e(TAG, "Failed to open data source", e);
        } finally {
            mDataSource.close();
        }
    }

    public class ModelDataSource {
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
                columns[i] = getName(field);
                types[i] = getType(field);
            }

            dbHelper = new ModelHelper(context, getDbName());
        }

        public void open() throws SQLException {
            try {
                database = dbHelper.getWritableDatabase();
                dbHelper.onCreate(database);
            } catch (SQLiteException e) {
                throw new SQLException(e);
            }
        }

        public void close() {
            dbHelper.close();
        }

        private Param[] getUniqueParams(T instance) {
            ArrayList<Param> params = new ArrayList<>();
            boolean hasKey = false;
            try {
                for (Field field : getFields()) {
                    if (isUnique(field)) {
                        Object key = field.get(instance);
                        hasKey |= key != null;
                        params.add(new Param(getName(field), key));
                    }
                }
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to read field", e);
            }

            // If no unique values were set, use the _id value instead.
            if (!hasKey) {
                return new Param[] { new Param(_ID, instance._id) };
            }

            // Otherwise, ignore _id and use their unique values
            return params.toArray(new Param[0]);
        }

        private String printQueryStatementForDebugging(Param... params) {
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

        private String createParameterizedQuery(Param... params) {
            StringBuilder query = new StringBuilder();
            for (Param param : params) {
                if (query.length() > 0) {
                    query.append(" AND ");
                }
                String column = param.getKey();
                if (param.getUnformattedValue() == null) {
                    query.append(column).append(" IS NULL");
                } else if (param.getUnformattedValue() instanceof byte[]) {
                    // Android selectionArgs bind strings. hex(NULL) and hex(an empty blob) are both empty.
                    query.append(column).append(" IS NOT NULL AND hex(").append(column).append(") = ?");
                } else {
                    query.append(column).append(" = ?");
                }
            }
            return query.toString();
        }

        private String[] createParameterizedArgs(Param... params) {
            List<String> args = new ArrayList<>();
            for (Param param : params) {
                Object value = param.getUnformattedValue();
                if (value instanceof byte[]) {
                    StringBuilder hex = new StringBuilder();
                    for (byte b : (byte[]) value) {
                        hex.append("0123456789ABCDEF".charAt((b & 0xff) >> 4));
                        hex.append("0123456789ABCDEF".charAt(b & 0xf));
                    }
                    args.add(hex.toString());
                } else if (value != null) {
                    args.add(param.getParameterizedValue());
                }
            }
            return args.toArray(new String[0]);
        }

        public String getDbName() {
            return Util.getDatabaseName(getModelClass());
        }

        public String getTableName() {
            return Util.getTableName(getModelClass());
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
            instance._id = database.insertWithOnConflict(getTableName(), null, values, SQLiteDatabase.CONFLICT_REPLACE);
        }

        public void update(T instance) {
            Param[] params = getUniqueParams(instance);
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            if (DEBUG) Log.d(TAG, "Updating existing entry. query{" + printQueryStatementForDebugging(params) + "}");
            ContentValues values = getContentValues(instance);
            values.remove(_ID);
            database.updateWithOnConflict(getTableName(), values, query, queryArgs, SQLiteDatabase.CONFLICT_REPLACE);
        }

        public void save(T instance) {
            if (DEBUG) Log.d(TAG, "Saving");
            Param[] params = getUniqueParams(instance);
            database.beginTransaction();
            try {
                try (Cursor row = database.query(getTableName(), new String[]{_ID}, createParameterizedQuery(params),
                        createParameterizedArgs(params), null, null, null, "1")) {
                    if (row.moveToFirst()) {
                        instance._id = row.getLong(0);
                        update(instance);
                    } else {
                        create(instance);
                    }
                }
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        void cacheResponse(List<T> rows, Param[] selection, boolean replace) {
            database.beginTransaction();
            try {
                if (replace) delete(selection);
                for (T row : rows) save(row);
                database.setTransactionSuccessful();
            } finally {
                database.endTransaction();
            }
        }

        public void delete(T instance) {
            Param[] params = getUniqueParams(instance);
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            if (DEBUG) Log.d(TAG, "Deleting. query{" + printQueryStatementForDebugging(params) + "}");
            int rowsDeleted = database.delete(getTableName(), query, queryArgs);
            Log.i(TAG, "Removed " + rowsDeleted + " rows");
        }

        public long count(Param... params) {
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            if (DEBUG) Log.d(TAG, "Counting. query{" + printQueryStatementForDebugging(params) + "}");
            return DatabaseUtils.queryNumEntries(database, getTableName(), query, queryArgs);
        }

        public void dropTable() {
            dropTable(database);
        }

        private void dropTable(SQLiteDatabase database) {
            database.execSQL("DROP TABLE IF EXISTS " + getTableName() + ";");
        }

        public List<T> query(String orderBy, Param... params) {
            return readRows(orderBy, null, params);
        }

        public List<T> query(String orderBy, int limit, Param... params) {
            return query(orderBy, limit, 0, params);
        }

        public List<T> query(String orderBy, int limit, int offset, Param... params) {
            if (limit < 0 || offset < 0) throw new IllegalArgumentException("Limit and offset must be non-negative");
            return readRows(orderBy, offset + "," + limit, params);
        }

        private List<T> readRows(String orderBy, String limit, Param... params) {
            List<T> list = new ArrayList<>();
            try (Cursor rows = database.query(getTableName(), getColumns(), createParameterizedQuery(params),
                    createParameterizedArgs(params), null, null, orderBy, limit)) {
                while (rows.moveToNext()) list.add(inflate(newInstance(getModelClass(), mContext), rows));
            }
            return list;
        }

        public T first(String orderBy, Param... params) {
            List<T> rows = readRows(orderBy, "1", params);
            return rows.isEmpty() ? null : rows.get(0);
        }

        public Cursor cursor(String orderBy, Param... params) {
            return database.query(getTableName(), getColumns(), createParameterizedQuery(params),
                    createParameterizedArgs(params), null, null, orderBy);
        }

        public List<T> getAll() {
            return query(null);
        }

        public int delete(Param... params) {
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            int rowsDeleted = database.delete(getTableName(), query, queryArgs);
            Log.i(TAG, "Removed " + rowsDeleted + " rows");
            return rowsDeleted;
        }

        private class ModelHelper extends SQLiteOpenHelper {
            public ModelHelper(Context context, String databaseName) {
                super(context, databaseName, null, getDatabaseVersion());
            }

            @Override
            public void onCreate(SQLiteDatabase database) {
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
                String createStatement = builder.toString();
                if (DEBUG) {
                    Log.v(TAG, "Creating table: " + createStatement);
                }
                database.execSQL(createStatement);
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
                                    database.execSQL("ALTER TABLE " + getTableName() + " ADD COLUMN " + getName(field) + " " + type);
                                }
                            }
                        }
                    }
                } else {
                    dropTable(database);
                    onCreate(database);
                }
            }
        }
    }
}
