package com.donnKey.aesopPlayer.ui.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.StringRes;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.ListPreference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.donnKey.aesopPlayer.R;

import java.util.Objects;

abstract class BaseSettingsFragment
        extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    @Override
    public void onStart() {
        super.onStart();
        getSharedPreferences().registerOnSharedPreferenceChangeListener(this);

        final ActionBar actionBar = ((AppCompatActivity) Objects.requireNonNull(getActivity())).getSupportActionBar();
        Preconditions.checkNotNull(actionBar);
        actionBar.setTitle(getTitle());

    }

    @Override
    public void onStop() {
        getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
        super.onStop();
    }

    @NonNull
    SharedPreferences getSharedPreferences() {
        return PreferenceManager.getDefaultSharedPreferences(Objects.requireNonNull(getActivity()));
    }

    @StringRes
    protected abstract int getTitle();

    void updateListPreferenceSummary(@NonNull SharedPreferences sharedPreferences,
                                     @NonNull String key,
                                     int default_value_res_id) {
        String stringValue = sharedPreferences.getString(key, getString(default_value_res_id));
        ListPreference preference =
                (ListPreference) findPreference(key);
        int index = preference.findIndexOfValue(stringValue);
        if (index < 0)
            index = 0;
        preference.setSummary(preference.getEntries()[index]);
    }

    void openUrl(@SuppressWarnings("SameParameterValue") @NonNull String url) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setData(Uri.parse(url));
        try {
            startActivity(i);
        }
        catch(ActivityNotFoundException noActivity) {
            Preconditions.checkNotNull(getView());
            Toast.makeText(getView().getContext(),
                    R.string.pref_no_browser_toast, Toast.LENGTH_LONG).show();
        }
    }
}
