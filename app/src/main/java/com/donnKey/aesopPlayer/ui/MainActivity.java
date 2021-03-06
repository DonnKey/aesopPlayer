/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018-2020 Donn S. Terry
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

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.PersistableBundle;
import android.os.PowerManager;
import android.speech.tts.TextToSpeech;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.donnKey.aesopPlayer.AesopPlayerApplication;
import com.donnKey.aesopPlayer.GlobalSettings;
import com.donnKey.aesopPlayer.KioskModeSwitcher;
import com.donnKey.aesopPlayer.R;
import com.donnKey.aesopPlayer.analytics.CrashWrapper;
import com.donnKey.aesopPlayer.battery.BatteryStatusProvider;
import com.donnKey.aesopPlayer.concurrency.SimpleDeferred;
import com.donnKey.aesopPlayer.ui.provisioning.ProvisioningActivity;
import com.donnKey.aesopPlayer.ui.classic.ClassicMainUiModule;
import com.donnKey.aesopPlayer.ui.classic.DaggerClassicMainUiComponent;
import com.donnKey.aesopPlayer.concurrency.SimpleFuture;
import com.donnKey.aesopPlayer.ui.provisioning.RemoteAuto;
import com.donnKey.aesopPlayer.ui.settings.SettingsActivity;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

import javax.inject.Inject;

import org.greenrobot.eventbus.EventBus;

public class MainActivity extends AppCompatActivity implements SpeakerProvider {

    private static final int TTS_CHECK_CODE = 1;
    private static final String KIOSK_MODE_ENABLE_ACTION = "KioskModeEnable";
    private static final String ENABLE_EXTRA = "Enable";
    private static final String ANALYTICS_URL = AesopPlayerApplication.WEBSITE_URL + "install-configure.html";
    private static final String TAG = "MainActivity";
    @SuppressWarnings("FieldCanBeLocal")
    private MainUiComponent mainUiComponent;

    private BatteryStatusIndicator batteryStatusIndicator;
    private static @Nullable
    SimpleDeferred<Object> ttsDeferred;
    private OrientationActivityDelegate orientationDelegate;

    @Inject
    public UiControllerMain controller;
    @Inject
    public BatteryStatusProvider batteryStatusProvider;
    @Inject
    public GlobalSettings globalSettings;
    @Inject
    public KioskModeHandler kioskModeHandler;
    @Inject
    public KioskModeSwitcher kioskModeSwitcher;

    private PowerManager powerManager;
    private ActivityManager activityManager;
    private StatusBarCollapser statusBarCollapser;
    private TextView maintenanceMessage;
    private TextView newFeaturesMessage;

    // Used for Oreo and up suppression of status bar.
    private boolean isPaused = false;

    /* General comments:
       This section contains a lot of heuristically arrived at ad-hoc code to make the
       "simple" kiosk mode work reasonably well. Most things are easy enough, but handling of
       the Home (a.k.a Start) key (effectively "application pause") is messy. There are a few
       timeouts, but their values are not particularly critical to the design,
       although they may affect apparent responsiveness.

       Expected results do include strange partial animations, temporary reversions to portrait
       mode, and black screens. I know of no way to make any of these "stick" - they'll always
       go away after several seconds. (But I don't claim it's impossible.)

       It also posts toasts telling the user that exit was blocked. They're not always accurate,
       with both false positives and false negatives, but neither actually affects anything, and
       are associated with fast, repeated key presses.

       See restoreApp() for details on how it and cancelRestore() interact as the last line
       of defense from excessive button pushing.

       This works pretty well on 4.4.2 devices: on newer devices it's not quite as solid and more
       prone to odd things happening. Application Pinning and or full Kiosk may be better there.

       Note that Samsung has the S-Voice application that's started with a double-tap of the Home
       key on some devices. That has to be explicitly disabled manually for this to be reliable.
       See http://www.androidbeat.com/2014/05/disable-s-voice-galaxy-s5/ .
     */

    // The decision on when to toast about exit being blocked can be tricky:
    // The "exit blocked" toast looks much better after the resume.
    private final Toaster toaster = new Toaster();

    private final Restorer restorer = new Restorer();

    // For pause overrides force some restarts that would otherwise hang.
    private final PulsedBoolean recentPauseOverride = new PulsedBoolean(2000);

