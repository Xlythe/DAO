package com.xlythe.dao.sample;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.xlythe.dao.Model;
import com.xlythe.dao.sample.model.Note;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity implements Model.Observer {

    private NoteAdapter mNoteAdapter;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));

        mNoteAdapter = new NoteAdapter(this);
        list.setAdapter(mNoteAdapter);
        mNoteAdapter.setOnClickListener(new NoteAdapter.OnClickListener() {
            @Override
            public void onItemClick(Note note) {
                editNote(note);
            }

            @Override
            public void onItemLongClick(final Note note) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(note.getTitle())
                        .setMessage(note.getBody())
                        .setPositiveButton(R.string.action_delete, (dialog, which) -> note.delete())
                        .show();
            }
        });

        Note.registerObserver(this);
        invalidateCursor();
    }

    public void createNote(View view) {
        Intent intent = new Intent(this, DetailActivity.class);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                Pair.create(view, "fab"),
                Pair.create(findViewById(R.id.app_bar), "title"));
        startActivity(intent, options.toBundle());
    }

    public void editNote(Note note) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_NOTE, note);
        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(
                this,
                Pair.create(findViewById(R.id.fab), "fab"),
                Pair.create(findViewById(R.id.app_bar), "title"));
        startActivity(intent, options.toBundle());
    }

    private void invalidateCursor() {
        mNoteAdapter.setCursor(new Note.Query(this).orderByTimestamp().cursor());
        findViewById(R.id.empty_view).setVisibility(mNoteAdapter.getCursor().getCount() > 0 ? View.GONE : View.VISIBLE);
    }

    @Override
    public void onChange() {
        invalidateCursor();
    }

    @Override
    protected void onDestroy() {
        mNoteAdapter.close();
        super.onDestroy();
    }

    public static class NoteAdapter extends RecyclerView.Adapter<NoteAdapter.ViewHolder> {
        static class ViewHolder extends RecyclerView.ViewHolder {
            private final View mRoot;
            private final TextView mTitleView;
            private final TextView mBodyView;

            private Note mNote;

            ViewHolder(View view) {
                super(view);
                mRoot = view;
                mTitleView = view.findViewById(R.id.title);
                mBodyView = view.findViewById(R.id.body);
            }

            void setNote(Note note) {
                mNote = note;
                mTitleView.setText(note.getTitle());
                mBodyView.setText(note.getBody());
            }

            Note getNote() {
                return mNote;
            }

            void setOnClickListener(final OnClickListener listener) {
                if (listener == null) {
                    mRoot.setOnClickListener(null);
                    return;
                }

                mRoot.setOnClickListener(v -> listener.onItemClick(getNote()));
                mRoot.setOnLongClickListener(v -> {
                    listener.onItemLongClick(getNote());
                    return true;
                });
            }
        }

        public interface OnClickListener {
            void onItemClick(Note note);
            void onItemLongClick(Note note);
        }

        private final Context mContext;
        private Note.Cursor mCursor;
        private OnClickListener mOnClickListener;

        NoteAdapter(Context context) {
            mContext = context;
        }

        @SuppressLint("NotifyDataSetChanged")
        void setCursor(Note.Cursor cursor) {
            if (mCursor != null) {
                mCursor.close();
            }

            mCursor = cursor;
            notifyDataSetChanged();
        }

        Note.Cursor getCursor() {
            return mCursor;
        }

        void setOnClickListener(OnClickListener listener) {
            mOnClickListener = listener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.list_item_note, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.setNote(mCursor.getNote());
            holder.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }

        void close() {
            mCursor.close();
        }
    }
}
