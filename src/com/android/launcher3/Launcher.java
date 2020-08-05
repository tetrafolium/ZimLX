/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.launcher3;

import static android.content.pm.ActivityInfo.CONFIG_ORIENTATION;
import static android.content.pm.ActivityInfo.CONFIG_SCREEN_SIZE;
import static com.android.launcher3.LauncherAnimUtils.SPRING_LOADED_EXIT_DELAY;
import static com.android.launcher3.LauncherState.ALL_APPS;
import static com.android.launcher3.LauncherState.NORMAL;
import static com.android.launcher3.dragndrop.DragLayer.ALPHA_INDEX_LAUNCHER_LOAD;
import static com.android.launcher3.logging.LoggerUtils.newContainerTarget;
import static com.android.launcher3.logging.LoggerUtils.newTarget;
import static com.android.launcher3.logging.UserEventDispatcher.UserEventDelegate;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.TargetApi;
import android.app.ActivityOptions;
import android.appwidget.AppWidgetHostView;
import android.appwidget.AppWidgetManager;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks2;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.os.Process;
import android.os.StrictMode;
import android.os.SystemClock;
import android.os.UserHandle;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.method.TextKeyListener;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.KeyEvent;
import android.view.KeyboardShortcutGroup;
import android.view.KeyboardShortcutInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.InputMethodManager;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.drawerlayout.widget.DrawerLayout;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.accessibility.LauncherAccessibilityDelegate;
import com.android.launcher3.allapps.AllAppsContainerView;
import com.android.launcher3.allapps.AllAppsTransitionController;
import com.android.launcher3.allapps.DiscoveryBounce;
import com.android.launcher3.badge.BadgeInfo;
import com.android.launcher3.compat.AppWidgetManagerCompat;
import com.android.launcher3.compat.LauncherAppsCompatVO;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.dragndrop.DragController;
import com.android.launcher3.dragndrop.DragLayer;
import com.android.launcher3.dragndrop.DragView;
import com.android.launcher3.dynamicui.WallpaperColorInfo;
import com.android.launcher3.folder.FolderIcon;
import com.android.launcher3.folder.FolderIconPreviewVerifier;
import com.android.launcher3.keyboard.CustomActionsPopup;
import com.android.launcher3.keyboard.ViewGroupFocusHelper;
import com.android.launcher3.logging.FileLog;
import com.android.launcher3.logging.UserEventDispatcher;
import com.android.launcher3.model.ModelWriter;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.popup.PopupContainerWithArrow;
import com.android.launcher3.popup.PopupDataProvider;
import com.android.launcher3.shortcuts.DeepShortcutManager;
import com.android.launcher3.states.InternalStateHandler;
import com.android.launcher3.states.RotationHelper;
import com.android.launcher3.touch.ItemClickHandler;
import com.android.launcher3.uioverrides.UiFactory;
import com.android.launcher3.userevent.nano.LauncherLogProto;
import com.android.launcher3.userevent.nano.LauncherLogProto.Action;
import com.android.launcher3.userevent.nano.LauncherLogProto.ContainerType;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ActivityResultInfo;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ComponentKeyMapper;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.MultiHashMap;
import com.android.launcher3.util.MultiValueAlpha;
import com.android.launcher3.util.PackageManagerHelper;
import com.android.launcher3.util.PackageUserKey;
import com.android.launcher3.util.PendingRequestArgs;
import com.android.launcher3.util.RunnableWithId;
import com.android.launcher3.util.SystemUiController;
import com.android.launcher3.util.TestingUtils;
import com.android.launcher3.util.Themes;
import com.android.launcher3.util.Thunk;
import com.android.launcher3.util.TraceHelper;
import com.android.launcher3.util.UiThreadHelper;
import com.android.launcher3.util.ViewOnDrawExecutor;
import com.android.launcher3.views.OptionsPopupView;
import com.android.launcher3.widget.LauncherAppWidgetHostView;
import com.android.launcher3.widget.PendingAddShortcutInfo;
import com.android.launcher3.widget.PendingAddWidgetInfo;
import com.android.launcher3.widget.PendingAppWidgetHostView;
import com.android.launcher3.widget.WidgetAddFlowHandler;
import com.android.launcher3.widget.WidgetHostViewLoader;
import com.android.launcher3.widget.WidgetListRowEntry;
import com.android.launcher3.widget.WidgetsFullSheet;
import com.android.launcher3.widget.custom.CustomWidgetParser;
import com.google.android.apps.nexuslauncher.CustomAppPredictor;
import com.google.android.apps.nexuslauncher.NexusLauncherActivity;
import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.zimmob.zimlx.ZimLauncher;
import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.blur.BlurWallpaperProvider;

/**
 * Default launcher application.
 */
public class Launcher extends BaseDraggingActivity
	implements LauncherExterns, LauncherModel.Callbacks,
	                      LauncherProviderChangeListener, UserEventDelegate,
	                      WallpaperColorInfo.OnThemeChangeListener {
public static final String TAG = "Launcher";
static final boolean LOGD = false;

static final boolean DEBUG_STRICT_MODE = false;

private static final int REQUEST_CREATE_SHORTCUT = 1;
private static final int REQUEST_CREATE_APPWIDGET = 5;

private static final int REQUEST_PICK_APPWIDGET = 9;

private static final int REQUEST_BIND_APPWIDGET = 11;
public static final int REQUEST_BIND_PENDING_APPWIDGET = 12;
public static final int REQUEST_RECONFIGURE_APPWIDGET = 13;

private static final int REQUEST_PERMISSION_CALL_PHONE = 14;

private static final float BOUNCE_ANIMATION_TENSION = 1.3f;

// Type: int
private static final String RUNTIME_STATE_CURRENT_SCREEN =
	"launcher.current_screen";
// Type: int
private static final String RUNTIME_STATE = "launcher.state";
// Type: PendingRequestArgs
private static final String RUNTIME_STATE_PENDING_REQUEST_ARGS =
	"launcher.request_args";
// Type: ActivityResultInfo
private static final String RUNTIME_STATE_PENDING_ACTIVITY_RESULT =
	"launcher.activity_result";
// Type: SparseArray<Parcelable>
private static final String RUNTIME_STATE_WIDGET_PANEL =
	"launcher.widget_panel";

public LauncherStateManager mStateManager;

private static final int ON_ACTIVITY_RESULT_ANIMATION_DELAY = 500;

// How long to wait before the new-shortcut animation automatically pans the
// workspace
private static final int NEW_APPS_PAGE_MOVE_DELAY = 500;
private static final int NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS = 5;
@Thunk static final int NEW_APPS_ANIMATION_DELAY = 500;
public static final int EXIT_SPRINGLOADED_MODE_SHORT_TIMEOUT = 500;
/**
 * IntentStarter uses request codes starting with this. This must be greater
 * than all activity request codes used internally.
 */
protected static final HashMap<String, CustomAppWidget> sCustomAppWidgets =
	new HashMap<>();
static final boolean DEBUG_WIDGETS = false;

// Determines how long to wait after a rotation before restoring the screen
// orientation to match the sensor state.
private static final int RESTORE_SCREEN_ORIENTATION_DELAY = 500;
public static Context mContext;

static {
	if (TestingUtils.ENABLE_CUSTOM_WIDGET_TEST) {
		TestingUtils.addDummyWidget(sCustomAppWidgets);
	}
}

private final int[] mTmpAddItemCellCoordinates = new int[2];
private final ArrayList<Runnable> mBindOnResumeCallbacks = new ArrayList<>();
private final ArrayList<Integer> mSynchronouslyBoundPages = new ArrayList<>();
public View mWeightWatcher;
public ViewGroupFocusHelper mFocusHandler;

private LauncherAppTransitionManager mAppTransitionManager;
private Configuration mOldConfig;

@Thunk public Workspace mWorkspace;
@Thunk DragLayer mDragLayer;
@Thunk Hotseat mHotseat;
@Nullable private View mHotseatSearchBox;
// Main container view for the all apps screen.
@Thunk public AllAppsContainerView mAppsView;
public AllAppsTransitionController mAllAppsController;
// Main container view and the model for the widget tray screen.

@Thunk boolean mWorkspaceLoading = true;
private OnResumeCallback mOnResumeCallback;

private View mLauncherView;
private DragController mDragController;
private View mQsbContainer;
private AppWidgetManagerCompat mAppWidgetManager;
private LauncherAppWidgetHost mAppWidgetHost;
// UI and state for the overview panel
private View mOverviewPanel;

private DropTargetBar mDropTargetBar;
private SpannableStringBuilder mDefaultKeySsb = null;
private boolean mPaused = true;
private ViewOnDrawExecutor mPendingExecutor;
private LauncherModel mModel;
private ModelWriter mModelWriter;
private IconCache mIconCache;
private LauncherAccessibilityDelegate mAccessibilityDelegate;
private PopupDataProvider mPopupDataProvider;
// We only want to get the SharedPreferences once since it does an FS stat
// each time we get it from the context.
private SharedPreferences mSharedPrefs;
private ActivityResultInfo mPendingActivityResult;
/**
 * Holds extra information required to handle a result from an external call,
 * like
 * {@link #startActivityForResult(Intent, int)} or {@link
 * #requestPermissions(String[], int)}
 */
private PendingRequestArgs mPendingRequestArgs;

private boolean mRotationEnabled = false;
private LauncherCallbacks mLauncherCallbacks;
public static boolean showNotificationCount;

private RotationHelper mRotationHelper;

private final Handler mHandler = new Handler();
private final Runnable mLogOnDelayedResume = this::logOnDelayedResume;

private BlurWallpaperProvider mBlurWallpaperProvider;

public Runnable mUpdatePredictionsIfResumed = ()->updatePredictions(false);

@Override
protected void onCreate(final Bundle savedInstanceState) {
	if (DEBUG_STRICT_MODE) {
		StrictMode.setThreadPolicy(
			new StrictMode.ThreadPolicy
			.Builder()
			//.detectDiskReads()
			//.detectDiskWrites()
			.detectNetwork() // or .detectAll() for all detectable problems
			//.penaltyLog()
			.build());
		StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
		                       .detectLeakedSqlLiteObjects()
		                       .detectLeakedClosableObjects()
		                       .detectActivityLeaks()
		                       //.penaltyLog()
		                       //.penaltyDeath()
		                       .build());
	}
	TraceHelper.beginSection("Launcher-onCreate");

	super.onCreate(savedInstanceState);
	TraceHelper.partitionSection("Launcher-onCreate", "super call");
	mContext = this;

	WallpaperColorInfo wallpaperColorInfo =
		WallpaperColorInfo.getInstance(this);
	wallpaperColorInfo.setOnThemeChangeListener(this);
	overrideTheme(wallpaperColorInfo.isDark(),
	              wallpaperColorInfo.supportsDarkText());

	LauncherAppState app = LauncherAppState.getInstance(this);
	ZimPreferences prefs = Utilities.getZimPrefs(this);
	prefs.getGridSize();
	prefs.getDockGridSize();
	prefs.getDrawerGridSize();
	mOldConfig = new Configuration(getResources().getConfiguration());
	mModel = app.setLauncher(this);
	initDeviceProfile(app.getInvariantDeviceProfile());

	showNotificationCount = prefs.getFolderBadgeCount();
	mSharedPrefs = Utilities.getPrefs(this);
	mIconCache = app.getIconCache();
	mAccessibilityDelegate = new LauncherAccessibilityDelegate(this);

	mDragController = new DragController(this);
	mBlurWallpaperProvider = new BlurWallpaperProvider(this);
	mAllAppsController = new AllAppsTransitionController(this);
	mStateManager = new LauncherStateManager(this);
	UiFactory.onCreate(this);

	mAppWidgetManager = AppWidgetManagerCompat.getInstance(this);

	mAppWidgetHost = new LauncherAppWidgetHost(this);
	mAppWidgetHost.startListening();

	mLauncherView = LayoutInflater.from(this).inflate(R.layout.launcher, null);

	setupViews();

	mPopupDataProvider = new PopupDataProvider(this);

	mRotationHelper = new RotationHelper(this);
	mAppTransitionManager = LauncherAppTransitionManager.newInstance(this);

	boolean internalStateHandled =
		InternalStateHandler.handleCreate(this, getIntent());
	if ((internalStateHandled) && (savedInstanceState != null)) {
		// InternalStateHandler has already set the appropriate state.
		// We dont need to do anything.
		savedInstanceState.remove(RUNTIME_STATE);
	}
	restoreState(savedInstanceState);

	// We only load the page synchronously if the user rotates (or triggers a
	// configuration change) while launcher is in the foreground
	int currentScreen = PagedView.INVALID_RESTORE_PAGE;
	if (savedInstanceState != null) {
		currentScreen = savedInstanceState.getInt(RUNTIME_STATE_CURRENT_SCREEN,
		                                          currentScreen);
	}

	if (!mModel.startLoader(currentScreen)) {
		if (!internalStateHandled) {
			// If we are not binding synchronously, show a fade in animation when
			// the first page bind completes.
			mDragLayer.getAlphaProperty(ALPHA_INDEX_LAUNCHER_LOAD).setValue(0);
		}
	} else {
		// Pages bound synchronously.
		mWorkspace.setCurrentPage(currentScreen);

		setWorkspaceLoading(true);
	}

	// For handling default keys
	setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

	setContentView(mLauncherView);
	getRootView().dispatchInsets();

	// Listen for broadcasts
	registerReceiver(mScreenOffReceiver,
	                 new IntentFilter(Intent.ACTION_SCREEN_OFF));

	getSystemUiController().updateUiState(
		SystemUiController.UI_STATE_BASE_WINDOW,
		Themes.getAttrBoolean(this, R.attr.isWorkspaceDarkText));

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onCreate(savedInstanceState);
	}
	mRotationHelper.initialize();
	// initMinibar();
	TraceHelper.endSection("Launcher-onCreate");
	Utilities.checkRestoreSuccess(this);

	if (prefs.getShowPredictions()) {
		updatePredictions(true);
	}
}

