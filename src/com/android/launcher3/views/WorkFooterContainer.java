/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.views;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * Container to show work footer in all-apps.
 */
public class WorkFooterContainer extends RelativeLayout {

    public WorkFooterContainer(final Context context) {
        super(context);
    }

    public WorkFooterContainer(final Context context, final AttributeSet attrs) {
        super(context, attrs);
    }

    public WorkFooterContainer(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(final boolean changed, final int l, final int t, final int r, final int b) {
        super.onLayout(changed, l, t, r, b);
        updateTranslation();
    }

    @Override
    public void offsetTopAndBottom(final int offset) {
        super.offsetTopAndBottom(offset);
        updateTranslation();
    }

    private void updateTranslation() {
        if (getParent() instanceof View) {
            View parent = (View) getParent();
            int availableBot = parent.getHeight() - parent.getPaddingBottom();
            setTranslationY(Math.max(0, availableBot - getBottom()));
        }
    }
}
