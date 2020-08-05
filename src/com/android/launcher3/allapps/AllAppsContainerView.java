/*
 * Copyright (C) 2015 The Android Open Source Project
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
package com.android.launcher3.allapps;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.os.Process;
import android.text.Selection;
import android.text.SpannableStringBuilder;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.android.launcher3.AppInfo;
import com.android.launcher3.DeviceProfile;
import com.android.launcher3.DeviceProfile.OnDeviceProfileChangeListener;
import com.android.launcher3.DragSource;
import com.android.launcher3.DropTarget.DragObject;
import com.android.launcher3.Insettable;
import com.android.launcher3.InsettableFrameLayout;
import com.android.launcher3.ItemInfo;
import com.android.launcher3.Launcher;
import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.launcher3.config.FeatureFlags;
import com.android.launcher3.keyboard.FocusedItemDecorator;
import com.android.launcher3.userevent.nano.LauncherLogProto.Target;
import com.android.launcher3.util.ItemInfoMatcher;
import com.android.launcher3.util.Themes;
import com.android.launcher3.views.BottomUserEducationView;
import com.android.launcher3.views.RecyclerViewFastScroller;
import com.android.launcher3.views.SpringRelativeLayout;
import com.google.android.apps.nexuslauncher.allapps.PredictionsFloatingHeader;
import com.google.android.apps.nexuslauncher.qsb.AllAppsQsbLayout;

import org.zimmob.zimlx.ZimPreferences;
import org.zimmob.zimlx.ZimUtilsKt;
import org.zimmob.zimlx.allapps.AllAppsTabs;
import org.zimmob.zimlx.allapps.AllAppsTabsController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static androidx.recyclerview.widget.RecyclerView.OnScrollListener;

/**
 * The all apps view container.
 */