public void updatePredictions(final boolean force) {
	if (hasBeenResumed() || force) {
		List<ComponentKeyMapper> apps =
			((CustomAppPredictor)getUserEventDispatcher()).getPredictions();
		if (apps != null) {
			mAppsView.getFloatingHeaderView().setPredictedApps(
				Utilities.getZimPrefs(this).getShowPredictions(), apps);
			Log.e(TAG, "Predictions Size " + apps.size());
		}
	}
}

@Override
public void onDestroy() {
	super.onDestroy();

	unregisterReceiver(mScreenOffReceiver);
	mWorkspace.removeFolderListeners();

	UiFactory.setOnTouchControllersChangedListener(this, null);

	// Stop callbacks from LauncherModel
	// It's possible to receive onDestroy after a new Launcher activity has
	// been created. In this case, don't interfere with the new Launcher.
	if (mModel.isCurrentCallbacks(this)) {
		mModel.stopLoader();
		LauncherAppState.getInstance(this).setLauncher(null);
	}
	mRotationHelper.destroy();

	try {
		mAppWidgetHost.stopListening();
	} catch (NullPointerException ex) {
		Log.w(TAG,
		      "problem while stopping AppWidgetHost during Launcher destruction",
		      ex);
	}

	TextKeyListener.getInstance().release();

	LauncherAnimUtils.onDestroyActivity();

	clearPendingBinds();

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onDestroy();
	}
}

/*public void initMinibar() {
    ZimPreferences prefs = Utilities.getZimPrefs(this);
    ArrayList<DashItem> dashItems = new ArrayList<>();
    for (String action : prefs.getMinibarItems()) {
        //if (action.length() > 1) {
        Log.d("Dash APP", "Loading Action length: " + action.length());
        DashItem item = null;
        if (action.length() == 2) {
            item = DashUtils.getDashItemFromString(action);
        } else {
            ComponentKey keyMapper = new ComponentKey(this, action);

            AppInfo app =
   mAllAppsController.getAppsView().getAppsStore().getApp(keyMapper);

            Log.d("Dash APP", "Loading App " + action);
            if (app != null) {
                item = DashItem.asApp(app, 0);
            }
        }

        if (item != null) {
            dashItems.add(item);
        }
        //}
    }

    SwipeListView minibar = findViewById(R.id.minibar);
    minibar.setAdapter(new DashAdapter(this, dashItems));


    minibar.setOnItemClickListener((parent, view, i, id) -> {
        DashAction.Action action =
   DashAction.Action.valueOf(dashItems.get(i).action.name());
        DashUtils.RunAction(action, this);
        if (action != DashAction.Action.DeviceSettings && action !=
   DashAction.Action.LauncherSettings && action != DashAction.Action.EditMinibar)
   {
            ((DrawerLayout) findViewById(R.id.drawer_layout)).closeDrawers();
        }
    });
    // frame layout spans the entire side while the minibar container has gaps
   at the top and bottom
    ((FrameLayout)
   minibar.getParent()).setBackgroundColor(prefs.getMinibarColor());
   }*/

public static Launcher getLauncher(final Context context) {
	if (context instanceof Launcher) {
		return (Launcher)context;
	}
	return ((Launcher)((ContextWrapper)context).getBaseContext());
}

protected void overrideTheme(final boolean isDark,
                             final boolean supportsDarkText) {
	if (isDark) {
		setTheme(R.style.LauncherTheme_Dark);
	} else if (supportsDarkText) {
		setTheme(R.style.LauncherTheme_DarkText);
	}
}

@Override
public void onEnterAnimationComplete() {
	super.onEnterAnimationComplete();
	UiFactory.onEnterAnimationComplete(this);
}

@Override
public void onConfigurationChanged(final Configuration newConfig) {
	int diff = newConfig.diff(mOldConfig);
	if ((diff & (CONFIG_ORIENTATION | CONFIG_SCREEN_SIZE)) != 0) {
		mUserEventDispatcher = null;
		initDeviceProfile(mDeviceProfile.inv);
		dispatchDeviceProfileChanged();
		reapplyUi();
		mDragLayer.recreateControllers();

		// TODO: We can probably avoid rebind when only screen size changed.
		rebindModel();
	}

	mOldConfig.setTo(newConfig);
	UiFactory.onLauncherStateOrResumeChanged(this);
	super.onConfigurationChanged(newConfig);
}

@Override
public void reapplyUi() {
	getRootView().dispatchInsets();
	getStateManager().reapplyState(true /* cancelCurrentAnimation */);
}

@Override
public void rebindModel() {
	int currentPage = mWorkspace.getNextPage();
	if (mModel.startLoader(currentPage)) {
		mWorkspace.setCurrentPage(currentPage);
		setWorkspaceLoading(true);
	}
}

private void initDeviceProfile(final InvariantDeviceProfile idp) {
	// Load configuration-specific DeviceProfile
	mDeviceProfile = idp.getDeviceProfile(this);
	if (isInMultiWindowModeCompat()) {
		Display display = getWindowManager().getDefaultDisplay();
		Point mwSize = new Point();
		display.getSize(mwSize);
		mDeviceProfile = mDeviceProfile.getMultiWindowProfile(this, mwSize);
	}
	onDeviceProfileInitiated();
	mModelWriter = mModel.getWriter(mDeviceProfile.isVerticalBarLayout(), true);
}

public RotationHelper getRotationHelper() {
	return mRotationHelper;
}

public LauncherStateManager getStateManager() {
	return mStateManager;
}

@Override
public <T extends View> T findViewById(final int id) {
	return mLauncherView.findViewById(id);
}

@Override
public void onAppWidgetHostReset() {
	if (mAppWidgetHost != null) {
		mAppWidgetHost.startListening();
	}
}

@NonNull
public final DrawerLayout getDrawerLayout() {
	return findViewById(R.id.drawer_layout);
}

/**
 * Call this after onCreate to set or clear overlay.
 */
public void setLauncherOverlay(final LauncherOverlay overlay) {
	if (overlay != null) {
		overlay.setOverlayCallbacks(new LauncherOverlayCallbacksImpl());
	}
	mWorkspace.setLauncherOverlay(overlay);
}

public boolean setLauncherCallbacks(final LauncherCallbacks callbacks) {
	mLauncherCallbacks = callbacks;
	return true;
}

@Override
public void onLauncherProviderChanged() {
	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onLauncherProviderChange();
	}
}

public boolean isDraggingEnabled() {
	// We prevent dragging when we are loading the workspace as it is possible
	// to pick up a view that is subsequently removed from the workspace in
	// startBinding().
	return !isWorkspaceLoading();
}

public int getViewIdForItem(final ItemInfo info) {
	// aapt-generated IDs have the high byte nonzero; clamp to the range under
	// that. This cast is safe as long as the id < 0x00FFFFFF Since we jail all
	// the dynamically generated views, there should be no clashes with any
	// other views.
	return (int)info.id;
}

public PopupDataProvider getPopupDataProvider() {
	return mPopupDataProvider;
}

public void onInsetsChanged(final Rect insets) {
	mDeviceProfile.updateInsets(insets);
	mDeviceProfile.layout(this, true);
}

@Override
public BadgeInfo getBadgeInfoForItem(final ItemInfo info) {
	return mPopupDataProvider.getBadgeInfoForItem(info);
}

@Override
public void invalidateParent(final ItemInfo info) {
	FolderIconPreviewVerifier verifier =
		new FolderIconPreviewVerifier(getDeviceProfile().inv);
	if (verifier.isItemInPreview(info.rank) && (info.container >= 0)) {
		View folderIcon =
			getWorkspace().getHomescreenIconByItemId(info.container);
		if (folderIcon != null) {
			folderIcon.invalidate();
		}
	}
}

/**
 * Returns whether we should delay spring loaded mode -- for shortcuts and
 * widgets that have a configuration step, this allows the proper animations
 * to run after other transitions.
 */
private long completeAdd(final int requestCode, final Intent intent,
                         final int appWidgetId,
                         final PendingRequestArgs info) {
	long screenId = info.screenId;
	if (info.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
		// When the screen id represents an actual screen (as opposed to a rank)
		// we make sure that the drop page actually exists.
		screenId = ensurePendingDropLayoutExists(info.screenId);
	}

	switch (requestCode) {
	case REQUEST_CREATE_SHORTCUT:
		completeAddShortcut(intent, info.container, screenId, info.cellX,
		                    info.cellY, info);
		break;
	case REQUEST_CREATE_APPWIDGET:
		completeAddAppWidget(appWidgetId, info, null, null);
		break;
	case REQUEST_RECONFIGURE_APPWIDGET:
		completeRestoreAppWidget(appWidgetId,
		                         LauncherAppWidgetInfo.RESTORE_COMPLETED);
		break;
	case REQUEST_BIND_PENDING_APPWIDGET: {
		int widgetId = appWidgetId;
		LauncherAppWidgetInfo widgetInfo = completeRestoreAppWidget(
			widgetId, LauncherAppWidgetInfo.FLAG_UI_NOT_READY);
		if (widgetInfo != null) {
			// Since the view was just bound, also launch the configure activity if
			// needed
			LauncherAppWidgetProviderInfo provider =
				mAppWidgetManager.getLauncherAppWidgetInfo(widgetId);
			if (provider != null) {
				new WidgetAddFlowHandler(provider).startConfigActivity(
					this, widgetInfo, REQUEST_RECONFIGURE_APPWIDGET);
			}
		}
		break;
	}
	}

	return screenId;
}

