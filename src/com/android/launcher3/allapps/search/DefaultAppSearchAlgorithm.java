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
package com.android.launcher3.allapps.search;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.LauncherActivityInfo;
import android.os.Handler;
import android.os.UserHandle;

import com.android.launcher3.AppFilter;
import com.android.launcher3.AppInfo;
import com.android.launcher3.IconCache;
import com.android.launcher3.LauncherAppState;
import com.android.launcher3.Utilities;
import com.android.launcher3.compat.LauncherAppsCompat;
import com.android.launcher3.compat.UserManagerCompat;
import com.android.launcher3.util.ComponentKey;

import org.zimmob.zimlx.ZimAppFilter;
import org.zimmob.zimlx.globalsearch.SearchProvider;
import org.zimmob.zimlx.globalsearch.SearchProviderController;
import org.zimmob.zimlx.globalsearch.providers.web.WebSearchProvider;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The default search implementation.
 */
public class DefaultAppSearchAlgorithm implements SearchAlgorithm {

    public final static String SEARCH_HIDDEN_APPS = "pref_search_hidden_apps";
    private final static Pattern complementaryGlyphs = Pattern.compile("\\p{M}");
    private final Context mContext;
    private final List<AppInfo> mApps;
    protected final Handler mResultHandler;

    private final AppFilter mBaseFilter;

    public DefaultAppSearchAlgorithm(final Context context, final List<AppInfo> apps) {
        mContext = context;
        mApps = apps;
        mResultHandler = new Handler();
        mBaseFilter = new ZimAppFilter(context);
    }

    @Override
    public void cancel(final boolean interruptActiveRequests) {
        if (interruptActiveRequests) {
            mResultHandler.removeCallbacksAndMessages(null);
        }
    }

    @Override
    public void doSearch(final String query,
                         final AllAppsSearchBarController.Callbacks callback) {
        final ArrayList<ComponentKey> result = getTitleMatchResult(query);
        final List<String> suggestions = getSuggestions(query);
        mResultHandler.post(new Runnable() {

            @Override
            public void run() {
                callback.onSearchResult(query, result, suggestions);
            }
        });
    }
    private ArrayList<ComponentKey> getTitleMatchResult(final String query) {
        // Do an intersection of the words in the query and each title, and filter out all the
        // apps that don't match all of the words in the query.
        final String queryTextLower = query.toLowerCase();
        final ArrayList<ComponentKey> result = new ArrayList<>();
        StringMatcher matcher = StringMatcher.getInstance();
        for (AppInfo info : getApps(mContext, mApps, mBaseFilter)) {
            if (matches(info, queryTextLower, matcher)) {
                result.add(info.toComponentKey());
            }
        }
        return result;
    }

    private List<String> getSuggestions(final String query) {
        SearchProvider provider = SearchProviderController.Companion
                                  .getInstance(mContext).getSearchProvider();
        if (provider instanceof WebSearchProvider) {
            return ((WebSearchProvider) provider).getSuggestions(query);
        }
        return Collections.emptyList();
    }

    public static List<AppInfo> getApps(final Context context, final List<AppInfo> defaultApps, final AppFilter filter) {
        if (!Utilities.getPrefs(context).getBoolean(SEARCH_HIDDEN_APPS, false)) {
            return defaultApps;
        }
        final List<AppInfo> apps = new ArrayList<>();
        final IconCache iconCache = LauncherAppState.getInstance(context).getIconCache();
        for (UserHandle user : UserManagerCompat.getInstance(context).getUserProfiles()) {
            final List<ComponentName> duplicatePreventionCache = new ArrayList<>();
            for (LauncherActivityInfo info : LauncherAppsCompat.getInstance(context).getActivityList(null, user)) {
                if (!filter.shouldShowApp(info.getComponentName(), user)) {
                    continue;
                }
                if (!duplicatePreventionCache.contains(info.getComponentName())) {
                    duplicatePreventionCache.add(info.getComponentName());
                    final AppInfo appInfo = new AppInfo(context, info, user);
                    iconCache.getTitleAndIcon(appInfo, false);
                    apps.add(appInfo);
                }
            }
        }
        return apps;
    }

