package com.ominous.quickweather.activity;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ominous.quickweather.R;
import com.ominous.quickweather.dialog.LocationDialog;
import com.ominous.quickweather.util.Logger;
import com.ominous.quickweather.util.SnackbarUtils;
import com.ominous.quickweather.util.WeatherPreferences;
import com.ominous.quickweather.view.LocationDragListView;
import com.ominous.quickweather.weather.Weather;
import com.ominous.quickweather.weather.WeatherLocationManager;
import com.ominous.quickweather.weather.WeatherResponse;
import com.ominous.tylerutils.activity.OnboardingActivity;
import com.ominous.tylerutils.browser.CustomTabs;
import com.ominous.tylerutils.util.ViewUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

//TODO update dark mode onClick somehow
public class SettingsActivity extends OnboardingActivity {
    private final static String TAG = "SettingsActivity";
    public final static String EXTRA_SKIP_WELCOME = "extra_skip_welcome";
    private final static int REQUEST_PERMISSION_LOCATION = 1000;
    private final static int REQUEST_PERMISSION_BACKGROUND = 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CustomTabs.getInstance(this).setColor(ContextCompat.getColor(this, R.color.color_accent_emphasis));
    }

    @Override
    public void onFinish() {
        ContextCompat.startActivity(this, new Intent(this, MainActivity.class), null);
        doExitAnimation();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        doExitAnimation();
    }

    private void doExitAnimation() {
        this.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
    }

    @Override
    public void addFragments() {
        if (this.getIntent().getExtras() == null || !this.getIntent().getExtras().getBoolean(EXTRA_SKIP_WELCOME, false)) {
            this.addFragment(WelcomeFragment.class);
        }
        this.addFragment(ApiKeyFragment.class);
        this.addFragment(LocationFragment.class);
        this.addFragment(UnitsFragment.class);
    }

    public static class WelcomeFragment extends OnboardingFragment {

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            notifyViewPager(true);

            return inflater.inflate(R.layout.fragment_welcome, parent, false);
        }

        @Override
        public void onFinish() {

        }
    }

    public static class LocationFragment extends OnboardingFragment implements View.OnClickListener {
        private LocationDragListView dragListView;
        private MaterialButton currentLocationButton, otherLocationButton;
        private List<WeatherPreferences.WeatherLocation> locations;
        private LocationAdapterDataObserver locationAdapterDataObserver;
        private Snackbar locationDisabledSnackbar;

        private final static String KEY_LOCATIONS = "locationList";

        @Override
        public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_location, parent, false);

            dragListView = v.findViewById(R.id.drag_list_view);
            currentLocationButton = v.findViewById(R.id.button_current_location);
            otherLocationButton = v.findViewById(R.id.button_other_location);

            return v;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                locations = savedInstanceState.getParcelableArrayList(KEY_LOCATIONS);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onSaveInstanceState(@NonNull Bundle outBundle) {
            super.onSaveInstanceState(outBundle);

            outBundle.putParcelableArrayList(KEY_LOCATIONS, (ArrayList<WeatherPreferences.WeatherLocation>) dragListView.getAdapter().getItemList());
        }

        @Override
        public void onStart() {
            super.onStart();

            if (locations == null) {
                locations = WeatherPreferences.getLocations();
            }

            dragListView.setAdapterFromList(locations);

            if (locationAdapterDataObserver == null) {
                locationAdapterDataObserver = new LocationAdapterDataObserver();
            }

            dragListView.getAdapter().registerAdapterDataObserver(locationAdapterDataObserver);

            currentLocationButton.setOnClickListener(this);
            otherLocationButton.setOnClickListener(this);
        }

        private void addCurrentLocation() {
            addLocation(new WeatherPreferences.WeatherLocation(
                    getFragmentActivity().getString(R.string.text_current_location),
                    0,
                    0));
        }

        private void addLocation(WeatherPreferences.WeatherLocation weatherLocation) {
            dragListView.addLocation(dragListView.getAdapter().getItemCount(), weatherLocation);
        }

        private void setCurrentLocationEnabled(boolean enabled) {
            currentLocationButton.setEnabled(enabled);
        }

        @Override
        public void onPageDeselected() {
            dismissLocationSnackbar();
        }

        public void dismissLocationSnackbar() {
            if (locationDisabledSnackbar != null) {
                locationDisabledSnackbar.dismiss();
            }
        }

        @Override
        public void onPageSelected() {
            checkLocationSnackbar();
        }

        public void checkLocationSnackbar() {
            if (!currentLocationButton.isEnabled() && !WeatherLocationManager.isLocationEnabled(getFragmentActivity())) {
                locationDisabledSnackbar = SnackbarUtils.notifyLocPermDenied(getFragmentActivity().findViewById(R.id.viewpager_coordinator), getFragmentActivity(), REQUEST_PERMISSION_LOCATION);
            } else {
                dismissLocationSnackbar();
            }
        }

        @Override
        public void onClick(final View v) {
            switch (v.getId()) {
                case R.id.button_current_location:
                    if (!dragListView.getAdapter().getItemList().contains(getFragmentActivity().getString(R.string.text_current_location))) {
                        if (WeatherLocationManager.isLocationEnabled(getFragmentActivity())) {
                            addCurrentLocation();
                            v.setEnabled(false);
                        } else {
                            WeatherLocationManager.requestLocationPermissions(getFragmentActivity(),REQUEST_PERMISSION_LOCATION);
                        }
                    }
                    break;

                case R.id.button_other_location:
                    LocationDialog locationDialog = new LocationDialog(getFragmentActivity(), new LocationDialog.OnLocationChosenListener() {
                        @Override
                        public void onLocationChosen(String location, double latitude, double longitude) {
                            addLocation(new WeatherPreferences.WeatherLocation(location, latitude, longitude));
                        }

                        @Override
                        public void onGeoCoderError(Throwable throwable) {
                            Logger.e(getFragmentActivity(), TAG, getFragmentActivity().getString(R.string.error_connecting_geocoder), throwable);
                        }
                    });

                    locationDialog.show();

                    break;
            }
        }

        @Override
        public void onFinish() {
            WeatherPreferences.setLocations(dragListView.getItemList());
        }

        private class LocationAdapterDataObserver extends RecyclerView.AdapterDataObserver {
            LocationAdapterDataObserver() {
                doUpdate();
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                doUpdate();
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                doUpdate();
            }

            private void doUpdate() {
                setCurrentLocationEnabled(!dragListView.hasLocation(getFragmentActivity().getString(R.string.text_current_location)));

                notifyViewPager(dragListView.getItemList().size() > 0);
            }
        }
    }

    public static class UnitsFragment extends OnboardingFragment implements View.OnClickListener {
        private MaterialButton
                buttonFahrenheit, buttonCelsius,
                buttonMph, buttonKmh, buttonMs,
                buttonThemeLight, buttonThemeDark, buttonThemeAuto,
                buttonNotifAlertEnabled, buttonNotifAlertDisabled,
                buttonNotifPersistEnabled, buttonNotifPersistDisabled;

        private CoordinatorLayout coordinatorLayout;

        private String temperature = null, speed = null, theme = null, alertNotifEnabled = null, persistNotifEnabled = null;

        private static final String KEY_TEMPERATURE = "temperature", KEY_SPEED = "speed", KEY_THEME = "theme", KEY_ALERTNOTIF = "alertnotif", KEY_PERSISTNOTIF = "persistnotif";
        private Snackbar locationDisabledSnackbar;


        @Override
        public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            View v = inflater.inflate(R.layout.fragment_units, parent, false);

            buttonFahrenheit = v.findViewById(R.id.button_fahrenheit);
            buttonCelsius = v.findViewById(R.id.button_celsius);
            buttonMph = v.findViewById(R.id.button_mph);
            buttonKmh = v.findViewById(R.id.button_kmh);
            buttonMs = v.findViewById(R.id.button_ms);
            buttonThemeLight = v.findViewById(R.id.button_theme_light);
            buttonThemeDark = v.findViewById(R.id.button_theme_dark);
            buttonThemeAuto = v.findViewById(R.id.button_theme_auto);
            buttonNotifAlertEnabled = v.findViewById(R.id.button_alert_notif_enabled);
            buttonNotifAlertDisabled = v.findViewById(R.id.button_alert_notif_disabled);
            buttonNotifPersistEnabled = v.findViewById(R.id.button_weather_notif_enabled);
            buttonNotifPersistDisabled = v.findViewById(R.id.button_weather_notif_disabled);
            coordinatorLayout = v.findViewById(R.id.viewpager_coordinator);

            return v;
        }

        @Override
        public void onActivityCreated(Bundle savedInstanceState) {
            super.onActivityCreated(savedInstanceState);

            if (savedInstanceState != null) {
                temperature = savedInstanceState.getString(KEY_TEMPERATURE);
                speed = savedInstanceState.getString(KEY_SPEED);
                theme = savedInstanceState.getString(KEY_THEME);
                alertNotifEnabled = savedInstanceState.getString(KEY_ALERTNOTIF);
                persistNotifEnabled = savedInstanceState.getString(KEY_PERSISTNOTIF);
            }
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outBundle) {
            super.onSaveInstanceState(outBundle);

            outBundle.putString(KEY_TEMPERATURE, temperature);
            outBundle.putString(KEY_SPEED, speed);
            outBundle.putString(KEY_THEME, theme);
            outBundle.putString(KEY_ALERTNOTIF, alertNotifEnabled);
            outBundle.putString(KEY_PERSISTNOTIF, persistNotifEnabled);
        }

        @Override
        public void onStart() {
            super.onStart();

            buttonFahrenheit.setOnClickListener(this);
            buttonCelsius.setOnClickListener(this);
            buttonMph.setOnClickListener(this);
            buttonKmh.setOnClickListener(this);
            buttonMs.setOnClickListener(this);
            buttonThemeLight.setOnClickListener(this);
            buttonThemeDark.setOnClickListener(this);
            buttonThemeAuto.setOnClickListener(this);
            buttonNotifAlertEnabled.setOnClickListener(this);
            buttonNotifAlertDisabled.setOnClickListener(this);
            buttonNotifPersistEnabled.setOnClickListener(this);
            buttonNotifPersistDisabled.setOnClickListener(this);

            buttonFahrenheit.setTag(WeatherPreferences.TEMPERATURE_FAHRENHEIT);
            buttonCelsius.setTag(WeatherPreferences.TEMPERATURE_CELSIUS);
            buttonMph.setTag(WeatherPreferences.SPEED_MPH);
            buttonKmh.setTag(WeatherPreferences.SPEED_KMH);
            buttonMs.setTag(WeatherPreferences.SPEED_MS);
            buttonThemeLight.setTag(WeatherPreferences.THEME_LIGHT);
            buttonThemeDark.setTag(WeatherPreferences.THEME_DARK);
            buttonThemeAuto.setTag(WeatherPreferences.THEME_AUTO);
            buttonNotifAlertEnabled.setTag(WeatherPreferences.ENABLED);
            buttonNotifAlertDisabled.setTag(WeatherPreferences.DISABLED);
            buttonNotifPersistEnabled.setTag(WeatherPreferences.ENABLED);
            buttonNotifPersistDisabled.setTag(WeatherPreferences.DISABLED);


            if (speed == null) {
                speed = WeatherPreferences.getSpeedUnit();
            }

            if (temperature == null) {
                temperature = WeatherPreferences.getTemperatureUnit();
            }

            if (theme == null) {
                theme = WeatherPreferences.getTheme();
            }

            if (alertNotifEnabled == null) {
                alertNotifEnabled = WeatherPreferences.getShowAlertNotification();
            }

            if (persistNotifEnabled == null) {
                persistNotifEnabled = WeatherPreferences.getShowPersistentNotification();
            }

            switch (speed) {
                case WeatherPreferences.SPEED_KMH:
                    buttonKmh.setSelected(true);
                    break;
                case WeatherPreferences.SPEED_MPH:
                    buttonMph.setSelected(true);
                    break;
                case WeatherPreferences.SPEED_MS:
                    buttonMs.setSelected(true);
                    break;
            }

            switch (temperature) {
                case WeatherPreferences.TEMPERATURE_CELSIUS:
                    buttonCelsius.setSelected(true);
                    break;
                case WeatherPreferences.TEMPERATURE_FAHRENHEIT:
                    buttonFahrenheit.setSelected(true);
                    break;
            }

            switch (theme) {
                case WeatherPreferences.THEME_LIGHT:
                    buttonThemeLight.setSelected(true);
                    break;
                case WeatherPreferences.THEME_DARK:
                    buttonThemeDark.setSelected(true);
                    break;
                case WeatherPreferences.THEME_AUTO:
                    buttonThemeAuto.setSelected(true);
                    break;
            }

            switch (alertNotifEnabled) {
                case WeatherPreferences.ENABLED:
                    buttonNotifAlertEnabled.setSelected(true);
                    break;
                case WeatherPreferences.DISABLED:
                    buttonNotifAlertDisabled.setSelected(true);
                    break;
            }

            switch (persistNotifEnabled) {
                case WeatherPreferences.ENABLED:
                    buttonNotifPersistEnabled.setSelected(true);
                    break;
                case WeatherPreferences.DISABLED:
                    buttonNotifPersistDisabled.setSelected(true);
                    break;
            }

            if (!temperature.isEmpty() && !speed.isEmpty() && !theme.isEmpty() && !alertNotifEnabled.isEmpty() && !persistNotifEnabled.isEmpty()) {
                notifyViewPager(true);
            }
        }

        @Override
        public void onFinish() {
            WeatherPreferences.setTemperatureUnit(temperature);
            WeatherPreferences.setSpeedUnit(speed);
            WeatherPreferences.setTheme(theme);
            WeatherPreferences.setShowAlertNotification(alertNotifEnabled);
            WeatherPreferences.setShowPersistentNotification(persistNotifEnabled);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.button_fahrenheit:
                case R.id.button_celsius:
                    buttonFahrenheit.setSelected(false);
                    buttonCelsius.setSelected(false);

                    temperature = v.getTag().toString();
                    break;
                case R.id.button_mph:
                case R.id.button_kmh:
                case R.id.button_ms:
                    buttonKmh.setSelected(false);
                    buttonMph.setSelected(false);
                    buttonMs.setSelected(false);

                    speed = v.getTag().toString();
                    break;

                case R.id.button_theme_auto:
                case R.id.button_theme_light:
                case R.id.button_theme_dark:
                    buttonThemeLight.setSelected(false);
                    buttonThemeDark.setSelected(false);
                    buttonThemeAuto.setSelected(false);

                    theme = v.getTag().toString();
                    break;

                case R.id.button_alert_notif_enabled:
                case R.id.button_alert_notif_disabled:
                    buttonNotifAlertEnabled.setSelected(false);
                    buttonNotifAlertDisabled.setSelected(false);

                    alertNotifEnabled = v.getTag().toString();
                    break;

                case R.id.button_weather_notif_enabled:
                case R.id.button_weather_notif_disabled:
                    buttonNotifPersistEnabled.setSelected(false);
                    buttonNotifPersistDisabled.setSelected(false);

                    persistNotifEnabled = v.getTag().toString();
                    break;
            }

            v.setSelected(true);
            checkIfBackgroundLocationEnabled();

            if (!temperature.isEmpty() && !speed.isEmpty() && !theme.isEmpty()) {
                notifyViewPager(true);
            }
        }

        @Override
        public void onPageDeselected() {
            dismissSnackbar();
        }

        @Override
        public void onPageSelected() {
            checkIfBackgroundLocationEnabled();
        }

        public void dismissSnackbar() {
            if (locationDisabledSnackbar != null) {
                locationDisabledSnackbar.dismiss();
            }
        }

        public void checkIfBackgroundLocationEnabled() {
            if (buttonNotifAlertEnabled.isSelected() || buttonNotifPersistEnabled.isSelected()) {
                if (!WeatherLocationManager.isBackgroundLocationEnabled(getActivity())) {
                    locationDisabledSnackbar = SnackbarUtils.notifyBackLocPermDenied(coordinatorLayout, getFragmentActivity(), REQUEST_PERMISSION_BACKGROUND);
                }
            } else {
                dismissSnackbar();
            }
        }
    }

    public static class ApiKeyFragment extends OnboardingFragment implements View.OnClickListener, TextWatcher, Weather.WeatherListener, View.OnFocusChangeListener {
        private static final int STATE_NULL = -1, STATE_NEUTRAL = 0, STATE_PASS = 1, STATE_FAIL = 2;
        private static final String KEY_OWMAPIKEY = "owmApiKey", KEY_DSAPIKEY = "dsApiKey",  KEY_APIKEYSTATE = "apiKeyState";

        private TextView dsTextView;
        private TextInputEditText owmApiKeyEditText, dsApiKeyEditText;
        private TextInputLayout owmApiKeyEditTextLayout, dsApiKeyEditTextLayout;
        private MaterialButton testApiKeyButton;
        private View container;
        private int apiKeyState = STATE_NULL;
        private boolean apiKeyFocused = true;
        private int[][] colorStates = new int[][]{
                new int[]{-android.R.attr.state_focused},
                new int[]{android.R.attr.state_focused}
        };

        @Override
        public View onCreateView(@NonNull final LayoutInflater inflater, ViewGroup parent, Bundle savedInstanceState) {
            container = inflater.inflate(R.layout.fragment_apikey, parent, false);

            dsTextView = container.findViewById(R.id.onboarding_apikey_ds_text);
            dsApiKeyEditText = container.findViewById(R.id.onboarding_apikey_ds_apikey);
            dsApiKeyEditTextLayout = container.findViewById(R.id.onboarding_apikey_ds_edittext_layout);
            owmApiKeyEditText = container.findViewById(R.id.onboarding_apikey_owm_apikey);
            owmApiKeyEditTextLayout = container.findViewById(R.id.onboarding_apikey_owm_edittext_layout);
            testApiKeyButton = container.findViewById(R.id.test_api_key);

            return container;
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outBundle) {
            super.onSaveInstanceState(outBundle);

            outBundle.putString(KEY_OWMAPIKEY, getApiKeyFromEditText(owmApiKeyEditText));
            outBundle.putString(KEY_DSAPIKEY, getApiKeyFromEditText(dsApiKeyEditText));
            outBundle.putInt(KEY_APIKEYSTATE, apiKeyState);
        }

        @Override
        public void onActivityCreated(Bundle state) {
            super.onActivityCreated(state);

            if (state != null) {
                owmApiKeyEditText.setText(state.getString(KEY_OWMAPIKEY));
                dsApiKeyEditText.setText(state.getString(KEY_DSAPIKEY));
                apiKeyState = state.getInt(KEY_APIKEYSTATE);
            }
        }

        @Override
        public void onStart() {
            super.onStart();

            String provider = WeatherPreferences.getProvider();

            if (apiKeyState == STATE_NULL) {
                String apiKey = WeatherPreferences.getApiKey();

                if (apiKey.equals(WeatherPreferences.DEFAULT_VALUE)) {
                    setApiKeyState(STATE_NEUTRAL);//TODO Test api key, dont assume
                } else {
                    if (provider.equals(WeatherPreferences.PROVIDER_DS)) {
                        dsApiKeyEditText.setText(apiKey);
                    } else {
                        owmApiKeyEditText.setText(apiKey);
                    }
                    setApiKeyState(STATE_PASS);
                }
            } else {
                setApiKeyState(apiKeyState);
            }

            if (provider.equals(WeatherPreferences.PROVIDER_OWM)) {
                dsApiKeyEditTextLayout.setVisibility(View.GONE);
                dsTextView.setVisibility(View.GONE);
            }

            owmApiKeyEditText.addTextChangedListener(this);
            owmApiKeyEditText.setOnFocusChangeListener(this);

            container.setOnClickListener(this);

            testApiKeyButton.setOnClickListener(this);
        }

        @Override
        public void onPageSelected() {
            owmApiKeyEditText.requestFocus();

            updateApiKeyColors(apiKeyState);

            ViewUtils.toggleKeyboardState(owmApiKeyEditText, true);
        }

        @Override
        public void onPageDeselected() {
            if (owmApiKeyEditText != null) {
                owmApiKeyEditText.clearFocus();

                updateApiKeyColors(apiKeyState);

                ViewUtils.toggleKeyboardState(owmApiKeyEditText, false);
            }
        }

        @Override
        public void onFinish() {
            String owmApiKey = getApiKeyFromEditText(owmApiKeyEditText);
            if (apiKeyState == STATE_PASS && !owmApiKey.isEmpty()) {
                    WeatherPreferences.setProvider(WeatherPreferences.PROVIDER_OWM);
                    WeatherPreferences.setApiKey(owmApiKey);
            }
        }

        private void updateApiKeyColors(int state) {
            int textColorRes, editTextDrawableRes;

            switch (state) {
                default:
                case STATE_NEUTRAL:
                    textColorRes = R.color.text_primary_emphasis;
                    editTextDrawableRes = 0;

                    break;
                case STATE_PASS:
                    textColorRes = R.color.color_green;
                    editTextDrawableRes = R.drawable.ic_done_white_24dp;

                    break;
                case STATE_FAIL:
                    textColorRes = R.color.color_red;
                    editTextDrawableRes = R.drawable.ic_clear_white_24dp;
            }

            int coloredTextColor = getResources().getColor(textColorRes);
            int greyTextColor = getResources().getColor(R.color.text_primary_disabled);

            ColorStateList textColor = new ColorStateList(
                    colorStates,
                    new int[]{
                            greyTextColor,
                            coloredTextColor
                    }
            );

            owmApiKeyEditTextLayout.setBoxStrokeColor(coloredTextColor);
            owmApiKeyEditTextLayout.setHintTextColor(textColor);

            if (state == STATE_NEUTRAL) {
                owmApiKeyEditText.setCompoundDrawables(null, null, null, null);
            } else {
                ViewUtils.setDrawable(owmApiKeyEditText, editTextDrawableRes, apiKeyFocused ? coloredTextColor : greyTextColor, ViewUtils.FLAG_END);
            }
        }

        private void setApiKeyState(int state) {
            apiKeyState = state;

            updateApiKeyColors(state);

            testApiKeyButton.setEnabled(state == STATE_NEUTRAL);

            notifyViewPager(state == STATE_PASS);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.test_api_key:
                    String apiKeyText = getApiKeyFromEditText(owmApiKeyEditText);

                    if (apiKeyText.length() > 0) {
                        testApiKeyButton.setEnabled(false);

                        //Welcome to Atlanta!
                        Weather.getWeatherAsync(WeatherPreferences.PROVIDER_OWM, apiKeyText, 33.7490, -84.3880, this);

                        owmApiKeyEditText.clearFocus();
                    }
                    break;
                case R.id.api_key_container:
                    owmApiKeyEditText.clearFocus();
                    break;
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            setApiKeyState(STATE_NEUTRAL);
        }

        @Override
        public void onWeatherRetrieved(WeatherResponse weatherResponse) {
            setApiKeyState(STATE_PASS);
        }

        @Override
        public void onWeatherError(String error, Throwable throwable) {
            if (error.contains("403")) {
                setApiKeyState(STATE_FAIL);
                Snackbar.make(owmApiKeyEditText, R.string.text_invalid_api_key, Snackbar.LENGTH_LONG).show();
            } else {
                testApiKeyButton.setEnabled(true);
                Logger.e(getFragmentActivity(), TAG, error, throwable);
            }
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            apiKeyFocused = hasFocus;
            updateApiKeyColors(apiKeyState);
        }

        private String getApiKeyFromEditText(EditText editText) {
            Editable text = editText.getText();

            return text == null ? "" : text.toString();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION_LOCATION) {
            for (Fragment fragment : getInstantiatedFragments()) {
                if (fragment instanceof LocationFragment) {
                    ((LocationFragment) fragment).checkLocationSnackbar();

                    for (int i=0,l=permissions.length;i<l;i++) {
                        if (permissions[i].equals(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                            if (grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                                ((LocationFragment) fragment).addCurrentLocation();
                            }
                        }
                    }

                }
            }
        } else if (requestCode == REQUEST_PERMISSION_BACKGROUND) {
            for (Fragment fragment : getInstantiatedFragments()) {
                if (fragment instanceof UnitsFragment) {
                    ((UnitsFragment) fragment).checkIfBackgroundLocationEnabled();
                }
            }
        }
    }
}