private void handleActivityResult(final int requestCode, final int resultCode,
                                  final Intent data) {
	if (isWorkspaceLoading()) {
		// process the result once the workspace has loaded.
		mPendingActivityResult =
			new ActivityResultInfo(requestCode, resultCode, data);
		return;
	}
	mPendingActivityResult = null;

	// Reset the startActivity waiting flag
	final PendingRequestArgs requestArgs = mPendingRequestArgs;
	setWaitingForResult(null);
	if (requestArgs == null) {
		return;
	}

	final int pendingAddWidgetId = requestArgs.getWidgetId();

	Runnable exitSpringLoaded = new Runnable() {
		@Override
		public void run() {
			mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
		}
	};

	if (requestCode == REQUEST_BIND_APPWIDGET) {
		// This is called only if the user did not previously have permissions to
		// bind widgets
		final int appWidgetId =
			data != null
	      ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
	      : -1;
		if (resultCode == RESULT_CANCELED) {
			completeTwoStageWidgetDrop(RESULT_CANCELED, appWidgetId, requestArgs);
			mWorkspace.removeExtraEmptyScreenDelayed(
				true, exitSpringLoaded, ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
		} else if (resultCode == RESULT_OK) {
			addAppWidgetImpl(appWidgetId, requestArgs, null,
			                 requestArgs.getWidgetHandler(),
			                 ON_ACTIVITY_RESULT_ANIMATION_DELAY);
		}
		return;
	}

	boolean isWidgetDrop = (requestCode == REQUEST_PICK_APPWIDGET ||
	                        requestCode == REQUEST_CREATE_APPWIDGET);

	// We have special handling for widgets
	if (isWidgetDrop) {
		final int appWidgetId;
		int widgetId =
			data != null
	      ? data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
	      : -1;
		if (widgetId < 0) {
			appWidgetId = pendingAddWidgetId;
		} else {
			appWidgetId = widgetId;
		}

		final int result;
		if (appWidgetId < 0 || resultCode == RESULT_CANCELED) {
			Log.e(TAG, "Error: appWidgetId (EXTRA_APPWIDGET_ID) was not "
			      + "returned from the widget configuration activity.");
			result = RESULT_CANCELED;
			completeTwoStageWidgetDrop(result, appWidgetId, requestArgs);
			final Runnable onComplete = ()->getStateManager().goToState(NORMAL);

			mWorkspace.removeExtraEmptyScreenDelayed(
				true, onComplete, ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
		} else {
			if (requestArgs.container ==
			    LauncherSettings.Favorites.CONTAINER_DESKTOP) {
				// When the screen id represents an actual screen (as opposed to a
				// rank) we make sure that the drop page actually exists.
				requestArgs.screenId =
					ensurePendingDropLayoutExists(requestArgs.screenId);
			}
			final CellLayout dropLayout =
				mWorkspace.getScreenWithId(requestArgs.screenId);

			dropLayout.setDropPending(true);
			final Runnable onComplete = ()->{
				completeTwoStageWidgetDrop(resultCode, appWidgetId, requestArgs);
				dropLayout.setDropPending(false);
			};
			mWorkspace.removeExtraEmptyScreenDelayed(
				true, onComplete, ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
		}
		return;
	}

	if (requestCode == REQUEST_RECONFIGURE_APPWIDGET ||
	    requestCode == REQUEST_BIND_PENDING_APPWIDGET) {
		if (resultCode == RESULT_OK) {
			// Update the widget view.
			completeAdd(requestCode, data, pendingAddWidgetId, requestArgs);
		}
		// Leave the widget in the pending state if the user canceled the
		// configure.
		return;
	}

	if (requestCode == REQUEST_CREATE_SHORTCUT) {
		// Handle custom shortcuts created using ACTION_CREATE_SHORTCUT.
		if (resultCode == RESULT_OK && requestArgs.container != ItemInfo.NO_ID) {
			completeAdd(requestCode, data, -1, requestArgs);
			mWorkspace.removeExtraEmptyScreenDelayed(
				true, exitSpringLoaded, ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);

		} else if (resultCode == RESULT_CANCELED) {
			mWorkspace.removeExtraEmptyScreenDelayed(
				true, exitSpringLoaded, ON_ACTIVITY_RESULT_ANIMATION_DELAY, false);
		}
	}
	mDragLayer.clearAnimatedView();
}
@Override
public void onActivityResult(final int requestCode, final int resultCode,
                             final Intent data) {
	handleActivityResult(requestCode, resultCode, data);
	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onActivityResult(requestCode, resultCode, data);
	}
}

/**
 * @Override for MNC
 */
public void onRequestPermissionsResult(final int requestCode,
                                       final String[] permissions,
                                       final int[] grantResults) {
	PendingRequestArgs pendingArgs = mPendingRequestArgs;
	if (requestCode == REQUEST_PERMISSION_CALL_PHONE && pendingArgs != null &&
	    pendingArgs.getRequestCode() == REQUEST_PERMISSION_CALL_PHONE) {
		setWaitingForResult(null);

		View v = null;
		CellLayout layout =
			getCellLayout(pendingArgs.container, pendingArgs.screenId);
		if (layout != null) {
			v = layout.getChildAt(pendingArgs.cellX, pendingArgs.cellY);
		}
		Intent intent = pendingArgs.getPendingIntent();

		if (grantResults.length > 0 &&
		    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			startActivitySafely(v, intent, null);
		} else {
			// TODO: Show a snack bar with link to settings
			Toast
			.makeText(this,
			          getString(R.string.msg_no_phone_permission,
			                    getString(R.string.derived_app_name)),
			          Toast.LENGTH_SHORT)
			.show();
		}
	}
	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onRequestPermissionsResult(requestCode, permissions,
		                                              grantResults);
	}
}

/**
 * Check to see if a given screen id exists. If not, create it at the end,
 * return the new id.
 *
 * @param screenId the screen id to check
 * @return the new screen, or screenId if it exists
 */
private long ensurePendingDropLayoutExists(final long screenId) {
	CellLayout dropLayout = mWorkspace.getScreenWithId(screenId);
	if (dropLayout == null) {
		// it's possible that the add screen was removed because it was
		// empty and a re-bind occurred
		mWorkspace.addExtraEmptyScreen();
		return mWorkspace.commitExtraEmptyScreen();
	} else {
		return screenId;
	}
}

@Thunk
void completeTwoStageWidgetDrop(final int resultCode, final int appWidgetId,
                                final PendingRequestArgs requestArgs) {
	CellLayout cellLayout = mWorkspace.getScreenWithId(requestArgs.screenId);
	Runnable onCompleteRunnable = null;
	int animationType = 0;

	AppWidgetHostView boundWidget = null;
	if (resultCode == RESULT_OK) {
		animationType = Workspace.COMPLETE_TWO_STAGE_WIDGET_DROP_ANIMATION;
		final AppWidgetHostView layout = mAppWidgetHost.createView(
			this, appWidgetId,
			requestArgs.getWidgetHandler().getProviderInfo(this));
		boundWidget = layout;
		onCompleteRunnable = ()->{
			completeAddAppWidget(appWidgetId, requestArgs, layout, null);
			mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
		};
	} else if (resultCode == RESULT_CANCELED) {
		mAppWidgetHost.deleteAppWidgetId(appWidgetId);
		animationType = Workspace.CANCEL_TWO_STAGE_WIDGET_DROP_ANIMATION;
	}
	if (mDragLayer.getAnimatedView() != null) {
		mWorkspace.animateWidgetDrop(
			requestArgs, cellLayout, (DragView)mDragLayer.getAnimatedView(),
			onCompleteRunnable, animationType, boundWidget, true);
	} else if (onCompleteRunnable != null) {
		// The animated view may be null in the case of a rotation during widget
		// configuration
		onCompleteRunnable.run();
	}
}

@Override
protected void onStop() {
	super.onStop();
	FirstFrameAnimatorHelper.setIsVisible(false);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onStop();
	}
	getUserEventDispatcher().logActionCommand(
		Action.Command.STOP, mStateManager.getState().containerType, -1);

	mAppWidgetHost.setListenIfResumed(false);

	NotificationListener.removeNotificationsChangedListener();
	getStateManager().moveToRestState();

	UiFactory.onLauncherStateOrResumeChanged(this);

	// Workaround for b/78520668, explicitly trim memory once UI is hidden
	onTrimMemory(TRIM_MEMORY_UI_HIDDEN);
}

@Override
protected void onStart() {
	super.onStart();
	FirstFrameAnimatorHelper.setIsVisible(true);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onStart();
	}
	mAppWidgetHost.setListenIfResumed(true);
	NotificationListener.setNotificationsChangedListener(mPopupDataProvider);
	UiFactory.onStart(this);
}

private void logOnDelayedResume() {
	if (hasBeenResumed()) {
		getUserEventDispatcher().logActionCommand(
			Action.Command.RESUME, mStateManager.getState().containerType, -1);
		getUserEventDispatcher().startSession();
	}
}

@Override
protected void onResume() {
	TraceHelper.beginSection("ON_RESUME");
	super.onResume();
	TraceHelper.partitionSection("ON_RESUME", "superCall");

	mHandler.removeCallbacks(mLogOnDelayedResume);
	Utilities.postAsyncCallback(mHandler, mLogOnDelayedResume);

	setOnResumeCallback(null);
	// Process any items that were added while Launcher was away.
	InstallShortcutReceiver.disableAndFlushInstallQueue(
		InstallShortcutReceiver.FLAG_ACTIVITY_PAUSED, this);

	// Refresh shortcuts if the permission changed.
	mModel.refreshShortcutsIfRequired();

	DiscoveryBounce.showForHomeIfNeeded(this);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onResume();
	}

	Handler handler = getDragLayer().getHandler();
	if (handler != null) {
		handler.removeCallbacks(mUpdatePredictionsIfResumed);
		Utilities.postAsyncCallback(handler, mUpdatePredictionsIfResumed);
	}

	UiFactory.onLauncherStateOrResumeChanged(this);

	TraceHelper.endSection("ON_RESUME");
}

@Override
protected void onPause() {
	// Ensure that items added to Launcher are queued until Launcher returns
	InstallShortcutReceiver.enableInstallQueue(
		InstallShortcutReceiver.FLAG_ACTIVITY_PAUSED);

	super.onPause();
	mDragController.cancelDrag();
	mDragController.resetLastGestureUpTime();

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onPause();
	}
}

@Override
protected void onUserLeaveHint() {
	super.onUserLeaveHint();
	UiFactory.onLauncherStateOrResumeChanged(this);
}

public boolean isInState(final LauncherState state) {
	return mStateManager.getState() == state;
}

@Override
public Object onRetainNonConfigurationInstance() {
	// Flag the loader to stop early before switching
	if (mModel.isCurrentCallbacks(this)) {
		mModel.stopLoader();
	}
	// TODO(hyunyoungs): stop the widgets loader when there is a rotation.

	return Boolean.TRUE;
}

// We can't hide the IME if it was forced open.  So don't bother
@Override
public void onWindowFocusChanged(final boolean hasFocus) {
	super.onWindowFocusChanged(hasFocus);
	mStateManager.onWindowFocusChanged();
}

private boolean acceptFilter() {
	final InputMethodManager inputManager =
		(InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
	return !inputManager.isFullscreenMode();
}

@Override
public boolean onKeyDown(final int keyCode, final KeyEvent event) {
	final int uniChar = event.getUnicodeChar();
	final boolean handled = super.onKeyDown(keyCode, event);
	final boolean isKeyNotWhitespace =
		uniChar > 0 && !Character.isWhitespace(uniChar);
	if (!handled && acceptFilter() && isKeyNotWhitespace) {
		boolean gotKey = TextKeyListener.getInstance().onKeyDown(
			mWorkspace, mDefaultKeySsb, keyCode, event);
		if (gotKey && mDefaultKeySsb != null && mDefaultKeySsb.length() > 0) {
			// something usable has been typed - start a search
			// the typed text will be retrieved and cleared by
			// showSearchDialog()
			// If there are multiple keystrokes before the search dialog takes
			// focus, onSearchRequested() will be called for every keystroke, but it
			// is idempotent, so it's fine.
			return onSearchRequested();
		}
	}

	// Eat the long press event so the keyboard doesn't come up.
	if (keyCode == KeyEvent.KEYCODE_MENU && event.isLongPress()) {
		return true;
	}

	return handled;
}

/*
   @Override
   public void clearTypedText() {
    mDefaultKeySsb.clear();
    mDefaultKeySsb.clearSpans();
    Selection.setSelection(mDefaultKeySsb, 0);
   }*/

/**
 * Restores the previous state, if it exists.
 *
 * @param savedState The previous state.
 */
private void restoreState(final Bundle savedState) {
	if (savedState == null) {
		return;
	}

	int stateOrdinal = savedState.getInt(RUNTIME_STATE, NORMAL.ordinal);
	LauncherState[] stateValues = LauncherState.values();
	LauncherState state = stateValues[stateOrdinal];
	if (!state.disableRestore) {
		mStateManager.goToState(state, false /* animated */);
	}

	PendingRequestArgs requestArgs =
		savedState.getParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS);
	if (requestArgs != null) {
		setWaitingForResult(requestArgs);
	}

	mPendingActivityResult =
		savedState.getParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT);

	SparseArray<Parcelable> widgetsState =
		savedState.getSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL);
	if (widgetsState != null) {
		WidgetsFullSheet.show(this, false).restoreHierarchyState(widgetsState);
	}
}

