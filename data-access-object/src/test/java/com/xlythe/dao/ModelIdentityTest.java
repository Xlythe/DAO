package com.xlythe.dao;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
public class ModelIdentityTest {
    private final Context context = RuntimeEnvironment.getApplication();

    @Test public void unkeyedRowsUseTheirAssignedSqliteId() {
        MockModel a = new MockModel(context);
        MockModel b = new MockModel(context);
        assertEquals(a, a);
        assertNotEquals(a, b);
        a.save();
        b.save();
        assertNotEquals(a, b);
        MockModel loaded = new MockModel.Query(context).where("_id", a.get_Id()).first();
        assertEquals(a, loaded);
        assertEquals(a.hashCode(), loaded.hashCode());
    }

    public static class Composite extends Model<Composite> {
        @Unique String groupName;
        @Unique byte[] itemKey;
        String value;
        public Composite(Context context) { super(context); }
    }

    @Test public void aNullKeyComponentDoesNotOverwriteAnotherMemberOfTheGroup() {
        Composite other = new Composite(context);
        other.groupName = "one";
        other.itemKey = new byte[]{42};
        other.value = "original";
        other.save();
        Composite empty = new Composite(context);
        empty.groupName = "one";
        empty.value = "no item";
        empty.save();
        Model.Query<Composite> query = new Model.Query<>(Composite.class, context);
        assertEquals(2, query.count());
        assertEquals(empty, query.where("itemKey", null).first());
        empty.delete();
        Composite saved = new Model.Query<>(Composite.class, context).first();
        assertEquals("original", saved.value);
        assertEquals(other, saved);
        assertEquals(other.hashCode(), saved.hashCode());
    }

    @Database(name = "migration", tableName = "records", version = 3, retainDataOnUpgrade = true)
    public static class Record extends Model<Record> {
        String name;
        @Version(2) @Schema(columnName = "new_count") int count;
        @Version(3) String description;
        public Record(Context context) { super(context); }
    }

    @Test public void retainedDatabaseMigratesAcrossTwoVersionsAndKeepsItsRowId() {
        try (SQLiteDatabase old = context.openOrCreateDatabase("migration.db", 0, null)) {
            old.execSQL("CREATE TABLE records (_id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT)");
            old.execSQL("INSERT INTO records (_id, name) VALUES (37, 'existing')");
            old.setVersion(1);
        }
        Record row = new Model.Query<>(Record.class, context).first();
        assertEquals(37, row.get_Id());
        assertEquals("existing", row.name);
        assertEquals(0, row.count);
        assertNull(row.description);
        row.count = 9;
        row.description = "migrated";
        row.save();
        assertEquals("migrated", new Model.Query<>(Record.class, context).where("new_count", 9).first().description);
    }
}
