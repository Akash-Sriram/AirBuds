package org.airbridge.tws;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.view.DisplayCutout;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity implements BudsState.Listener {

    private static final int FORM_MAIN = 0;
    private static final int FORM_SOUND = 1;
    private static final int FORM_DEVICES = 2;
    private static final int FORM_GESTURES = 3;

    private int mCurrentForm = FORM_MAIN;

    // Bound Service Reference
    private BudsService mService;
    private boolean mBound = false;

    private final Handler mUiHandler = new Handler(Looper.getMainLooper());

    // UI Views
    private TextView tvDeviceName;
    private TextView tvStatus;
    private TextView tvBatteryLeft;
    private ImageView ivChargeLeft;
    private TextView tvBatteryRight;
    private ImageView ivChargeRight;
    private TextView tvBatteryCase;
    private ImageView ivChargeCase;
    private TextView tvUsageBadge;

    private LinearLayout llAncBtnCancel;
    private FrameLayout flAncCircleOn;
    private ImageView ivAncIconOn;
    private TextView tvAncLabelOn;

    private LinearLayout llAncBtnOff;
    private FrameLayout flAncCircleOff;
    private ImageView ivAncIconOff;
    private TextView tvAncLabelOff;

    private LinearLayout llAncBtnTrans;
    private FrameLayout flAncCircleTrans;
    private ImageView ivAncIconTrans;
    private TextView tvAncLabelTrans;

    private View rowAncLevel;
    private TextView tvAncLevelVal;
    private LinearLayout llAncNoiseOptions;
    private LinearLayout llAncTransOptions;

    private CheckBox cbCycleAnc;
    private CheckBox cbCycleTrans;
    private CheckBox cbCycleOff;

    private Spinner spLeftDouble;
    private Spinner spLeftTriple;
    private Spinner spLeftHold;
    private Spinner spRightDouble;
    private Spinner spRightTriple;
    private Spinner spRightHold;
    private Button btnApplyTouch;

    private Switch swMultiDevice;
    private View btnRefreshDevices;
    private LinearLayout llDeviceList;

    private Switch swGameMode;
    private Switch swAutoAnswer;
    private Switch swInEar;
    private Switch swDynamicBass;
    private Switch swSpatialAudio;
    private TextView tvSpatialModeLabel;
    private Switch swWindNoise;
    private Switch swVocalEnhance;
    private Switch swHiRes;
    private Switch swGoldenSound;
    private Switch swShowNotification;
    private Switch swSuppressGoogle;
    private TextView tvSuppressGoogleDesc;
    private Button btnReconnect;

    private TextView tvHeroCodec;
    private TextView tvLeftWearStatus;
    private TextView tvCaseWearStatus;
    private TextView tvRightWearStatus;
    private Button btnRingBuds;
    private boolean mIsRinging = false;

    private Button btnFitTest;
    private AlertDialog mFitTestDialog;
    private TextView tvFitTestLeft;
    private TextView tvFitTestRight;
    private TextView tvFitTestStatus;
    private Button btnFitTestAct;
    private boolean mFitTestRunning = false;

    private LinearLayout llCustomEqPanel;
    private EqGraphView mEqGraphView;
    private Button btnApplyCustomEq;
    private Button btnResetCustomEq;

    private LinearLayout llDynamicSliders;
    private SeekBar sbDynLow;
    private SeekBar sbDynMed;
    private SeekBar sbDynHigh;
    private TextView tvDynLowVal;
    private TextView tvDynMedVal;
    private TextView tvDynHighVal;

    private LinearLayout llFormMain;
    private LinearLayout llFormSound;
    private LinearLayout llFormDevices;
    private LinearLayout llFormGestures;

    private View cardNavSound;
    private View cardNavDevices;
    private View cardNavGestures;

    private View btnBackSound;
    private View btnBackDevices;
    private View btnBackGestures;

    private TextView tvDeviceNavSubtitle;
    private View rowOpenEqMode;
    private TextView tvSoundEqSubtitle;

    private View rowEqSerenade;
    private View rowEqOriginal;
    private View rowEqClearBass;
    private View rowEqDeepBass;
    private View rowEqCustom1;

    private ImageView ivRadioSerenade;
    private ImageView ivRadioOriginal;
    private ImageView ivRadioClearBass;
    private ImageView ivRadioDeepBass;
    private ImageView ivRadioCustom1;

    private TextView tvInfoFirmware;
    private TextView tvInfoHardware;

    public static class GestureOption {
        public final int id;
        public final String name;

        public GestureOption(int id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private final List<GestureOption> mGestureOptions = new ArrayList<>();

    // ------------------------------------------------------------------------
    // Service Connection
    // ------------------------------------------------------------------------

    private final ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            BudsService.LocalBinder binder = (BudsService.LocalBinder) service;
            mService = binder.getService();
            mBound = true;
            mService.addListener(MainActivity.this);
            renderState(mService.getState());
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        adjustWindowInsets();
        initViews();
        setupGestureOptions();
        setupListeners();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                () -> {
                    if (mCurrentForm != FORM_MAIN) {
                        showForm(FORM_MAIN);
                    } else {
                        finish();
                    }
                }
            );
        }

        checkPermissionsAndStartService();
    }

    private void checkPermissionsAndStartService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
            if (checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    perms.add(Manifest.permission.POST_NOTIFICATIONS);
                }
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), 101);
                return;
            }
        }
        startAndBindService();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        startAndBindService();
    }

    private void startAndBindService() {
        Intent intent = new Intent(this, BudsService.class);
        startService(intent);
        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateNotificationListenerUi();
        if (mBound && mService != null) {
            renderState(mService.getState());
        }
    }

    private void updateNotificationListenerUi() {
        if (swSuppressGoogle == null) return;
        boolean granted = AirBudsNotificationListener.isPermissionGranted(this);
        boolean prefEnabled = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
            .getBoolean("pref_suppress_google", true);
        swSuppressGoogle.setChecked(granted && prefEnabled);
        if (tvSuppressGoogleDesc != null) {
            if (!granted) {
                tvSuppressGoogleDesc.setText("Tap to grant Notification Access in Android Settings");
                tvSuppressGoogleDesc.setTextColor(getColor(R.color.primary));
            } else {
                tvSuppressGoogleDesc.setText("Active • Automatically suppresses Google Fast Pair popups");
                tvSuppressGoogleDesc.setTextColor(getColor(R.color.text_dim));
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBound && mService != null) {
            mService.removeListener(this);
            unbindService(mConnection);
            mBound = false;
            mService = null;
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (mCurrentForm != FORM_MAIN) {
            showForm(FORM_MAIN);
        } else {
            super.onBackPressed();
        }
    }

    public void showForm(int formId) {
        mCurrentForm = formId;
        if (llFormMain != null) llFormMain.setVisibility(formId == FORM_MAIN ? View.VISIBLE : View.GONE);
        if (llFormSound != null) llFormSound.setVisibility(formId == FORM_SOUND ? View.VISIBLE : View.GONE);
        if (llFormDevices != null) llFormDevices.setVisibility(formId == FORM_DEVICES ? View.VISIBLE : View.GONE);
        if (llFormGestures != null) llFormGestures.setVisibility(formId == FORM_GESTURES ? View.VISIBLE : View.GONE);

        ScrollView rootScroll = findViewById(R.id.root_scroll);
        if (rootScroll != null) {
            rootScroll.smoothScrollTo(0, 0);
        }
    }

    @SuppressWarnings("deprecation")
    private void adjustWindowInsets() {
        boolean isNight = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                int appearance = isNight ? 0 : (WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
                controller.setSystemBarsAppearance(appearance, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            View decor = getWindow().getDecorView();
            int flags = decor.getSystemUiVisibility();
            if (!isNight) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            } else {
                flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags &= ~View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
                }
            }
            decor.setSystemUiVisibility(flags);
        }

        View root = findViewById(R.id.root_scroll);
        if (root != null) {
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                int top = 0;
                int bottom = 0;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    android.graphics.Insets insetsBars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                    top = insetsBars.top;
                    bottom = insetsBars.bottom;
                } else {
                    top = insets.getSystemWindowInsetTop();
                    bottom = insets.getSystemWindowInsetBottom();
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        DisplayCutout cutout = insets.getDisplayCutout();
                        if (cutout != null) {
                            top = Math.max(top, cutout.getSafeInsetTop());
                        }
                    }
                }
                float density = getResources().getDisplayMetrics().density;
                int padH = (int) (16 * density);
                int padTop = top + (int) (10 * density);
                int padB = bottom + (int) (24 * density);
                v.setPadding(padH, padTop, padH, padB);
                return insets;
            });
        }
    }

    private void initViews() {
        tvDeviceName = findViewById(R.id.tv_device_name);
        tvStatus = findViewById(R.id.tv_status);
        tvHeroCodec = findViewById(R.id.tv_hero_codec);
        tvBatteryLeft = findViewById(R.id.tv_battery_left);
        ivChargeLeft = findViewById(R.id.iv_charge_left);
        tvLeftWearStatus = findViewById(R.id.tv_left_wear_status);
        tvBatteryRight = findViewById(R.id.tv_battery_right);
        ivChargeRight = findViewById(R.id.iv_charge_right);
        tvRightWearStatus = findViewById(R.id.tv_right_wear_status);
        tvBatteryCase = findViewById(R.id.tv_battery_case);
        ivChargeCase = findViewById(R.id.iv_charge_case);
        tvCaseWearStatus = findViewById(R.id.tv_case_wear_status);
        btnRingBuds = findViewById(R.id.btn_ring_buds);
        swGoldenSound = findViewById(R.id.sw_golden_sound);
        btnFitTest = findViewById(R.id.btn_fit_test);

        llAncBtnCancel = findViewById(R.id.ll_anc_btn_cancel);
        flAncCircleOn = findViewById(R.id.fl_anc_circle_on);
        ivAncIconOn = findViewById(R.id.iv_anc_icon_on);
        tvAncLabelOn = findViewById(R.id.tv_anc_label_on);

        llAncBtnOff = findViewById(R.id.ll_anc_btn_off);
        flAncCircleOff = findViewById(R.id.fl_anc_circle_off);
        ivAncIconOff = findViewById(R.id.iv_anc_icon_off);
        tvAncLabelOff = findViewById(R.id.tv_anc_label_off);

        llAncBtnTrans = findViewById(R.id.ll_anc_btn_trans);
        flAncCircleTrans = findViewById(R.id.fl_anc_circle_trans);
        ivAncIconTrans = findViewById(R.id.iv_anc_icon_trans);
        tvAncLabelTrans = findViewById(R.id.tv_anc_label_trans);

        rowAncLevel = findViewById(R.id.row_anc_level);
        tvAncLevelVal = findViewById(R.id.tv_anc_level_val);
        llAncNoiseOptions = findViewById(R.id.ll_anc_noise_options);
        llAncTransOptions = findViewById(R.id.ll_anc_trans_options);

        spLeftDouble = findViewById(R.id.sp_left_double);
        spLeftTriple = findViewById(R.id.sp_left_triple);
        spLeftHold = findViewById(R.id.sp_left_hold);
        spRightDouble = findViewById(R.id.sp_right_double);
        spRightTriple = findViewById(R.id.sp_right_triple);
        spRightHold = findViewById(R.id.sp_right_hold);
        btnApplyTouch = findViewById(R.id.btn_apply_touch);

        swMultiDevice = findViewById(R.id.sw_multi_device);
        btnRefreshDevices = findViewById(R.id.btn_refresh_devices);
        llDeviceList = findViewById(R.id.ll_device_list);

        swGameMode = findViewById(R.id.sw_game_mode);
        swInEar = findViewById(R.id.sw_in_ear);
        swWindNoise = findViewById(R.id.sw_wind_noise);
        swVocalEnhance = findViewById(R.id.sw_vocal_enhance);
        swShowNotification = findViewById(R.id.sw_show_notification);
        swSuppressGoogle = findViewById(R.id.sw_suppress_google);
        tvSuppressGoogleDesc = findViewById(R.id.tv_suppress_google_desc);
        updateNotificationListenerUi();
        btnReconnect = findViewById(R.id.btn_reconnect);
        llFormMain = findViewById(R.id.ll_form_main);
        llFormSound = findViewById(R.id.ll_form_sound);
        llFormDevices = findViewById(R.id.ll_form_devices);
        llFormGestures = findViewById(R.id.ll_form_gestures);
        tvInfoFirmware = findViewById(R.id.tv_info_firmware);
        tvInfoHardware = findViewById(R.id.tv_info_hardware);
        TextView tvInfoAppVersion = findViewById(R.id.tv_info_app_version);
        if (tvInfoAppVersion != null) {
            try {
                String verName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                tvInfoAppVersion.setText("v" + verName + " (Production)");
            } catch (Exception ignored) {}
        }

        cardNavSound = findViewById(R.id.card_nav_sound);
        cardNavDevices = findViewById(R.id.card_nav_devices);
        cardNavGestures = findViewById(R.id.card_nav_gestures);

        btnBackSound = findViewById(R.id.btn_back_sound);
        btnBackDevices = findViewById(R.id.btn_back_devices);
        btnBackGestures = findViewById(R.id.btn_back_gestures);

        tvDeviceNavSubtitle = findViewById(R.id.tv_device_nav_subtitle);
        rowOpenEqMode = findViewById(R.id.row_open_eq_mode);
        tvSoundEqSubtitle = findViewById(R.id.tv_sound_eq_subtitle);

        rowEqSerenade = findViewById(R.id.row_eq_serenade);
        rowEqOriginal = findViewById(R.id.row_eq_original);
        rowEqClearBass = findViewById(R.id.row_eq_clear_bass);
        rowEqDeepBass = findViewById(R.id.row_eq_deep_bass);
        rowEqCustom1 = findViewById(R.id.row_open_eq_mode);

        ivRadioSerenade = findViewById(R.id.iv_radio_serenade);
        ivRadioOriginal = findViewById(R.id.iv_radio_original);
        ivRadioClearBass = findViewById(R.id.iv_radio_clear_bass);
        ivRadioDeepBass = findViewById(R.id.iv_radio_deep_bass);
        ivRadioCustom1 = findViewById(R.id.iv_radio_custom1);

        swSpatialAudio = findViewById(R.id.sw_spatial_audio);
        swDynamicBass = findViewById(R.id.sw_dynamic_bass);
        swHiRes = findViewById(R.id.sw_hi_res);

        llDynamicSliders = findViewById(R.id.ll_dynamic_sliders);
        sbDynLow = findViewById(R.id.sb_dyn_low);
        sbDynMed = findViewById(R.id.sb_dyn_med);
        sbDynHigh = findViewById(R.id.sb_dyn_high);
        tvDynLowVal = findViewById(R.id.tv_dyn_low_val);
        tvDynMedVal = findViewById(R.id.tv_dyn_med_val);
        tvDynHighVal = findViewById(R.id.tv_dyn_high_val);

        swAutoAnswer = findViewById(R.id.sw_auto_answer);
        tvSpatialModeLabel = findViewById(R.id.tv_spatial_mode_label);

        llCustomEqPanel = findViewById(R.id.ll_custom_eq_panel);
        mEqGraphView = findViewById(R.id.sound_eq_graph);

        btnApplyCustomEq = findViewById(R.id.btn_apply_custom_eq);
        btnResetCustomEq = findViewById(R.id.btn_reset_custom_eq);

        cbCycleAnc = findViewById(R.id.cb_cycle_anc);
        cbCycleTrans = findViewById(R.id.cb_cycle_trans);
        cbCycleOff = findViewById(R.id.cb_cycle_off);

        updateAncUi(8);
    }

    private void setupGestureOptions() {
        mGestureOptions.clear();
        mGestureOptions.add(new GestureOption(1, "Play / Pause"));
        mGestureOptions.add(new GestureOption(6, "Next Track"));
        mGestureOptions.add(new GestureOption(5, "Previous Track"));
        mGestureOptions.add(new GestureOption(8, "ANC Cycle"));
        mGestureOptions.add(new GestureOption(11, "Volume Up"));
        mGestureOptions.add(new GestureOption(12, "Volume Down"));
        mGestureOptions.add(new GestureOption(17, "Game Mode"));
        mGestureOptions.add(new GestureOption(13, "Switch Device"));
        mGestureOptions.add(new GestureOption(3, "Voice Assistant"));
        mGestureOptions.add(new GestureOption(0, "None"));

        ArrayAdapter<GestureOption> adapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                mGestureOptions
        );

        spLeftDouble.setAdapter(adapter);
        spLeftTriple.setAdapter(adapter);
        if (spLeftHold != null) spLeftHold.setAdapter(adapter);
        spRightDouble.setAdapter(adapter);
        spRightTriple.setAdapter(adapter);
        if (spRightHold != null) spRightHold.setAdapter(adapter);
    }

    private void setupListeners() {
        if (btnReconnect != null) btnReconnect.setOnClickListener(v -> {
            if (mService != null) {
                if (!mService.hasBondedBuds() || "No Buds Found".equals(mService.getState().statusText)) {
                    showPairingHelperDialog();
                } else {
                    mService.reconnect();
                }
            }
        });


        // Form Navigation
        if (cardNavSound != null) cardNavSound.setOnClickListener(v -> showForm(FORM_SOUND));
        if (cardNavDevices != null) cardNavDevices.setOnClickListener(v -> {
            showForm(FORM_DEVICES);
            if (mService != null) mService.queryConnectedDevices();
        });
        if (cardNavGestures != null) cardNavGestures.setOnClickListener(v -> showForm(FORM_GESTURES));

        View.OnClickListener backToMain = v -> showForm(FORM_MAIN);
        if (btnBackSound != null) btnBackSound.setOnClickListener(backToMain);
        if (btnBackDevices != null) btnBackDevices.setOnClickListener(backToMain);
        if (btnBackGestures != null) btnBackGestures.setOnClickListener(backToMain);

        // ANC Modes
        if (llAncBtnCancel != null) llAncBtnCancel.setOnClickListener(v -> {
            if (mService != null) mService.setAncMode(8);
        });
        if (llAncBtnOff != null) llAncBtnOff.setOnClickListener(v -> {
            if (mService != null) mService.setAncMode(1);
        });
        if (llAncBtnTrans != null) llAncBtnTrans.setOnClickListener(v -> {
            if (mService != null) mService.setAncMode(2);
        });

        if (rowAncLevel != null) rowAncLevel.setOnClickListener(v -> showAncLevelDialog());

        // Noise Control Cycling
        android.widget.CompoundButton.OnCheckedChangeListener cycleListener = (btn, checked) -> {
            if (btn.isPressed()) updateAncHoldCycleFromUi();
        };
        if (cbCycleAnc != null) cbCycleAnc.setOnCheckedChangeListener(cycleListener);
        if (cbCycleTrans != null) cbCycleTrans.setOnCheckedChangeListener(cycleListener);
        if (cbCycleOff != null) cbCycleOff.setOnCheckedChangeListener(cycleListener);

        // EQ Presets
        if (rowEqSerenade != null) rowEqSerenade.setOnClickListener(v -> { if (mService != null) mService.setEqPreset(2); });
        if (rowEqOriginal != null) rowEqOriginal.setOnClickListener(v -> { if (mService != null) mService.setEqPreset(0); });
        if (rowEqClearBass != null) rowEqClearBass.setOnClickListener(v -> { if (mService != null) mService.setEqPreset(3); });
        if (rowEqDeepBass != null) rowEqDeepBass.setOnClickListener(v -> { if (mService != null) mService.setEqPreset(1); });
        if (rowEqCustom1 != null) rowEqCustom1.setOnClickListener(v -> { if (mService != null) mService.setEqPreset(4); });

        // Interactive 6-Band EQ Graph
        if (mEqGraphView != null) {
            mEqGraphView.setOnEqChangeListener(new EqGraphView.OnEqChangeListener() {
                @Override
                public void onGainChanged(int bandIdx, int gainDb) {
                    if (mService != null && mService.getState() != null) {
                        mService.getState().eqBands[bandIdx] = gainDb;
                    }
                }

                @Override
                public void onGainChangeFinished(int[] allGains) {
                    if (mService != null) {
                        mService.applyCustomEq(allGains);
                    }
                }
            });
        }

        if (btnApplyCustomEq != null) btnApplyCustomEq.setOnClickListener(v -> {
            if (mEqGraphView != null && mService != null) {
                mService.applyCustomEq(mEqGraphView.getGains());
            }
            Toast.makeText(this, "Custom EQ applied", Toast.LENGTH_SHORT).show();
        });

        if (btnResetCustomEq != null) btnResetCustomEq.setOnClickListener(v -> {
            if (mEqGraphView != null) {
                mEqGraphView.resetFlat();
                if (mService != null) {
                    mService.applyCustomEq(mEqGraphView.getGains());
                }
            }
            Toast.makeText(this, "Reset to Flat (0dB)", Toast.LENGTH_SHORT).show();
        });

        // Feature Switches (Protected by isPressed to prevent echo feedback)
        if (swAutoAnswer != null) swAutoAnswer.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_AUTO_ANSWER, checked ? 1 : 0);
        });
        if (swInEar != null) swInEar.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_IN_EAR, checked ? 1 : 0);
        });
        if (swGameMode != null) swGameMode.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_GAME_MODE, checked ? 1 : 0);
        });
        if (swWindNoise != null) swWindNoise.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_WIND_NOISE, checked ? 1 : 0);
        });
        if (swVocalEnhance != null) swVocalEnhance.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_VOCAL_ENHANCE, checked ? 1 : 0);
        });
        if (swSpatialAudio != null) swSpatialAudio.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_SPATIAL_AUDIO, checked ? 1 : 0);
        });
        if (swHiRes != null) swHiRes.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_HI_RES, checked ? 1 : 0);
        });
        if (swGoldenSound != null) swGoldenSound.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_GOLDEN_SOUND, checked ? 1 : 0);
        });
        if (swShowNotification != null) {
            swShowNotification.setOnCheckedChangeListener((btn, checked) -> {
                if (btn.isPressed() && mService != null) {
                    mService.setShowNotification(checked);
                }
            });
        }
        if (swSuppressGoogle != null) {
            swSuppressGoogle.setOnClickListener(v -> {
                boolean granted = AirBudsNotificationListener.isPermissionGranted(this);
                if (!granted) {
                    swSuppressGoogle.setChecked(false);
                    try {
                        startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
                        Toast.makeText(this, "Enable 'AirBuds Notification Controller'", Toast.LENGTH_LONG).show();
                    } catch (Exception e) {
                        Toast.makeText(this, "Open Settings -> Apps -> Special app access -> Notification access", Toast.LENGTH_LONG).show();
                    }
                } else {
                    boolean enable = swSuppressGoogle.isChecked();
                    getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("pref_suppress_google", enable)
                        .apply();
                    updateNotificationListenerUi();
                }
            });
        }
        if (swMultiDevice != null) swMultiDevice.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) mService.setFeature(RealmeProtocol.FEAT_MULTI_DEVICE, checked ? 1 : 0);
        });

        // Dynamic Bass
        if (swDynamicBass != null) swDynamicBass.setOnCheckedChangeListener((btn, checked) -> {
            if (btn.isPressed() && mService != null) {
                int level = (sbDynMed != null) ? sbDynMed.getProgress() + 1 : 3;
                mService.setDynamicBass(checked, level);
            }
        });

        if (sbDynMed != null) sbDynMed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && tvDynMedVal != null) {
                    tvDynMedVal.setText(String.valueOf(progress + 1));
                }
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (mService != null) {
                    mService.setDynamicBass(swDynamicBass != null && swDynamicBass.isChecked(), seekBar.getProgress() + 1);
                }
            }
        });

        // Gestures Apply Button
        if (btnApplyTouch != null) btnApplyTouch.setOnClickListener(v -> applyTouchSettings());

        // Multipoint Refresh
        if (btnRefreshDevices != null) btnRefreshDevices.setOnClickListener(v -> {
            if (mService != null) {
                mService.queryConnectedDevices();
                Toast.makeText(this, "Refreshing device routing...", Toast.LENGTH_SHORT).show();
            }
        });

        // Ring Buds & Fit Test
        if (btnRingBuds != null) btnRingBuds.setOnClickListener(v -> toggleRingBuds());
        if (btnFitTest != null) btnFitTest.setOnClickListener(v -> showFitTestDialog());
    }

    private void updateAncHoldCycleFromUi() {
        boolean anc = cbCycleAnc != null && cbCycleAnc.isChecked();
        boolean trans = cbCycleTrans != null && cbCycleTrans.isChecked();
        boolean off = cbCycleOff != null && cbCycleOff.isChecked();

        int selectedCount = (anc ? 1 : 0) + (trans ? 1 : 0) + (off ? 1 : 0);
        if (selectedCount < 2) {
            Toast.makeText(this, "Select at least 2 modes for cycling", Toast.LENGTH_SHORT).show();
            if (!anc && !trans) cbCycleAnc.setChecked(true);
            else if (!trans) cbCycleTrans.setChecked(true);
            else cbCycleAnc.setChecked(true);
            return;
        }

        int mask = (off ? 1 : 0) | (anc ? 2 : 0) | (trans ? 4 : 0);
        if (mService != null) {
            mService.setAncHoldCycle(mask);
            Toast.makeText(this, "Cycling modes updated on earbuds", Toast.LENGTH_SHORT).show();
        }
    }


    private void applyTouchSettings() {
        if (mService == null) return;
        GestureOption ld = (GestureOption) spLeftDouble.getSelectedItem();
        GestureOption lt = (GestureOption) spLeftTriple.getSelectedItem();
        GestureOption lh = spLeftHold != null ? (GestureOption) spLeftHold.getSelectedItem() : null;
        GestureOption rd = (GestureOption) spRightDouble.getSelectedItem();
        GestureOption rt = (GestureOption) spRightTriple.getSelectedItem();
        GestureOption rh = spRightHold != null ? (GestureOption) spRightHold.getSelectedItem() : null;

        if (ld != null) mService.setGesture(1, 2, ld.id);
        if (lt != null) mService.setGesture(1, 3, lt.id);
        if (lh != null) mService.setGesture(1, 4, lh.id);
        if (rd != null) mService.setGesture(2, 2, rd.id);
        if (rt != null) mService.setGesture(2, 3, rt.id);
        if (rh != null) mService.setGesture(2, 4, rh.id);

        Toast.makeText(this, "Touch controls saved to earbuds", Toast.LENGTH_SHORT).show();
    }

    // ------------------------------------------------------------------------
    // BudsState.Listener Implementation
    // ------------------------------------------------------------------------

    @Override
    public void onStateChanged(BudsState state) {
        runOnUiThread(() -> renderState(state));
    }

    @Override
    public void onRawPacket(String direction, String hex) {}

    private void renderState(BudsState state) {
        if (state == null) return;

        // Device Header
        if (tvDeviceName != null) tvDeviceName.setText(state.deviceName);
        if (tvInfoHardware != null) {
            String mac = state.deviceAddress;
            if (mac == null || mac.isEmpty()) {
                mac = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
                    .getString("last_known_mac", "60:55:56:F9:98:FD");
            }
            tvInfoHardware.setText(mac);
        }
        if (tvInfoFirmware != null) {
            String fw = state.firmwareVersion;
            if (fw == null || fw.isEmpty() || "--".equals(fw)) {
                fw = getSharedPreferences("airbridge_prefs", Context.MODE_PRIVATE)
                    .getString("last_known_firmware", "1.1.0.104");
            }
            tvInfoFirmware.setText(fw);
        }
        if (tvHeroCodec != null) tvHeroCodec.setText(state.codecName);

        // Status Badge
        if (tvStatus != null) {
            tvStatus.setText(state.statusText);
            int color;
            if (state.connState == BudsState.ConnState.CONNECTED) {
                color = getColor(R.color.success);
            } else if (state.connState == BudsState.ConnState.CONNECTING) {
                color = getColor(R.color.primary);
            } else {
                color = getColor(R.color.offline);
            }
            tvStatus.setTextColor(color);
        }

        if (btnReconnect != null) {
            btnReconnect.setVisibility(state.connState == BudsState.ConnState.CONNECTED ? View.GONE : View.VISIBLE);
            if (mService != null && !mService.hasBondedBuds()) {
                btnReconnect.setText("Pair Buds");
                if (state.connState != BudsState.ConnState.CONNECTING) {
                    tvStatus.setText("○ Not Paired");
                }
            } else {
                btnReconnect.setText("Reconnect");
            }
        }

        // Battery Displays
        if (tvBatteryLeft != null) {
            tvBatteryLeft.setText(state.batteryLeft >= 0 ? state.batteryLeft + "%" : "--");
        }
        if (ivChargeLeft != null) {
            ivChargeLeft.setVisibility(state.chargingLeft ? View.VISIBLE : View.GONE);
        }
        if (tvBatteryRight != null) {
            tvBatteryRight.setText(state.batteryRight >= 0 ? state.batteryRight + "%" : "--");
        }
        if (ivChargeRight != null) {
            ivChargeRight.setVisibility(state.chargingRight ? View.VISIBLE : View.GONE);
        }
        if (tvBatteryCase != null) {
            tvBatteryCase.setText(state.batteryCase >= 0 ? state.batteryCase + "%" : "--");
        }
        if (ivChargeCase != null) {
            ivChargeCase.setVisibility(state.chargingCase ? View.VISIBLE : View.GONE);
        }

        // Wear Status
        updateWearStatus(tvLeftWearStatus, state.wearLeft);
        updateWearStatus(tvRightWearStatus, state.wearRight);
        if (tvCaseWearStatus != null) {
            tvCaseWearStatus.setText(state.wearCase == 4 ? "Docked" : "Active");
            tvCaseWearStatus.setVisibility(View.VISIBLE);
        }

        // ANC UI
        updateAncUi(state.ancMode);

        if (tvAncLevelVal != null) {
            String lvlName;
            switch (state.ancLevel) {
                case 0x10: lvlName = "Smart"; break;
                case 4: lvlName = "Max"; break;
                case 3: lvlName = "Moderate"; break;
                case 2: lvlName = "Mild"; break;
                default: lvlName = "Level " + state.ancLevel; break;
            }
            tvAncLevelVal.setText(lvlName + "  ›");
        }

        // Noise Control Cycling Checkboxes
        if (cbCycleOff != null) cbCycleOff.setChecked((state.ancHoldCycleMask & 1) != 0);
        if (cbCycleAnc != null) cbCycleAnc.setChecked((state.ancHoldCycleMask & 2) != 0);
        if (cbCycleTrans != null) cbCycleTrans.setChecked((state.ancHoldCycleMask & 4) != 0);

        // EQ Presets
        updateEqRadioUi(state.eqPreset);
        if (tvSoundEqSubtitle != null) {
            String name;
            switch (state.eqPreset) {
                case 0: name = "Original sound"; break;
                case 1: name = "Deep Bass"; break;
                case 2: name = "Serenade"; break;
                case 3: name = "Clear Bass"; break;
                case 4: name = "Custom1"; break;
                default: name = "Preset " + state.eqPreset; break;
            }
            tvSoundEqSubtitle.setText(name);
        }

        // Custom EQ Graph
        if (mEqGraphView != null && state.eqBands != null) {
            mEqGraphView.setGains(state.eqBands);
        }

        // Feature Switches
        if (swAutoAnswer != null) swAutoAnswer.setChecked(state.autoAnswer);
        if (swInEar != null) swInEar.setChecked(state.inEarDetection);
        if (swGameMode != null) swGameMode.setChecked(state.gameMode);
        if (swDynamicBass != null) {
            swDynamicBass.setChecked(state.dynamicBass);
            if (llDynamicSliders != null) {
                llDynamicSliders.setVisibility(state.dynamicBass ? View.VISIBLE : View.GONE);
            }
        }
        if (sbDynMed != null) sbDynMed.setProgress(Math.max(0, state.dynamicBassLevel - 1));
        if (tvDynMedVal != null) tvDynMedVal.setText(String.valueOf(state.dynamicBassLevel));

        if (swSpatialAudio != null) swSpatialAudio.setChecked(state.spatialAudio);
        if (tvSpatialModeLabel != null) tvSpatialModeLabel.setVisibility(state.spatialAudio ? View.VISIBLE : View.GONE);
        if (swWindNoise != null) swWindNoise.setChecked(state.windNoiseReduction);
        if (swVocalEnhance != null) swVocalEnhance.setChecked(state.vocalEnhancement);
        if (swHiRes != null) swHiRes.setChecked(state.hiResAudio);
        if (swGoldenSound != null) swGoldenSound.setChecked(state.goldenSound);
        if (swMultiDevice != null) swMultiDevice.setChecked(state.dualDeviceEnabled);
        if (swShowNotification != null && mService != null) swShowNotification.setChecked(mService.isShowNotification());

        // Multipoint Device List
        renderDeviceList(state.devices);

        // Gesture Spinners
        setSpinnerSelection(spLeftDouble, state.gestureLeftDouble);
        setSpinnerSelection(spLeftTriple, state.gestureLeftTriple);
        setSpinnerSelection(spLeftHold, state.gestureLeftHold);
        setSpinnerSelection(spRightDouble, state.gestureRightDouble);
        setSpinnerSelection(spRightTriple, state.gestureRightTriple);
        setSpinnerSelection(spRightHold, state.gestureRightHold);
    }

    private void updateWearStatus(TextView tv, int status) {
        if (tv == null) return;
        String text;
        int color;
        if (status == 3 || status == 7) {
            text = "● In Ear";
            color = getColor(R.color.success);
        } else if (status == 4) {
            text = "In Case";
            color = getColor(R.color.text_dim);
        } else if (status == 1 || status == 5) {
            text = "Off Ear";
            color = getColor(R.color.primary);
        } else {
            text = "Disconnected";
            color = getColor(R.color.text_dim);
        }
        tv.setText(text);
        tv.setTextColor(color);
        tv.setVisibility(View.VISIBLE);
    }

    private void updateAncUi(int mode) {
        int textActive = getColor(R.color.primary);
        int textInactive = getColor(R.color.text_dim);
        int iconActive = Color.WHITE;
        int iconInactive = getColor(R.color.text_main);

        if (flAncCircleOn != null) flAncCircleOn.setBackgroundResource(mode == 8 ? R.drawable.bg_circle_anc_active : R.drawable.bg_circle_anc_inactive);
        if (ivAncIconOn != null) ivAncIconOn.setColorFilter(mode == 8 ? iconActive : iconInactive);
        if (tvAncLabelOn != null) tvAncLabelOn.setTextColor(mode == 8 ? textActive : textInactive);

        if (flAncCircleOff != null) flAncCircleOff.setBackgroundResource(mode == 1 ? R.drawable.bg_circle_anc_active : R.drawable.bg_circle_anc_inactive);
        if (ivAncIconOff != null) ivAncIconOff.setColorFilter(mode == 1 ? iconActive : iconInactive);
        if (tvAncLabelOff != null) tvAncLabelOff.setTextColor(mode == 1 ? textActive : textInactive);

        if (flAncCircleTrans != null) flAncCircleTrans.setBackgroundResource(mode == 2 ? R.drawable.bg_circle_anc_active : R.drawable.bg_circle_anc_inactive);
        if (ivAncIconTrans != null) ivAncIconTrans.setColorFilter(mode == 2 ? iconActive : iconInactive);
        if (tvAncLabelTrans != null) tvAncLabelTrans.setTextColor(mode == 2 ? textActive : textInactive);

        if (llAncNoiseOptions != null) llAncNoiseOptions.setVisibility(mode == 8 ? View.VISIBLE : View.GONE);
        if (llAncTransOptions != null) llAncTransOptions.setVisibility(mode == 2 ? View.VISIBLE : View.GONE);
    }

    private void updateEqRadioUi(int preset) {
        if (ivRadioSerenade != null) ivRadioSerenade.setImageResource(preset == 2 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivRadioOriginal != null) ivRadioOriginal.setImageResource(preset == 0 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivRadioClearBass != null) ivRadioClearBass.setImageResource(preset == 3 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivRadioDeepBass != null) ivRadioDeepBass.setImageResource(preset == 1 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivRadioCustom1 != null) ivRadioCustom1.setImageResource(preset == 4 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (llCustomEqPanel != null) llCustomEqPanel.setVisibility(preset == 4 ? View.VISIBLE : View.GONE);
    }

    private void setSpinnerSelection(Spinner sp, int functionId) {
        if (sp == null || sp.getAdapter() == null) return;
        for (int i = 0; i < sp.getAdapter().getCount(); i++) {
            GestureOption opt = (GestureOption) sp.getAdapter().getItem(i);
            if (opt != null && opt.id == functionId) {
                sp.setSelection(i);
                break;
            }
        }
    }

    // ------------------------------------------------------------------------
    // Dialogs & Sheets
    // ------------------------------------------------------------------------

    private void showAncLevelDialog() {
        Dialog dialog = new Dialog(this);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(R.layout.dialog_anc_level);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            dialog.getWindow().setGravity(Gravity.BOTTOM);
        }

        View rowSmart = dialog.findViewById(R.id.row_anc_smart);
        View rowMax = dialog.findViewById(R.id.row_anc_max);
        View rowMod = dialog.findViewById(R.id.row_anc_moderate);
        View rowMild = dialog.findViewById(R.id.row_anc_mild);

        int currentLvl = (mService != null) ? mService.getState().ancLevel : 0x10;

        ImageView ivSmart = dialog.findViewById(R.id.iv_radio_anc_smart);
        ImageView ivMax = dialog.findViewById(R.id.iv_radio_anc_max);
        ImageView ivMod = dialog.findViewById(R.id.iv_radio_anc_moderate);
        ImageView ivMild = dialog.findViewById(R.id.iv_radio_anc_mild);

        if (ivSmart != null) ivSmart.setImageResource(currentLvl == 0x10 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivMax != null) ivMax.setImageResource(currentLvl == 0x04 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivMod != null) ivMod.setImageResource(currentLvl == 0x03 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);
        if (ivMild != null) ivMild.setImageResource(currentLvl == 0x02 ? R.drawable.ic_radio_checked : R.drawable.ic_radio_unchecked);

        if (rowSmart != null) rowSmart.setOnClickListener(v -> { if (mService != null) mService.setAncLevel(0x10); dialog.dismiss(); });
        if (rowMax != null) rowMax.setOnClickListener(v -> { if (mService != null) mService.setAncLevel(0x04); dialog.dismiss(); });
        if (rowMod != null) rowMod.setOnClickListener(v -> { if (mService != null) mService.setAncLevel(0x03); dialog.dismiss(); });
        if (rowMild != null) rowMild.setOnClickListener(v -> { if (mService != null) mService.setAncLevel(0x02); dialog.dismiss(); });

        dialog.show();
    }

    private void renderDeviceList(List<RealmeProtocol.DeviceInfo> devices) {
        if (llDeviceList == null) return;
        llDeviceList.removeAllViews();
        if (devices == null || devices.isEmpty()) return;

        if (tvDeviceNavSubtitle != null) {
            int count = 0;
            for (RealmeProtocol.DeviceInfo d : devices) {
                if (d.isConnected) count++;
            }
            if (count > 0) {
                tvDeviceNavSubtitle.setText(count + " device" + (count > 1 ? "s" : "") + " connected");
            } else {
                tvDeviceNavSubtitle.setText("Triple-device pairing");
            }
        }

        for (int i = 0; i < devices.size(); i++) {
            RealmeProtocol.DeviceInfo dev = devices.get(i);
            boolean isLocal = isThisPhone(dev.name) || dev.isCurrent;

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            int pad = (int) (12 * getResources().getDisplayMetrics().density);
            row.setPadding(pad, pad, pad, pad);

            ImageView icon = new ImageView(this);
            icon.setLayoutParams(new LinearLayout.LayoutParams(
                (int) (26 * getResources().getDisplayMetrics().density),
                (int) (26 * getResources().getDisplayMetrics().density)
            ));
            icon.setImageResource(getDeviceIcon(dev.name));
            row.addView(icon);

            LinearLayout textCol = new LinearLayout(this);
            textCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams lpCol = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lpCol.setMarginStart((int) (12 * getResources().getDisplayMetrics().density));
            lpCol.setMarginEnd((int) (8 * getResources().getDisplayMetrics().density));
            textCol.setLayoutParams(lpCol);

            TextView tvName = new TextView(this);
            tvName.setText(dev.name);
            tvName.setTextColor(getColor(R.color.text_main));
            tvName.setTextSize(14);
            tvName.setTypeface(null, Typeface.BOLD);
            textCol.addView(tvName);

            TextView tvSub = new TextView(this);
            String subPrefix = isLocal ? "This phone • " : "Paired device • ";
            tvSub.setText(subPrefix + dev.getMacString());
            tvSub.setTextColor(getColor(R.color.text_dim));
            tvSub.setTextSize(11);
            textCol.addView(tvSub);

            row.addView(textCol);

            TextView tvDevStatus = new TextView(this);
            tvDevStatus.setTextSize(11);
            tvDevStatus.setTypeface(null, Typeface.BOLD);
            tvDevStatus.setBackgroundResource(R.drawable.pill_badge);
            int padH = (int) (10 * getResources().getDisplayMetrics().density);
            int padV = (int) (4 * getResources().getDisplayMetrics().density);
            tvDevStatus.setPadding(padH, padV, padH, padV);

            if (isLocal) {
                tvDevStatus.setText("This phone");
                tvDevStatus.setTextColor(getColor(R.color.info));
            } else if (dev.isConnected) {
                tvDevStatus.setText("Connected");
                tvDevStatus.setTextColor(getColor(R.color.success));
            } else {
                tvDevStatus.setText("Not connected");
                tvDevStatus.setTextColor(getColor(R.color.offline));
            }
            row.addView(tvDevStatus);

            row.setOnClickListener(v -> onDeviceRowClicked(dev));
            row.setOnLongClickListener(v -> {
                showRemoveDeviceDialog(dev);
                return true;
            });
            llDeviceList.addView(row);

            if (i < devices.size() - 1) {
                View divider = new View(this);
                divider.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1));
                divider.setBackgroundColor(getColor(R.color.card_border));
                llDeviceList.addView(divider);
            }
        }
    }

    private int getDeviceIcon(String devName) {
        if (devName == null) return R.drawable.ic_device_phone;
        String n = devName.toLowerCase();
        if (n.contains("tab") || n.contains("ipad")) {
            return R.drawable.ic_device_tablet;
        } else if (n.contains("lap") || n.contains("cdc5") || n.contains("macbook") || n.contains("thinkpad") || n.contains("dell") || n.contains("hp") || n.contains("notebook")) {
            return R.drawable.ic_device_laptop;
        } else if (n.contains("pc") || n.contains("desktop") || n.contains("h310m") || n.contains("motherboard") || n.contains("tower")) {
            return R.drawable.ic_device_pc;
        } else if (n.contains("phone") || n.contains("s24") || n.contains("poco") || n.contains("iphone") || n.contains("pixel") || n.contains("galaxy") || n.contains("oneplus") || n.contains("realme") || n.contains("xiaomi")) {
            return R.drawable.ic_device_phone;
        }
        return R.drawable.ic_nav_devices;
    }

    private boolean isThisPhone(String devName) {
        if (devName == null) return false;
        String curDevName = android.provider.Settings.Global.getString(getContentResolver(), "device_name");
        if (curDevName == null) curDevName = Build.MODEL;
        return devName.equalsIgnoreCase(curDevName) || devName.equalsIgnoreCase(Build.MODEL);
    }

    private void onDeviceRowClicked(RealmeProtocol.DeviceInfo dev) {
        if (dev == null || mService == null) return;
        boolean isLocal = isThisPhone(dev.name) || dev.isCurrent;

        AlertDialog dialog = new AlertDialog.Builder(this).create();
        View view = getLayoutInflater().inflate(R.layout.dialog_device_action, null);
        dialog.setView(view);
        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        ImageView ivIcon = view.findViewById(R.id.iv_dialog_dev_icon);
        TextView tvName = view.findViewById(R.id.tv_dialog_dev_name);
        TextView tvMac = view.findViewById(R.id.tv_dialog_dev_mac);
        TextView tvBadge = view.findViewById(R.id.tv_dialog_dev_badge);

        ivIcon.setImageResource(getDeviceIcon(dev.name));
        tvName.setText(dev.name);
        tvMac.setText(dev.getMacString());

        if (isLocal) {
            tvBadge.setText("This Phone");
            tvBadge.setTextColor(getColor(R.color.info));
        } else if (dev.isConnected) {
            tvBadge.setText("Connected");
            tvBadge.setTextColor(getColor(R.color.success));
        } else {
            tvBadge.setText("Not connected");
            tvBadge.setTextColor(getColor(R.color.offline));
        }

        View rowSwitch = view.findViewById(R.id.row_action_switch_audio);
        View rowConnect = view.findViewById(R.id.row_action_connect_toggle);
        ImageView ivConnectIcon = view.findViewById(R.id.iv_action_connect_icon);
        TextView tvConnectTitle = view.findViewById(R.id.tv_action_connect_title);
        TextView tvConnectSub = view.findViewById(R.id.tv_action_connect_sub);
        View rowRemove = view.findViewById(R.id.row_action_remove);

        // Switch audio output
        if (dev.isConnected && !isLocal) {
            rowSwitch.setVisibility(View.VISIBLE);
            rowSwitch.setOnClickListener(v -> {
                mService.switchAudioDevice(dev);
                Toast.makeText(this, "Switching audio to " + dev.name + "...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        } else {
            rowSwitch.setVisibility(View.GONE);
        }

        // Connect / Disconnect action
        if (dev.isConnected) {
            tvConnectTitle.setText("Disconnect");
            tvConnectSub.setText("Disconnect Bluetooth link");
            ivConnectIcon.setImageResource(R.drawable.ic_disconnect);
            rowConnect.setOnClickListener(v -> {
                mService.disconnectDevice(dev);
                Toast.makeText(this, "Disconnecting " + dev.name + "...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        } else {
            tvConnectTitle.setText("Connect");
            tvConnectSub.setText("Establish Bluetooth connection");
            ivConnectIcon.setImageResource(R.drawable.ic_audio_switch);
            rowConnect.setOnClickListener(v -> {
                mService.connectDevice(dev);
                Toast.makeText(this, "Connecting " + dev.name + "...", Toast.LENGTH_SHORT).show();
                dialog.dismiss();
            });
        }

        // Remove device action
        rowRemove.setOnClickListener(v -> {
            dialog.dismiss();
            showRemoveDeviceDialog(dev);
        });

        dialog.show();
    }

    private void showRemoveDeviceDialog(RealmeProtocol.DeviceInfo dev) {
        if (dev == null || mService == null) return;
        new AlertDialog.Builder(this)
            .setTitle("Remove " + dev.name + "?")
            .setIcon(R.drawable.ic_delete)
            .setMessage("This device will be removed from your earbuds' paired memory.")
            .setPositiveButton("Remove", (dialog, which) -> {
                mService.removeDevice(dev);
                Toast.makeText(this, "Removing " + dev.name + "...", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void toggleRingBuds() {
        if (mService == null || mService.getState().connState != BudsState.ConnState.CONNECTED) {
            Toast.makeText(this, "Connect to earbuds first", Toast.LENGTH_SHORT).show();
            return;
        }
        if (mIsRinging) {
            mService.ringBuds(false);
            mIsRinging = false;
            if (btnRingBuds != null) {
                btnRingBuds.setText("Ring Earbuds");
                btnRingBuds.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ring, 0, 0, 0);
            }
            Toast.makeText(this, "Ringing stopped", Toast.LENGTH_SHORT).show();
        } else {
            new AlertDialog.Builder(this)
                .setTitle("Loud Sound Warning")
                .setIcon(R.drawable.ic_warning)
                .setMessage("Your earbuds will play a loud ringing chime to help locate them.\n\nPlease take the earbuds OUT of your ears before proceeding!")
                .setPositiveButton("Start Ringing", (d, w) -> {
                    mService.ringBuds(true);
                    mIsRinging = true;
                    if (btnRingBuds != null) {
                        btnRingBuds.setText("Stop Ringing");
                        btnRingBuds.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_ring, 0, 0, 0);
                    }
                    Toast.makeText(this, "Earbuds ringing...", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
        }
    }

    private void showPairingHelperDialog() {
        new AlertDialog.Builder(this)
            .setTitle("Pair realme Buds First")
            .setMessage("Your realme Buds Air 8 must be paired with your phone in Android Bluetooth Settings before AirBuds can connect.\n\n"
                    + "1. Place both earbuds inside the charging case.\n"
                    + "2. Leave the lid open.\n"
                    + "3. Press and hold the case button for 3 seconds until the indicator flashes.\n"
                    + "4. Pair with your phone in Bluetooth Settings.")
            .setPositiveButton("Open Bluetooth Settings", (d, w) -> {
                try {
                    startActivity(new Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS));
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Could not open Bluetooth Settings", Toast.LENGTH_SHORT).show();
                }
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

    private void showFitTestDialog() {
        if (mService == null || mService.getState().connState != BudsState.ConnState.CONNECTED) {
            Toast.makeText(this, "Connect to earbuds first", Toast.LENGTH_SHORT).show();
            return;
        }
        BudsState st = mService.getState();
        if ((st.wearLeft != 3 && st.wearLeft != 7) || (st.wearRight != 3 && st.wearRight != 7)) {
            new AlertDialog.Builder(this)
                .setTitle("Wear Both Earbuds")
                .setMessage("Please insert both earbuds into your ears first to perform the acoustic fit test.")
                .setPositiveButton("OK", null)
                .show();
            return;
        }

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 32, 48, 24);

        tvFitTestStatus = new TextView(this);
        tvFitTestStatus.setText("Both earbuds will play a brief musical test chime to measure acoustic seal and noise isolation.");
        tvFitTestStatus.setTextColor(getColor(R.color.text_dim));
        tvFitTestStatus.setTextSize(13);
        layout.addView(tvFitTestStatus);

        LinearLayout rowResults = new LinearLayout(this);
        rowResults.setOrientation(LinearLayout.HORIZONTAL);
        rowResults.setPadding(0, 36, 0, 36);

        tvFitTestLeft = new TextView(this);
        tvFitTestLeft.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvFitTestLeft.setText("Left: Ready");
        tvFitTestLeft.setTextColor(getColor(R.color.text_main));
        tvFitTestLeft.setTypeface(null, Typeface.BOLD);
        tvFitTestLeft.setTextSize(14);
        rowResults.addView(tvFitTestLeft);

        tvFitTestRight = new TextView(this);
        tvFitTestRight.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        tvFitTestRight.setText("Right: Ready");
        tvFitTestRight.setTextColor(getColor(R.color.text_main));
        tvFitTestRight.setTypeface(null, Typeface.BOLD);
        tvFitTestRight.setTextSize(14);
        rowResults.addView(tvFitTestRight);

        layout.addView(rowResults);

        btnFitTestAct = new Button(this);
        btnFitTestAct.setText("Start Fit Test");
        btnFitTestAct.setTextColor(Color.WHITE);
        btnFitTestAct.setBackground(getDrawable(R.drawable.btn_primary));
        btnFitTestAct.setTypeface(null, Typeface.BOLD);
        layout.addView(btnFitTestAct);

        mFitTestDialog = new AlertDialog.Builder(this)
            .setTitle("Earbud Fit Test")
            .setView(layout)
            .setNegativeButton("Close", (d, w) -> {
                if (mFitTestRunning && mService != null) {
                    mService.stopFitTest();
                    mFitTestRunning = false;
                }
            })
            .create();

        btnFitTestAct.setOnClickListener(v -> {
            if (!mFitTestRunning && mService != null) {
                mFitTestRunning = true;
                btnFitTestAct.setText("Testing acoustic seal...");
                tvFitTestStatus.setText("Playing reference test tone in both ears...");
                mService.startFitTest();
            }
        });

        mFitTestDialog.show();
    }
}
