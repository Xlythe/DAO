package com.xlythe.dao.sample;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.xlythe.dao.Model;
import com.xlythe.dao.sample.model.Note;

public class MainActivity extends AppCompatActivity implements Model.Observer {

    private NoteAdapter mNoteAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        RecyclerView list = (RecyclerView) findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setItemAnimator(new DefaultItemAnimator());

        mNoteAdapter = new NoteAdapter(this);
        list.setAdapter(mNoteAdapter);
        mNoteAdapter.setOnClickListener(new NoteAdapter.OnClickListener() {
            @Override
            public void onItemClick(Note note) {
                editNote(note);
            }
        });

        Note.registerObserver(this);
        invalidateCursor();
    }

    public void createNote(View view) {
        Intent intent = new Intent(this, DetailActivity.class);
        ActivityOptionsCompat options = ActivityOptionsCompat.
                makeSceneTransitionAnimation(this, view, "fab");
        startActivity(intent, options.toBundle());
    }

    public void editNote(Note note) {
        Intent intent = new Intent(this, DetailActivity.class);
        intent.putExtra(DetailActivity.EXTRA_NOTE, note);
        ActivityOptionsCompat options = ActivityOptionsCompat.
                makeSceneTransitionAnimation(this, findViewById(R.id.fab), "fab");
        startActivity(intent, options.toBundle());
    }

    private void invalidateCursor() {
        mNoteAdapter.setCursor(new Note.Query(this).orderByTimestamp().cursor());
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
        public static class ViewHolder extends RecyclerView.ViewHolder {
            private final View mRoot;
            private final TextView mTitleView;
            private final TextView mBodyView;

            private Note mNote;

            public ViewHolder(View view) {
                super(view);
                mRoot = view;
                mTitleView = (TextView) view.findViewById(R.id.title);
                mBodyView = (TextView) view.findViewById(R.id.body);
            }

            public void setNote(Note note) {
                mNote = note;
                mTitleView.setText(note.getTitle());
                mBodyView.setText(note.getBody());
            }

            public Note getNote() {
                return mNote;
            }

            public void setOnClickListener(final OnClickListener listener) {
                if (listener == null) {
                    mRoot.setOnClickListener(null);
                    return;
                }

                mRoot.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        listener.onItemClick(getNote());
                    }
                });
            }
        }

        public interface OnClickListener {
            void onItemClick(Note note);
        }

        private final Context mContext;
        private Note.Cursor mCursor;
        private OnClickListener mOnClickListener;

        public NoteAdapter(Context context) {
            mContext = context;
        }

        public void setCursor(Note.Cursor cursor) {
            if (mCursor != null) {
                mCursor.close();
            }

            mCursor = cursor;
            notifyDataSetChanged();
        }

        public void setOnClickListener(OnClickListener listener) {
            mOnClickListener = listener;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new ViewHolder(LayoutInflater.from(mContext).inflate(R.layout.list_item_note, parent, false));
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            mCursor.moveToPosition(position);
            holder.setNote(mCursor.getNote());
            holder.setOnClickListener(mOnClickListener);
        }

        @Override
        public int getItemCount() {
            return mCursor.getCount();
        }

        public void close() {
            mCursor.close();
        }
    }
}