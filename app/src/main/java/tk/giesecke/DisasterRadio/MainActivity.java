package tk.giesecke.DisasterRadio;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.mapbox.mapboxsdk.camera.CameraUpdateFactory;
import com.mapbox.mapboxsdk.geometry.LatLng;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;

import java.util.Objects;

import static tk.giesecke.DisasterRadio.TerminalFragment.fragMapboxMap;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, LocationListener {

    private static final String TAG = "DisasterRadio";

    public static String userName = null;

    public static Context appContext;

    public static double latDouble = 0;
    public static double longDouble = 0;

    public static boolean mapVisible = false;
    /*
     * Access to activities shared preferences
     */
    public static SharedPreferences mPrefs;
    /* Name of shared preferences */
    private static final String sharedPrefName = "DisasterRadio";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportFragmentManager().addOnBackStackChangedListener(this);
        if (savedInstanceState == null)
            getSupportFragmentManager().beginTransaction().add(R.id.fragment, new DevicesFragment(), "devices").commit();
        else
            onBackStackChanged();

        appContext = this;
        LocationManager locationManagerGPS = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationManager locationManagerNetwork = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        LocationManager locationManagerPassive = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle(R.string.location_permission_title);
                builder.setMessage(R.string.location_permission_message);
                builder.setPositiveButton(android.R.string.ok,
                        (dialog, which) -> requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 0));
                builder.show();
                return;
            }
        }

        boolean locationEnabled = false;
        // TODO select the best provider
        try {
            locationEnabled = Objects.requireNonNull(locationManagerGPS).isProviderEnabled(LocationManager.GPS_PROVIDER);
            locationManagerGPS.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        } catch (Exception ignored) {
        }
//        if (!locationEnabled) {
            try {
                locationEnabled = Objects.requireNonNull(locationManagerNetwork).isProviderEnabled(LocationManager.NETWORK_PROVIDER);
                locationManagerNetwork.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, this);
            } catch (Exception ignored) {
            }
//        }
//        if (!locationEnabled) {
            try {
                locationEnabled = Objects.requireNonNull(locationManagerPassive).isProviderEnabled(LocationManager.PASSIVE_PROVIDER);
                locationManagerPassive.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 0, 0, this);
            } catch (Exception ignored) {
            }
//        }
        if (!locationEnabled) {
            Log.e(TAG, "GPS disabled");
        }
    }

    @Override
    public void onBackStackChanged() {
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount()>0);
        // Get pointer to shared preferences
        mPrefs = getSharedPreferences(sharedPrefName,0);
        if (mPrefs.contains("userName")) {
            userName = mPrefs.getString("userName","me");
        }
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public void onLocationChanged(Location location) {
        Log.d(TAG, "location from " + location.getProvider());
        latDouble = location.getLatitude();
        longDouble = location.getLongitude();
        Log.d(TAG, "Got Lat: " + latDouble + " Long: " + longDouble);
        if (mapVisible && (fragMapboxMap != null)) {
//            LatLng point = new LatLng();
//            point.setLatitude(latDouble);
//            point.setLongitude(longDouble);
////            fragMapboxMap.moveCamera(CameraUpdateFactory.newLatLng(point));
//            fragMapboxMap.moveCamera(CameraUpdateFactory.newLatLngPadding(point,5.0,5.0,5.0,5.0));
        }
    }

    @Override
    public void onProviderDisabled(String provider) {
        Log.d(TAG, provider + " disabled");
    }

    @Override
    public void onProviderEnabled(String provider) {
        Log.d(TAG, provider + " enabled");
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
        Log.d(TAG, provider + " status");
    }
}