/**
 * Finds all the views we need and configure them properly.
 */
private void setupViews() {
	mDragLayer = findViewById(R.id.drag_layer);
	mFocusHandler = mDragLayer.getFocusIndicatorHelper();
	mWorkspace = mDragLayer.findViewById(R.id.workspace);
	if (Utilities.getZimPrefs(this).getUsePillQsb()) {
		mQsbContainer = mDragLayer.findViewById(R.id.workspace_blocked_row);
	}
	mWorkspace.initParentViews(mDragLayer);
	mOverviewPanel = findViewById(R.id.overview_panel);
	mHotseat = findViewById(R.id.hotseat);
	mHotseatSearchBox = findViewById(R.id.search_container_hotseat);

	mLauncherView.setSystemUiVisibility(
		View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
		View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
		View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

	// Setup the drag layer
	mDragLayer.setup(mDragController, mWorkspace);
	UiFactory.setOnTouchControllersChangedListener(
		this, mDragLayer::recreateControllers);

	mWorkspace.setup(mDragController);
	// Until the workspace is bound, ensure that we keep the wallpaper offset
	// locked to the default state, otherwise we will update to the wrong
	// offsets in RTL
	mWorkspace.lockWallpaperToDefaultPage();
	mWorkspace.bindAndInitFirstWorkspaceScreen(null /* recycled qsb */);
	mDragController.addDragListener(mWorkspace);

	// Get the search/delete/uninstall bar
	mDropTargetBar = mDragLayer.findViewById(R.id.drop_target_bar);

	// Setup Apps
	mAppsView = findViewById(R.id.apps_view);

	// Setup the drag controller (drop targets have to be added in reverse order
	// in priority)
	mDragController.setMoveTarget(mWorkspace);
	mDropTargetBar.setup(mDragController);

	mAllAppsController.setupViews(mAppsView, getHotseat());
}

/**
 * Add a shortcut to the workspace or to a Folder.
 *
 * @param data The intent describing the shortcut.
 */
private void completeAddShortcut(final Intent data, final long container,
                                 final long screenId, final int cellX,
                                 final int cellY,
                                 final PendingRequestArgs args) {
	if (args.getRequestCode() != REQUEST_CREATE_SHORTCUT ||
	    args.getPendingIntent().getComponent() == null) {
		return;
	}

	int[] cellXY = mTmpAddItemCellCoordinates;
	CellLayout layout = getCellLayout(container, screenId);

	ShortcutInfo info = null;
	if (Utilities.ATLEAST_OREO) {
		info = LauncherAppsCompatVO.createShortcutInfoFromPinItemRequest(
			this, LauncherAppsCompatVO.getPinItemRequest(data), 0);
	}

	if (info == null) {
		// Legacy shortcuts are only supported for primary profile.
		info = Process.myUserHandle().equals(args.user)
		 ? InstallShortcutReceiver.fromShortcutIntent(this, data)
		 : null;

		if (info == null) {
			Log.e(TAG, "Unable to parse a valid custom shortcut result");
			return;
		} else if (!new PackageManagerHelper(this).hasPermissionForActivity(
				   info.intent,
				   args.getPendingIntent().getComponent().getPackageName())) {
			// The app is trying to add a shortcut without sufficient permissions
			Log.e(TAG, "Ignoring malicious intent " + info.intent.toUri(0));
			return;
		}
	}

	if (container < 0) {
		// Adding a shortcut to the Workspace.
		final View view = createShortcut(info);
		boolean foundCellSpan = false;
		// First we check if we already know the exact location where we want to
		// add this item.
		if (cellX >= 0 && cellY >= 0) {
			cellXY[0] = cellX;
			cellXY[1] = cellY;
			foundCellSpan = true;

			// If appropriate, either create a folder or add to an existing folder
			if (mWorkspace.createUserFolderIfNecessary(view, container, layout,
			                                           cellXY, 0, true, null)) {
				return;
			}
			DragObject dragObject = new DragObject();
			dragObject.dragInfo = info;
			if (mWorkspace.addToExistingFolderIfNecessary(view, layout, cellXY, 0,
			                                              dragObject, true)) {
				return;
			}
		} else {
			foundCellSpan = layout.findCellForSpan(cellXY, 1, 1);
		}

		if (!foundCellSpan) {
			mWorkspace.onNoCellFound(layout);
			return;
		}

		getModelWriter().addItemToDatabase(info, container, screenId, cellXY[0],
		                                   cellXY[1]);
		mWorkspace.addInScreen(view, info);
	} else {
		// Adding a shortcut to a Folder.
		FolderIcon folderIcon = findFolderIcon(container);
		if (folderIcon != null) {
			FolderInfo folderInfo = (FolderInfo)folderIcon.getTag();
			folderInfo.add(info, args.rank, false);
		} else {
			Log.e(TAG, "Could not find folder with id " + container +
			      " to add shortcut.");
		}
	}
}

/**
 * Creates a view representing a shortcut.
 *
 * @param info The data structure describing the shortcut.
 */
View createShortcut(final ShortcutInfo info) {
	return createShortcut(
		(ViewGroup)mWorkspace.getChildAt(mWorkspace.getCurrentPage()), info);
}

/**
 * Creates a view representing a shortcut inflated from the specified
 * resource.
 *
 * @param parent The group the shortcut belongs to.
 * @param info   The data structure describing the shortcut.
 * @return A View inflated from layoutResId.
 */
public View createShortcut(final ViewGroup parent, final ShortcutInfo info) {
	BubbleTextView favorite =
		(BubbleTextView)LayoutInflater.from(parent.getContext())
		.inflate(R.layout.app_icon, parent, false);
	favorite.applyFromShortcutInfo(info);
	favorite.setOnClickListener(ItemClickHandler.INSTANCE);
	favorite.setOnFocusChangeListener(mFocusHandler);
	return favorite;
}

public FolderIcon findFolderIcon(final long folderIconId) {
	return (FolderIcon)mWorkspace.getFirstMatch(
		(info, view)->info != null && info.id == folderIconId);
}

/**
 * Add a widget to the workspace.
 *
 * @param appWidgetId The app widget id
 */
@Thunk
void completeAddAppWidget(final int appWidgetId, final ItemInfo itemInfo,
                          final AppWidgetHostView hostView,
                          final LauncherAppWidgetProviderInfo appWidgetInfo) {

	if (appWidgetInfo == null) {
		appWidgetInfo = mAppWidgetManager.getLauncherAppWidgetInfo(appWidgetId);
	}

	LauncherAppWidgetInfo launcherInfo;
	launcherInfo =
		new LauncherAppWidgetInfo(appWidgetId, appWidgetInfo.provider);
	launcherInfo.spanX = itemInfo.spanX;
	launcherInfo.spanY = itemInfo.spanY;
	launcherInfo.minSpanX = itemInfo.minSpanX;
	launcherInfo.minSpanY = itemInfo.minSpanY;
	launcherInfo.user = appWidgetInfo.getProfile();

	getModelWriter().addItemToDatabase(launcherInfo, itemInfo.container,
	                                   itemInfo.screenId, itemInfo.cellX,
	                                   itemInfo.cellY);

	if (hostView == null) {
		// Perform actual inflation because we're live
		hostView = mAppWidgetHost.createView(this, appWidgetId, appWidgetInfo);
	}
	hostView.setVisibility(View.VISIBLE);
	prepareAppWidget(hostView, launcherInfo);
	mWorkspace.addInScreen(hostView, launcherInfo);
}

private void prepareAppWidget(final AppWidgetHostView hostView,
                              final LauncherAppWidgetInfo item) {
	hostView.setTag(item);
	item.onBindAppWidget(this, hostView);
	hostView.setFocusable(true);
	hostView.setOnFocusChangeListener(mFocusHandler);
}

private final BroadcastReceiver mScreenOffReceiver = new BroadcastReceiver() {
	@Override
	public void onReceive(final Context context, final Intent intent) {
		// Reset AllApps to its initial state only if we are not in the middle of
		// processing a multi-step drop
		if (mPendingRequestArgs == null) {
			mStateManager.goToState(NORMAL);
		}
	}
};

public void updateIconBadges(final Set<PackageUserKey> updatedBadges) {
	mWorkspace.updateIconBadges(updatedBadges);
	mAppsView.getAppsStore().updateIconBadges(updatedBadges);

	PopupContainerWithArrow popup =
		PopupContainerWithArrow.getOpen(Launcher.this);
	if (popup != null) {
		popup.updateNotificationHeader(updatedBadges);
	}
}

@Override
public void onAttachedToWindow() {
	super.onAttachedToWindow();

	FirstFrameAnimatorHelper.initializeDrawListener(getWindow().getDecorView());
	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onAttachedToWindow();
	}
}

@Override
public void onDetachedFromWindow() {
	super.onDetachedFromWindow();

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onDetachedFromWindow();
	}
}

/**
 * Shows the overview button, and if {@param requestButtonFocus} is set, will
 * force the focus onto one of the overview panel buttons.
 */
void showOverviewMode(final boolean animated,
                      final boolean requestButtonFocus) {
	if (requestButtonFocus) {
	}
	mWorkspace.setVisibility(View.VISIBLE);
	// mStateTransitionAnimation.startAnimationToWorkspace(mState,
	// mWorkspace.getState(),
	//        Workspace.State.OVERVIEW, animated, postAnimRunnable);
	// setState(State.WORKSPACE);

	// If animated from long press, then don't allow any of the controller in
	// the drag layer to intercept any remaining touch.
	mWorkspace.requestDisallowInterceptTouchEvent(animated);
}

public AllAppsTransitionController getAllAppsController() {
	return mAllAppsController;
}

@Override
public LauncherRootView getRootView() {
	return (LauncherRootView)mLauncherView.findViewById(R.id.launcher);
}

@Override
public DragLayer getDragLayer() {
	return mDragLayer;
}

public AllAppsContainerView getAppsView() {
	return mAppsView;
}

public Workspace getWorkspace() {
	return mWorkspace;
}

public Hotseat getHotseat() {
	return mHotseat;
}

public View getHotseatSearchBox() {
	return mHotseatSearchBox;
}

public <T extends View> T getOverviewPanel() {
	return (T)mOverviewPanel;
}

public DropTargetBar getDropTargetBar() {
	return mDropTargetBar;
}

public LauncherAppWidgetHost getAppWidgetHost() {
	return mAppWidgetHost;
}

public LauncherModel getModel() {
	return mModel;
}

public ModelWriter getModelWriter() {
	return mModelWriter;
}

public SharedPreferences getSharedPrefs() {
	return mSharedPrefs;
}

public int getOrientation() {
	return mOldConfig.orientation;
}

