/*
 * Copyright (C) 2015 The Android Open Source Project
 * Copyright (C) 2017 The MoKee Open Source Project
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

package org.zimmob.zimlx.settings.ui;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.ActionMenuView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentManager.OnBackStackChangedListener;
import androidx.preference.EditTextPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceRecyclerViewAccessibilityDelegate;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;
import androidx.preference.internal.AbstractMultiSelectListPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.android.launcher3.BuildConfig;
import com.android.launcher3.LauncherFiles;
import com.android.launcher3.LauncherSettings;
import com.android.launcher3.R;
import com.android.launcher3.SessionCommitReceiver;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.notification.NotificationListener;
import com.android.launcher3.util.ComponentKey;
import com.android.launcher3.util.ContentWriter;
import com.android.launcher3.util.SettingsObserver;
import com.android.launcher3.views.ButtonPreference;
import com.jaredrummler.android.colorpicker.ColorPickerDialog;
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener;

import net.gsantner.opoc.format.markdown.SimpleMarkdownParser;

import org.jetbrains.annotations.NotNull;
import org.zimmob.zimlx.FakeLauncherKt;
import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.ZimPreferencesChangeCallback;
import org.zimmob.zimlx.ZimUtilsKt;
import org.zimmob.zimlx.colors.ThemedEditTextPreferenceDialogFragmentCompat;
import org.zimmob.zimlx.colors.ThemedListPreferenceDialogFragment;
import org.zimmob.zimlx.colors.ThemedMultiSelectListPreferenceDialogFragmentCompat;
import org.zimmob.zimlx.colors.preferences.ColorPickerPreference;
import org.zimmob.zimlx.gestures.ui.GesturePreference;
import org.zimmob.zimlx.gestures.ui.SelectGestureHandlerFragment;
import org.zimmob.zimlx.globalsearch.ui.SearchProviderPreference;
import org.zimmob.zimlx.globalsearch.ui.SelectSearchProviderFragment;
import org.zimmob.zimlx.preferences.ColorPreferenceCompat;
import org.zimmob.zimlx.preferences.GridSizeDialogFragmentCompat;
import org.zimmob.zimlx.preferences.GridSizePreference;
import org.zimmob.zimlx.preferences.IconShapePreference;
import org.zimmob.zimlx.preferences.ResumablePreference;
import org.zimmob.zimlx.preferences.SingleDimensionGridSizeDialogFragmentCompat;
import org.zimmob.zimlx.preferences.SingleDimensionGridSizePreference;
import org.zimmob.zimlx.preferences.SmartspaceEventProvidersFragment;
import org.zimmob.zimlx.preferences.SmartspaceEventProvidersPreference;
import org.zimmob.zimlx.smartspace.FeedBridge;
import org.zimmob.zimlx.smartspace.OnboardingProvider;
import org.zimmob.zimlx.theme.ThemeOverride;
import org.zimmob.zimlx.theme.ThemeOverride.ThemeSet;
import org.zimmob.zimlx.views.SpringRecyclerView;

import java.io.IOException;
import java.util.Objects;
import java.util.Set;

import static androidx.preference.PreferenceFragmentCompat.OnPreferenceDisplayDialogCallback;
import static androidx.recyclerview.widget.RecyclerView.Adapter;
import static androidx.recyclerview.widget.RecyclerView.AdapterDataObserver;

/**
 * Settings activity for Launcher.
 */
