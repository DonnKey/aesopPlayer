/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2019 Donn S. Terry
 * Copyright (c) 2015-2017 Marcin Simonides
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.donnKey.aesopPlayer.ui;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.AnalyticsTracker;

import javax.inject.Inject;

@ActivityScope
public class KioskModeHandler {

    private static final int PERMISSION_REQUEST_FOR_SIMPLE_KIOSK = 2;
    private static final int PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS = 3;

    private final @NonNull
    AppCompatActivity activity;
    private final @NonNull AnalyticsTracker analyticsTracker;

    @Inject
    KioskModeHandler(@NonNull AppCompatActivity activity,
                     @NonNull AnalyticsTracker analyticsTracker) {
        this.activity = activity;
        this.analyticsTracker = analyticsTracker;
    }

    public static void triggerSimpleKioskPermissionsIfNecessary(AppCompatActivity activity) {
        PermissionUtils.checkAndRequestPermission(
                activity,
                new String[]{Manifest.permission.REORDER_TASKS,
                        Manifest.permission.SYSTEM_ALERT_WINDOW},
                PERMISSION_REQUEST_FOR_SIMPLE_KIOSK);
    }

    // This callback comes via Settings Activity
    public void onRequestPermissionResult(
            int code, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (code) {
        case PERMISSION_REQUEST_FOR_SIMPLE_KIOSK:
            //noinspection StatementWithEmptyBody
            if (grantResults.length == 2 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
                // Have them now, will need them later but not right now.
            } else {
                boolean canRetry =
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                activity, Manifest.permission.REORDER_TASKS) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                                activity, Manifest.permission.SYSTEM_ALERT_WINDOW);
                AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                        activity, R.string.permission_rationale_simple_kiosk);
                // The only live scenarios appear to be <=22, when the permissions are just
                // granted, or >=23, where SYSTEM_ALERT_WINDOW is never granted (no chance of
                // retry), thus this is never called. But this can't hurt if there's some middle.
                if (canRetry) {
                    dialogBuilder.setPositiveButton(
                            R.string.permission_rationale_try_again,
                            (dI, i) -> PermissionUtils.checkAndRequestPermission(
                                    activity, permissions, PERMISSION_REQUEST_FOR_SIMPLE_KIOSK));
                } else {
                    analyticsTracker.onPermissionRationaleShown("simpleKioskEnable");
                    dialogBuilder.setPositiveButton(
                            R.string.permission_rationale_settings,
                            (dI, i) -> PermissionUtils.openAppSettings(activity));
                }
                dialogBuilder.setNegativeButton(
                        R.string.permission_rationale_exit,
                        (dI, i) -> forceExit(activity));
                dialogBuilder.create().show();
            }
            break;
        }
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean canDrawOverlays(Context context)
    {
        if (android.os.Build.VERSION.SDK_INT <= 22) { // Lollipop
            // Before API 23, there was no permission required (or even available)
            return true;
        }
        if (android.os.Build.VERSION.SDK_INT >= 26) { // Oreo
            // Permission to paint over status bar never granted to ordinary processes
            return false;
        }
        // Makes sense for only M and N
        return Settings.canDrawOverlays(context);
    }

    @TargetApi(23)
    static public void triggerOverlayPermissionsIfNecessary(AppCompatActivity activity)
    {
        if (!canDrawOverlays(activity.getApplicationContext())) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + activity.getPackageName()));
            activity.startActivityForResult(intent, PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS);
        }
    }

    // This callback chains from Settings Activity
    @SuppressWarnings("unused")
    public void onActivityResult(
            int code, int resultCode, Intent data) {
        //noinspection SwitchStatementWithTooFewBranches
        switch (code) {
        case PERMISSION_REQUEST_FOR_MANAGE_OVERLAYS:
            Preconditions.checkState(android.os.Build.VERSION.SDK_INT >= 23); // Marshmallow

            if (!canDrawOverlays(activity.getApplicationContext())) {
                analyticsTracker.onPermissionRationaleShown("manageOverlays");
                AlertDialog.Builder dialogBuilder = PermissionUtils.permissionRationaleDialogBuilder(
                        activity, R.string.permission_rationale_simple_kiosk_overlay);
                dialogBuilder.setPositiveButton(
                        R.string.permission_rationale_try_again,
                        (dI, i) -> triggerOverlayPermissionsIfNecessary(activity));
                dialogBuilder.setNeutralButton(
                        R.string.permission_rationale_ignore,
                        (dI, i) -> {
                            // Do nothing, the StatusBarCollapser in MainActivity will take over
                        });
                /*
                dialogBuilder.setNegativeButton(
                        R.string.permission_rationale_exit,
                        (dI, i) -> forceExit(activity));
                */
                dialogBuilder.create().show();
            }
            break;
        }
    }


    // We can't fully disable the status bar, but we can make it harmless.
    // (After Andreas Schrade's article on Kiosks)
    private static CustomViewGroup suppressStatusView;

    public static void controlStatusBarExpansion(Context context, boolean enable) {

        if (!canDrawOverlays(context)) {
            // We don't have permission (see the function above for the rules)
            // For API levels at or above 26 (Oreo) there's code in MainActivity that
            // does the best it can do achieve the same goal.
            return;
        }

        if (enable) {
            if (suppressStatusView != null) {
                // we already did the below
                return;
            }

            WindowManager manager = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE));
            WindowManager.LayoutParams localLayoutParams = new WindowManager.LayoutParams();
            localLayoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ERROR;
            localLayoutParams.gravity = Gravity.TOP;
            localLayoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                    | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;

            localLayoutParams.width = WindowManager.LayoutParams.MATCH_PARENT;

            int resId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
            int result = 60; // default in px if we can't get it
            if (resId > 0) {
                result = context.getResources().getDimensionPixelSize(resId);
            }

            localLayoutParams.height = result;
            localLayoutParams.format = PixelFormat.TRANSPARENT;  // this is optional - without it
                                                                   // the bar area is just black.

            suppressStatusView = new CustomViewGroup(context);
            try {
                manager.addView(suppressStatusView, localLayoutParams);
            }
            catch (Exception e) {
                // if for some reason we lose the permission to do this, just go on.
                suppressStatusView = null;
            }
        }
        else {
            if (suppressStatusView != null) {
                WindowManager manager = ((WindowManager) context.getApplicationContext().getSystemService(Context.WINDOW_SERVICE));
                manager.removeView(suppressStatusView);
                suppressStatusView = null;
            }
        }
    }

    private static class CustomViewGroup extends ViewGroup {

        public CustomViewGroup(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
        }

        @Override
        public boolean onInterceptTouchEvent(MotionEvent ev) {
            // Intercepted touch!
            return true;
        }
    }

    private static void forceExit(@NonNull AppCompatActivity activity)
    {
        if (Build.VERSION.SDK_INT >= 21) { // Lollipop
            // If it's already pinned, un-pin it. (Not strictly necessary)
            activity.stopLockTask();
        }
        // First bring up "the usual android" screen. Then exit so we start a new process next
        // time, rather than resuming.  (The recents window may well show the settings
        // screen we just left, but it's just a snapshot.)
        activity.moveTaskToBack(true);
        System.exit(0);
    }
}
