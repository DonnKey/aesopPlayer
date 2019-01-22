package com.studio4plus.homerplayer.ui;

import androidx.appcompat.app.AppCompatActivity;

import com.studio4plus.homerplayer.ApplicationComponent;
import com.studio4plus.homerplayer.ui.settings.SettingsActivity;

import dagger.Component;

@ActivityScope
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent {
    @SuppressWarnings("unused")
    AppCompatActivity activity();
    void inject(SettingsActivity settingsActivity);
}
