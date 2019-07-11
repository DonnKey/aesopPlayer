package com.studio4plus.aesopPlayer.content;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.studio4plus.aesopPlayer.GlobalSettings;
import com.studio4plus.aesopPlayer.AesopPlayerApplication;
import com.studio4plus.aesopPlayer.KioskModeSwitcher;

import javax.inject.Inject;

/**
 * Makes certain configuration settings available to other apps (most notably: the adb shell).
 * Don't expose any sensitive information through this class.
 */
public class ConfigurationContentProvider extends ContentProvider {

    @SuppressWarnings("WeakerAccess")
    @Inject public KioskModeSwitcher kioskModeSwitcher;
    @SuppressWarnings("WeakerAccess")
    @Inject public GlobalSettings globalSettings;

    @Override
    public boolean onCreate() {
        return true;
    }

    private void injectDependenciesIfNecessary(){
        // onCreate is called before the application object is initialized therefore
        // Dagger injection is run by the first operation on the content provider.
        if (globalSettings == null) {
            Context c = getContext();
            assert c != null;
            AesopPlayerApplication.getComponent(c).inject(this);
        }
    }

    @Nullable
    @Override
    public Cursor query(
            @NonNull Uri uri,
            @Nullable String[] projection,
            @Nullable String selection,
            @Nullable String[] selectionArgs,
            @Nullable String sortOrder) {
        injectDependenciesIfNecessary();
        return new ConfigurationCursor(
                kioskModeSwitcher.isLockTaskPermitted(),
                globalSettings.isFullKioskModeEnabled());
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues contentValues) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues contentValues, @Nullable String s, @Nullable String[] strings) {
        return 0;
    }
}