    // The value here should be greater than that in restoreAppWorker to get the Toast
    // below to show when it should.
    private final PulsedBoolean justCreated = new PulsedBoolean(2000);

    @Nullable
    private ColorTheme currentTheme;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        restorer.cancelRestore();
        super.onCreate(savedInstanceState);

        powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);

        mainUiComponent = DaggerClassicMainUiComponent.builder()
                .applicationComponent(AesopPlayerApplication.getComponent())
                .activityModule(new ActivityModule(this))
                .classicMainUiModule(new ClassicMainUiModule(this))
                .build();
        mainUiComponent.inject(this);

        setTheme(globalSettings.colorTheme());
        setContentView(R.layout.main_activity);

        controller.onActivityCreated();

        batteryStatusIndicator = new BatteryStatusIndicator(
                findViewById(R.id.batteryStatusIndicator), EventBus.getDefault());

        orientationDelegate = new OrientationActivityDelegate(this, globalSettings);

        maintenanceMessage = findViewById(R.id.maintenance_warning);
        newFeaturesMessage = findViewById(R.id.new_version_message);

        // (Needs above initialization)
        statusBarCollapser = new StatusBarCollapser();

        View touchEventEater = findViewById(R.id.touchEventEater);
        touchEventEater.setOnTouchListener((v, event) -> {
            // Tell the other views that the event has been handled.
            return true;
        });

        justCreated.start();

        // Periodic tasks tend to survive some restarts in some sort of zombie state which explicitly
        // deactivating and reactivating cures. Deactivate so the reactivate actually starts a worker.
        // Sometimes the worker thread starts before we even get here!
        RemoteAuto.activate(false);
        RemoteAuto.activate(globalSettings.getMailPollEnabled() || globalSettings.getFilePollEnabled());
        captureLauncher();

    }

    @Override
    protected void onStart() {
        // If the user turned off analytics, it won't take until we restart.
        if (globalSettings.forceAppRestart) {
            globalSettings.forceAppRestart = false; // Should be the case, but to be sure...
            restartMe();
        }

        // One time only opt-in.
        doesUserAllowAnalytics();

        isPaused = false;
        // If app is started with a black screen (thus, from the debugger) various bad things
        // appear to happen not under our control. At a minimum it will loop between start
        // and stop states (with all the intermediate stuff as expected) at a fairly high
        // rate (about 1.2 sec interval on one machine: not even seconds). This does not
        // occur if the screen is on.
        restorer.cancelRestore();

        super.onStart();
        // onStart must be called before the UI controller can manipulate fragments.
        controller.onActivityStart();
        orientationDelegate.onStart();
        batteryStatusProvider.start();
        handleIntent(getIntent());
        KioskModeSwitcher.enableMaintenanceMode(this, globalSettings.isMaintenanceMode());
    }

    @Override
    protected void onResume() {
        isPaused = false;
        restorer.cancelRestore();
        kioskModeSwitcher.switchToCurrentKioskMode(this);
        if (globalSettings.isMaintenanceMode()) {
            maintenanceMessage.setVisibility(View.VISIBLE);
            newFeaturesMessage.setVisibility(View.GONE);
        }
        else {
            maintenanceMessage.setVisibility(View.GONE);
            newFeaturesMessage.setVisibility(
                !globalSettings.versionIsCurrent
                    && globalSettings.getNewVersionAction() == GlobalSettings.NewVersionAction.ALL
                    ? View.VISIBLE : View.GONE);
        }

        if (!justCreated.get() && !controller.justDidPauseActionAndReset()) {
            // We toast for kiosk active here because it looks better after the screen changes stop.
            toaster.toastKioskActive();
        }

        super.onResume();

        ColorTheme theme = globalSettings.colorTheme();
        if (currentTheme != theme) {
            setTheme(theme);
            // Although it sort-of works not to call recreate(), some things don't get reset without it.
            // The cost is a replay of "Scanning Books", which is livable.
            recreate();
        }
    }

    @SuppressLint("MissingSuperCall")
    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        // Do nothing, this activity takes state from the PlayerService and the AudioBookManager.
        // There's no fault here, but a runtime error would point you here.
        // See ClassicMainUi:showPage for details.
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState, @NonNull PersistableBundle outPersistentState) {
        // Do nothing, this activity takes state from the PlayerService and the AudioBookManager.
    }

    private boolean isInteractive() {
        return Build.VERSION.SDK_INT >= 20
                ? powerManager.isInteractive()
                : powerManager.isScreenOn();
    }

    @Override
    protected void onPause() {
        // When onPause is called, it might either be a "real" pause, or a synthetic one from
        // the system that will be immediately resumed.

        if (globalSettings.isSimpleKioskModeEnabled() && isInteractive() &&
                !SettingsActivity.getInSettings() &&
                !ProvisioningActivity.getInProvisioning()
        ) {
            // Work with onStop to ignore user presses of the home key when in kiosk mode.

            // The real work of ignoring the button.
            activityManager.moveTaskToFront(getTaskId(), 0);

            // This may later be suppressed if we're doing "real" starts where we get a
            // system-generated pause "just because".
            toaster.requestToast();

            // Tell onStop that we were just here, making sure it clears after a moment.
            // According to debug log, 1/2 second is enough, but let's assume
            // slower hardware and be generous since it's a human pressing the buttons.
            // Cancel any prior clear so a prior one doesn't clear recentPauseOverride too soon.
            recentPauseOverride.start();
            restorer.restoreApp(); // In case something goes awry
        }

        // Call super.onPause() first. It may, among other things, call onResumeFragments(), so
        // calling super.onPause() before controller.onActivityPause() is necessary to ensure that
        // controller.onActivityResumeFragments() is called in the right order.
        isPaused = true;
        super.onPause();
        controller.onActivityPause();
    }

    @Override
    protected void onStop() {
        if (globalSettings.isSimpleKioskModeEnabled()) {

            // The user pressed buttons we're trying to ignore
            boolean kioskUndoStop = isInteractive() && hasWindowFocus();

            // If home (pause) is pressed twice with an interval around 1/2 second, there
            // is a narrow interval (1/10(?) second wide) where it will go directly to Stop state
            // and one or the other of isInteractive and hasWindowFocus (or both) is not set and
            // it gets into a limbo of stopped but not restarting. Thus...
            // If home (pause) was just recently pressed (and we're in kiosk mode),
            // undo stop forcibly.
            kioskUndoStop |= recentPauseOverride.get();

            // But if we're entering settings, just let it stop.
            kioskUndoStop &= !SettingsActivity.getInSettings()
                          && !ProvisioningActivity.getInProvisioning();

            if (kioskUndoStop) {
                // We have to let the stop proceed, but then force a restart
                toaster.requestToast();
                restorer.restoreApp();
            }
        }

        controller.onActivityStop();
        orientationDelegate.onStop();
        super.onStop();
        batteryStatusProvider.stop();

    }

    // Defer posting a toast until onResume is called, but only for a little while after
    // the request to do so.  The toast looks better after onResume. Reduce stuttering and
    // pile-up of toast times.
    private class Toaster {
        private final Handler handler = new Handler();
        boolean toastNeeded;
        Toast lastToast = null;

        void requestToast() {
            toastNeeded = true;
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(() -> toastNeeded = false, 2000);
        }

        void toastKioskActive() {
            if (toastNeeded)
            {
                doToast();
                toastNeeded = false;
            }
        }

        void doToast() {
            if (lastToast != null) {
                // Cancel prior one (that may prove a no-op) so the times don't accumulate.
                // It'll clear LENGTH_SHORT after the last toast is posted.
                lastToast.cancel();
                lastToast = null;
            }

            lastToast = Toast.makeText(MainActivity.this, R.string.back_suppressed_by_kiosk, Toast.LENGTH_SHORT);
            lastToast.show();
        }
    }

    @Override
    public void onBackPressed() {
        if (globalSettings.isAnyKioskModeEnabled()) {
            toaster.doToast();
        } else {
            // This allows us to keep the Home Activity as aesopPlayer, while when there's
            // no active Kiosk, going to the launcher the user expects.  See captureLauncher.
            if (globalSettings.getOriginalLauncherPackage().equals("android")) {
                // That's the fallback default... fire off a chooser.
                Intent i = new Intent(Intent.ACTION_MAIN)
                        .addCategory(Intent.CATEGORY_HOME);
                startActivity(i);
            }
            else {
                Intent i = new Intent();
                i.setClassName(globalSettings.getOriginalLauncherPackage(), globalSettings.getOriginalLauncherActivity());
                startActivity(i);
            }

            super.onBackPressed();
            finish(); // gets us to the general launcher.
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        //noinspection StatementWithEmptyBody
        if (hasFocus) {
            // Start animations.
            batteryStatusIndicator.startAnimations();
        }
        else {
            /* The below works too well, making it difficult to deal with (at least) the
               Application pinning dialog and "volume too loud" warnings. For future
               reference if dialogs end up confusing users.
            // Close every kind of system dialog
            Intent closeDialog = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            sendBroadcast(closeDialog);
            */
        }

        if (globalSettings.isSimpleKioskModeEnabled()) {
            statusBarCollapser.closeStatusBar();
        }
    }

    @Override
    protected void onDestroy() {
        restorer.cancelRestore();
        batteryStatusIndicator.shutdown();
        controller.onActivityDestroy();
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        // A call with no grantResults means the dialog has been closed without any user decision.
        if (grantResults.length > 0) {
            // Book scan results:
            controller.onRequestPermissionResult(requestCode, permissions, grantResults);
        }
    }

    protected void onActivityResult(
            int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == TTS_CHECK_CODE) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
                if (ttsDeferred != null) {
                    ttsDeferred.setResult(this); // Result value not needed, ignored.
                    ttsDeferred = null;
                }
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                        TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                try {
                    startActivity(installIntent);
                } catch (ActivityNotFoundException e) {
                    CrashWrapper.log(TAG,  "No activity to handle Text-to-Speech data installation.");
                    if (ttsDeferred != null) {
                        ttsDeferred.setException(e);
                        ttsDeferred = null;
                    }
                }
            }
        }
    }

    @Override
    @NonNull
    public SimpleFuture<Object> obtainTts() {
        SimpleDeferred<Object> result = ttsDeferred;
        if (ttsDeferred == null) {
            result = new SimpleDeferred<>();
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            try {
                startActivityForResult(checkIntent, TTS_CHECK_CODE);
                ttsDeferred = result;
            } catch (ActivityNotFoundException e) {
                CrashWrapper.log(TAG, "Text-to-Speech not available");
                result.setException(e);
                // ttsDeferred stays unset because the exception is delivered.
            }
        }
        return result;
    }

    private void captureLauncher() {
        PackageManager pm = getPackageManager();

        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_HOME);

        // A possible value is android/com.android.internal.app.ResolverActivity, for when
        // no default is set. That's intended to yield a chooser, and when we launch the
        // intent, we honor that explicitly.
        // Note: none of the entries in the IntentActivities list directly matches that!
        ResolveInfo defaultResolution = getPackageManager().resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY);

        String packageName = null;
        String activityName = null;
        if (globalSettings.getOriginalLauncherPackage().isEmpty()) {
            // no launcher recorded... find one
            if (defaultResolution == null || defaultResolution.activityInfo.packageName.contains("aesopPlayer")) {
                // We don't have a default launcher to refer to... guess
                List<ResolveInfo> resolveInfos = pm.queryIntentActivities(i, PackageManager.MATCH_DEFAULT_ONLY);
                ResolveInfo selectedInfo = resolveInfos.get(0);
                for (ResolveInfo resolveInfo : resolveInfos) {
                    if (resolveInfo.activityInfo.packageName.contains("Resolver")) {
                        selectedInfo = resolveInfo;
                        break;
                    }
                }
                packageName = selectedInfo.activityInfo.packageName;
                activityName = selectedInfo.activityInfo.name;
            } else {
                packageName = defaultResolution.activityInfo.packageName;
                activityName = defaultResolution.activityInfo.name;
            }
        } else {
            // We have a launcher recorded... did the user change it?
            if (defaultResolution != null && !defaultResolution.activityInfo.packageName.contains("aesopPlayer")) {
                packageName = defaultResolution.activityInfo.packageName;
                activityName = defaultResolution.activityInfo.name;
            }
        }
        if (packageName != null) {
            globalSettings.setOriginalLauncherPackage(packageName);
            globalSettings.setOriginalLauncherActivity(activityName);
        }
    }

    // Triggered from an external (PC) app that's not finished yet. Not reachable from
    // within this app.
    private void handleIntent(Intent intent) {
        if (intent != null && KIOSK_MODE_ENABLE_ACTION.equals(intent.getAction())) {
            if (KioskModeSwitcher.isLockTaskPermitted(getApplicationContext())) {
                boolean enable = intent.getBooleanExtra(ENABLE_EXTRA, false);
                if (globalSettings.isFullKioskModeEnabled() != enable) {
                    globalSettings.setKioskModeNow(GlobalSettings.SettingsKioskMode.FULL);

                    // For some reason clearing the preferred Home activity only takes effect if the
                    // application exits (finishing the activity doesn't help).
                    // This issue doesn't happen when disabling the kiosk mode from the settings
                    // screen and I'm out of ideas.
                    if (!enable) {
                        new Handler(getMainLooper()).postDelayed(() -> System.exit(0), 500);
                    }
                }
            }
        }
    }

    private void doesUserAllowAnalytics() {
        if (!globalSettings.getAnalyticsQueried()) {
            AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(this)
                    .setMessage(Html.fromHtml(getString(R.string.permission_rationale_analytics, ANALYTICS_URL)))
                    .setTitle(R.string.permission_rationale_analytics_title)
                    .setIcon(R.drawable.ic_launcher);
            dialogBuilder.setPositiveButton(R.string.permission_kind_allow,
                    (dialogInterface, i) -> {
                        globalSettings.setAnalytics(true);
                        CrashWrapper.start(getApplicationContext(), true);
                    });
            dialogBuilder.setNegativeButton(R.string.permission_kind_disallow,
                    (dialogInterface, i) -> globalSettings.setAnalytics(false));
            AlertDialog dialog = dialogBuilder.create();
            dialog.show();
            ((TextView) Objects.requireNonNull(dialog.findViewById(android.R.id.message))).setMovementMethod(LinkMovementMethod.getInstance());
        }

        globalSettings.setAnalyticsQueried(true);
    }

    private void restartMe() {
        Context context = getApplicationContext();
        Intent newActivity = new Intent(context, AesopPlayerApplication.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 5551212, newActivity, PendingIntent.FLAG_CANCEL_CURRENT);
        AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        assert mgr != null;
        mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, pendingIntent);
        System.exit(0);
    }

    private class Restorer {
        // The functions below serve as the last line of defense from too many button
        // pushes. restoreApp is called whenever there's a chance that that's happened.
        // cancelRestore is called when something "good" happens to indicate that normal
        // processing has occurred, and this isn't needed. If the cancellation doesn't
        // occur, a call to forcibly restore the app will occur after a delay. If a
        // cancellation doesn't then occur, it will try one more time to force the issue
        // after a longer delay. Note that the "counting" is done in the program state
        // so there's no issue of local variables being reset in the process.

        private static final int restoreDelay = 100;
        private final Handler restoreHandler = new Handler();

        // If the app hasn't started after the delay, force the issue.
        private void restoreApp() {
            cancelRestore(); // Only one restart should be active, so clean up.
            restoreHandler.postDelayed(this::doRestoreApp, restoreDelay);
        }

        // All went well, cancel the forced restart
        private void cancelRestore() {
            restoreHandler.removeCallbacksAndMessages(null);
        }

        // Post another try at cleaning up that won't itself try again
        // and then force the restore.
        private void doRestoreApp() {
            restoreHandler.postDelayed(this::restoreAppWorker, 2 * restoreDelay);
            restoreAppWorker();
        }

        // Restart this activity after stop shut it down.
        // (After Andreas Schrade's article on Kiosks)
        private void restoreAppWorker() {
            // In case there are others pending, kill them
            cancelRestore();

            if (SettingsActivity.getInSettings()
                || ProvisioningActivity.getInProvisioning() ) {
                // A switch to settings or provisioning mode looks like an unexpected pause, so
                // don't actually do anything. (This would be very hard to get
                // right in the main line, and is easy here.)
                return;
            }
            Context context = getApplicationContext();
            Intent i = new Intent(context, MainActivity.this.getClass());
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            // This "indirection" triggers the intent immediately rather than
            // dealing with a 5 second delay. Works up through Pie.
            // Q forces a switch to root screen regardless, which won't work, so
            // we don't support Simple on Q (which is not much of a loss).
            PendingIntent pi = PendingIntent.getActivity(context, 0, i, 0);
            try {
                pi.send();
            } catch (PendingIntent.CanceledException e) {
                CrashWrapper.recordException(TAG, e);
            }
        }
    }

    class StatusBarCollapser {
        // On Oreo and above, it's not possible to disable the status bar in the ways that
        // work on earlier releases. There's an arms-race between folks who want a solid
        // Kiosk mode and the Android team's concerns about security. This is as good as
        // can be done on Oreo. This might not always work.
        // We'll just ignore any failure, and if the user doesn't like it, there are other
        // Kiosk modes available.
        StatusBarCollapser() {
            getStatusBarCollapser();
        }

        private Object statusBarService;
        private Method collapseStatusBar = null;

        @SuppressLint({"WrongConstant", "PrivateApi"})
        private void getStatusBarCollapser() {
            // This bit of code is accessing a function not in the SDK, and thus isn't guaranteed
            // to be supported forever.  However it's been this way since Lollipop.
            //
            // If collapseStatusBar doesn't get set, then we can't suppress the status bar this way,
            // and we'll just ignore it.
            //
            // This requires EXPAND_STATUS_BAR permissions, which is granted from the manifest.

            if (KioskModeHandler.canDrawOverlays(getApplicationContext())) {
                // Don't bother - something else better is doing the job (if the user wants).
                return;
            }

            // The string "statusbar" below is reporting a severe warning (but not an error); we
            // will just ignore that.
            statusBarService = getSystemService("statusbar"); // wrong constant warning
            Class<?> statusBarManager;

            try {
                statusBarManager = Class.forName("android.app.StatusBarManager");
                // Prior to API 17, the method to call is 'collapse()'
                // API 17 onwards, the method to call is `collapsePanels()`
                //noinspection JavaReflectionMemberAccess
                collapseStatusBar = statusBarManager.getMethod("collapsePanels"); // private api
            }
            catch (Exception e) { // possible ClassNotFound
                // collapseStatusBar remains null
                return;
            }

            collapseStatusBar.setAccessible(true);
        }

        private final Handler collapseNotificationHandler = new Handler();

        private void forceCollapse() {
            // Do the real work.
            try {
                collapseStatusBar.invoke(statusBarService);
            }
            catch (Exception e) {
                collapseStatusBar = null;
            }
        }
        private void redoCollapse() {
            forceCollapse();
            if (hasWindowFocus() || isPaused) return;
            collapseNotificationHandler.postDelayed(this::redoCollapse, 1000);
        }

        private void closeStatusBar() {
            if (collapseStatusBar == null) {
                // Just ignore the situation
                return;
            }

            // Not focused, but not Paused == status bar is active
            if (!hasWindowFocus() && !isPaused) {
                // Get it closed as quickly as possible, but on some releases (seen on API25)
                // the sweet spot is out one second or so, but by that time the focus and paused
                // test claims it's gone, and it really isn't. Yes, ugly. (On later releases
                // it does work to keep checking focus and paused until that's done.)
                // (It appears that focus is changed when the bar is only half removed, and
                // it takes a second try to finish the job.)
                collapseNotificationHandler.postDelayed(this::forceCollapse,  250);
                collapseNotificationHandler.postDelayed(this::forceCollapse,  500);

                collapseNotificationHandler.postDelayed(this::redoCollapse, 1000);
            }
        }
    }

    static class PulsedBoolean {
        boolean mBool;
        final long mDelay;
        // These need to be on separate handler queues so they can be cleared.
        // (I can't make removing specific run-ables from the common queue work!)
        final Handler handler = new Handler();

        @SuppressWarnings("SameParameterValue")
        PulsedBoolean(long delay)
        {
            mDelay = delay;
        }

        void start() {
            mBool = true;
            // Reset the request each time, so there's only the most recent.
            handler.removeCallbacksAndMessages(null);
            handler.postDelayed(()->mBool = false, mDelay);
        }

        boolean get() {
            return mBool;
        }
    }

    private void setTheme(@NonNull ColorTheme theme) {
        currentTheme = theme;
        setTheme(theme.styleId);
    }
}