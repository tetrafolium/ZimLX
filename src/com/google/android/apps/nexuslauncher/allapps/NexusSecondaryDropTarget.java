package com.google.android.apps.nexuslauncher.allapps;

import android.content.ComponentName;
import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.R;
import com.android.launcher3.SecondaryDropTarget;
import com.android.launcher3.logging.LoggerUtils;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;

public class NexusSecondaryDropTarget extends SecondaryDropTarget {

public static final int DISMISS = R.id.action_dismiss_suggestion;

public NexusSecondaryDropTarget(final Context context,
                                final AttributeSet attrs) {
	super(context, attrs);
}

public NexusSecondaryDropTarget(final Context context,
                                final AttributeSet attrs,
                                final int defStyle) {
	super(context, attrs, defStyle);
}

@Override
protected void setupUi(final int action) {
	if (action == mCurrentAccessibilityAction) {
		super.setupUi(action);
		return;
	}

	if (action == DISMISS) {
		mCurrentAccessibilityAction = action;
		mHoverColor = getResources().getColor(R.color.dismiss_target_hover_tint);
		setDrawable(R.drawable.ic_dismiss_no_shadow);
		updateText(R.string.dismiss_drop_target_label);
	} else {
		super.setupUi(action);
	}
}

@Override
public Target getDropTargetForLogging() {
	Target newTarget = LoggerUtils.newTarget(2);
	newTarget.controlType = 5;
	return newTarget;
}

@Override
public boolean supportsAccessibilityDrop(final ItemInfo info,
                                         final View view) {
	setupUi(R.id.action_dismiss_suggestion);
	return true;
}

@Override
protected ComponentName performDropAction(final View view,
                                          final ItemInfo info) {
	if (this.mCurrentAccessibilityAction != DISMISS) {
		return super.performDropAction(view, info);
	}
	return null;
}
}