@Override
protected void onNewIntent(final Intent intent) {
	TraceHelper.beginSection("NEW_INTENT");
	super.onNewIntent(intent);

	boolean alreadyOnHome =
		hasWindowFocus() &&
		((intent.getFlags() & Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT) !=
		 Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT);

	// Check this condition before handling isActionMain, as this will get
	// reset.
	boolean shouldMoveToDefaultScreen =
		alreadyOnHome && isInState(NORMAL) &&
		AbstractFloatingView.getTopOpenView(this) == null;
	boolean isActionMain = Intent.ACTION_MAIN.equals(intent.getAction());
	boolean internalStateHandled =
		InternalStateHandler.handleNewIntent(this, intent, isStarted());

	boolean handled = false;
	if (isActionMain) {
		if (!internalStateHandled) {
			// Note: There should be at most one log per method call. This is
			// enforced implicitly by using if-else statements.
			UserEventDispatcher ued = getUserEventDispatcher();
			AbstractFloatingView topOpenView =
				AbstractFloatingView.getTopOpenView(this);
			if (topOpenView != null) {
				topOpenView.logActionCommand(Action.Command.HOME_INTENT);
				handled = true;
			} else if (alreadyOnHome) {
				Target target =
					newContainerTarget(mStateManager.getState().containerType);
				target.pageIndex = mWorkspace.getCurrentPage();
				ued.logActionCommand(Action.Command.HOME_INTENT, target,
				                     newContainerTarget(ContainerType.WORKSPACE));
			}

			// In all these cases, only animate if we're already on home
			AbstractFloatingView.closeAllOpenViews(this, isStarted());

			if (!isInState(NORMAL)) {
				// Only change state, if not already the same. This prevents
				// cancelling any animations running as part of resume
				mStateManager.goToState(NORMAL);
				handled = true;
			}

			// Reset the apps view
			if (!alreadyOnHome) {
				mAppsView.reset(isStarted() /* animate */);
				handled = true;
			}

			if (shouldMoveToDefaultScreen && !mWorkspace.isTouchActive()) {
				if (mWorkspace.getCurrentPage() != 0) {
					handled = true;
				}
				mWorkspace.post(mWorkspace::moveToDefaultScreen);
			}

			if (!handled && this instanceof ZimLauncher) {
				((ZimLauncher)this).getGestureController().onPressHome();
			}
		}

		final View v = getWindow().peekDecorView();
		if (v != null && v.getWindowToken() != null) {
			UiThreadHelper.hideKeyboardAsync(this, v.getWindowToken());
		}

		if (mLauncherCallbacks != null) {
			mLauncherCallbacks.onHomeIntent(internalStateHandled);
		}
	}

	TraceHelper.endSection("NEW_INTENT");
}

@Override
public void onRestoreInstanceState(final Bundle state) {
	super.onRestoreInstanceState(state);
	for (int page : mSynchronouslyBoundPages) {
		mWorkspace.restoreInstanceStateForChild(page);
	}
}

@Override
protected void onSaveInstanceState(final Bundle outState) {
	if (mWorkspace.getChildCount() > 0) {
		outState.putInt(RUNTIME_STATE_CURRENT_SCREEN, mWorkspace.getNextPage());
	}
	outState.putInt(RUNTIME_STATE, mStateManager.getState().ordinal);

	AbstractFloatingView widgets = AbstractFloatingView.getOpenView(
		this, AbstractFloatingView.TYPE_WIDGETS_FULL_SHEET);
	if (widgets != null) {
		SparseArray<Parcelable> widgetsState = new SparseArray<>();
		widgets.saveHierarchyState(widgetsState);
		outState.putSparseParcelableArray(RUNTIME_STATE_WIDGET_PANEL,
		                                  widgetsState);
	} else {
		outState.remove(RUNTIME_STATE_WIDGET_PANEL);
	}

	// We close any open folders and shortcut containers since they will not be
	// re-opened, and we need to make sure this state is reflected.
	AbstractFloatingView.closeAllOpenViews(this, false);
	finishAutoCancelActionMode();

	if (mPendingRequestArgs != null) {
		outState.putParcelable(RUNTIME_STATE_PENDING_REQUEST_ARGS,
		                       mPendingRequestArgs);
	}
	if (mPendingActivityResult != null) {
		outState.putParcelable(RUNTIME_STATE_PENDING_ACTIVITY_RESULT,
		                       mPendingActivityResult);
	}

	super.onSaveInstanceState(outState);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onSaveInstanceState(outState);
	}
}

public LauncherAccessibilityDelegate getAccessibilityDelegate() {
	return mAccessibilityDelegate;
}

public DragController getDragController() {
	return mDragController;
}

@Override
public void startActivityForResult(final Intent intent, final int requestCode,
                                   final Bundle options) {
	super.startActivityForResult(intent, requestCode, options);
}

@Override
public void
startIntentSenderForResult(final IntentSender intent, final int requestCode,
                           final Intent fillInIntent, final int flagsMask,
                           final int flagsValues, final int extraFlags,
                           final Bundle options) {
	try {
		super.startIntentSenderForResult(intent, requestCode, fillInIntent,
		                                 flagsMask, flagsValues, extraFlags,
		                                 options);
	} catch (IntentSender.SendIntentException e) {
		throw new ActivityNotFoundException();
	}
}

/**
 * Indicates that we want global search for this activity by setting the
 * globalSearch argument for {@link #startSearch} to true.
 */
@Override
public void
startSearch(final String initialQuery, final boolean selectInitialQuery,
            final Bundle appSearchData, final boolean globalSearch) {
	if (appSearchData == null) {
		appSearchData = new Bundle();
		appSearchData.putString("source", "launcher-search");
	}

	if (mLauncherCallbacks == null ||
	    !mLauncherCallbacks.startSearch(initialQuery, selectInitialQuery,
	                                    appSearchData)) {
		// Starting search from the callbacks failed. Start the default global
		// search.
		super.startSearch(initialQuery, selectInitialQuery, appSearchData, true);
	}

	// We need to show the workspace after starting the search
	mStateManager.goToState(NORMAL);
}

@Override
public boolean onSearchRequested() {
	startSearch(null, false, null, true);
	// Use a custom animation for launching search
	return true;
}

public boolean isWorkspaceLocked() {
	return mWorkspaceLoading || mPendingRequestArgs != null;
}

public boolean isWorkspaceLoading() {
	return mWorkspaceLoading;
}

private void setWorkspaceLoading(final boolean value) {
	mWorkspaceLoading = value;
}

public void setWaitingForResult(final PendingRequestArgs args) {
	mPendingRequestArgs = args;
}

void addAppWidgetFromDropImpl(final int appWidgetId, final ItemInfo info,
                              final AppWidgetHostView boundWidget,
                              final WidgetAddFlowHandler addFlowHandler) {
	if (LOGD) {
		Log.d(TAG, "Adding widget from drop");
	}
	addAppWidgetImpl(appWidgetId, info, boundWidget, addFlowHandler, 0);
}

void addAppWidgetImpl(final int appWidgetId, final ItemInfo info,
                      final AppWidgetHostView boundWidget,
                      final WidgetAddFlowHandler addFlowHandler,
                      final int delay) {
	if (!addFlowHandler.startConfigActivity(this, appWidgetId, info,
	                                        REQUEST_CREATE_APPWIDGET)) {
		// If the configuration flow was not started, add the widget

		Runnable onComplete = ()->{
			// Exit spring loaded mode if necessary after adding the widget
			mStateManager.goToState(NORMAL, SPRING_LOADED_EXIT_DELAY);
		};
		completeAddAppWidget(appWidgetId, info, boundWidget,
		                     addFlowHandler.getProviderInfo(this));
		mWorkspace.removeExtraEmptyScreenDelayed(true, onComplete, delay, false);
	}
}

public void addPendingItem(final PendingAddItemInfo info,
                           final long container, final long screenId,
                           final int[] cell, final int spanX,
                           final int spanY) {
	info.container = container;
	info.screenId = screenId;
	if (cell != null) {
		info.cellX = cell[0];
		info.cellY = cell[1];
	}
	info.spanX = spanX;
	info.spanY = spanY;

	switch (info.itemType) {
	case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET:
	case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
		addAppWidgetFromDrop((PendingAddWidgetInfo)info);
		break;
	case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
		processShortcutFromDrop((PendingAddShortcutInfo)info);
		break;
	default:
		throw new IllegalStateException("Unknown item type: " + info.itemType);
	}
}

/**
 * Process a shortcut drop.
 */
private void processShortcutFromDrop(final PendingAddShortcutInfo info) {
	Intent intent = new Intent(Intent.ACTION_CREATE_SHORTCUT)
	                .setComponent(info.componentName);
	setWaitingForResult(
		PendingRequestArgs.forIntent(REQUEST_CREATE_SHORTCUT, intent, info));
	if (!info.activityInfo.startConfigActivity(this, REQUEST_CREATE_SHORTCUT)) {
		handleActivityResult(REQUEST_CREATE_SHORTCUT, RESULT_CANCELED, null);
	}
}

/**
 * Process a widget drop.
 */
private void addAppWidgetFromDrop(final PendingAddWidgetInfo info) {
	AppWidgetHostView hostView = info.boundWidget;
	int appWidgetId;
	WidgetAddFlowHandler addFlowHandler = info.getHandler();
	if (hostView != null) {
		// In the case where we've prebound the widget, we remove it from the
		// DragLayer
		if (LOGD) {
			Log.d(
				TAG,
				"Removing widget view from drag layer and setting boundWidget to null");
		}
		getDragLayer().removeView(hostView);

		appWidgetId = hostView.getAppWidgetId();
		addAppWidgetFromDropImpl(appWidgetId, info, hostView, addFlowHandler);

		// Clear the boundWidget so that it doesn't get destroyed.
		info.boundWidget = null;
	} else {
		// In this case, we either need to start an activity to get permission to
		// bind the widget, or we need to start an activity to configure the
		// widget, or both.
		if (FeatureFlags.ENABLE_CUSTOM_WIDGETS &&
		    info.itemType ==
		    LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET) {
			appWidgetId = CustomWidgetParser.getWidgetIdForCustomProvider(
				this, info.componentName);
		} else {
			appWidgetId = getAppWidgetHost().allocateAppWidgetId();
		}
		Bundle options = info.bindOptions;

		boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
			appWidgetId, info.info, options);
		if (success) {
			addAppWidgetFromDropImpl(appWidgetId, info, null, addFlowHandler);
		} else {
			addFlowHandler.startBindFlow(this, appWidgetId, info,
			                             REQUEST_BIND_APPWIDGET);
		}
	}
}

FolderIcon addFolder(final CellLayout layout, final long container,
                     final long screenId, final int cellX, final int cellY) {
	final FolderInfo folderInfo = new FolderInfo();
	folderInfo.title = getText(R.string.folder_name);

	// Update the model
	getModelWriter().addItemToDatabase(folderInfo, container, screenId, cellX,
	                                   cellY);

	// Create the view
	FolderIcon newFolder =
		FolderIcon.fromXml(R.layout.folder_icon, this, layout, folderInfo);
	mWorkspace.addInScreen(newFolder, folderInfo);
	// Force measure the new folder icon
	CellLayout parent = mWorkspace.getParentCellLayoutForView(newFolder);
	parent.getShortcutsAndWidgets().measureChild(newFolder);
	return newFolder;
}

/**
 * Unbinds the view for the specified item, and removes the item and all its
 * children.
 *
 * @param v            the view being removed.
 * @param itemInfo     the {@link ItemInfo} for this view.
 * @param deleteFromDb whether or not to delete this item from the db.
 */
public boolean removeItem(final View v, final ItemInfo itemInfo,
                          final boolean deleteFromDb) {
	if (itemInfo instanceof ShortcutInfo) {
		// Remove the shortcut from the folder before removing it from launcher
		View folderIcon =
			mWorkspace.getHomescreenIconByItemId(itemInfo.container);
		if (folderIcon instanceof FolderIcon) {
			((FolderInfo)folderIcon.getTag()).remove((ShortcutInfo)itemInfo, true);
		} else {
			mWorkspace.removeWorkspaceItem(v);
		}
		if (deleteFromDb) {
			getModelWriter().deleteItemFromDatabase(itemInfo);
		}
	} else if (itemInfo instanceof FolderInfo) {
		final FolderInfo folderInfo = (FolderInfo)itemInfo;
		if (v instanceof FolderIcon) {
			((FolderIcon)v).removeListeners();
		}
		mWorkspace.removeWorkspaceItem(v);
		if (deleteFromDb) {
			getModelWriter().deleteFolderAndContentsFromDatabase(folderInfo);
		}
	} else if (itemInfo instanceof LauncherAppWidgetInfo) {
		final LauncherAppWidgetInfo widgetInfo = (LauncherAppWidgetInfo)itemInfo;
		mWorkspace.removeWorkspaceItem(v);
		if (deleteFromDb) {
			deleteWidgetInfo(widgetInfo);
		}
	} else {
		return false;
	}
	return true;
}

/**
 * Deletes the widget info and the widget id.
 */
private void deleteWidgetInfo(final LauncherAppWidgetInfo widgetInfo) {
	getModelWriter().deleteWidgetInfo(widgetInfo, getAppWidgetHost());
}

