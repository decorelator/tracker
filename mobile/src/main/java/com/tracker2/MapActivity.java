package com.tracker2;

import android.Manifest;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Chronometer;
import android.widget.TextView;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationListener;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

import static com.tracker2.R.id.map;

public class MapActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, LocationListener {

    private static final int REQUEST_CHECK_SETTINGS = 101;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Location mLastLocation;
    private LocationRequest mLocationRequest;
    private Location mCurrentLocation;
    private Marker mMarker;
    private ArrayList<LatLng> track = new ArrayList<>();
    private Polyline trackLine;

    public static final int ACCURACY_DECAYS_TIME = 3; // Metres per second

    private KalmanLatLong kalmanLatLong = new KalmanLatLong(ACCURACY_DECAYS_TIME);
    private TextView distanceTxt;
    private double distance = 0;
    private Location lastLocation;
    private Chronometer chronometer2;
    private TextView curSpeed;
    private TextView avgSpeed;
    private long startTime;
    private long lastTime;
    private float localDist;
    private boolean started = false;
    private double time;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        distanceTxt = (TextView) findViewById(R.id.distance);

        chronometer2 = (Chronometer) findViewById(R.id.chronometer2);
        avgSpeed = (TextView) findViewById(R.id.avgSpeed);
        curSpeed = (TextView) findViewById(R.id.curSpeed);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(map);
        mapFragment.getMapAsync(this);

        if (mGoogleApiClient == null) {
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(new GoogleApiClient.OnConnectionFailedListener() {
                        @Override
                        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

                        }
                    })
                    .addApi(LocationServices.API)
                    .build();
        }

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        // Add a marker in Sydney and move the camera
        LatLng pos = new LatLng(-34, 151);
        mMarker = mMap.addMarker(new MarkerOptions().position(pos).title("Marker in Sydney"));
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(pos, 15));
    }

    @Override
    protected void onStart() {
        mGoogleApiClient.connect();
        super.onStart();
    }

    protected void onStop() {
        mGoogleApiClient.disconnect();
        super.onStop();
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(500);
        mLocationRequest.setFastestInterval(500);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(mLocationRequest);

        PendingResult<LocationSettingsResult> result =
                LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient,
                        builder.build());

        result.setResultCallback(new ResultCallback<LocationSettingsResult>() {
            @Override
            public void onResult(@NonNull LocationSettingsResult result) {
                final Status status = result.getStatus();
                switch (status.getStatusCode()) {
                    case LocationSettingsStatusCodes.SUCCESS:
                        // All location settings are satisfied. The client can
                        // initialize location requests here.
                        break;
                    case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                        // Location settings are not satisfied, but this can be fixed
                        // by showing the user a dialog.
                        try {
                            // Show the dialog by calling startResolutionForResult(),
                            // and check the result in onActivityResult().
                            status.startResolutionForResult(
                                    MapActivity.this,
                                    REQUEST_CHECK_SETTINGS);
                        } catch (IntentSender.SendIntentException e) {
                            // Ignore the error.
                        }
                        break;
                    case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                        // Location settings are not satisfied. However, we have no way
                        // to fix the settings so we won't show the dialog.
                        break;
                }
            }
        });

    }

    protected void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        createLocationRequest();

        startLocationUpdates();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            }
            // TODO: Consider calling
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
    }

    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        mLastLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);

    }

    @Override
    public void onLocationChanged(Location location) {

        Object mLastUpdateTime = DateFormat.getTimeInstance().format(new Date());
        kalmanLatLong.process(
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getTime());

        location.setLatitude(kalmanLatLong.get_lat());
        location.setLongitude(kalmanLatLong.get_lng());
        location.setAccuracy(kalmanLatLong.get_accuracy());
        mCurrentLocation = location;
        if (!started) {
            showMe(new LatLng(location.getLatitude(), location.getLongitude()));
            return;
        }
        if (lastLocation != null) {
            localDist = lastLocation.distanceTo(location);
            time = calcTime(lastTime);
        }
        lastLocation = location;
        lastTime = System.currentTimeMillis();
        LatLng pos = new LatLng(location.getLatitude(), location.getLongitude());


        StringBuilder info = new StringBuilder();
        info.append(localDist).append(" ").append(localDist / 1000 ).append(" / ").append((localDist / 1000) / time);
        curSpeed.setText(info);
        if ((localDist / 1000) / time <= 30) {
            distance += localDist;
            updateUI(pos);
        }
    }

    private void showMe(LatLng pos) {
        mMarker.setPosition(pos);
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
    }


    private void updateUI(LatLng pos) {
        track.add(pos);
        mMarker.setPosition(pos);
        //mMap.moveCamera(CameraUpdateFactory.newLatLng(pos));
        if (trackLine == null) {
            trackLine = mMap.addPolyline(new PolylineOptions().clickable(true));
            trackLine.setPoints(track);
        } else {
            trackLine.setPoints(track);
        }

        float disKm = (float) (distance / 1000);
        //float localDisKm = (localDist / 1000);

        distanceTxt.setText(String.format(Locale.getDefault(), "%.2fm", distance));
        avgSpeed.setText(String.format(Locale.getDefault(), "avg Speed %.2fkm/h", disKm / calcTime(startTime)));
        curSpeed.setText(String.format(Locale.getDefault(), "cur Speed %.2fkm/h", (localDist / 1000) / time));
    }

    private double calcTime(long startTime) {
        return (System.currentTimeMillis() - startTime * 1.0) / (1000 * 60 * 60);
    }

    public void onStartClick(View view) {
        if (!started) {
            distance = 0;
            chronometer2.setBase(SystemClock.elapsedRealtime());
            chronometer2.start();
            startTime = System.currentTimeMillis();
            started = true;
            lastLocation = mCurrentLocation;
            ((TextView) view).setText("Stop");
        } else {
            chronometer2.stop();
            //startTime = System.currentTimeMillis();
            started = false;
            ((TextView) view).setText("Start");

        }
    }

    public void onLocClick(View view) {
        mMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(mCurrentLocation.getLatitude(), mCurrentLocation.getLongitude())));
    }
}
