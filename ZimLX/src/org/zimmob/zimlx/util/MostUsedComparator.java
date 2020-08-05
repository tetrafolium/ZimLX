package org.zimmob.zimlx.util;

import com.android.launcher3.AppInfo;
import java.util.Comparator;
import java.util.List;
import org.zimmob.zimlx.model.AppCountInfo;

public class MostUsedComparator implements Comparator<AppInfo> {
private String TAG = "MostUsedComparator";
private List<AppCountInfo> mApps;

public MostUsedComparator(final List<AppCountInfo> apps) {
	mApps = apps;
}

@Override
public int compare(final AppInfo app1, final AppInfo app2) {
	int item1 = 0;
	int item2 = 0;

	for (int i = 0; i < mApps.size(); i++) {
		if (mApps.get(i).getPackageName().equals(
			    app1.componentName.getPackageName())) {
			item1 = mApps.get(i).getCount();
		}
		if (mApps.get(i).getPackageName().equals(
			    app2.componentName.getPackageName())) {
			item2 = mApps.get(i).getCount();
		}
	}

	if (item1 < item2) {
		return 1;
	} else if (item2 < item1) {
		return -1;
	} else {
		return 0;
	}
}
}
