/*
 *     Copyright 2019 - 2022 Tyler Williamson
 *
 *     This file is part of QuickWeather.
 *
 *     QuickWeather is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     QuickWeather is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with QuickWeather.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.ominous.quickweather.activity;

import android.Manifest;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.ominous.quickweather.R;
import com.ominous.quickweather.api.OpenWeatherMap;
import com.ominous.quickweather.data.WeatherDatabase;
import com.ominous.quickweather.dialog.LocationManualDialog;
import com.ominous.quickweather.dialog.LocationMapDialog;
import com.ominous.quickweather.dialog.LocationSearchDialog;
import com.ominous.quickweather.dialog.OnLocationChosenListener;
import com.ominous.quickweather.location.WeatherLocationManager;
import com.ominous.quickweather.util.ColorUtils;
import com.ominous.quickweather.util.DialogHelper;
import com.ominous.quickweather.util.NotificationUtils;
import com.ominous.quickweather.util.SnackbarHelper;
import com.ominous.quickweather.util.WeatherPreferences;
import com.ominous.quickweather.view.LocationDragListView;
import com.ominous.tylerutils.activity.OnboardingActivity2;
import com.ominous.tylerutils.async.Promise;
import com.ominous.tylerutils.browser.CustomTabs;
import com.ominous.tylerutils.util.ViewUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutionException;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

//TODO snackbar error message if no locations, switch to location tab
public class SettingsActivity extends OnboardingActivity2 {
    public final static String EXTRA_WEATHERLOCATION = "extra_weatherlocation";

    private DialogHelper dialogHelper;
    private final WeatherLocationManager weatherLocationManager = WeatherLocationManager.getInstance();

    private final ActivityResultLauncher<String> notificationRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestPermission(), grantedResults -> {
        for (OnboardingContainer onboardingContainer : getOnboardingContainers()) {
            if (onboardingContainer instanceof UnitsPageContainer) {
                ((UnitsPageContainer) onboardingContainer).checkIfNotificationAllowed();
            }
        }
    });

    private final ActivityResultLauncher<String[]> backgroundLocationRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedResults -> {
        for (OnboardingContainer onboardingContainer : getOnboardingContainers()) {
            if (onboardingContainer instanceof UnitsPageContainer) {
                ((UnitsPageContainer) onboardingContainer).checkIfBackgroundLocationEnabled();
            }
        }
    });

    private final ActivityResultLauncher<String[]> hereRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedResults -> {
        Boolean locationResult = grantedResults.get(Manifest.permission.ACCESS_COARSE_LOCATION);

        if (Boolean.TRUE.equals(locationResult)) {
            for (OnboardingContainer onboardingContainer : getOnboardingContainers()) {
                if (onboardingContainer instanceof LocationPageContainer) {
                    ((LocationPageContainer) onboardingContainer).addHere();
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                !Boolean.TRUE.equals(locationResult) &&
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            dialogHelper.showLocationRationale();
        }
    });

    private final ActivityResultLauncher<String[]> currentLocationRequestLauncher = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions(), grantedResults -> {
        Boolean locationResult = grantedResults.get(Manifest.permission.ACCESS_COARSE_LOCATION);

        for (OnboardingContainer onboardingContainer : getOnboardingContainers()) {
            if (onboardingContainer instanceof LocationPageContainer) {
                LocationPageContainer locationPageContainer = (LocationPageContainer) onboardingContainer;

                locationPageContainer.checkLocationSnackbar();

                if (Boolean.TRUE.equals(locationResult)) {
                    locationPageContainer.addCurrentLocation();
                }
            }
        }

        if (Build.VERSION.SDK_INT >= 23 &&
                !Boolean.TRUE.equals(locationResult) &&
                shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            dialogHelper.showLocationRationale();
        }
    });

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        initActivity();

        dialogHelper = new DialogHelper(this);

        CustomTabs.getInstance(this).setColor(ContextCompat.getColor(this, R.color.color_accent));

        if (getIntent().hasExtra(EXTRA_WEATHERLOCATION)) {
            this.findViewById(android.R.id.content).post(() -> setCurrentPage(2));
        }
    }

    private void initActivity() {
        ColorUtils.initialize(this);//Initializing after Activity created to get day/night properly

        setTaskDescription(
                Build.VERSION.SDK_INT >= 28 ?
                        new ActivityManager.TaskDescription(
                                getString(R.string.app_name),
                                R.mipmap.ic_launcher_round,
                                ContextCompat.getColor(this, R.color.color_app_accent)) :
                        new ActivityManager.TaskDescription(
                                getString(R.string.app_name),
                                BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher_round),
                                ContextCompat.getColor(this, R.color.color_app_accent)));
    }

    @Override
    public void onFinish() {
        WeatherPreferences.getInstance(this).commitChanges();

        ContextCompat.startActivity(this, new Intent(this, MainActivity.class), null);
    }

    @Override
    public List<OnboardingContainer> createOnboardingContainers() {
        final ArrayList<OnboardingContainer> containers;

        if (WeatherPreferences.getInstance(this).isInitialized()) {
            containers = new ArrayList<>(3);
        } else {
            containers = new ArrayList<>(4);
            containers.add(new WelcomePageContainer(this));
        }

        containers.add(new ApiKeyPageContainer(this));
        containers.add(new LocationPageContainer(this));
        containers.add(new UnitsPageContainer(this));

        return containers;
    }

    @Override
    public void finish() {
        super.finish();
        this.overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
    }

    private static class WelcomePageContainer extends OnboardingContainer {
        public WelcomePageContainer(Context context) {
            super(context);
        }

        @Override
        public int getViewRes() {
            return R.layout.fragment_welcome;
        }

        @Override
        public boolean canAdvanceToNextPage() {
            return true;
        }
    }

    private class LocationPageContainer extends OnboardingContainer implements View.OnClickListener {
        private final static String KEY_LOCATIONS = "locationList";
        private LocationDragListView dragListView;
        private final OnLocationChosenListener onLocationChosenListener = (name, latitude, longitude) -> addLocation(new WeatherDatabase.WeatherLocation(
                0,
                latitude,
                longitude,
                name,
                false,
                false,
                0
        ));
        private MaterialButton currentLocationButton;
        private MaterialButton otherLocationButton;
        private MaterialButton mapButton;
        private MaterialButton thisLocationButton;
        private List<WeatherDatabase.WeatherLocation> locations;
        private LocationAdapterDataObserver locationAdapterDataObserver;
        private boolean hasShownBundledLocation = false;
        private Promise<Void, Void> lastDatabaseUpdate;
        private SnackbarHelper snackbarHelper;
        private LocationSearchDialog locationSearchDialog;
        private LocationManualDialog locationManualDialog;
        private LocationMapDialog locationMapDialog;

        public LocationPageContainer(Context context) {
            super(context);
        }

        @Override
        public int getViewRes() {
            return R.layout.fragment_location;
        }

        @Override
        public boolean canAdvanceToNextPage() {
            return dragListView.getLocationList().size() > 0;
        }

        @Override
        public void onCreateView(View v) {
            dragListView = v.findViewById(R.id.drag_list_view);
            currentLocationButton = v.findViewById(R.id.button_current_location);
            otherLocationButton = v.findViewById(R.id.button_other_location);
            mapButton = v.findViewById(R.id.button_map);
            thisLocationButton = v.findViewById(R.id.button_here);

            snackbarHelper = new SnackbarHelper(v.findViewById(R.id.viewpager_coordinator));

            locationManualDialog = new LocationManualDialog(v.getContext());
            locationSearchDialog = new LocationSearchDialog(v.getContext());
            locationMapDialog = new LocationMapDialog(v.getContext());
        }

        @Override
        public void onBindView(View v) {
            Promise.create((a) -> {
                if (locations == null) {
                    locations = WeatherDatabase.getInstance(v.getContext()).locationDao().getAllWeatherLocations();
                }

                SettingsActivity.this.runOnUiThread(() -> {
                    dragListView.setLocationList(locations);

                    if (locationAdapterDataObserver == null) {
                        locationAdapterDataObserver = new LocationAdapterDataObserver();
                        dragListView.getAdapter().registerAdapterDataObserver(locationAdapterDataObserver);
                    }
                });
            });

            currentLocationButton.setOnClickListener(this);
            otherLocationButton.setOnClickListener(this);
            mapButton.setOnClickListener(this);
            thisLocationButton.setOnClickListener(this);
        }

        @SuppressWarnings("unchecked")
        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putParcelableArrayList(KEY_LOCATIONS, (ArrayList<WeatherDatabase.WeatherLocation>) dragListView.getAdapter().getItemList());
        }

        @Override
        public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            locations = savedInstanceState.getParcelableArrayList(KEY_LOCATIONS);
        }

        private void addCurrentLocation() {
            if (!isCurrentLocationSelected()) {
                addLocation(new WeatherDatabase.WeatherLocation(
                        0,
                        0,
                        0,
                        getContext().getString(R.string.text_current_location),
                        false,
                        true,
                        0
                ));
            }
        }

        private void addHere() {
            if (weatherLocationManager.isLocationPermissionGranted(getContext())) {
                thisLocationButton.setEnabled(false);
                snackbarHelper.notifyObtainingLocation();

                Promise.create((a) -> {
                    Location l = weatherLocationManager.obtainCurrentLocation(getContext(), false);

                    SettingsActivity.this.runOnUiThread(() -> {
                        snackbarHelper.dismiss();
                        thisLocationButton.setEnabled(true);
                    });

                    return l;
                }, e -> {
                    if (e instanceof WeatherLocationManager.LocationPermissionNotAvailableException) {
                        snackbarHelper.notifyLocPermDenied(SettingsActivity.this.hereRequestLauncher);
                    } else if (e instanceof WeatherLocationManager.LocationDisabledException) {
                        snackbarHelper.notifyLocDisabled();
                    } else {
                        snackbarHelper.notifyNullLoc();
                    }
                }).then((l) -> {
                    if (l != null) {
                        runOnUiThread(() ->
                                locationManualDialog.show(new WeatherDatabase.WeatherLocation(l.getLatitude(), l.getLongitude(), null), onLocationChosenListener));
                    } else {
                        snackbarHelper.notifyNullLoc();
                    }
                });
            } else {
                weatherLocationManager.showLocationDisclosure(getContext(), () -> {
                    WeatherPreferences.getInstance(getContext()).setShowLocationDisclosure(false);

                    weatherLocationManager.requestLocationPermissions(getContext(), SettingsActivity.this.hereRequestLauncher);
                });
            }
        }

        private void addLocation(WeatherDatabase.WeatherLocation weatherLocation) {
            dragListView.addLocation(weatherLocation);
        }

        private void setCurrentLocationEnabled(boolean enabled) {
            currentLocationButton.setEnabled(enabled);
        }

        @SuppressWarnings("BooleanMethodIsAlwaysInverted")
        public boolean isCurrentLocationSelected() {
            for (WeatherDatabase.WeatherLocation weatherLocation : dragListView.getLocationList()) {
                if (weatherLocation.isCurrentLocation) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public void onPageSelected() {
            checkLocationSnackbar();

            WeatherDatabase.WeatherLocation weatherLocation = !hasShownBundledLocation &&
                    SettingsActivity.this.getIntent() != null ?
                    SettingsActivity.this.getIntent().getParcelableExtra(EXTRA_WEATHERLOCATION) : null;

            hasShownBundledLocation = true;

            if (weatherLocation != null) {
                locationManualDialog.show(weatherLocation, onLocationChosenListener);
            }
        }

        @Override
        public void onPageDeselected() {
            dismissSnackbar();
        }

        public void checkLocationSnackbar() {
            if (currentLocationButton != null && snackbarHelper != null) {
                if (!currentLocationButton.isEnabled() && !weatherLocationManager.isLocationPermissionGranted(getContext())) {
                    snackbarHelper.notifyLocPermDenied(SettingsActivity.this.currentLocationRequestLauncher);
                } else {
                    dismissSnackbar();
                }
            }
        }

        private void dismissSnackbar() {
            if (snackbarHelper != null) {
                snackbarHelper.dismiss();
            }
        }

        @Override
        public void onClick(final View v) {
            if (v.getId() == R.id.button_current_location) {
                if (!isCurrentLocationSelected()) {
                    if (weatherLocationManager.isLocationPermissionGranted(v.getContext())) {
                        addCurrentLocation();
                        v.setEnabled(false);
                    } else {
                        weatherLocationManager.showLocationDisclosure(v.getContext(), () -> {
                            WeatherPreferences.getInstance(getContext()).setShowLocationDisclosure(false);

                            weatherLocationManager.requestLocationPermissions(v.getContext(), SettingsActivity.this.currentLocationRequestLauncher);
                        });
                    }
                }
            } else if (v.getId() == R.id.button_other_location) {
                locationSearchDialog.show(onLocationChosenListener);
            } else if (v.getId() == R.id.button_map) {
                locationMapDialog.show(onLocationChosenListener);
            } else if (v.getId() == R.id.button_here) {
                addHere();
            }
        }

        private void updateLocations() {
            Promise.VoidPromiseCallable<Void> callable = (a) -> {
                WeatherDatabase.WeatherLocationDao weatherDatabaseDao = WeatherDatabase.getInstance(getContext()).locationDao();

                List<WeatherDatabase.WeatherLocation> newWeatherLocations = dragListView.getLocationList();
                List<WeatherDatabase.WeatherLocation> oldWeatherLocations = weatherDatabaseDao.getAllWeatherLocations();

                for (WeatherDatabase.WeatherLocation oldWeatherLocation : oldWeatherLocations) {
                    boolean wasFound = false;

                    for (WeatherDatabase.WeatherLocation newWeatherLocation : newWeatherLocations) {
                        wasFound = wasFound || oldWeatherLocation.id == newWeatherLocation.id;
                    }

                    if (!wasFound) {
                        weatherDatabaseDao.delete(oldWeatherLocation);
                    }
                }

                boolean selectedExists = false;

                for (WeatherDatabase.WeatherLocation weatherLocation : newWeatherLocations) {
                    selectedExists = selectedExists || weatherLocation.isSelected;
                }

                if (!selectedExists && newWeatherLocations.size() > 0) {
                    newWeatherLocations.get(0).isSelected = true;
                }

                for (int i = 0, l = newWeatherLocations.size(); i < l; i++) {
                    WeatherDatabase.WeatherLocation weatherLocation = newWeatherLocations.get(i);
                    weatherLocation.order = i;

                    if (weatherLocation.id > 0) {
                        weatherDatabaseDao.update(weatherLocation);
                    } else {
                        int id = (int) weatherDatabaseDao.insert(weatherLocation);

                        newWeatherLocations.set(i,
                                new WeatherDatabase.WeatherLocation(
                                        id,
                                        weatherLocation.latitude,
                                        weatherLocation.longitude,
                                        weatherLocation.name,
                                        weatherLocation.isSelected,
                                        weatherLocation.isCurrentLocation,
                                        weatherLocation.order));
                    }
                }
            };

            //TODO: Louder errors
            if (lastDatabaseUpdate == null || !(lastDatabaseUpdate.getState().equals(Promise.PromiseState.STARTED) || lastDatabaseUpdate.getState().equals(Promise.PromiseState.NOT_STARTED))) {
                lastDatabaseUpdate = Promise.create(callable, Throwable::printStackTrace);
            } else {
                lastDatabaseUpdate = lastDatabaseUpdate.then(callable, Throwable::printStackTrace);
            }
        }

        @Override
        public void onFinish() {
            if (lastDatabaseUpdate != null) {
                try {
                    lastDatabaseUpdate.await();
                } catch (ExecutionException | InterruptedException e) {
                    //
                }
            }
        }

        //TODO Smarter Database Updates - Currently doUpdate gets called multiple times per update
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

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                doUpdate();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                doUpdate();
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                doUpdate();
            }

            @Override
            public void onChanged() {
                doUpdate();
            }

            private void doUpdate() {
                setCurrentLocationEnabled(!isCurrentLocationSelected());

                notifyViewPager();

                updateLocations();
            }
        }
    }

    private class UnitsPageContainer extends OnboardingContainer {
        private static final String KEY_TEMPERATURE = "temperature", KEY_SPEED = "speed", KEY_THEME = "theme", KEY_ALERTNOTIF = "alertnotif", KEY_PERSISTNOTIF = "persistnotif", KEY_GADGETBRIDGE = "gadgetbridge";
        private UnitsButtonGroup<Boolean> alertsButtonGroup, persistentButtonGroup;
        private Boolean gadgetbridgeEnabled = null, alertNotifEnabled = null, persistNotifEnabled = null;

        private WeatherPreferences.SpeedUnit speed = null;
        private WeatherPreferences.Theme theme = null;
        private WeatherPreferences.TemperatureUnit temperature = null;
        private SnackbarHelper snackbarHelper;

        public UnitsPageContainer(Context context) {
            super(context);
        }

        @Override
        public int getViewRes() {
            return R.layout.fragment_units;
        }

        @Override
        public void onCreateView(View v) {
            snackbarHelper = new SnackbarHelper(v.findViewById(R.id.viewpager_coordinator));
        }

        @Override
        public void onBindView(View v) {
            if (speed == null) {
                speed = WeatherPreferences.getInstance(getContext()).getSpeedUnit();
            }

            if (temperature == null) {
                temperature = WeatherPreferences.getInstance(getContext()).getTemperatureUnit();
            }

            if (theme == null) {
                theme = WeatherPreferences.getInstance(getContext()).getTheme();
            }

            if (alertNotifEnabled == null) {
                alertNotifEnabled = WeatherPreferences.getInstance(getContext()).getShowAlertNotification();
            }

            if (persistNotifEnabled == null) {
                persistNotifEnabled = WeatherPreferences.getInstance(getContext()).getShowPersistentNotification();
            }

            if (gadgetbridgeEnabled == null) {
                gadgetbridgeEnabled = WeatherPreferences.getInstance(getContext()).getGadgetbridgeEnabled();
            }

            new UnitsButtonGroup<WeatherPreferences.TemperatureUnit>(v, temperature -> {
                WeatherPreferences.getInstance(getContext()).setTemperatureUnit(this.temperature = temperature);
                notifyViewPagerConditionally();
                checkIfBackgroundLocationEnabled();
            })
                    .addButton(R.id.button_fahrenheit, WeatherPreferences.TemperatureUnit.FAHRENHEIT)
                    .addButton(R.id.button_celsius, WeatherPreferences.TemperatureUnit.CELSIUS)
                    .selectButton(temperature);

            new UnitsButtonGroup<WeatherPreferences.SpeedUnit>(v, speed -> {
                WeatherPreferences.getInstance(getContext()).setSpeedUnit(this.speed = speed);
                notifyViewPagerConditionally();
                checkIfBackgroundLocationEnabled();
            })
                    .addButton(R.id.button_mph, WeatherPreferences.SpeedUnit.MPH)
                    .addButton(R.id.button_kmh, WeatherPreferences.SpeedUnit.KMH)
                    .addButton(R.id.button_ms, WeatherPreferences.SpeedUnit.MS)
                    .addButton(R.id.button_kn, WeatherPreferences.SpeedUnit.KN)
                    .selectButton(speed);

            new UnitsButtonGroup<WeatherPreferences.Theme>(v, theme -> {
                checkIfBackgroundLocationEnabled();
                if (this.theme != theme) {
                    WeatherPreferences.getInstance(getContext()).setTheme(this.theme = theme);
                    notifyViewPagerConditionally();
                    ColorUtils.setNightMode(getContext());
                }
            })
                    .addButton(R.id.button_theme_light, WeatherPreferences.Theme.LIGHT)
                    .addButton(R.id.button_theme_dark, WeatherPreferences.Theme.DARK)
                    .addButton(R.id.button_theme_auto, WeatherPreferences.Theme.AUTO)
                    .selectButton(theme);

            alertsButtonGroup = new UnitsButtonGroup<Boolean>(v, alertNotifEnabled -> {
                WeatherPreferences.getInstance(getContext()).setShowAlertNotification(this.alertNotifEnabled = alertNotifEnabled);
                notifyViewPagerConditionally();
                checkIfBackgroundLocationEnabled();

                if (Build.VERSION.SDK_INT >= 33 && alertNotifEnabled) {
                    notificationRequestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            })
                    .addButton(R.id.button_alert_notif_enabled, true)
                    .addButton(R.id.button_alert_notif_disabled, false);

            alertsButtonGroup.selectButton(alertNotifEnabled);

            persistentButtonGroup = new UnitsButtonGroup<Boolean>(v, persistNotifEnabled -> {
                WeatherPreferences.getInstance(getContext()).setShowPersistentNotification(this.persistNotifEnabled = persistNotifEnabled);
                notifyViewPagerConditionally();
                checkIfBackgroundLocationEnabled();

                if (Build.VERSION.SDK_INT >= 33 && persistNotifEnabled) {
                    notificationRequestLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
                }
            })
                    .addButton(R.id.button_weather_notif_enabled, true)
                    .addButton(R.id.button_weather_notif_disabled, false);

            persistentButtonGroup.selectButton(persistNotifEnabled);

            new UnitsButtonGroup<Boolean>(v, gadgetbridgeEnabled -> {
                WeatherPreferences.getInstance(getContext()).setGadgetbridgeEnabled(this.gadgetbridgeEnabled = gadgetbridgeEnabled);
                notifyViewPagerConditionally();
                checkIfBackgroundLocationEnabled();
            })
                    .addButton(R.id.button_gadgetbridge_enabled, true)
                    .addButton(R.id.button_gadgetbridge_disabled, false)
                    .selectButton(gadgetbridgeEnabled);

            notifyViewPagerConditionally();
        }

        //TODO create WeatherPreferences Boolean class for Enableds
        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putString(KEY_TEMPERATURE, temperature.getValue());
            outState.putString(KEY_SPEED, speed.getValue());
            outState.putString(KEY_THEME, theme.getValue());
            outState.putString(KEY_ALERTNOTIF, alertNotifEnabled == null ? "null" : alertNotifEnabled ? "true" : "false");
            outState.putString(KEY_PERSISTNOTIF, persistNotifEnabled == null ? "null" : persistNotifEnabled ? "true" : "false");
            outState.putString(KEY_GADGETBRIDGE, gadgetbridgeEnabled == null ? "null" : gadgetbridgeEnabled ? "true" : "false");
        }

        @Override
        public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            temperature = WeatherPreferences.TemperatureUnit.from(savedInstanceState.getString(KEY_TEMPERATURE), WeatherPreferences.TemperatureUnit.DEFAULT);
            speed = WeatherPreferences.SpeedUnit.from(savedInstanceState.getString(KEY_SPEED), WeatherPreferences.SpeedUnit.DEFAULT);
            theme = WeatherPreferences.Theme.from(savedInstanceState.getString(KEY_THEME), WeatherPreferences.Theme.DEFAULT);
            alertNotifEnabled = savedInstanceState.containsKey(KEY_ALERTNOTIF) && !savedInstanceState.getString(KEY_ALERTNOTIF).equals("null") ? savedInstanceState.getString(KEY_ALERTNOTIF).equals("true") : null;
            persistNotifEnabled = savedInstanceState.containsKey(KEY_PERSISTNOTIF) && !savedInstanceState.getString(KEY_PERSISTNOTIF).equals("null") ? savedInstanceState.getString(KEY_PERSISTNOTIF).equals("true") : null;
            gadgetbridgeEnabled = savedInstanceState.containsKey(KEY_GADGETBRIDGE) && !savedInstanceState.getString(KEY_GADGETBRIDGE).equals("null") ? savedInstanceState.getString(KEY_GADGETBRIDGE).equals("true") : null;
        }

        private void notifyViewPagerConditionally() {
            if (canAdvanceToNextPage()) {
                notifyViewPager();
            }
        }

        @Override
        public void onPageSelected() {
            checkIfBackgroundLocationEnabled();
        }

        @Override
        public void onPageDeselected() {
            dismissSnackbar();
        }

        @Override
        public boolean canAdvanceToNextPage() {
            return !temperature.equals(WeatherPreferences.TemperatureUnit.DEFAULT) &&
                    !speed.equals(WeatherPreferences.SpeedUnit.DEFAULT) &&
                    !theme.equals(WeatherPreferences.Theme.DEFAULT) &&
                    alertNotifEnabled != null &&
                    persistNotifEnabled != null &&
                    gadgetbridgeEnabled != null;
        }

        public void checkIfBackgroundLocationEnabled() {
            Promise.create(a -> {
                if (WeatherDatabase.getInstance(getContext()).locationDao().isCurrentLocationSelected() &&
                        WeatherPreferences.getInstance(getContext()).shouldRunBackgroundJob()) {
                    if (!weatherLocationManager.isBackgroundLocationPermissionGranted(getContext())) {
                        SettingsActivity.this.runOnUiThread(() ->
                                snackbarHelper.notifyBackLocPermDenied(SettingsActivity.this.backgroundLocationRequestLauncher, WeatherPreferences.getInstance(getContext()).shouldShowNotifications()));
                    }
                } else {
                    SettingsActivity.this.runOnUiThread(this::dismissSnackbar);
                }
            });
        }

        public void checkIfNotificationAllowed() {
            if (!NotificationUtils.canShowNotifications(getContext())) {
                persistentButtonGroup.selectButton(false);
                alertsButtonGroup.selectButton(false);
            }
        }

        private void dismissSnackbar() {
            if (snackbarHelper != null) {
                snackbarHelper.dismiss();
            }
        }
    }

    private class ApiKeyPageContainer extends OnboardingContainer implements View.OnClickListener, TextWatcher, View.OnFocusChangeListener {
        private static final String KEY_APIKEY = "apiKey", KEY_APIKEYSTATE = "apiKeyState";
        private final int[][] colorStates = new int[][]{
                new int[]{-android.R.attr.state_focused},
                new int[]{android.R.attr.state_focused}
        };
        private TextInputEditText apiKeyEditText;
        private TextInputLayout apiKeyEditTextLayout;
        private MaterialButton testApiKeyButton;
        private LinearProgressIndicator testApiProgressIndicator;
        private ApiKeyState apiKeyState = ApiKeyState.NULL;
        private boolean apiKeyFocused = true;
        private SnackbarHelper snackbarHelper;

        public ApiKeyPageContainer(Context context) {
            super(context);
        }

        @Override
        public void onCreateView(View v) {
            apiKeyEditText = v.findViewById(R.id.onboarding_apikey_edittext);
            apiKeyEditTextLayout = v.findViewById(R.id.onboarding_apikey_edittext_layout);
            testApiKeyButton = v.findViewById(R.id.test_api_key);
            testApiProgressIndicator = v.findViewById(R.id.onboarding_apikey_progress);
        }

        @Override
        public void onBindView(View v) {
            if (getContext() != null) {
                ViewUtils.setEditTextCursorColor(apiKeyEditText, ContextCompat.getColor(getContext(), R.color.color_accent_text));
            }

            snackbarHelper = new SnackbarHelper(apiKeyEditTextLayout);

            if (apiKeyState == ApiKeyState.NULL) {
                String apiKey = WeatherPreferences.getInstance(getContext()).getAPIKey();

                if (apiKey.isEmpty()) {
                    //TODO Test api key, dont assume
                    setApiKeyState(ApiKeyState.NEUTRAL);
                } else {
                    apiKeyEditText.setText(apiKey);

                    setApiKeyState(ApiKeyState.PASS);
                }
            } else {
                setApiKeyState(apiKeyState);
            }

            apiKeyEditText.addTextChangedListener(this);
            apiKeyEditText.setOnFocusChangeListener(this);
            apiKeyEditText.setOnEditorActionListener((textView, i, keyEvent) -> {
                testApiKeyButton.performClick();
                return true;
            });

            v.setOnClickListener(this);

            testApiKeyButton.setOnClickListener(this);
        }

        @Override
        public void onSaveInstanceState(@NonNull Bundle outState) {
            outState.putString(KEY_APIKEY, ViewUtils.editTextToString(apiKeyEditText));
            outState.putInt(KEY_APIKEYSTATE, apiKeyState.ordinal());
        }

        @Override
        public void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
            apiKeyEditText.setText(savedInstanceState.getString(KEY_APIKEY));
            apiKeyState = ApiKeyState.values()[savedInstanceState.getInt(KEY_APIKEYSTATE)];
        }

        @Override
        public void onPageSelected() {
            apiKeyEditText.requestFocus();

            updateApiKeyColors(apiKeyState);

            ViewUtils.toggleKeyboardState(apiKeyEditText, true);
        }

        @Override
        public void onPageDeselected() {
            if (apiKeyEditText != null) {
                apiKeyEditText.clearFocus();

                updateApiKeyColors(apiKeyState);

                ViewUtils.toggleKeyboardState(apiKeyEditText, false);
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
            setApiKeyState(ApiKeyState.NEUTRAL);
        }


        @Override
        public void onClick(View v) {
            if (v.getId() == R.id.test_api_key) {
                String apiKeyText = ViewUtils.editTextToString(apiKeyEditText).replaceAll("[^0-9A-Za-z]", "");

                if (apiKeyText.length() > 0) {
                    testApiKeyButton.setEnabled(false);
                    testApiProgressIndicator.show();

                    apiKeyEditText.setEnabled(false);
                    apiKeyEditText.clearFocus();

                    Promise.create((a) -> {
                                WeatherPreferences.ApiVersion apiVersion = OpenWeatherMap.determineApiVersion(apiKeyText);

                                SettingsActivity.this.runOnUiThread(() -> {

                                    testApiProgressIndicator.hide();

                                    if (apiVersion == null) {
                                        setApiKeyState(ApiKeyState.BAD_API_KEY);
                                    } else if (apiVersion == WeatherPreferences.ApiVersion.WEATHER_2_5) {
                                        setApiKeyState(ApiKeyState.NO_ONECALL);
                                    } else {
                                        setApiKeyState(ApiKeyState.PASS);
                                        WeatherPreferences.getInstance(getContext()).setAPIKey(apiKeyText);
                                        WeatherPreferences.getInstance(getContext()).setAPIVersion(apiVersion.equals(WeatherPreferences.ApiVersion.ONECALL_3_0) ? WeatherPreferences.ApiVersion.ONECALL_3_0 : WeatherPreferences.ApiVersion.ONECALL_2_5);
                                    }
                                });

                            },
                            (t) -> SettingsActivity.this.runOnUiThread(() -> {
                                        testApiProgressIndicator.hide();
                                        testApiKeyButton.setEnabled(true);
                                        apiKeyEditText.setEnabled(true);

                                        snackbarHelper.logError("API Key Test Error: " + t.getMessage(), t);
                                    }
                            ));
                } else {
                    //if the user clicks outside the edittext (on the container), clear the focus
                    apiKeyEditText.clearFocus();
                }
            }
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            apiKeyFocused = hasFocus;
            updateApiKeyColors(apiKeyState);
        }

        @Override
        public int getViewRes() {
            return R.layout.fragment_apikey;
        }

        @Override
        public boolean canAdvanceToNextPage() {
            return apiKeyState == ApiKeyState.PASS;
        }

        private void updateApiKeyColors(ApiKeyState state) {
            switch (state) {
                case BAD_API_KEY:
                    apiKeyEditTextLayout.setError(getString(R.string.text_invalid_api_key));
                    break;
                case NO_ONECALL:
                    apiKeyEditTextLayout.setError(getString(R.string.text_invalid_subscription));
                    break;
                default:
                    apiKeyEditTextLayout.setError(null);

                    int textColorRes, editTextDrawableRes;

                    if (state == ApiKeyState.PASS) {
                        textColorRes = R.color.color_green;
                        editTextDrawableRes = R.drawable.ic_done_white_24dp;
                    } else {
                        textColorRes = R.color.text_primary_emphasis;
                        editTextDrawableRes = 0;

                        apiKeyEditText.post(() -> apiKeyEditText.setCompoundDrawables(null, null, null, null));
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

                    apiKeyEditTextLayout.setBoxStrokeColor(coloredTextColor);
                    apiKeyEditTextLayout.setHintTextColor(textColor);

                    if (state == ApiKeyState.PASS) {
                        apiKeyEditText.post(() -> ViewUtils.setDrawable(apiKeyEditText, editTextDrawableRes, apiKeyFocused ? coloredTextColor : greyTextColor, ViewUtils.FLAG_END));
                    }
            }
        }

        private void setApiKeyState(ApiKeyState state) {
            apiKeyState = state;

            updateApiKeyColors(state);

            testApiKeyButton.setEnabled(state == ApiKeyState.NEUTRAL);

            apiKeyEditText.setEnabled(true);

            notifyViewPager();
        }
    }

    private enum ApiKeyState {
        NULL,
        NEUTRAL,
        PASS,
        BAD_API_KEY,
        NO_ONECALL
    }

    private static class UnitsButtonGroup<T> implements View.OnClickListener {
        private final HashMap<T, View> valueViewMap = new HashMap<>();

        private final View container;
        private final OnUnitsButtonSelected<T> onUnitsButtonSelected;

        public UnitsButtonGroup(View container, @NonNull OnUnitsButtonSelected<T> onUnitsButtonSelected) {
            this.container = container;
            this.onUnitsButtonSelected = onUnitsButtonSelected;
        }

        public UnitsButtonGroup<T> addButton(@IdRes int buttonResId, T value) {
            View view = container.findViewById(buttonResId);
            view.setOnClickListener(this);

            valueViewMap.put(value, view);

            return this;
        }

        public void selectButton(T value) {
            selectButton(valueViewMap.get(value), value);
        }

        private void selectButton(View v, T value) {
            for (T key : valueViewMap.keySet()) {
                View button = valueViewMap.get(key);

                if (button != null) {
                    button.setSelected(false);

                    if (value == null && v != null && v.getId() == button.getId()) {
                        value = key;
                    }
                }
            }

            if (v != null) {
                v.setSelected(true);

                onUnitsButtonSelected.onUnitsButtonSelected(value);
            }
        }

        @Override
        public void onClick(View v) {
            selectButton(v, null);
        }
    }

    private interface OnUnitsButtonSelected<T> {
        void onUnitsButtonSelected(T value);
    }
}
