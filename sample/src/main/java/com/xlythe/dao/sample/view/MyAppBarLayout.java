package com.xlythe.dao.sample.view;

import android.content.Context;
import android.support.design.widget.AppBarLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.List;

public class MyAppBarLayout extends AppBarLayout {
    public MyAppBarLayout(Context context) {
        super(context);
    }

    public MyAppBarLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Overrides ViewGroup's hidden method, to allow both children and ViewGroups to be
     * animated simultaneously.
     */
    public void captureTransitioningViews(List<View> transitioningViews) {
        if (getVisibility() != View.VISIBLE) {
            return;
        }
        transitioningViews.add(this);
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            transitioningViews.add(child);
        }
    }
}
