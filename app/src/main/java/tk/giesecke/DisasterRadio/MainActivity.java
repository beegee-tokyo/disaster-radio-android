package tk.giesecke.DisasterRadio;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.FragmentManager;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;


import java.util.ArrayList;
import java.util.Objects;

import static android.location.Criteria.ACCURACY_FINE;
import static android.location.Criteria.ACCURACY_HIGH;
import static tk.giesecke.DisasterRadio.TerminalFragment.meEntry;

public class MainActivity extends AppCompatActivity implements FragmentManager.OnBackStackChangedListener, LocationListener {

	private static final String TAG = "DisasterRadio";
	public static final String LOCATION_UPDATE = "location";

	public static String userName = null;

	public static Context appContext;

	public static double latDouble = 0;
	public static double longDouble = 0;

	private LocationManager locationManagerGPS;
	private LocationManager locationManagerNetwork;
	private LocationManager locationManagerPassive;

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

		// Get pointer to shared preferences
		mPrefs = getSharedPreferences(sharedPrefName, 0);
		if (mPrefs.contains("userName")) {
			userName = mPrefs.getString("userName", "me");
		}

		appContext = this;

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

		ArrayList<String> arrPerm = new ArrayList<>();
		// On newer Android versions it is required to get the permission of the user to
		// access the storage of the device.
		if(ActivityCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
			arrPerm.add(Manifest.permission.INTERNET);
		}
		// On newer Android versions it is required to get the permission of the user to
		// access the storage of the device.
		if(ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
			arrPerm.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
		}
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
			arrPerm.add(Manifest.permission.ACCESS_COARSE_LOCATION);
		}

		if(!arrPerm.isEmpty()) {
			String[] permissions = new String[arrPerm.size()];
			permissions = arrPerm.toArray(permissions);
			ActivityCompat.requestPermissions(this, permissions, 0);
		}

		// Enable access to internet
		// ThreadPolicy to get permission to access internet
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		// TODO select the best provider
		locationManagerGPS = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManagerNetwork = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManagerPassive = (LocationManager) getSystemService(Context.LOCATION_SERVICE);


		Criteria bestProviderCriteria = new Criteria();
		bestProviderCriteria.setHorizontalAccuracy(ACCURACY_HIGH);
		bestProviderCriteria.setAccuracy(ACCURACY_FINE);
		String bestProvider = locationManagerGPS.getBestProvider(bestProviderCriteria, true);
		Log.d(TAG, "Best provider is " + bestProvider);

		boolean locationGPSEnabled = false;
		boolean locationNetEnabled = false;
		boolean locationOtherEnabled = false;
		// TODO use the best provider
		try {
			locationGPSEnabled = Objects.requireNonNull(locationManagerGPS).isProviderEnabled(LocationManager.GPS_PROVIDER);
			locationManagerGPS.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, this);
		} catch (Exception ignored) {
		}
		try {
			locationNetEnabled = Objects.requireNonNull(locationManagerNetwork).isProviderEnabled(LocationManager.NETWORK_PROVIDER);
			locationManagerNetwork.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000, 0, this);
		} catch (Exception ignored) {
		}
		try {
			locationOtherEnabled = Objects.requireNonNull(locationManagerPassive).isProviderEnabled(LocationManager.PASSIVE_PROVIDER);
			locationManagerPassive.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 1000, 0, this);
		} catch (Exception ignored) {
		}

		if ((!locationGPSEnabled) && (!locationNetEnabled) && (!locationOtherEnabled)) {
			Toast.makeText(getApplicationContext(), getString(R.string.info_no_gps), Toast.LENGTH_SHORT).show();
			Log.e(TAG, getString(R.string.info_no_gps));
		}
	}

	@Override
	public void onBackStackChanged() {
		Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(getSupportFragmentManager().getBackStackEntryCount() > 0);
	}

	@Override
	public boolean onSupportNavigateUp() {
		onBackPressed();
		return true;
	}

	// We can suppress the "MissingPermission" error here because we are sure that this is only called if we have the permission
	@SuppressLint("MissingPermission")
	@Override
	public void onLocationChanged(Location location) {
		Log.d(TAG, "location from " + location.getProvider());
		boolean locHasChanged = false;
		double newLatDouble = location.getLatitude();
		double newLongDouble = location.getLongitude();
		if ((newLatDouble != latDouble) || (newLongDouble != longDouble)) {
			locHasChanged = true;
		}

		if (meEntry != null) {
			latDouble = newLatDouble;
			longDouble = newLongDouble;
			// Change update frequency
			locationManagerGPS.requestLocationUpdates(LocationManager.GPS_PROVIDER, 60000, 50, this);
			locationManagerNetwork.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 60000, 50, this);
			locationManagerPassive.requestLocationUpdates(LocationManager.PASSIVE_PROVIDER, 60000, 50, this);
		}

		if (BuildConfig.DEBUG) {
			// TODO just for testing
			if ((userName != null) && (userName.equalsIgnoreCase("Huawei"))) {
				latDouble -= 0.01;
				longDouble += 0.01;
			}
		}
		Log.d(TAG, "Got Lat: " + latDouble + " Long: " + longDouble);

		// Update TerminalFragment with the new location
		final Intent broadcast = new Intent(LOCATION_UPDATE);
		broadcast.putExtra("lat", latDouble);
		broadcast.putExtra("long", longDouble);
		broadcast.putExtra("hasChanged", locHasChanged);
		LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
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
