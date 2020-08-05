package com.google.android.apps.nexuslauncher.search;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import com.android.launcher3.ItemInfo;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.userevent.nano.LauncherLogProto;

class LogContainerProvider extends FrameLayout implements UserEventDispatcher.LogContainerProvider {
    private final int mPredictedRank;

    public LogContainerProvider(final Context context, final int predictedRank) {
        super(context);
        mPredictedRank = predictedRank;
    }

    @Override
    public void fillInLogContainerData(final View v, final ItemInfo info, final LauncherLogProto.Target target, final LauncherLogProto.Target targetParent) {
        if (mPredictedRank >= 0) {
            targetParent.containerType = 7;
            target.predictedRank = mPredictedRank;
        } else {
            targetParent.containerType = 8;
        }
    }
}
