package com.google.android.apps.nexuslauncher.graphics;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.widget.TextView;

import com.android.launcher3.views.DoubleShadowBubbleTextView;

public class DoubleShadowTextView extends TextView {
    private final DoubleShadowBubbleTextView.ShadowInfo mShadowInfo;

    public DoubleShadowTextView(final Context context) {
        this(context, null);
    }

    public DoubleShadowTextView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DoubleShadowTextView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mShadowInfo = new DoubleShadowBubbleTextView.ShadowInfo(context, attrs, defStyleAttr);
        setShadowLayer(Math.max(mShadowInfo.keyShadowBlur + mShadowInfo.keyShadowOffset, mShadowInfo.ambientShadowBlur), 0f, 0f, mShadowInfo.keyShadowColor);
    }

    protected void onDraw(final Canvas canvas) {
        if (mShadowInfo.skipDoubleShadow(this)) {
            super.onDraw(canvas);
            return;
        }
        getPaint().setShadowLayer(mShadowInfo.ambientShadowBlur, 0.0f, 0.0f, mShadowInfo.ambientShadowColor);
        super.onDraw(canvas);
        getPaint().setShadowLayer(mShadowInfo.keyShadowBlur, 0.0f, mShadowInfo.keyShadowOffset, mShadowInfo.keyShadowColor);
        super.onDraw(canvas);
    }

}
