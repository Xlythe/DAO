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
            Log.e(TAG, "Failed to open database", e);
        }
    }

    protected void close() {
        mDataSource.close();
    }

    @Override
    public int hashCode() {
        int result = 17;
        for (Field field : mFields) {
            if (isUnique(field)) {
                try {
                    Object o = field.get(this);
                    result = 31 * result + ((o == null) ? 0 : o.hashCode());
                } catch (IllegalAccessException e) {
                    Log.e(TAG, "Should not happen", e);
                }
            }
        }
        return result;
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
            try {
                for (Field field : getFields()) {
                    if (isUnique(field) && field.get(instance) != null) {
                        params.add(new Param(getName(field), field.get(instance)));
                    }
                }
            } catch (IllegalAccessException e) {
                Log.e(TAG, "Failed to read field", e);
            }

            // If no unique values were set, use the _id value instead.
            if (params.isEmpty()) {
                return new Param[] { new Param(_ID, _id) };
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
                query.append(param.getKey());
                query.append(" = ?");
            }
            return query.toString();
        }

        private String[] createParameterizedArgs(Param... params) {
            String[] args = new String[params.length];
            for (int i=0; i<args.length; i++) {
                args[i] = params[i].getParameterizedValue();
            }
            return args;
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
            database.updateWithOnConflict(getTableName(), values, query, queryArgs, SQLiteDatabase.CONFLICT_REPLACE);
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
            List<T> list = new ArrayList<>();
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            Cursor cursor = database.query(getTableName(), getColumns(), query, queryArgs, null, null, orderBy);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                T newInstance = newInstance(getModelClass(), mContext);
                inflate(newInstance, cursor);
                list.add(newInstance);
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        public List<T> query(String orderBy, int limit, Param... params) {
            List<T> list = new ArrayList<>();
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            Cursor cursor = database.query(getTableName(), getColumns(), query, queryArgs, null, null, orderBy, Integer.toString(limit));
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                T newInstance = newInstance(getModelClass(), mContext);
                inflate(newInstance, cursor);
                list.add(newInstance);
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        public List<T> query(String orderBy, int limit, int offset, Param... params) {
            List<T> list = new ArrayList<>();
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            Cursor cursor = database.query(getTableName(), getColumns(), query, queryArgs, null, null, orderBy, offset + "," + limit);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                T newInstance = newInstance(getModelClass(), mContext);
                inflate(newInstance, cursor);
                list.add(newInstance);
                cursor.moveToNext();
            }
            cursor.close();
            return list;
        }

        public T first(String orderBy, Param... params) {
            T instance = null;
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            if (DEBUG) Log.d(TAG, "First. query{" + printQueryStatementForDebugging(params) + "}");
            Cursor cursor = database.query(getTableName(), getColumns(), query, queryArgs, null, null, orderBy, "1");
            cursor.moveToFirst();
            if (!cursor.isAfterLast()) {
                instance = newInstance(getModelClass(), mContext);
                inflate(instance, cursor);
            }
            cursor.close();
            return instance;
        }

        public Cursor cursor(String orderBy, Param... params) {
            String query = createParameterizedQuery(params);
            String[] queryArgs = createParameterizedArgs(params);
            return database.query(getTableName(), getColumns(), query, queryArgs, null, null, orderBy);
        }

        public List<T> getAll() {
            List<T> list = new ArrayList<>();
            Cursor cursor = database.query(getTableName(), getColumns(), null, null, null, null, null);
            cursor.moveToFirst();
            while (!cursor.isAfterLast()) {
                T newInstance = newInstance(getModelClass(), mContext);
                inflate(newInstance, cursor);
                list.add(newInstance);
                cursor.moveToNext();
            }
            cursor.close();
            return list;
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
