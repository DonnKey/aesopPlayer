package com.studio4plus.homerplayer.ui;

import android.app.Activity;

public interface MainUiComponent {
    @SuppressWarnings("unused")
    Activity activity();
    void inject(MainActivity mainActivity);
}
