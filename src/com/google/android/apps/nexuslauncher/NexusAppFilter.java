package com.google.android.apps.nexuslauncher;

import android.content.ComponentName;
import android.content.Context;
import android.os.UserHandle;
import com.android.launcher3.AppFilter;
import java.util.HashSet;

public class NexusAppFilter extends AppFilter {
private final HashSet<ComponentName> mHideList = new HashSet<>();

public NexusAppFilter(final Context context) {
	// Voice Search
	mHideList.add(ComponentName.unflattenFromString(
			      "com.google.android.googlequicksearchbox/.VoiceSearchActivity"));

	// Wallpapers
	mHideList.add(ComponentName.unflattenFromString(
			      "com.google.android.apps.wallpaper/.picker.CategoryPickerActivity"));

	// Google Now Launcher
	mHideList.add(ComponentName.unflattenFromString(
			      "com.google.android.launcher/.StubApp"));

	// Actions Services
	mHideList.add(ComponentName.unflattenFromString(
			      "com.google.android.as/com.google.android.apps.miphone.aiai.allapps.main.MainDummyActivity"));
}

@Override
public boolean shouldShowApp(final ComponentName componentName,
                             final UserHandle user) {
	return !mHideList.contains(componentName);
}
}
