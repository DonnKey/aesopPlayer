package com.studio4plus.homerplayer.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;

import androidx.annotation.StringRes;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AlertDialog;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.studio4plus.homerplayer.R;

import java.util.Arrays;
import java.util.Collection;

public class PermissionUtils {

    public static boolean checkAndRequestPermission(
            final Activity activity, String[] permissions, int requestCode) {
        Collection<String> missingPermissions = Collections2.filter(Arrays.asList(permissions), new Predicate<String>() {
            @Override
            public boolean apply(String permission) {
                Preconditions.checkNotNull(permission);
                return ContextCompat.checkSelfPermission(activity, permission) != PackageManager.PERMISSION_GRANTED;
            }
        });
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity, missingPermissions.toArray(new String[0]), requestCode);
            return false;
        }
        return true;
    }

    public static AlertDialog.Builder permissionRationaleDialogBuilder(
            Activity activity, @StringRes int rationaleMessage) {
        return new AlertDialog.Builder(activity)
                .setMessage(rationaleMessage)
                .setTitle(R.string.permission_rationale_title)
                .setIcon(R.mipmap.ic_launcher);
    }

    public static void openAppSettings(Activity activity) {
        activity.startActivity(new Intent(
                android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + activity.getApplication().getPackageName())));
    }
}
