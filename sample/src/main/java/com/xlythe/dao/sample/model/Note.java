package com.xlythe.dao.sample.model;

import android.content.Context;
import android.database.CursorWrapper;

import com.xlythe.dao.Database;
import com.xlythe.dao.Model;
import com.xlythe.dao.Param;

@Database(version=1, retainDataOnUpgrade=false)
public class Note extends Model<Note> {
    public static void registerObserver(Observer observer) {
        registerObserver(Note.class, observer);
    }

    public static void unregisterObserver(Observer observer) {
        unregisterObserver(Note.class, observer);
    }

    public static class Cursor extends CursorWrapper {
        private final Context mContext;

        private Cursor(Context context, android.database.Cursor cursor) {
            super(cursor);
            mContext = context;
        }

        public Note getNote() {
            return new Note(mContext, this);
        }
    }

    public static class Query extends Model.Query<Note> {
        public Query(Context context) {
            super(Note.class, context);
        }

        public Note.Query title(String title) {
            where(new Param("title", title));
            return this;
        }

        public Note.Query body(String body) {
            where(new Param("body", body));
            return this;
        }

        public Note.Query timestamp(long timestamp) {
            where(new Param("timestamp", timestamp));
            return this;
        }

        public Note.Query orderByTimestamp() {
            orderBy("timestamp DESC");
            return this;
        }

        public Note.Cursor cursor() {
            return new Note.Cursor(getContext(), super.cursor());
        }
    }

    // The note's title
    private String title;
    // The note's content
    private String body;
    // The timestamp of the last update to the note
    private long timestamp;

    public Note(Context context) {
        super(context);
    }

    public Note(Context context, android.database.Cursor cursor) {
        super(context, cursor);
    }

    @Override
    public void save() {
        timestamp = System.currentTimeMillis();
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

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "[" + title + "]" + body;
    }
}
