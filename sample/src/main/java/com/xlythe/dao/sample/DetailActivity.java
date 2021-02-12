package com.xlythe.dao.sample;

import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.xlythe.dao.sample.model.Folder;
import com.xlythe.dao.sample.model.Note;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

public class DetailActivity extends AppCompatActivity {
    private static final String TAG = DetailActivity.class.getSimpleName();
    public static final String EXTRA_NOTE = "note";

    private Note mNote;

    private EditText mTitleView;
    private EditText mBodyView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        mTitleView = findViewById(R.id.title);
        mBodyView = findViewById(R.id.body);

        if (getIntent().hasExtra(EXTRA_NOTE)) {
            mNote = (Note) getIntent().getSerializableExtra(EXTRA_NOTE);
            mNote.setContext(this);

            mTitleView.setText(mNote.getTitle());
            mBodyView.setText(mNote.getBody());
        }
    }

    public void save(View view) {
        if (TextUtils.isEmpty(getNoteTitle())) {
            Log.w(TAG, "Ignoring attempt to set empty note title");
            Toast.makeText(this, R.string.warning_set_title, Toast.LENGTH_SHORT).show();
            return;
        }

        if (mNote != null) {
            mNote.setTitle(getNoteTitle());
            mNote.setBody(getNoteBody());
            mNote.save();
        } else {
            new Note.Query(this)
                    .title(getNoteTitle())
                    .body(getNoteBody())
                    .timestamp(System.currentTimeMillis())
                    .insert();
            new Folder.Query(this).name(getNoteTitle()).insert();
        }
        supportFinishAfterTransition();
    }

    private String getNoteTitle() {
        return mTitleView.getText().toString();
    }

    private String getNoteBody() {
        return mBodyView.getText().toString();
    }

}