public class SettingsActivity extends SettingsBaseActivity implements
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, OnPreferenceDisplayDialogCallback,
    OnBackStackChangedListener, OnClickListener {

    public static final String EXTRA_FRAGMENT_ARG_KEY = ":settings:fragment_args_key";

    private static final String ICON_BADGING_PREFERENCE_KEY = "pref_icon_badging";
    /**
     * Hidden field Settings.Secure.NOTIFICATION_BADGING
     */
    public static final String NOTIFICATION_BADGING = "notification_badging";
    /**
     * Hidden field Settings.Secure.ENABLED_NOTIFICATION_LISTENERS
     */
    private final static String NOTIFICATION_ENABLED_LISTENERS = "enabled_notification_listeners";

    public final static String SHOW_PREDICTIONS_PREF = "pref_show_predictions";
    public final static String ENABLE_MINUS_ONE_PREF = "pref_enable_minus_one";
    public final static String FEED_THEME_PREF = "pref_feedTheme";
    public final static String SMARTSPACE_PREF = "pref_smartspace";
    public final static String ALLOW_OVERLAP_PREF = "pref_allowOverlap";
    private final static String BRIDGE_TAG = "tag_bridge";

    public final static String EXTRA_TITLE = "title";

    public final static String EXTRA_FRAGMENT = "fragment";
    public final static String EXTRA_FRAGMENT_ARGS = "fragmentArgs";
    private boolean isSubSettings;
    protected boolean forceSubSettings = false;

    private boolean hasPreview = false;
    public static String defaultHome = "";

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        savedInstanceState = getRelaunchInstanceState(savedInstanceState);

        String fragmentName = getIntent().getStringExtra(EXTRA_FRAGMENT);
        int content = getIntent().getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        isSubSettings = content != 0 || fragmentName != null || forceSubSettings;
        hasPreview = getIntent().getBooleanExtra(SubSettingsFragment.HAS_PREVIEW, false);

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        if (savedInstanceState == null) {
            Fragment fragment = createLaunchFragment(getIntent());
            // Display the fragment as the main content.
            getSupportFragmentManager().beginTransaction()
            .replace(R.id.content, fragment)
            .commit();
        }

        getSupportFragmentManager().addOnBackStackChangedListener(this);

        updateUpButton();

        if (hasPreview) {
            overrideOpenAnim();
        }

        Utilities.getDevicePrefs(this).edit().putBoolean(OnboardingProvider.PREF_HAS_OPENED_SETTINGS, true).apply();
        defaultHome = resolveDefaultHome();
    }

    @Override
    public void finish() {
        super.finish();

        if (hasPreview) {
            overrideCloseAnim();
        }
    }

    protected Fragment createLaunchFragment(final Intent intent) {
        CharSequence title = intent.getCharSequenceExtra(EXTRA_TITLE);
        if (title != null) {
            setTitle(title);
        }
        String fragment = intent.getStringExtra(EXTRA_FRAGMENT);
        if (fragment != null) {
            return Fragment.instantiate(this, fragment, intent.getBundleExtra(EXTRA_FRAGMENT_ARGS));
        }
        int content = intent.getIntExtra(SubSettingsFragment.CONTENT_RES_ID, 0);
        return content != 0
               ? SubSettingsFragment.newInstance(getIntent())
               : new LauncherSettingsFragment();
    }

    protected boolean shouldShowSearch() {
        return FeatureFlags.FEATURE_SETTINGS_SEARCH && !isSubSettings;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (shouldShowSearch()) {
            Toolbar toolbar = findViewById(R.id.search_action_bar);
            toolbar.getMenu().clear();
            toolbar.inflateMenu(R.menu.menu_settings);
            ActionMenuView menuView;
            int count = toolbar.getChildCount();
            for (int i = 0; i < count; i++) {
                View child = toolbar.getChildAt(i);
                if (child instanceof ActionMenuView) {
                    menuView = (ActionMenuView) child;
                    break;
                }
            }

            if (!BuildConfig.APPLICATION_ID.equals(resolveDefaultHome())) {
                toolbar.inflateMenu(R.menu.menu_change_default_home);
            }
            toolbar.setOnMenuItemClickListener(menuItem -> {
                switch (menuItem.getItemId()) {
                case R.id.action_change_default_home:
                    FakeLauncherKt.changeDefaultHome(this);
                    break;
                case R.id.action_restart_zim:
                    Utilities.killLauncher();
                    break;
                default:
                    return false;
                }
                return true;
            });
        }
    }

    private String resolveDefaultHome() {
        Intent homeIntent = new Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_HOME);
        ResolveInfo info = getPackageManager()
                           .resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY);
        if (info != null && info.activityInfo != null) {
            return info.activityInfo.packageName;
        } else {
            return null;
        }
    }

    @Override
    public void onClick(final View v) {
        /*if (v.getId() == R.id.search_action_bar) {
            //startActivity(new Intent(this, SettingsSearchActivity.class));
        }*/
    }

    @NotNull
    @Override
    protected ThemeSet getThemeSet() {
        if (hasPreview) {
            return new ThemeOverride.SettingsTransparent();
        } else {
            return super.getThemeSet();
        }
    }

    @Override
    public boolean onPreferenceStartFragment(final PreferenceFragmentCompat caller, final Preference preference) {
        Fragment fragment;
        if (preference instanceof SubPreference) {
            ((SubPreference) preference).start(this);
            return true;
        } else if (preference instanceof ColorPickerPreference) {
            ((ColorPickerPreference) preference).showDialog(getSupportFragmentManager());
            return true;
        } else {
            fragment = Fragment.instantiate(this, preference.getFragment(), preference.getExtras());
        }
        if (fragment instanceof DialogFragment) {
            ((DialogFragment) fragment).show(getSupportFragmentManager(), preference.getKey());
        } else {
            startFragment(this, preference.getFragment(), preference.getExtras(), preference.getTitle());
        }
        return true;
    }

    @Override
    public boolean onPreferenceDisplayDialog(final @NonNull PreferenceFragmentCompat caller, final Preference pref) {
        if (ENABLE_MINUS_ONE_PREF.equals(pref.getKey())) {
            InstallFragment fragment = new InstallFragment();
            fragment.show(getSupportFragmentManager(), BRIDGE_TAG);
            return true;
        }
        return false;
    }

    private void updateUpButton() {
        updateUpButton(isSubSettings || getSupportFragmentManager().getBackStackEntryCount() != 0);
    }

    private void updateUpButton(final boolean enabled) {
        if (getSupportActionBar() == null) {
            return;
        }
        getSupportActionBar().setDisplayHomeAsUpEnabled(enabled);
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackStackChanged() {
        updateUpButton();
    }

    public abstract static class BaseFragment extends PreferenceFragmentCompat {

        private static final String SAVE_HIGHLIGHTED_KEY = "android:preference_highlighted";

        private HighlightablePreferenceGroupAdapter mAdapter;
        private boolean mPreferenceHighlighted = false;

        private Adapter mCurrentRootAdapter;
        private boolean mIsDataSetObserverRegistered = false;
        private AdapterDataObserver mDataSetObserver =
        new AdapterDataObserver() {
            @Override
            public void onChanged() {
                onDataSetChanged();
            }

            @Override
            public void onItemRangeChanged(final int positionStart, final int itemCount) {
                onDataSetChanged();
            }

            @Override
            public void onItemRangeChanged(final int positionStart, final int itemCount,
                                           final Object payload) {
                onDataSetChanged();
            }

            @Override
            public void onItemRangeInserted(final int positionStart, final int itemCount) {
                onDataSetChanged();
            }

            @Override
            public void onItemRangeRemoved(final int positionStart, final int itemCount) {
                onDataSetChanged();
            }

            @Override
            public void onItemRangeMoved(final int fromPosition, final int toPosition, final int itemCount) {
                onDataSetChanged();
            }
        };

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            if (savedInstanceState != null) {
                mPreferenceHighlighted = savedInstanceState.getBoolean(SAVE_HIGHLIGHTED_KEY);
            }
        }

        public void highlightPreferenceIfNeeded() {
            if (!isAdded()) {
                return;
            }
            if (mAdapter != null) {
                mAdapter.requestHighlight(Objects.requireNonNull(getView()), getListView());
            }
        }

        @SuppressLint("RestrictedApi")
        public RecyclerView onCreateRecyclerView(final LayoutInflater inflater, final ViewGroup parent,
                final Bundle savedInstanceState) {
            RecyclerView recyclerView = (RecyclerView) inflater
                                        .inflate(getRecyclerViewLayoutRes(), parent, false);
            if (recyclerView instanceof SpringRecyclerView) {
                ((SpringRecyclerView) recyclerView).setShouldTranslateSelf(false);
            }

            recyclerView.setLayoutManager(onCreateLayoutManager());
            recyclerView.setAccessibilityDelegateCompat(
                new PreferenceRecyclerViewAccessibilityDelegate(recyclerView));

            return recyclerView;
        }

        abstract protected int getRecyclerViewLayoutRes();

        @Override
        public void setDivider(final Drawable divider) {
            super.setDivider(null);
        }

        @Override
        public void setDividerHeight(final int height) {
            super.setDividerHeight(0);
        }

        @Override
        protected Adapter onCreateAdapter(final PreferenceScreen preferenceScreen) {
            final Bundle arguments = getActivity().getIntent().getExtras();
            mAdapter = new HighlightablePreferenceGroupAdapter(preferenceScreen,
                    arguments == null
                    ? null : arguments.getString(SettingsActivity.EXTRA_FRAGMENT_ARG_KEY),
                    mPreferenceHighlighted);
            return mAdapter;
        }

        @Override
        public void onSaveInstanceState(final Bundle outState) {
            super.onSaveInstanceState(outState);

            if (mAdapter != null) {
                outState.putBoolean(SAVE_HIGHLIGHTED_KEY, mAdapter.isHighlightRequested());
            }
        }

        protected void onDataSetChanged() {
            highlightPreferenceIfNeeded();
        }

        public int getInitialExpandedChildCount() {
            return -1;
        }

        @Override
        public void onResume() {
            super.onResume();
            highlightPreferenceIfNeeded();

            dispatchOnResume(getPreferenceScreen());
        }

        public void dispatchOnResume(final PreferenceGroup group) {
            int count = group.getPreferenceCount();
            for (int i = 0; i < count; i++) {
                Preference preference = group.getPreference(i);

                if (preference instanceof ResumablePreference) {
                    ((ResumablePreference) preference).onResume();
                }

                if (preference instanceof PreferenceGroup) {
                    dispatchOnResume((PreferenceGroup) preference);
                }
            }
        }

        @Override
        protected void onBindPreferences() {
            registerObserverIfNeeded();
        }

        @Override
        protected void onUnbindPreferences() {
            unregisterObserverIfNeeded();
        }

        public void registerObserverIfNeeded() {
            if (!mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                }
                mCurrentRootAdapter = getListView().getAdapter();
                mCurrentRootAdapter.registerAdapterDataObserver(mDataSetObserver);
                mIsDataSetObserverRegistered = true;
                onDataSetChanged();
            }
        }

        public void unregisterObserverIfNeeded() {
            if (mIsDataSetObserverRegistered) {
                if (mCurrentRootAdapter != null) {
                    mCurrentRootAdapter.unregisterAdapterDataObserver(mDataSetObserver);
                    mCurrentRootAdapter = null;
                }
                mIsDataSetObserverRegistered = false;
            }
        }

        void onPreferencesAdded(final PreferenceGroup group) {
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference preference = group.getPreference(i);

                if (preference instanceof ControlledPreference) {
                    PreferenceController controller = ((ControlledPreference) preference)
                                                      .getController();
                    if (controller != null) {
                        if (!controller.onPreferenceAdded(preference)) {
                            i--;
                            continue;
                        }
                    }
                }

                if (preference instanceof PreferenceGroup) {
                    onPreferencesAdded((PreferenceGroup) preference);
                }

            }
        }
    }

    /**
     * This fragment shows the launcher preferences.
     */
    public static class LauncherSettingsFragment extends BaseFragment {

        private boolean mShowDevOptions;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mShowDevOptions = Utilities.getZimPrefs(getActivity()).getDeveloperOptionsEnabled();
            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            setHasOptionsMenu(true);
        }

        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            addPreferencesFromResource(R.xml.zim_preferences);
            onPreferencesAdded(getPreferenceScreen());
        }

        @Override
        public void onResume() {
            super.onResume();
            getActivity().setTitle(R.string.settings_button_text);
            getActivity().setTitleColor(R.color.white);
            boolean dev = Utilities.getZimPrefs(getActivity()).getDeveloperOptionsEnabled();
            if (dev != mShowDevOptions) {
                getActivity().recreate();
            }
        }

        @Override
        public boolean onPreferenceTreeClick(final Preference preference) {
            return super.onPreferenceTreeClick(preference);
        }

        @Override
        public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
            inflater.inflate(R.menu.menu_settings, menu);
            if (!BuildConfig.APPLICATION_ID.equals(defaultHome)) {
                inflater.inflate(R.menu.menu_change_default_home, menu);
            }
        }

        @Override
        public boolean onOptionsItemSelected(final MenuItem item) {
            switch (item.getItemId()) {
            case R.id.action_change_default_home:
                FakeLauncherKt.changeDefaultHome(getContext());
                break;
            case R.id.action_restart_zim:
                Utilities.killLauncher();
                break;
            case R.id.action_dev_options:
                Intent intent = new Intent(getContext(), SettingsActivity.class);
                intent.putExtra(SettingsActivity.SubSettingsFragment.TITLE,
                                getString(R.string.developer_options_title));
                intent.putExtra(SettingsActivity.SubSettingsFragment.CONTENT_RES_ID,
                                R.xml.zim_preferences_dev_options);
                intent.putExtra(SettingsBaseActivity.EXTRA_FROM_SETTINGS, true);
                startActivity(intent);
                break;
            default:
                return false;
            }

            return true;
        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_dialog_recyclerview;
        }
    }

    public static class SubSettingsFragment extends BaseFragment implements
        Preference.OnPreferenceChangeListener, Preference.OnPreferenceClickListener {

        public static final String TITLE = "title";
        public static final String CONTENT_RES_ID = "content_res_id";
        public static final String HAS_PREVIEW = "has_preview";

        private SystemDisplayRotationLockObserver mRotationLockObserver;
        private IconBadgingObserver mIconBadgingObserver;

        private Context mContext;

        @Override
        public void onCreate(final Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            mContext = getActivity();

            getPreferenceManager().setSharedPreferencesName(LauncherFiles.SHARED_PREFERENCES_KEY);
            int preference = getContent();
            ContentResolver resolver = mContext.getContentResolver();
            switch (preference) {
            case R.xml.zim_preferences_desktop:
                if (!Utilities.ATLEAST_OREO) {
                    getPreferenceScreen().removePreference(
                        findPreference(SessionCommitReceiver.ADD_ICON_PREFERENCE_KEY));
                }
                // Setup allow rotation preference
                Preference rotationPref = findPreference(Utilities.ALLOW_ROTATION_PREFERENCE_KEY);
                if (getResources().getBoolean(R.bool.allow_rotation)) {
                    // Launcher supports rotation by default. No need to show this setting.
                    getPreferenceScreen().removePreference(rotationPref);
                } else {
                    mRotationLockObserver = new SystemDisplayRotationLockObserver(rotationPref, resolver);

                    // Register a content observer to listen for system setting changes while
                    // this UI is active.
                    mRotationLockObserver.register(Settings.System.ACCELEROMETER_ROTATION);

                    // Initialize the UI once
                    rotationPref.setDefaultValue(Utilities.getAllowRotationDefaultValue(getActivity()));
                }

                break;

            case R.xml.zim_preferences_app_drawer:
                findPreference(SHOW_PREDICTIONS_PREF).setOnPreferenceChangeListener(this);
                break;

            case R.xml.zim_preferences_theme:
                Preference resetIconsPreference = findPreference("pref_resetCustomIcons");
                resetIconsPreference.setOnPreferenceClickListener(pref -> {
                    new SettingsActivity.ResetIconsConfirmation()
                    .show(getFragmentManager(), "reset_icons");
                    return true;
                });

                break;

            case R.xml.zim_preferences_notification:
                if (getResources().getBoolean(R.bool.notification_badging_enabled)) {
                    ButtonPreference iconBadgingPref = (ButtonPreference) findPreference(ICON_BADGING_PREFERENCE_KEY);
                    // Listen to system notification badge settings while this UI is active.
                    mIconBadgingObserver = new IconBadgingObserver(
                        iconBadgingPref, getActivity().getContentResolver(), getFragmentManager());
                    mIconBadgingObserver.register(NOTIFICATION_BADGING, NOTIFICATION_ENABLED_LISTENERS);

                }

                break;

            case R.xml.zim_preferences_dev_options:
                findPreference("kill").setOnPreferenceClickListener(this);
                break;

            case R.xml.zim_preferences_about:
                AboutUtils au = new AboutUtils(getActivity(), getContext());
                Preference app = findPreference("pref_key__about_app");
                Preference appInfo = findPreference("pref_key__about_copy_build_information");
                au.updateAppSummary(app);
                au.updateAppInfoSummary(appInfo);
                updateProjectTeam(au);
                break;
            }
        }

        public void updateProjectTeam(final AboutUtils au) {
            Preference pref;
            if ((pref = findPreference("pref_key__about_project_team")) != null && ((PreferenceGroup) pref).getPreferenceCount() == 0) {
                String[] data = (au.readTextfileFromRawRes(R.raw.team, "", "").trim() + "\n\n").split("\n");
                for (int i = 0; i + 2 < data.length; i += 4) {
                    Preference person = new Preference(getContext());
                    person.setTitle(data[i]);
                    person.setSummary(data[i + 1]);
                    person.setIcon(R.drawable.ic_person_black_24dp);
                    try {
                        Uri uri = Uri.parse(data[i + 2]);
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        person.setIntent(intent);
                    } catch (Exception ignored) {
                    }
                    appendPreference(person, (PreferenceGroup) pref);
                }

            }
        }

        protected boolean appendPreference(final Preference pref, final @Nullable PreferenceGroup target) {
            if (target == null) {
                if ((target = getPreferenceScreen()) == null) {
                    return false;
                }
            }
            if (pref.getIcon() != null) {
                pref.setIcon(pref.getIcon());
            }
            return target.addPreference(pref);
        }

        @Override
        public void onCreatePreferences(final Bundle savedInstanceState, final String rootKey) {
            addPreferencesFromResource(getContent());
            onPreferencesAdded(getPreferenceScreen());
        }

        private int getContent() {
            return getArguments().getInt(CONTENT_RES_ID);
        }

        @Override
        public void onResume() {
            super.onResume();
            setActivityTitle();
            if (getContent() == R.xml.zim_preferences_smartspace) {
                SwitchPreference minusOne = (SwitchPreference) findPreference(ENABLE_MINUS_ONE_PREF);
                if (minusOne != null && !FeedBridge.Companion.getInstance(getActivity())
                        .isInstalled()) {
                    minusOne.setChecked(false);
                }
            }
        }

        protected void setActivityTitle() {
            getActivity().setTitle(getArguments().getString(TITLE));
        }

        @Override
        public void onDestroy() {
            if (mRotationLockObserver != null) {
                mRotationLockObserver.unregister();
                mRotationLockObserver = null;
            }
            super.onDestroy();
        }

        @Override
        public void onDisplayPreferenceDialog(final Preference preference) {
            final DialogFragment f;
            if (preference instanceof GridSizePreference) {
                f = GridSizeDialogFragmentCompat.Companion.newInstance(preference.getKey());
            } else if (preference instanceof SingleDimensionGridSizePreference) {
                f = SingleDimensionGridSizeDialogFragmentCompat.Companion
                    .newInstance(preference.getKey());
            } else if (preference instanceof GesturePreference) {
                f = SelectGestureHandlerFragment.Companion
                    .newInstance((GesturePreference) preference);
            } else if (preference instanceof SearchProviderPreference) {
                f = SelectSearchProviderFragment.Companion
                    .newInstance((SearchProviderPreference) preference);
            } else if (preference instanceof PreferenceDialogPreference) {
                f = PreferenceScreenDialogFragment.Companion
                    .newInstance((PreferenceDialogPreference) preference);
            } else if (preference instanceof IconShapePreference) {
                f = ((IconShapePreference) preference).createDialogFragment();
            } else if (preference instanceof ListPreference) {
                Log.d("success", "onDisplayPreferenceDialog: yay");
                f = ThemedListPreferenceDialogFragment.Companion.newInstance(preference.getKey());
            } else if (preference instanceof EditTextPreference) {
                f = ThemedEditTextPreferenceDialogFragmentCompat.Companion
                    .newInstance(preference.getKey());
            } else if (preference instanceof AbstractMultiSelectListPreference) {
                f = ThemedMultiSelectListPreferenceDialogFragmentCompat.Companion
                    .newInstance(preference.getKey());
            } else if (preference instanceof SmartspaceEventProvidersPreference) {
                f = SmartspaceEventProvidersFragment.Companion.newInstance(preference.getKey());
            } else {
                super.onDisplayPreferenceDialog(preference);
                return;
            }
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), "android.support.v7.preference.PreferenceFragment.DIALOG");
            //f.show(getFragmentManager(), preference.getKey());
        }

        public static SubSettingsFragment newInstance(final SubPreference preference) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, (String) preference.getTitle());
            b.putInt(CONTENT_RES_ID, preference.getContent());
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(final Intent intent) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, intent.getStringExtra(TITLE));
            b.putInt(CONTENT_RES_ID, intent.getIntExtra(CONTENT_RES_ID, 0));
            fragment.setArguments(b);
            return fragment;
        }

        public static SubSettingsFragment newInstance(final String title, final @XmlRes int content) {
            SubSettingsFragment fragment = new SubSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        public boolean onPreferenceChange(final Preference preference, final Object newValue) {
            switch (preference.getKey()) {
            case SHOW_PREDICTIONS_PREF:
                if ((boolean) newValue) {
                    return true;
                }
                SuggestionConfirmationFragment confirmationFragment = new SuggestionConfirmationFragment();
                confirmationFragment.setTargetFragment(this, 0);
                confirmationFragment.show(getFragmentManager(), preference.getKey());
                break;

            case ENABLE_MINUS_ONE_PREF:
                if (FeedBridge.Companion.getInstance(getActivity()).isInstalled()) {
                    return true;
                }
                FragmentManager fm = getFragmentManager();
                if (fm.findFragmentByTag(BRIDGE_TAG) == null) {
                    InstallFragment fragment = new InstallFragment();
                    fragment.show(fm, BRIDGE_TAG);
                }
                break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceClick(final Preference preference) {
            switch (preference.getKey()) {
            case "kill":
                Utilities.killLauncher();
                break;
            }
            return false;
        }

        @Override
        public boolean onPreferenceTreeClick(final Preference preference) {
            AboutUtils au = new AboutUtils(getActivity(), getContext());
            if (preference.getKey() != null) {
                switch (preference.getKey()) {
                case "pref_key__about_rate_app":
                    au.showGooglePlayEntryForThisApp();
                    break;

                case "pref_key__about_donate":
                    au.openWebpageInExternalBrowser(getString(R.string.app_donate_url));
                    break;

                case "pref_key__about_bug_report":
                    au.openWebpageInExternalBrowser(getString(R.string.app_bugreport_url));
                    break;

                case "pref_key__about_project_license":
                    try {
                        au.showDialogWithHtmlTextView(R.string.licenses, new SimpleMarkdownParser().parse(
                                                          getResources().openRawResource(R.raw.license),
                                                          "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW).getHtml());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;

                case "pref_key__about_open_source_licenses":
                    try {
                        au.showDialogWithHtmlTextView(R.string.licenses, new SimpleMarkdownParser().parse(
                                                          getResources().openRawResource(R.raw.opensource),
                                                          "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW).getHtml());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;

                case "pref_key__about_source_code":
                    au.openWebpageInExternalBrowser(getString(R.string.app_source_code_url));
                    break;

                case "pref_key__about_contributors_public_info":
                    try {
                        au.showDialogWithHtmlTextView(R.string.contributors, new SimpleMarkdownParser().parse(
                                                          getResources().openRawResource(R.raw.contributors),
                                                          "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW).getHtml());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                case "pref_key__about_copy_build_information":
                    au.setClipboard(preference.getSummary());
                    SimpleMarkdownParser smp = new SimpleMarkdownParser();
                    try {
                        String html = smp.parse(getResources().openRawResource(R.raw.changelog), "", SimpleMarkdownParser.FILTER_ANDROID_TEXTVIEW, SimpleMarkdownParser.FILTER_CHANGELOG).getHtml();
                        au.showDialogWithHtmlTextView(R.string.changelog, html);
                    } catch (Exception ignored) {

                    }
                    break;

                default:
                    if (preference instanceof ColorPreferenceCompat) {
                        ColorPickerDialog dialog = ((ColorPreferenceCompat) preference).getDialog();
                        dialog.setColorPickerDialogListener(new ColorPickerDialogListener() {
                            public void onColorSelected(final int dialogId, final int color) {
                                ((ColorPreferenceCompat) preference).saveValue(color);
                            }

                            public void onDialogDismissed(final int dialogId) {
                            }
                        });
                        dialog.show((getActivity()).getSupportFragmentManager(), "color-picker-dialog");
                    } else if (preference.getFragment() != null) {
                        Log.d("Settings", "Opening Fragment: " + preference.getFragment());
                        SettingsActivity.startFragment(getContext(), preference.getFragment(), null, preference.getTitle());
                    }
                }
            }
            return false;
        }

        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_insettable_recyclerview;
        }
    }

    public static class DialogSettingsFragment extends SubSettingsFragment {

        @Override
        protected void setActivityTitle() {

        }

        @Override
        protected int getRecyclerViewLayoutRes() {
            return R.layout.preference_dialog_recyclerview;
        }

        public static DialogSettingsFragment newInstance(final String title, final @XmlRes int content) {
            DialogSettingsFragment fragment = new DialogSettingsFragment();
            Bundle b = new Bundle(2);
            b.putString(TITLE, title);
            b.putInt(CONTENT_RES_ID, content);
            fragment.setArguments(b);
            return fragment;
        }
    }

    public static class SuggestionConfirmationFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

        public void onClick(final DialogInterface dialogInterface, final int n) {
            if (getTargetFragment() instanceof PreferenceFragmentCompat) {
                Preference preference = ((PreferenceFragmentCompat) getTargetFragment())
                                        .findPreference(SHOW_PREDICTIONS_PREF);
                if (preference instanceof TwoStatePreference) {
                    ((TwoStatePreference) preference).setChecked(false);
                }
            }
        }

        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                   .setTitle(R.string.title_disable_suggestions_prompt)
                   .setMessage(R.string.msg_disable_suggestions_prompt)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.label_turn_off_suggestions, this).create();
        }

        @Override
        public void onStart() {
            super.onStart();
            ZimUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }
    }

    /**
     * Content observer which listens for system auto-rotate setting changes, and enables/disables
     * the launcher rotation setting accordingly.
     */
    private static class SystemDisplayRotationLockObserver extends SettingsObserver.System {

        private final Preference mRotationPref;

        public SystemDisplayRotationLockObserver(
            final Preference rotationPref, final ContentResolver resolver) {
            super(resolver);
            mRotationPref = rotationPref;
        }

        @Override
        public void onSettingChanged(final boolean enabled) {
            mRotationPref.setEnabled(enabled);
            mRotationPref.setSummary(enabled
                                     ? R.string.allow_rotation_desc : R.string.allow_rotation_blocked_desc);
        }
    }

    /**
     * Content observer which listens for system badging setting changes, and updates the launcher
     * badging setting subtext accordingly.
     */
    private static class IconBadgingObserver extends SettingsObserver.Secure
        implements Preference.OnPreferenceClickListener {

        private final ButtonPreference mBadgingPref;
        private final ContentResolver mResolver;
        private final FragmentManager mFragmentManager;
        private boolean serviceEnabled = true;

        public IconBadgingObserver(final ButtonPreference badgingPref, final ContentResolver resolver,
                                   final FragmentManager fragmentManager) {
            super(resolver);
            mBadgingPref = badgingPref;
            mResolver = resolver;
            mFragmentManager = fragmentManager;
        }

        @Override
        public void onSettingChanged(final boolean enabled) {
            int summary = enabled ? R.string.icon_badging_desc_on : R.string.icon_badging_desc_off;

            if (enabled) {
                // Check if the listener is enabled or not.
                String enabledListeners =
                    Settings.Secure.getString(mResolver, NOTIFICATION_ENABLED_LISTENERS);
                ComponentName myListener =
                    new ComponentName(mBadgingPref.getContext(), NotificationListener.class);
                serviceEnabled = enabledListeners != null
                                 && (enabledListeners.contains(myListener.flattenToString())
                                  || enabledListeners.contains(myListener.flattenToShortString()));
                if (!serviceEnabled) {
                    summary = R.string.title_missing_notification_access;
                }
            }
            mBadgingPref.setWidgetFrameVisible(!serviceEnabled);
            mBadgingPref.setOnPreferenceClickListener(
                serviceEnabled && Utilities.ATLEAST_OREO ? null : this);
            mBadgingPref.setSummary(summary);

        }

        @Override
        public boolean onPreferenceClick(final Preference preference) {
            if (!Utilities.ATLEAST_OREO && serviceEnabled) {
                ComponentName cn = new ComponentName(preference.getContext(),
                                                     NotificationListener.class);
                Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(":settings:fragment_args_key", cn.flattenToString());
                preference.getContext().startActivity(intent);
            } else {
                new SettingsActivity.NotificationAccessConfirmation()
                .show(mFragmentManager, "notification_access");
            }
            return true;
        }
    }

    public static class NotificationAccessConfirmation
        extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Context context = getActivity();
            String msg = context.getString(R.string.msg_missing_notification_access,
                                           context.getString(R.string.derived_app_name));
            return new AlertDialog.Builder(context)
                   .setTitle(R.string.title_missing_notification_access)
                   .setMessage(msg)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(R.string.title_change_settings, this)
                   .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            ZimUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }

        @Override
        public void onClick(final DialogInterface dialogInterface, final int i) {
            ComponentName cn = new ComponentName(getActivity(), NotificationListener.class);
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(":settings:fragment_args_key", cn.flattenToString());
            getActivity().startActivity(intent);
        }
    }

    public static class ResetIconsConfirmation
        extends DialogFragment implements DialogInterface.OnClickListener {

        @Override
        public Dialog onCreateDialog(final Bundle savedInstanceState) {
            final Context context = getActivity();
            return new AlertDialog.Builder(context)
                   .setTitle(R.string.reset_custom_icons)
                   .setMessage(R.string.reset_custom_icons_confirmation)
                   .setNegativeButton(android.R.string.cancel, null)
                   .setPositiveButton(android.R.string.ok, this)
                   .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            ZimUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }

        @Override
        public void onClick(final DialogInterface dialogInterface, final int i) {
            Context context = getContext();

            // Clear custom app icons
            ZimPreferences prefs = Utilities.getZimPrefs(context);
            Set<ComponentKey> toUpdateSet = prefs.getCustomAppIcon().toMap().keySet();
            prefs.beginBlockingEdit();
            prefs.getCustomAppIcon().clear();
            prefs.endBlockingEdit();

            // Clear custom shortcut icons
            ContentWriter writer = new ContentWriter(context, new ContentWriter.CommitParams(null, null));
            writer.put(LauncherSettings.Favorites.CUSTOM_ICON, (byte[]) null);
            writer.put(LauncherSettings.Favorites.CUSTOM_ICON_ENTRY, (String) null);
            writer.commit();

            // Reload changes
            ZimUtilsKt.reloadIconsFromComponents(context, toUpdateSet);
            ZimPreferencesChangeCallback prefsCallback = prefs.getOnChangeCallback();
            if (prefsCallback != null) {
                prefsCallback.reloadAll();
            }
        }
    }

    public static class InstallFragment extends DialogFragment {

        @Override
        public Dialog onCreateDialog(final Bundle bundle) {
            return new AlertDialog.Builder(getActivity())
                   .setTitle(R.string.bridge_missing_title)
                   .setMessage(R.string.bridge_missing_message)
                   .setNegativeButton(android.R.string.cancel, null)
                   .create();
        }

        @Override
        public void onStart() {
            super.onStart();
            ZimUtilsKt.applyAccent(((AlertDialog) getDialog()));
        }
    }

    public static void startFragment(final Context context, final String fragment, final int title) {
        startFragment(context, fragment, null, context.getString(title));
    }

    public static void startFragment(final Context context, final String fragment, final @Nullable Bundle args) {
        startFragment(context, fragment, args, null);
    }

    public static void startFragment(final Context context, final String fragment, final @Nullable Bundle args,
                                     final @Nullable CharSequence title) {
        context.startActivity(createFragmentIntent(context, fragment, args, title));
    }

    @NotNull
    private static Intent createFragmentIntent(final Context context, final String fragment,
            final @Nullable Bundle args, final @Nullable CharSequence title) {
        Intent intent = new Intent(context, SettingsActivity.class);
        intent.putExtra(EXTRA_FRAGMENT, fragment);
        intent.putExtra(EXTRA_FRAGMENT_ARGS, args);
        if (title != null) {
            intent.putExtra(EXTRA_TITLE, title);
        }
        return intent;
    }
}