@Override
public boolean dispatchKeyEvent(final KeyEvent event) {
	return (event.getKeyCode() == KeyEvent.KEYCODE_HOME) ||
	       super.dispatchKeyEvent(event);
}

@Override
public void onBackPressed() {
	DrawerLayout dl = findViewById(R.id.drawer_layout);
	FrameLayout sl = findViewById(R.id.dlview);
	if (dl.isDrawerOpen(sl)) {
		((DrawerLayout)findViewById(R.id.drawer_layout)).closeDrawers();
		return;
	}
	if (finishAutoCancelActionMode()) {
		return;
	}
	if (mLauncherCallbacks != null && mLauncherCallbacks.handleBackPressed()) {
		return;
	}

	if (mDragController.isDragging()) {
		mDragController.cancelDrag();
		return;
	}

	// Note: There should be at most one log per method call. This is enforced
	// implicitly by using if-else statements.
	UserEventDispatcher ued = getUserEventDispatcher();
	AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
	if (topView != null && topView.onBackPressed()) {
		// Handled by the floating view.
	} else if (!isInState(NORMAL)) {
		LauncherState lastState = mStateManager.getLastState();
		ued.logActionCommand(Action.Command.BACK,
		                     mStateManager.getState().containerType,
		                     lastState.containerType);
		mStateManager.goToState(lastState);
	} else {
		// Back button is a no-op here, but give at least some feedback for the
		// button press
		// mWorkspace.showOutlinesTemporarily();
		if (this instanceof ZimLauncher) {
			((ZimLauncher)this).getGestureController().onPressBack();
		}
	}
}

@TargetApi(Build.VERSION_CODES.M)
@Override
public ActivityOptions getActivityLaunchOptions(final View v) {
	return mAppTransitionManager.getActivityLaunchOptions(this, v);
}

public Rect getViewBounds(final View v) {
	int[] pos = new int[2];
	v.getLocationOnScreen(pos);
	return new Rect(pos[0], pos[1], pos[0] + v.getWidth(),
	                pos[1] + v.getHeight());
}

@Override
public void modifyUserEvent(final LauncherLogProto.LauncherEvent event) {
	if (event.srcTarget != null && event.srcTarget.length > 0 &&
	    event.srcTarget[1].containerType == ContainerType.PREDICTION) {
		Target[] targets = new Target[3];
		targets[0] = event.srcTarget[0];
		targets[1] = event.srcTarget[1];
		targets[2] = newTarget(Target.Type.CONTAINER);
		event.srcTarget = targets;
		LauncherState state = mStateManager.getState();
		if (state == LauncherState.ALL_APPS) {
			event.srcTarget[2].containerType = ContainerType.ALLAPPS;
		} else if (state == LauncherState.OVERVIEW) {
			event.srcTarget[2].containerType = ContainerType.TASKSWITCHER;
		}
	}
}

public boolean startActivitySafely(final View v, final Intent intent,
                                   final ItemInfo item) {
	boolean success = super.startActivitySafely(v, intent, item);
	if (success && v instanceof BubbleTextView) {
		// This is set to the view that launched the activity that navigated the
		// user away from launcher. Since there is no callback for when the
		// activity has finished launching, enable the press state and keep this
		// reference to reset the press state when we return to launcher.
		BubbleTextView btv = (BubbleTextView)v;
		btv.setStayPressed(true);
		setOnResumeCallback(btv);
	}
	return success;
}

boolean isHotseatLayout(final View layout) {
	// TODO: Remove this method
	return mHotseat != null && layout != null &&
	       (layout instanceof CellLayout) && (layout == mHotseat.getLayout());
}

/**
 * Returns the CellLayout of the specified container at the specified screen.
 */
public CellLayout getCellLayout(final long container, final long screenId) {
	if (container == LauncherSettings.Favorites.CONTAINER_HOTSEAT) {
		if (mHotseat != null) {
			return mHotseat.getLayout();
		} else {
			return null;
		}
	} else {
		return mWorkspace.getScreenWithId(screenId);
	}
}

@Override
public void onTrimMemory(final int level) {
	super.onTrimMemory(level);
	if (level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
		// The widget preview db can result in holding onto over
		// 3MB of memory for caching which isn't necessary.
		SQLiteDatabase.releaseMemory();

		// This clears all widget bitmaps from the widget tray
		// TODO(hyunyoungs)
	}
	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.onTrimMemory(level);
	}
	UiFactory.onTrimMemory(this, level);
}

@Override
public boolean
dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
	final boolean result = super.dispatchPopulateAccessibilityEvent(event);
	final List<CharSequence> text = event.getText();
	text.clear();
	// Populate event with a fake title based on the current state.
	// TODO: When can workspace be null?
	text.add(mWorkspace == null
	         ? getString(R.string.all_apps_home_button_label)
	         : mStateManager.getState().getDescription(this));
	return result;
}

public void setOnResumeCallback(final OnResumeCallback callback) {
	if (mOnResumeCallback != null) {
		mOnResumeCallback.onLauncherResume();
	}
	mOnResumeCallback = callback;
}

/**
 * If the activity is currently paused, signal that we need to run the passed
 * Runnable in onResume. <p> This needs to be called from incoming places
 * where resources might have been loaded while the activity is paused. That
 * is because the Configuration (e.g., rotation)  might be wrong when we're
 * not running, and if the activity comes back to what the configuration was
 * when we were paused, activity is not restarted.
 * <p>
 * Implementation of the method from LauncherModel.Callbacks.
 *
 * @return {@code true} if we are currently paused. The caller might be able
 *     to skip some work
 */
@Thunk
boolean waitUntilResume(final Runnable run) {
	if (mPaused) {
		if (LOGD)
			Log.d(TAG, "Deferring update until onResume");
		if (run instanceof RunnableWithId) {
			// Remove any runnables which have the same id
			while (mBindOnResumeCallbacks.remove(run)) {
			}
		}
		mBindOnResumeCallbacks.add(run);
		return true;
	} else {
		return false;
	}
}

/**
 * Implementation of the method from LauncherModel.Callbacks.
 */
@Override
public int getCurrentWorkspaceScreen() {
	if (mWorkspace != null) {
		return mWorkspace.getCurrentPage();
	} else {
		return 0;
	}
}

/**
 * Clear any pending bind callbacks. This is called when is loader is planning
 * to perform a full rebind from scratch.
 */
@Override
public void clearPendingBinds() {
	mBindOnResumeCallbacks.clear();
	if (mPendingExecutor != null) {
		mPendingExecutor.markCompleted();
		mPendingExecutor = null;
	}
}

/**
 * Refreshes the shortcuts shown on the workspace.
 *
 * Implementation of the method from LauncherModel.Callbacks.
 */
public void startBinding() {
	TraceHelper.beginSection("startBinding");
	// Floating panels (except the full widget sheet) are associated with
	// individual icons. If we are starting a fresh bind, close all such panels
	// as all the icons are about to go away.
	AbstractFloatingView.closeOpenViews(
		this, true,
		AbstractFloatingView.TYPE_ALL & ~AbstractFloatingView.TYPE_REBIND_SAFE);

	setWorkspaceLoading(true);

	// Clear the workspace because it's going to be rebound
	mWorkspace.clearDropTargets();
	mWorkspace.removeAllWorkspaceScreens();
	mAppWidgetHost.clearViews();

	if (mHotseat != null) {
		mHotseat.resetLayout(mDeviceProfile.isVerticalBarLayout());
	}
	TraceHelper.endSection("startBinding");
}

@Override
public void bindScreens(final ArrayList<Long> orderedScreenIds) {
	// Make sure the first screen is always at the start.
	if (FeatureFlags.QSB_ON_FIRST_SCREEN &&
	    orderedScreenIds.indexOf(Workspace.FIRST_SCREEN_ID) != 0) {
		orderedScreenIds.remove(Workspace.FIRST_SCREEN_ID);
		orderedScreenIds.add(0, Workspace.FIRST_SCREEN_ID);
		LauncherModel.updateWorkspaceScreenOrder(this, orderedScreenIds);
	} else if (!FeatureFlags.QSB_ON_FIRST_SCREEN &&
	           orderedScreenIds.isEmpty()) {
		// If there are no screens, we need to have an empty screen
		mWorkspace.addExtraEmptyScreen();
	}
	bindAddScreens(orderedScreenIds);

	// After we have added all the screens, if the wallpaper was locked to the
	// default state, then notify to indicate that it can be released and a
	// proper wallpaper offset can be computed before the next layout
	mWorkspace.unlockWallpaperFromDefaultPageOnNextLayout();
}
private void bindAddScreens(final ArrayList<Long> orderedScreenIds) {
	int count = orderedScreenIds.size();
	for (int i = 0; i < count; i++) {
		long screenId = orderedScreenIds.get(i);
		if (!FeatureFlags.QSB_ON_FIRST_SCREEN ||
		    screenId != Workspace.FIRST_SCREEN_ID) {
			// No need to bind the first screen, as its always bound.
			mWorkspace.insertNewWorkspaceScreenBeforeEmptyScreen(screenId);
		}
	}
}

@Override
public void bindAppsAdded(final ArrayList<Long> newScreens,
                          final ArrayList<ItemInfo> addNotAnimated,
                          final ArrayList<ItemInfo> addAnimated) {
	Runnable r = ()->bindAppsAdded(newScreens, addNotAnimated, addAnimated);
	if (waitUntilResume(r)) {
		return;
	}

	// Add the new screens
	if (newScreens != null) {
		bindAddScreens(newScreens);
	}

	// We add the items without animation on non-visible pages, and with
	// animations on the new page (which we will try and snap to).
	if (addNotAnimated != null && !addNotAnimated.isEmpty()) {
		bindItems(addNotAnimated, false);
	}
	if (addAnimated != null && !addAnimated.isEmpty()) {
		bindItems(addAnimated, true);
	}

	// Remove the extra empty screen
	mWorkspace.removeExtraEmptyScreen(false, false);
}

/**
 * Bind the items start-end from the list.
 * <p>
 * Implementation of the method from LauncherModel.Callbacks.
 */
@Override
public void bindItems(final List<ItemInfo> items,
                      final boolean forceAnimateIcons) {
	// Get the list of added items and intersect them with the set of items here
	final AnimatorSet anim = LauncherAnimUtils.createAnimatorSet();
	final Collection<Animator> bounceAnims = new ArrayList<>();
	final boolean animateIcons = forceAnimateIcons && canRunNewAppsAnimation();
	Workspace workspace = mWorkspace;
	long newItemsScreenId = -1;
	int end = items.size();

	for (int i = 0; i < end; i++) {
		final ItemInfo item = items.get(i);

		// Short circuit if we are loading dock items for a configuration which
		// has no dock
		if (item.container == LauncherSettings.Favorites.CONTAINER_HOTSEAT &&
		    mHotseat == null) {
			continue;
		}

		final View view;
		switch (item.itemType) {
		case LauncherSettings.Favorites.ITEM_TYPE_APPLICATION:
		case LauncherSettings.Favorites.ITEM_TYPE_SHORTCUT:
		case LauncherSettings.Favorites.ITEM_TYPE_DEEP_SHORTCUT: {
			ShortcutInfo info = (ShortcutInfo)item;
			view = createShortcut(info);
			break;
		}
		case LauncherSettings.Favorites.ITEM_TYPE_FOLDER: {
			view = FolderIcon.fromXml(
				R.layout.folder_icon, this,
				(ViewGroup)workspace.getChildAt(workspace.getCurrentPage()),
				(FolderInfo)item);
			break;
		}
		case LauncherSettings.Favorites.ITEM_TYPE_APPWIDGET:
		case LauncherSettings.Favorites.ITEM_TYPE_CUSTOM_APPWIDGET: {
			view = inflateAppWidget((LauncherAppWidgetInfo)item);
			if (view == null) {
				continue;
			}
			break;
		}
		default:
			throw new RuntimeException("Invalid Item Type");
		}

		/*
		 * Remove colliding items.
		 */
		if (item.container == LauncherSettings.Favorites.CONTAINER_DESKTOP) {
			CellLayout cl = mWorkspace.getScreenWithId(item.screenId);
			if (cl != null && cl.isOccupied(item.cellX, item.cellY)) {
				View v = cl.getChildAt(item.cellX, item.cellY);
				Object tag = v.getTag();
				String desc = "Collision while binding workspace item: " + item +
				              ". Collides with " + tag;
				if (FeatureFlags.IS_DOGFOOD_BUILD) {
					throw(new RuntimeException(desc));
				} else {
					Log.d(TAG, desc);
					getModelWriter().deleteItemFromDatabase(item);
					continue;
				}
			}
		}
		workspace.addInScreenFromBind(view, item);
		if (animateIcons) {
			// Animate all the applications up now
			view.setAlpha(0f);
			view.setScaleX(0f);
			view.setScaleY(0f);
			bounceAnims.add(createNewAppBounceAnimation(view, i));
			newItemsScreenId = item.screenId;
		}
	}

	// Animate to the correct page
	if ((animateIcons) && (newItemsScreenId > -1)) {
		long currentScreenId =
			mWorkspace.getScreenIdForPageIndex(mWorkspace.getNextPage());
		final int newScreenIndex =
			mWorkspace.getPageIndexForScreenId(newItemsScreenId);
		final Runnable startBounceAnimRunnable = ()->{
			anim.playTogether(bounceAnims);
			anim.start();
		};
		if (newItemsScreenId != currentScreenId) {
			// We post the animation slightly delayed to prevent slowdowns
			// when we are loading right after we return to launcher.
			mWorkspace.postDelayed(()->{
					if (mWorkspace != null) {
					        AbstractFloatingView.closeAllOpenViews(Launcher.this, false);
					        mWorkspace.snapToPage(newScreenIndex);
					        mWorkspace.postDelayed(startBounceAnimRunnable,
					                               NEW_APPS_ANIMATION_DELAY);
					}
				}, NEW_APPS_PAGE_MOVE_DELAY);
		} else {
			mWorkspace.postDelayed(startBounceAnimRunnable,
			                       NEW_APPS_ANIMATION_DELAY);
		}
	}
	workspace.requestLayout();
}