    public static boolean matches(final AppInfo info, final String query, final StringMatcher matcher) {
        return matches(info, query, matcher, false) || matches(info, query, matcher, true);
    }

    private static boolean matches(final AppInfo info, final String query, final StringMatcher matcher, final boolean normalize) {
        int queryLength = query.length();

        String title = info.title.toString();
        int titleLength = title.length();

        if (titleLength < queryLength || queryLength <= 0) {
            return false;
        }

        if (normalize) {
            title = normalize(title);
            query = normalize(query);
        }

        int lastType;
        int thisType = Character.UNASSIGNED;
        int nextType = Character.getType(title.codePointAt(0));

        int end = titleLength - queryLength;
        for (int i = 0; i <= end; i++) {
            lastType = thisType;
            thisType = nextType;
            nextType = i < (titleLength - 1)
                       ? Character.getType(title.codePointAt(i + 1)) : Character.UNASSIGNED;
            if (isBreak(thisType, lastType, nextType)
                    && matcher.matches(query, title.substring(i, i + queryLength))) {
                return true;
            }
        }
        return false;
    }

    private static String normalize(final String in) {
        return complementaryGlyphs.matcher(Normalizer.normalize(in, Normalizer.Form.NFKD)).replaceAll("");
    }

    /**
     * Returns true if the current point should be a break point. Following cases
     * are considered as break points:
     *      1) Any non space character after a space character
     *      2) Any digit after a non-digit character
     *      3) Any capital character after a digit or small character
     *      4) Any capital character before a small character
     */
    private static boolean isBreak(final int thisType, final int prevType, final int nextType) {
        switch (prevType) {
        case Character.UNASSIGNED:
        case Character.SPACE_SEPARATOR:
        case Character.LINE_SEPARATOR:
        case Character.PARAGRAPH_SEPARATOR:
            return true;
        }
        switch (thisType) {
        case Character.UPPERCASE_LETTER:
            if (nextType == Character.UPPERCASE_LETTER) {
                return true;
            }
        // Follow through
        case Character.TITLECASE_LETTER:
            // Break point if previous was not a upper case
            return prevType != Character.UPPERCASE_LETTER;
        case Character.LOWERCASE_LETTER:
            // Break point if previous was not a letter.
            return prevType > Character.OTHER_LETTER || prevType <= Character.UNASSIGNED;
        case Character.DECIMAL_DIGIT_NUMBER:
        case Character.LETTER_NUMBER:
        case Character.OTHER_NUMBER:
            // Break point if previous was not a number
            return !(prevType == Character.DECIMAL_DIGIT_NUMBER
                     || prevType == Character.LETTER_NUMBER
                     || prevType == Character.OTHER_NUMBER);
        case Character.MATH_SYMBOL:
        case Character.CURRENCY_SYMBOL:
        case Character.OTHER_PUNCTUATION:
        case Character.DASH_PUNCTUATION:
        case Character.OTHER_LETTER:
            // Always a break point for a symbol
            return true;
        default:
            return false;
        }
    }

    public static class StringMatcher {

        private static final char MAX_UNICODE = '\uFFFF';

        private final Collator mCollator;

        StringMatcher() {
            // On android N and above, Collator uses ICU implementation which has a much better
            // support for non-latin locales.
            mCollator = Collator.getInstance();
            mCollator.setStrength(Collator.PRIMARY);
            mCollator.setDecomposition(Collator.CANONICAL_DECOMPOSITION);
        }

        /**
         * Returns true if {@param query} is a prefix of {@param target}
         */
        public boolean matches(final String query, final String target) {
            switch (mCollator.compare(query, target)) {
            case 0:
                return true;
            case -1:
                // The target string can contain a modifier which would make it larger than
                // the query string (even though the length is same). If the query becomes
                // larger after appending a unicode character, it was originally a prefix of
                // the target string and hence should match.
                return mCollator.compare(query + MAX_UNICODE, target) > -1;
            default:
                return false;
            }
        }

        public static StringMatcher getInstance() {
            return new StringMatcher();
        }
    }
}