public class AllAppsContainerView extends SpringRelativeLayout implements DragSource,
    Insettable, OnDeviceProfileChangeListener {

    private static final float FLING_VELOCITY_MULTIPLIER = 135f;
    // Starts the springs after at least 55% of the animation has passed.
    private static final float FLING_ANIMATION_THRESHOLD = 0.55f;

    private final Launcher mLauncher;
    private AdapterHolder[] mAH;
    private final ItemInfoMatcher mPersonalMatcher = ItemInfoMatcher.ofUser(Process.myUserHandle());
    private final ItemInfoMatcher mWorkMatcher = ItemInfoMatcher.not(mPersonalMatcher);
    private final AllAppsStore mAllAppsStore = new AllAppsStore();

    private final Paint mNavBarScrimPaint;
    private int mNavBarScrimHeight = 0;
    private int mNavBarScrimColor;

    private SearchUiManager mSearchUiManager;
    private View mSearchContainer;
    private AllAppsPagedView mViewPager;
    private PredictionsFloatingHeader mHeader;

    private SpannableStringBuilder mSearchQueryBuilder;

    private boolean mUsingTabs;
    private boolean mSearchModeWhileUsingTabs = false;

    private RecyclerViewFastScroller mTouchHandler;
    private final Point mFastScrollerOffset = new Point();

    private AllAppsTabsController mTabsController;

    private String mLastSearchQuery;

    public AllAppsContainerView(final Context context) {
        this(context, null);
    }

    public AllAppsContainerView(final Context context, final AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AllAppsContainerView(final Context context, final AttributeSet attrs, final int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        mLauncher = Launcher.getLauncher(context);
        mLauncher.addOnDeviceProfileChangeListener(this);

        mSearchQueryBuilder = new SpannableStringBuilder();
        Selection.setSelection(mSearchQueryBuilder, 0);

        AllAppsTabs allAppsTabs = new AllAppsTabs(context);
        mTabsController = new AllAppsTabsController(allAppsTabs, this);
        createHolders();

        mNavBarScrimColor = Themes.getAttrColor(context, R.attr.allAppsNavBarScrimColor);

        mNavBarScrimPaint = new Paint();
        mNavBarScrimPaint.setColor(mNavBarScrimColor);

        mAllAppsStore.addUpdateListener(this::onAppsUpdated);

        addSpringView(R.id.all_apps_header);
        addSpringView(R.id.apps_list_view);
        addSpringView(R.id.all_apps_tabs_view_pager);
    }

    private void createHolders() {
        mAH = mTabsController.createHolders();
    }

    public AllAppsStore getAppsStore() {
        return mAllAppsStore;
    }

    @Override
    protected void setDampedScrollShift(final float shift) {
        // Bound the shift amount to avoid content from drawing on top (Y-val) of the QSB.
        float maxShift = getSearchView().getHeight() / 2f;
        super.setDampedScrollShift(Utilities.boundToRange(shift, -maxShift, maxShift));
    }

    @Override
    public void onDeviceProfileChanged(final DeviceProfile dp) {
        for (AdapterHolder holder : mAH) {
            if (holder.recyclerView != null) {
                // Remove all views and clear the pool, while keeping the data same. After this
                // call, all the viewHolders will be recreated.
                holder.recyclerView.swapAdapter(holder.recyclerView.getAdapter(), true);
                holder.recyclerView.getRecycledViewPool().clear();
            }
        }
    }

    private void onAppsUpdated() {
        boolean force = false;
        if (FeatureFlags.ALL_APPS_TABS_ENABLED && Utilities.getZimPrefs(getContext()).getSeparateWorkApps()) {

            boolean hasWorkApps = false;
            for (AppInfo app : mAllAppsStore.getApps()) {
                if (mWorkMatcher.matches(app, null)) {
                    hasWorkApps = true;
                    break;
                }
            }
            AllAppsTabs allAppsTabs = mTabsController.getTabs();
            force = allAppsTabs.getHasWorkApps() != hasWorkApps;
            allAppsTabs.setHasWorkApps(hasWorkApps);
        }

        rebindAdapters(mTabsController.getShouldShowTabs(), force);
    }

    /**
     * Returns whether the view itself will handle the touch event or not.
     */
    public boolean shouldContainerScroll(final MotionEvent ev) {
        // IF the MotionEvent is inside the search box, and the container keeps on receiving
        // touch input, container should move down.
        if (mLauncher.getDragLayer().isEventOverView(mSearchContainer, ev)) {
            return true;
        }
        AllAppsRecyclerView rv = getActiveRecyclerView();
        if (rv == null) {
            return true;
        }
        if (rv.getScrollbar().getThumbOffsetY() >= 0
                && mLauncher.getDragLayer().isEventOverView(rv.getScrollbar(), ev)) {
            return false;
        }
        return rv.shouldContainerScroll(ev, mLauncher.getDragLayer());
    }

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            AllAppsRecyclerView rv = getActiveRecyclerView();
            if (rv != null
                    && rv.getScrollbar().isHitInParent(ev.getX(), ev.getY(), mFastScrollerOffset)) {
                mTouchHandler = rv.getScrollbar();
            }
        }
        if (mTouchHandler != null) {
            return mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(final MotionEvent ev) {
        if (mTouchHandler != null) {
            mTouchHandler.handleTouchEvent(ev, mFastScrollerOffset);
            return true;
        }
        return false;
    }

    public String getDescription() {
        @StringRes int descriptionRes;
        if (mUsingTabs) {
            descriptionRes =
                mViewPager.getNextPage() == 0
                ? R.string.all_apps_button_personal_label
                : R.string.all_apps_button_work_label;
        } else {
            descriptionRes = R.string.all_apps_button_label;
        }
        return getContext().getString(descriptionRes);
    }

    public AllAppsRecyclerView getActiveRecyclerView() {
        if (!mUsingTabs) {
            return mAH[AdapterHolder.MAIN].recyclerView;
        } else {
            return mAH[mViewPager.getNextPage()].recyclerView;
        }
    }

    /**
     * Resets the state of AllApps.
     */
    public void reset(final boolean animate) {
        reset(animate, false);
    }

    public void reset(final boolean animate, final boolean force) {
        if (force || !Utilities.getZimPrefs(getContext()).getSaveScrollPosition()) {
            for (int i = 0; i < mAH.length; i++) {
                if (mAH[i].recyclerView != null) {
                    mAH[i].recyclerView.scrollToTop();
                }
            }
            if (isHeaderVisible()) {
                mHeader.reset(animate);
            }
        }
        // Reset the search bar and base recycler view after transitioning home
        mSearchUiManager.resetSearch();
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();

        // This is a focus listener that proxies focus from a view into the list view.  This is to
        // work around the search box from getting first focus and showing the cursor.
        setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && getActiveRecyclerView() != null) {
                getActiveRecyclerView().requestFocus();
            }
        });

        mHeader = findViewById(R.id.all_apps_header);
        rebindAdapters(mUsingTabs, true /* force */);

        mSearchContainer = findViewById(R.id.search_container_all_apps);
        mSearchUiManager = (SearchUiManager) mSearchContainer;
        mSearchUiManager.initialize(this);
    }

    public SearchUiManager getSearchUiManager() {
        return mSearchUiManager;
    }

    @Override
    public boolean dispatchKeyEvent(final KeyEvent event) {
        mSearchUiManager.preDispatchKeyEvent(event);
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onDropCompleted(final View target, final DragObject d, final boolean success) {
    }

    @Override
    public void fillInLogContainerData(final View v, final ItemInfo info, final Target target, final Target targetParent) {
        // This is filled in {@link AllAppsRecyclerView}
    }

    @Override
    public void setInsets(final Rect insets) {
        DeviceProfile grid = mLauncher.getDeviceProfile();
        int leftRightPadding = grid.desiredWorkspaceLeftRightMarginPx
                               + grid.cellLayoutPaddingLeftRightPx;

        mTabsController.setPadding(leftRightPadding, insets.bottom);

        ViewGroup.MarginLayoutParams mlp = (MarginLayoutParams) getLayoutParams();
        if (grid.isVerticalBarLayout()) {
            mlp.leftMargin = insets.left;
            mlp.rightMargin = insets.right;
            setPadding(grid.workspacePadding.left, 0, grid.workspacePadding.right, 0);
        } else {
            if (!ZimPreferences.Companion.getInstance(getContext()).getAllAppsSearch()) {
                AllAppsQsbLayout qsb = (AllAppsQsbLayout) mSearchContainer;
                mlp.topMargin = -(qsb.getTopMargin(insets) + qsb.getLayoutParams().height);
            }
            mlp.leftMargin = mlp.rightMargin = 0;
            setPadding(0, 0, 0, 0);
        }
        setLayoutParams(mlp);

        mNavBarScrimHeight = insets.bottom;
        InsettableFrameLayout.dispatchInsets(this, insets);
    }


    @Override
    protected void dispatchDraw(final Canvas canvas) {
        super.dispatchDraw(canvas);

        if (mNavBarScrimHeight > 0) {
            canvas.drawRect(0, getHeight() - mNavBarScrimHeight, getWidth(), getHeight(),
                            mNavBarScrimPaint);
        }
    }

    @Override
    public int getCanvasClipTopForOverscroll() {
        // Do not clip if the QSB is attached to the spring, otherwise the QSB will get clipped.
        return mSpringViews.get(getSearchView().getId()) ? 0 : mHeader.getTop();
    }

    public void reloadTabs() {
        mTabsController.reloadTabs();
        rebindAdapters(mTabsController.getShouldShowTabs(), true);
    }

    private void rebindAdapters(final boolean showTabs) {
        rebindAdapters(showTabs, false /* force */);
    }

    private void rebindAdapters(final boolean showTabs, final boolean force) {
        if (showTabs == mUsingTabs && !force) {
            return;
        }
        int currentTab = mViewPager != null ? mViewPager.getNextPage() : 0;
        mTabsController.unregisterIconContainers(mAllAppsStore);

        createHolders();
        replaceRVContainer(showTabs);
        mUsingTabs = showTabs;

        if (mUsingTabs) {
            mTabsController.setup(mViewPager);
            PersonalWorkSlidingTabStrip tabStrip = findViewById(R.id.tabs);
            tabStrip.inflateButtons(mTabsController.getTabs());
            if (currentTab == 0) {
                tabStrip.setScroll(0, 1);
            }
            onTabChanged(currentTab);
        } else {
            mTabsController.setup((View) findViewById(R.id.apps_list_view));
            AllAppsRecyclerView recyclerView = mAH[AdapterHolder.MAIN].recyclerView;
            if (recyclerView != null) {
                ZimUtilsKt.runOnAttached(recyclerView, () ->
                                         recyclerView.setScrollbarColor(Utilities.getZimPrefs(getContext()).getAccentColor()));
            }
        }
        setupHeader();

        mTabsController.registerIconContainers(mAllAppsStore);
        if (mViewPager != null) {
            mViewPager.snapToPage(Math.min(mTabsController.getTabsCount() - 1, currentTab), 0);
        }
    }

    private void replaceRVContainer(final boolean showTabs) {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.setLayoutManager(null);
            }
        }
        View oldView = getRecyclerViewContainer();
        int index = indexOfChild(oldView);
        removeView(oldView);

        int layout = showTabs ? R.layout.all_apps_tabs : R.layout.all_apps_rv_layout;

        View newView = LayoutInflater.from(getContext()).inflate(layout, this, false);
        addView(newView, index);
        if (showTabs) {
            mViewPager = (AllAppsPagedView) newView;
            mViewPager.addTabs(mTabsController.getTabsCount());
            mViewPager.initParentViews(this);
            mViewPager.getPageIndicator().setContainerView(this);
        } else {
            mViewPager = null;
        }
    }

    public View getRecyclerViewContainer() {
        return mViewPager != null ? mViewPager : findViewById(R.id.apps_list_view);
    }

    public void onTabChanged(final int pos) {
        pos = Utilities.boundToRange(pos, 0, mTabsController.getTabsCount() - 1);
        mHeader.setCurrentActive(pos);
        reset(true /* animate */, true);
        if (mAH[pos].recyclerView != null) {
            mAH[pos].recyclerView.bindFastScrollbar();
            mAH[pos].recyclerView.setScrollbarColor(Utilities.getZimPrefs(getContext()).getAccentColor());

            mTabsController.bindButtons(findViewById(R.id.tabs), mViewPager);
        }
        if (mAH[pos].isWork) {
            BottomUserEducationView.showIfNeeded(mLauncher);
        }
    }

    public AlphabeticalAppsList getApps() {
        return mAH[AdapterHolder.MAIN].appsList;
    }

    public Collection<AlphabeticalAppsList> getAppsLists() {
        List<AlphabeticalAppsList> results = new ArrayList<>();
        for (AdapterHolder holder : mAH) {
            results.add(holder.appsList);
        }
        return results;
    }

    public PredictionsFloatingHeader getFloatingHeaderView() {
        return mHeader;
    }

    public View getSearchView() {
        return mSearchContainer;
    }

    public View getContentView() {
        return mViewPager == null ? getActiveRecyclerView() : mViewPager;
    }

    public RecyclerViewFastScroller getScrollBar() {
        AllAppsRecyclerView rv = getActiveRecyclerView();
        return rv == null ? null : rv.getScrollbar();
    }

    public void setupHeader() {
        mHeader.setVisibility(View.VISIBLE);
        mHeader.setup(mAH, !mUsingTabs);

        int padding = mHeader.getMaxTranslation();
        for (AdapterHolder adapterHolder : mAH) {
            adapterHolder.padding.top = padding;
            adapterHolder.applyPadding();
        }
    }

    public void setLastSearchQuery(final String query) {
        mLastSearchQuery = query;
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].adapter.setLastSearchQuery(query);
        }
        if (mUsingTabs) {
            mSearchModeWhileUsingTabs = true;
            rebindAdapters(false); // hide tabs
        }
    }

    public void onClearSearchResult() {
        if (mSearchModeWhileUsingTabs) {
            rebindAdapters(true); // show tabs
            mSearchModeWhileUsingTabs = false;
        }
    }

    public void onSearchResultsChanged() {
        for (int i = 0; i < mAH.length; i++) {
            if (mAH[i].recyclerView != null) {
                mAH[i].recyclerView.onSearchResultsChanged();
            }
        }
    }

    public void setRecyclerViewVerticalFadingEdgeEnabled(final boolean enabled) {
        for (int i = 0; i < mAH.length; i++) {
            mAH[i].applyVerticalFadingEdgeEnabled(enabled);
        }
    }

    public void addElevationController(final OnScrollListener scrollListener) {
        if (!mUsingTabs) {
            mAH[AdapterHolder.MAIN].recyclerView.addOnScrollListener(scrollListener);
        }
    }

    public boolean isHeaderVisible() {
        return mHeader != null && mHeader.getVisibility() == View.VISIBLE;
    }

    public void onScrollUpEnd() {
        if (mUsingTabs) {
            ((PersonalWorkSlidingTabStrip) findViewById(R.id.tabs)).highlightWorkTabIfNecessary();
        }
    }

    /**
     * Adds an update listener to {@param animator} that adds springs to the animation.
     */
    public void addSpringFromFlingUpdateListener(final ValueAnimator animator, final float velocity) {
        animator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            boolean shouldSpring = true;

            @Override
            public void onAnimationUpdate(final ValueAnimator valueAnimator) {
                if (shouldSpring
                        && valueAnimator.getAnimatedFraction() >= FLING_ANIMATION_THRESHOLD) {
                    int searchViewId = getSearchView().getId();
                    addSpringView(searchViewId);

                    finishWithShiftAndVelocity(1, velocity * FLING_VELOCITY_MULTIPLIER,
                                               (animation, canceled, value, velocity1) -> removeSpringView(searchViewId));

                    shouldSpring = false;
                }
            }
        });
    }

    @Override
    public void getDrawingRect(final Rect outRect) {
        super.getDrawingRect(outRect);
        outRect.offset(0, (int) getTranslationY());
    }

    public AdapterHolder createHolder(final boolean isWork) {
        return new AdapterHolder(isWork);
    }

    public class AdapterHolder {
        public static final int MAIN = 0;
        public static final int WORK = 1;

        public final AllAppsGridAdapter adapter;
        final LinearLayoutManager layoutManager;
        final AlphabeticalAppsList appsList;
        public final Rect padding = new Rect();
        public AllAppsRecyclerView recyclerView;
        boolean verticalFadingEdge;

        private boolean isWork;

        AdapterHolder(final boolean isWork) {
            appsList = new AlphabeticalAppsList(mLauncher, mAllAppsStore, isWork);
            adapter = new AllAppsGridAdapter(mLauncher, appsList);
            appsList.setAdapter(adapter);
            layoutManager = adapter.getLayoutManager();
        }

        public void setup(final @NonNull View rv, final @Nullable ItemInfoMatcher matcher) {
            appsList.updateItemFilter(matcher);
            recyclerView = (AllAppsRecyclerView) rv;
            recyclerView.setEdgeEffectFactory(createEdgeEffectFactory());
            recyclerView.setApps(appsList, mUsingTabs);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.setAdapter(adapter);
            recyclerView.setHasFixedSize(true);
            // No animations will occur when changes occur to the items in this RecyclerView.
            recyclerView.setItemAnimator(null);
            FocusedItemDecorator focusedItemDecorator = new FocusedItemDecorator(recyclerView);
            recyclerView.addItemDecoration(focusedItemDecorator);
            adapter.setIconFocusListener(focusedItemDecorator.getFocusListener());
            applyVerticalFadingEdgeEnabled(verticalFadingEdge);
            applyPadding();
        }

        public void applyPadding() {
            if (recyclerView != null) {
                recyclerView.setPadding(padding.left, padding.top, padding.right, padding.bottom);
            }
        }

        public void applyVerticalFadingEdgeEnabled(final boolean enabled) {
            verticalFadingEdge = enabled;
            mAH[AdapterHolder.MAIN].recyclerView.setVerticalFadingEdgeEnabled(!mUsingTabs && verticalFadingEdge);
        }

        public void setIsWork(final boolean isWork) {
            this.isWork = isWork;
            appsList.setIsWork(isWork);
        }

        public boolean isWork() {
            return isWork;
        }
    }
}