/**
 * Add the views for a widget to the workspace.
 */
public void bindAppWidget(final LauncherAppWidgetInfo item) {
	View view = inflateAppWidget(item);
	if (view != null) {
		mWorkspace.addInScreen(view, item);
		mWorkspace.requestLayout();
	}
}

private View inflateAppWidget(final LauncherAppWidgetInfo item) {
	if (mIsSafeModeEnabled) {
		PendingAppWidgetHostView view =
			new PendingAppWidgetHostView(this, item, mIconCache, true);
		prepareAppWidget(view, item);
		return view;
	}

	final long start = DEBUG_WIDGETS ? SystemClock.uptimeMillis() : 0;
	if (DEBUG_WIDGETS) {
		Log.d(TAG, "bindAppWidget: " + item);
	}

	final LauncherAppWidgetProviderInfo appWidgetInfo;

	if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY)) {
		// If the provider is not ready, bind as a pending widget.
		appWidgetInfo = null;
	} else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
		// The widget id is not valid. Try to find the widget based on the
		// provider info.
		appWidgetInfo =
			mAppWidgetManager.findProvider(item.providerName, item.user);
	} else {
		appWidgetInfo =
			mAppWidgetManager.getLauncherAppWidgetInfo(item.appWidgetId);
	}

	// If the provider is ready, but the width is not yet restored, try to
	// restore it.
	if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_PROVIDER_NOT_READY) &&
	    (item.restoreStatus != LauncherAppWidgetInfo.RESTORE_COMPLETED)) {
		if (appWidgetInfo == null) {
			if (DEBUG_WIDGETS) {
				Log.d(TAG, "Removing restored widget: id=" + item.appWidgetId +
				      " belongs to component " + item.providerName +
				      ", as the provider is null");
			}
			getModelWriter().deleteItemFromDatabase(item);
			return null;
		}

		// If we do not have a valid id, try to bind an id.
		if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_NOT_VALID)) {
			if (!item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_ID_ALLOCATED)) {
				// Id has not been allocated yet. Allocate a new id.
				item.appWidgetId = mAppWidgetHost.allocateAppWidgetId();
				item.restoreStatus |= LauncherAppWidgetInfo.FLAG_ID_ALLOCATED;

				// Also try to bind the widget. If the bind fails, the user will be
				// shown a click to setup UI, which will ask for the bind permission.
				PendingAddWidgetInfo pendingInfo =
					new PendingAddWidgetInfo(appWidgetInfo);
				pendingInfo.spanX = item.spanX;
				pendingInfo.spanY = item.spanY;
				pendingInfo.minSpanX = item.minSpanX;
				pendingInfo.minSpanY = item.minSpanY;
				Bundle options = WidgetHostViewLoader.getDefaultOptionsForWidget(
					this, pendingInfo);

				boolean isDirectConfig =
					item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG);
				if (isDirectConfig && item.bindOptions != null) {
					Bundle newOptions = item.bindOptions.getExtras();
					if (options != null) {
						newOptions.putAll(options);
					}
					options = newOptions;
				}
				boolean success = mAppWidgetManager.bindAppWidgetIdIfAllowed(
					item.appWidgetId, appWidgetInfo, options);

				// We tried to bind once. If we were not able to bind, we would need
				// to go through the permission dialog, which means we cannot skip the
				// config activity.
				item.bindOptions = null;
				item.restoreStatus &= ~LauncherAppWidgetInfo.FLAG_DIRECT_CONFIG;

				// Bind succeeded
				if (success) {
					// If the widget has a configure activity, it is still needs to set
					// it up, otherwise the widget is ready to go.
					item.restoreStatus =
						(appWidgetInfo.configure == null) || isDirectConfig
		    ? LauncherAppWidgetInfo.RESTORE_COMPLETED
		    : LauncherAppWidgetInfo.FLAG_UI_NOT_READY;
				}

				getModelWriter().updateItemInDatabase(item);
			}
		} else if (item.hasRestoreFlag(LauncherAppWidgetInfo.FLAG_UI_NOT_READY) &&
		           (appWidgetInfo.configure == null)) {
			// The widget was marked as UI not ready, but there is no configure
			// activity to update the UI.
			item.restoreStatus = LauncherAppWidgetInfo.RESTORE_COMPLETED;
			getModelWriter().updateItemInDatabase(item);
		}
	}

	final AppWidgetHostView view;
	if (item.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
		if (DEBUG_WIDGETS) {
			Log.d(TAG, "bindAppWidget: id=" + item.appWidgetId +
			      " belongs to component " + appWidgetInfo.provider);
		}

		// Verify that we own the widget
		if (appWidgetInfo == null) {
			FileLog.e(TAG, "Removing invalid widget: id=" + item.appWidgetId);
			deleteWidgetInfo(item);
			return null;
		}

		item.minSpanX = appWidgetInfo.minSpanX;
		item.minSpanY = appWidgetInfo.minSpanY;
		view = mAppWidgetHost.createView(this, item.appWidgetId, appWidgetInfo);
	} else {
		view = new PendingAppWidgetHostView(this, item, mIconCache, false);
	}
	prepareAppWidget(view, item);

	if (DEBUG_WIDGETS) {
		Log.d(TAG, "bound widget id=" + item.appWidgetId + " in " +
		      (SystemClock.uptimeMillis() - start) + "ms");
	}
	return view;
}

/**
 * Restores a pending widget.
 *
 * @param appWidgetId The app widget id
 */
private LauncherAppWidgetInfo
completeRestoreAppWidget(final int appWidgetId, final int finalRestoreFlag) {
	LauncherAppWidgetHostView view =
		mWorkspace.getWidgetForAppWidgetId(appWidgetId);
	if ((view == null) || !(view instanceof PendingAppWidgetHostView)) {
		Log.e(TAG, "Widget update called, when the widget no longer exists.");
		return null;
	}

	LauncherAppWidgetInfo info = (LauncherAppWidgetInfo)view.getTag();
	info.restoreStatus = finalRestoreFlag;
	if (info.restoreStatus == LauncherAppWidgetInfo.RESTORE_COMPLETED) {
		info.pendingItemInfo = null;
	}

	if (((PendingAppWidgetHostView)view).isReinflateIfNeeded()) {
		view.reInflate();
	}

	getModelWriter().updateItemInDatabase(info);
	return info;
}

public void onPageBoundSynchronously(final int page) {
	mSynchronouslyBoundPages.add(page);
}

@Override
public void executeOnNextDraw(final ViewOnDrawExecutor executor) {
	if (mPendingExecutor != null) {
		mPendingExecutor.markCompleted();
	}
	mPendingExecutor = executor;
	if (!isInState(ALL_APPS)) {
		mAppsView.getAppsStore().setDeferUpdates(true);
		mPendingExecutor.execute(
			()->mAppsView.getAppsStore().setDeferUpdates(false));
	}

	executor.attachTo(this);
}

public void clearPendingExecutor(final ViewOnDrawExecutor executor) {
	if (mPendingExecutor == executor) {
		mPendingExecutor = null;
	}
}

@Override
public void finishFirstPageBind(final ViewOnDrawExecutor executor) {
	MultiValueAlpha.AlphaProperty property =
		mDragLayer.getAlphaProperty(ALPHA_INDEX_LAUNCHER_LOAD);
	if (property.getValue() < 1) {
		ObjectAnimator anim =
			ObjectAnimator.ofFloat(property, MultiValueAlpha.VALUE, 1);
		if (executor != null) {
			anim.addListener(new AnimatorListenerAdapter() {
					@Override
					public void onAnimationEnd(final Animator animation) {
					        executor.onLoadAnimationCompleted();
					}
				});
		}
		anim.start();
	} else if (executor != null) {
		executor.onLoadAnimationCompleted();
	}
}

/**
 * Callback saying that there aren't any more items to bind.
 *
 * Implementation of the method from LauncherModel.Callbacks.
 */
public void finishBindingItems() {
	TraceHelper.beginSection("finishBindingItems");
	mWorkspace.restoreInstanceStateForRemainingPages();

	setWorkspaceLoading(false);

	if (mPendingActivityResult != null) {
		handleActivityResult(mPendingActivityResult.requestCode,
		                     mPendingActivityResult.resultCode,
		                     mPendingActivityResult.data);
		mPendingActivityResult = null;
	}

	InstallShortcutReceiver.disableAndFlushInstallQueue(
		InstallShortcutReceiver.FLAG_LOADER_RUNNING, this);

	TraceHelper.endSection("finishBindingItems");
}

private boolean canRunNewAppsAnimation() {
	long diff =
		System.currentTimeMillis() - mDragController.getLastGestureUpTime();
	return diff > (NEW_APPS_ANIMATION_INACTIVE_TIMEOUT_SECONDS * 1000);
}

private ValueAnimator createNewAppBounceAnimation(final View v, final int i) {
	ValueAnimator bounceAnim =
		LauncherAnimUtils.ofViewAlphaAndScale(v, 1, 1, 1);
	bounceAnim.setDuration(
		InstallShortcutReceiver.NEW_SHORTCUT_BOUNCE_DURATION);
	bounceAnim.setStartDelay(
		i * InstallShortcutReceiver.NEW_SHORTCUT_STAGGER_DELAY);
	bounceAnim.setInterpolator(
		new OvershootInterpolator(BOUNCE_ANIMATION_TENSION));
	return bounceAnim;
}

public boolean useVerticalBarLayout() {
	return mDeviceProfile.isVerticalBarLayout();
}

/**
 * Add the icons for all apps.
 *
 * Implementation of the method from LauncherModel.Callbacks.
 */
public void bindAllApplications(final ArrayList<AppInfo> apps) {
	mAppsView.getAppsStore().setApps(apps);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.bindAllApplications(apps);
	}
}

/**
 * Copies LauncherModel's map of activities to shortcut ids to Launcher's.
 * This is necessary because LauncherModel's map is updated in the background,
 * while Launcher runs on the UI.
 */
@Override
public void bindDeepShortcutMap(
	final MultiHashMap<ComponentKey, String> deepShortcutMapCopy) {
	mPopupDataProvider.setDeepShortcutMap(deepShortcutMapCopy);
}

/**
 * A package was updated.
 *
 * Implementation of the method from LauncherModel.Callbacks.
 */
