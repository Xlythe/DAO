package com.xlythe.dao.sample;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.EditText;

import com.xlythe.dao.sample.model.Note;

public class DetailActivity extends AppCompatActivity {
    public static final String EXTRA_NOTE = "note";

    private Note mNote;

    private EditText mTitleView;
    private EditText mBodyView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_detail);

        mTitleView = (EditText) findViewById(R.id.title);
        mBodyView = (EditText) findViewById(R.id.body);

        if (getIntent().hasExtra(EXTRA_NOTE)) {
            mNote = (Note) getIntent().getSerializableExtra(EXTRA_NOTE);
            mNote.setContext(this);

            mTitleView.setText(mNote.getTitle());
            mBodyView.setText(mNote.getBody());
        }
    }

    public void save(View view) {
        if (mNote != null) {
            mNote.setTitle(mTitleView.getText().toString());
            mNote.setBody(mBodyView.getText().toString());
            mNote.save();
        } else {
            new Note.Query(this)
                    .title(mTitleView.getText().toString())
                    .body(mBodyView.getText().toString())
                    .insert();
        }
        supportFinishAfterTransition();
    }

}
