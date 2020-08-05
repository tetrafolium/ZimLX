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

package com.android.launcher3.compat;

import android.content.Context;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

public class AccessibilityManagerCompat {

  public static boolean isAccessibilityEnabled(final Context context) {
    return getManager(context).isEnabled();
  }

  public static boolean isObservedEventType(final Context context,
                                            final int eventType) {
    // TODO: Use new API once available
    return isAccessibilityEnabled(context);
  }

  public static void sendCustomAccessibilityEvent(final View target,
                                                  final int type,
                                                  final String text) {
    if (isObservedEventType(target.getContext(), type)) {
      AccessibilityEvent event = AccessibilityEvent.obtain(type);
      target.onInitializeAccessibilityEvent(event);
      event.getText().add(text);
      getManager(target.getContext()).sendAccessibilityEvent(event);
    }
  }

  private static AccessibilityManager getManager(final Context context) {
    return (AccessibilityManager)context.getSystemService(
        Context.ACCESSIBILITY_SERVICE);
  }
}