@Override
public void bindAppsAddedOrUpdated(final ArrayList<AppInfo> apps) {
	mAppsView.getAppsStore().addOrUpdateApps(apps);
}

@Override
public void bindPromiseAppProgressUpdated(final PromiseAppInfo app) {
	mAppsView.getAppsStore().updatePromiseAppProgress(app);
}

@Override
public void
bindWidgetsRestored(final ArrayList<LauncherAppWidgetInfo> widgets) {
	Runnable r = ()->bindWidgetsRestored(widgets);
	if (waitUntilResume(r)) {
		return;
	}
	mWorkspace.widgetsRestored(widgets);
}

/**
 * Some shortcuts were updated in the background.
 * Implementation of the method from LauncherModel.Callbacks.
 *
 * @param updated list of shortcuts which have changed.
 */
@Override
public void bindShortcutsChanged(final ArrayList<ShortcutInfo> updated,
                                 final UserHandle user) {
	Runnable r = ()->bindShortcutsChanged(updated, user);
	if (waitUntilResume(r)) {
		return;
	}

	if (!updated.isEmpty()) {
		mWorkspace.updateShortcuts(updated);
	}
}

/**
 * Update the state of a package, typically related to install state.
 * <p>
 * Implementation of the method from LauncherModel.Callbacks.
 */
@Override
public void bindRestoreItemsChange(final HashSet<ItemInfo> updates) {
	Runnable r = ()->bindRestoreItemsChange(updates);
	if (waitUntilResume(r)) {
		return;
	}

	mWorkspace.updateRestoreItems(updates);
}

/**
 * A package was uninstalled/updated.  We take both the super set of
 * packageNames in addition to specific applications to remove, the reason
 * being that this can be called when a package is updated as well.  In that
 * scenario, we only remove specific components from the workspace and
 * hotseat, where as package-removal should clear all items by package name.
 */
@Override
public void bindWorkspaceComponentsRemoved(final ItemInfoMatcher matcher) {
	Runnable r = ()->bindWorkspaceComponentsRemoved(matcher);
	if (waitUntilResume(r)) {
		return;
	}
	mWorkspace.removeItemsByMatcher(matcher);
	mDragController.onAppsRemoved(matcher);
}

@Override
public void bindAppInfosRemoved(final ArrayList<AppInfo> appInfos) {
	mAppsView.getAppsStore().removeApps(appInfos);
}

@Override
public void bindAllWidgets(final ArrayList<WidgetListRowEntry> allWidgets) {
	mPopupDataProvider.setAllWidgets(allWidgets);
	AbstractFloatingView topView = AbstractFloatingView.getTopOpenView(this);
	if (topView != null) {
		topView.onWidgetsBound();
	}
}

/**
 * @param packageUser if null, refreshes all widgets and shortcuts, otherwise
 *     only
 *                    refreshes the widgets and shortcuts associated with the
 * given package/user
 */
public void
refreshAndBindWidgetsForPackageUser(final
                                    @Nullable PackageUserKey packageUser) {
	mModel.refreshAndBindWidgetsAndShortcuts(packageUser);
}

/*private void loadExtractedColorsAndColorItems() {
    mExtractedColors.load(this);
    mHotseat.updateColor(mExtractedColors, !mPaused);
   }*/

public BlurWallpaperProvider getBlurWallpaperProvider() {
	return mBlurWallpaperProvider;
}

public void getAllAppsController(final boolean immediate) {
	if (mRotationEnabled) {
		if (immediate) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
		} else {
			mHandler.postDelayed(
				()
				->setRequestedOrientation(
					ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED),
				RESTORE_SCREEN_ORIENTATION_DELAY);
		}
	}
}

/**
 * $ adb shell dumpsys activity com.android.launcher3.Launcher [--all]
 */
@Override
public void dump(final String prefix, final FileDescriptor fd,
                 final PrintWriter writer, final String[] args) {
	super.dump(prefix, fd, writer, args);

	if (args.length > 0 && TextUtils.equals(args[0], "--all")) {
		writer.println(prefix + "Workspace Items");
		for (int i = 0; i < mWorkspace.getPageCount(); i++) {
			writer.println(prefix + "  Homescreen " + i);

			ViewGroup layout =
				((CellLayout)mWorkspace.getPageAt(i)).getShortcutsAndWidgets();
			for (int j = 0; j < layout.getChildCount(); j++) {
				Object tag = layout.getChildAt(j).getTag();
				if (tag != null) {
					writer.println(prefix + "    " + tag.toString());
				}
			}
		}

		writer.println(prefix + "  Hotseat");
		ViewGroup layout = mHotseat.getLayout().getShortcutsAndWidgets();
		for (int j = 0; j < layout.getChildCount(); j++) {
			Object tag = layout.getChildAt(j).getTag();
			if (tag != null) {
				writer.println(prefix + "    " + tag.toString());
			}
		}
	}

	writer.println(prefix + "Misc:");
	writer.print(prefix + "\tmWorkspaceLoading=" + mWorkspaceLoading);
	writer.print(" mPendingRequestArgs=" + mPendingRequestArgs);
	writer.println(" mPendingActivityResult=" + mPendingActivityResult);
	writer.println(" mRotationHelper: " + mRotationHelper);
	dumpMisc(writer);

	try {
		FileLog.flushAll(writer);
	} catch (Exception e) {
		// Ignore
	}

	mModel.dumpState(prefix, fd, writer, args);

	if (mLauncherCallbacks != null) {
		mLauncherCallbacks.dump(prefix, fd, writer, args);
	}
}

@Override
@TargetApi(Build.VERSION_CODES.N)
public void onProvideKeyboardShortcuts(final List<KeyboardShortcutGroup> data,
                                       final Menu menu, final int deviceId) {

	ArrayList<KeyboardShortcutInfo> shortcutInfos = new ArrayList<>();
	if (isInState(NORMAL)) {
		shortcutInfos.add(
			new KeyboardShortcutInfo(getString(R.string.all_apps_button_label),
			                         KeyEvent.KEYCODE_A, KeyEvent.META_CTRL_ON));
		shortcutInfos.add(
			new KeyboardShortcutInfo(getString(R.string.widget_button_text),
			                         KeyEvent.KEYCODE_W, KeyEvent.META_CTRL_ON));
	}
	final View currentFocus = getCurrentFocus();
	if (currentFocus != null) {
		if (new CustomActionsPopup(this, currentFocus).canShow()) {
			shortcutInfos.add(new KeyboardShortcutInfo(
						  getString(R.string.custom_actions), KeyEvent.KEYCODE_O,
						  KeyEvent.META_CTRL_ON));
		}
		if (currentFocus.getTag() instanceof ItemInfo &&
		    DeepShortcutManager.supportsShortcuts(
			    (ItemInfo)currentFocus.getTag())) {
			shortcutInfos.add(new KeyboardShortcutInfo(
						  getString(R.string.shortcuts_menu_with_notifications_description),
						  KeyEvent.KEYCODE_S, KeyEvent.META_CTRL_ON));
		}
	}
	if (!shortcutInfos.isEmpty()) {
		data.add(new KeyboardShortcutGroup(getString(R.string.home_screen),
		                                   shortcutInfos));
	}

	super.onProvideKeyboardShortcuts(data, menu, deviceId);
}

@Override
public boolean onKeyShortcut(final int keyCode, final KeyEvent event) {
	if (event.hasModifiers(KeyEvent.META_CTRL_ON)) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_A:
			if (isInState(NORMAL)) {
				getStateManager().goToState(ALL_APPS);
				return true;
			}
			break;
		case KeyEvent.KEYCODE_S: {
			View focusedView = getCurrentFocus();
			if (focusedView instanceof BubbleTextView &&
			    focusedView.getTag() instanceof ItemInfo &&
			    mAccessibilityDelegate.performAction(
				    focusedView, (ItemInfo)focusedView.getTag(),
				    LauncherAccessibilityDelegate.DEEP_SHORTCUTS)) {
				PopupContainerWithArrow.getOpen(this).requestFocus();
				return true;
			}
			break;
		}
		case KeyEvent.KEYCODE_O:
			if (new CustomActionsPopup(this, getCurrentFocus()).show()) {
				return true;
			}
			break;
		case KeyEvent.KEYCODE_W:
			if (isInState(NORMAL)) {
				OptionsPopupView.openWidgets(this);
				return true;
			}
			break;
		}
	}
	return super.onKeyShortcut(keyCode, event);
}

@Override
public boolean onKeyUp(final int keyCode, final KeyEvent event) {
	if (keyCode == KeyEvent.KEYCODE_MENU) {
		// KEYCODE_MENU is sent by some tests, for example
		// LauncherJankTests#testWidgetsContainerFling. Don't just remove its
		// handling.
		if (!mDragController.isDragging() && !mWorkspace.isSwitchingState() &&
		    isInState(NORMAL)) {
			// Close any open floating views.
			AbstractFloatingView.closeAllOpenViews(this);

			// Setting the touch point to (-1, -1) will show the options popup in
			// the center of the screen.
			OptionsPopupView.showDefaultOptions(this, -1, -1);
		}
		return true;
	}
	return super.onKeyUp(keyCode, event);
}

@Override
public void onThemeChanged() {
	recreate();
}

/**
 * Callback for listening for onResume
 */
public interface OnResumeCallback { void onLauncherResume(); }

public interface LauncherOverlay {

/**
 * Touch interaction leading to overscroll has begun
 */
void onScrollInteractionBegin();

/**
 * Touch interaction related to overscroll has ended
 */
void onScrollInteractionEnd();

/**
 * Scroll progress, between 0 and 100, when the user scrolls beyond the
 * leftmost screen (or in the case of RTL, the rightmost screen).
 */
void onScrollChange(float progress, boolean rtl);

/**
 * Called when the launcher is ready to use the overlay
 *
 * @param callbacks A set of callbacks provided by Launcher in relation to
 *     the overlay
 */
void setOverlayCallbacks(LauncherOverlayCallbacks callbacks);
}

public interface LauncherOverlayCallbacks {
void onScrollChanged(float progress);
}

class LauncherOverlayCallbacksImpl
	implements LauncherOverlayCallbacks, LauncherStateManager.StateListener {

public void onScrollChanged(final float progress) {
	addOrRemoveStateListener(progress);
	if (mWorkspace != null) {
		mWorkspace.onOverlayScrollChanged(progress);
	}
}

private void addOrRemoveStateListener(final float progress) {
	if (Float.compare(progress, 1.0f) == 0) {
		getStateManager().addStateListener(this);
	} else if (Float.compare(progress, 0.0f) == 0) {
		getStateManager().removeStateListener(this);
	}
}

private void hideOverlay(final LauncherState launcherState,
                         final boolean animate) {
	if (launcherState == LauncherState.OVERVIEW ||
	    launcherState == LauncherState.FAST_OVERVIEW) {
		hideOverlay(animate);
	}
}

private void hideOverlay(final boolean animate) {
	Launcher launcher = Launcher.this;
	if (launcher instanceof NexusLauncherActivity) {
		Objects.requireNonNull(((NexusLauncherActivity)launcher).getGoogleNow())
		.hideOverlay(animate);
	}
}

@Override
public void onStateSetImmediately(final LauncherState state) {
	hideOverlay(state, false);
}

@Override
public void onStateTransitionStart(final LauncherState toState) {
	hideOverlay(toState, true);
}

@Override
public void onStateTransitionComplete(final LauncherState finalState) {
}

public boolean hasSettings() {
	if (mLauncherCallbacks != null) {
		return mLauncherCallbacks.hasSettings();
	} else {
		// On O and above we there is always some setting present settings (add
		// icon to home screen or icon badging). On earlier APIs we will have
		// the allow rotation setting, on devices with a locked orientation,
		return Utilities.ATLEAST_OREO ||
		       !getResources().getBoolean(R.bool.allow_rotation);
	}
}

public boolean isInState(final LauncherState state) {
	return mStateManager.getState() == state;
}

public boolean isInOverview() {
	LauncherState state = mStateManager.getState();
	LauncherState toState = mStateManager.getToState();
	return (state == LauncherState.OVERVIEW &&
	        toState != LauncherState.NORMAL) ||
	       state == LauncherState.FAST_OVERVIEW;
}
}
}
