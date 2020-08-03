package tk.giesecke.DisasterRadio;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.ListFragment;

import android.text.util.Linkify;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Objects;

/**
 * Search and display BLE devices.
 * The BLE devices are filtered by there advertising name and only devices
 * that are running emy_chat firmware are shown in the resulting list
 */
public class DevicesFragment extends ListFragment {

	private enum ScanState {NONE, LESCAN, DISCOVERY, DISCOVERY_FINISHED}

	private ScanState scanState = ScanState.NONE;
	private static final long LESCAN_PERIOD = 10000; // similar to bluetoothAdapter.startDiscovery
	private final Handler leScanStopHandler = new Handler();
	private final BluetoothAdapter.LeScanCallback leScanCallback;
	private final BroadcastReceiver discoveryBroadcastReceiver;
	private final IntentFilter discoveryIntentFilter;

	private Menu menu;
	private BluetoothAdapter bluetoothAdapter;
	private final ArrayList<BluetoothDevice> listItems = new ArrayList<>();
	private ArrayAdapter<BluetoothDevice> listAdapter;

	public DevicesFragment() {
		leScanCallback = (device, rssi, scanRecord) -> {
			if (device != null && getActivity() != null) {
				getActivity().runOnUiThread(() -> updateScan(device));
			}
		};
		discoveryBroadcastReceiver = new BroadcastReceiver() {
			@Override
			public void onReceive(Context context, Intent intent) {
				String intentAction = intent.getAction();
				if ((intentAction != null) && (intentAction.equals(BluetoothDevice.ACTION_FOUND))) {
					BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
					assert device != null;
					if (device.getType() != BluetoothDevice.DEVICE_TYPE_CLASSIC && getActivity() != null) {
						getActivity().runOnUiThread(() -> updateScan(device));
					}
				}
				if ((intentAction != null) && (intentAction.equals((BluetoothAdapter.ACTION_DISCOVERY_FINISHED)))) {
					scanState = ScanState.DISCOVERY_FINISHED; // don't cancel again
					stopScan();
				}
			}
		};
		discoveryIntentFilter = new IntentFilter();
		discoveryIntentFilter.addAction(BluetoothDevice.ACTION_FOUND);
		discoveryIntentFilter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		if (Objects.requireNonNull(getActivity()).getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH))
			bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		listAdapter = new ArrayAdapter<BluetoothDevice>(getActivity(), 0, listItems) {
			@NonNull
			@Override
			public View getView(int position, View view, @NonNull ViewGroup parent) {
				BluetoothDevice device = listItems.get(position);
				if (view == null)
					view = Objects.requireNonNull(getActivity()).getLayoutInflater().inflate(R.layout.device_list_item, parent, false);
				TextView text1 = view.findViewById(R.id.text1);
				TextView text2 = view.findViewById(R.id.text2);
				if (device.getName() == null || device.getName().isEmpty())
					text1.setText(getString(R.string.devices_no_name));
				else
					text1.setText(device.getName());
				text2.setText(device.getAddress());
				return view;
			}
		};
	}

	@Override
	@SuppressLint("InflateParams")
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		setListAdapter(null);
		View header = Objects.requireNonNull(getActivity()).getLayoutInflater().inflate(R.layout.device_list_header, null, false);
		getListView().addHeaderView(header, null, false);
		setEmptyText(getString(R.string.devices_initializing_message));
		((TextView) getListView().getEmptyView()).setTextSize(18);
		setListAdapter(listAdapter);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.menu_devices, menu);
		this.menu = menu;
		if (bluetoothAdapter == null) {
			menu.findItem(R.id.bt_settings).setEnabled(false);
			menu.findItem(R.id.ble_scan).setEnabled(false);
		} else if (!bluetoothAdapter.isEnabled()) {
			menu.findItem(R.id.ble_scan).setEnabled(false);
		}
		startScan();
	}

	@Override
	public void onResume() {
		super.onResume();
		Objects.requireNonNull(getActivity()).registerReceiver(discoveryBroadcastReceiver, discoveryIntentFilter);
		if (bluetoothAdapter == null) {
			setEmptyText(getString(R.string.devices_not_supported_message));
		} else if (!bluetoothAdapter.isEnabled()) {
			setEmptyText(getString(R.string.devices_bluetooth_disabled_message));
			if (menu != null) {
				listItems.clear();
				listAdapter.notifyDataSetChanged();
				menu.findItem(R.id.ble_scan).setEnabled(false);
			}
		} else {
			setEmptyText(getString(R.string.devices_use_scan_message));
			if (menu != null)
				menu.findItem(R.id.ble_scan).setEnabled(true);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		stopScan();
		Objects.requireNonNull(getActivity()).unregisterReceiver(discoveryBroadcastReceiver);
	}

	@Override
	public void onDestroyView() {
		super.onDestroyView();
		menu = null;
	}

	@SuppressLint("ApplySharedPref")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case R.id.ble_scan:
				startScan();
				return true;
			case R.id.ble_scan_stop:
				stopScan();
				return true;
			case R.id.bt_settings:
				Intent intent = new Intent();
				intent.setAction(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS);
				startActivity(intent);
				return true;
			case R.id.dev_about:
				androidx.appcompat.app.AlertDialog.Builder aboutAlert = new androidx.appcompat.app.AlertDialog.Builder(Objects.requireNonNull(getContext()));

				aboutAlert.setTitle(getString(R.string.menu_about));
				aboutAlert.setMessage(getString(R.string.about_text));

				aboutAlert.setPositiveButton("Ok", (dialog, whichButton) -> {
					// Canceled.
				});
				androidx.appcompat.app.AlertDialog aboutDialog = aboutAlert.create();
				aboutDialog.show();

				TextView alertTextView = aboutDialog.findViewById(android.R.id.message);
				assert alertTextView != null;
				Linkify.addLinks(alertTextView, Linkify.WEB_URLS);
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	@SuppressLint("StaticFieldLeak") // AsyncTask needs reference to this fragment
	private void startScan() {
		if (scanState != ScanState.NONE)
			return;
		scanState = ScanState.LESCAN;

		listItems.clear();
		listAdapter.notifyDataSetChanged();
		setEmptyText(getString(R.string.devices_scanning_message));
		menu.findItem(R.id.ble_scan).setVisible(false);
		menu.findItem(R.id.ble_scan_stop).setVisible(true);
		if (scanState == ScanState.LESCAN) {
			leScanStopHandler.postDelayed(this::stopScan, LESCAN_PERIOD);
			new AsyncTask<Void, Void, Void>() {
				@Override
				protected Void doInBackground(Void[] params) {
					bluetoothAdapter.startLeScan(null, leScanCallback);
					return null;
				}
			}.execute(); // start async to prevent blocking UI, because startLeScan sometimes take some seconds
		} else {
			bluetoothAdapter.startDiscovery();
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		// ignore requestCode as there is only one in this fragment
		if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
			new Handler(Looper.getMainLooper()).postDelayed(this::startScan, 1); // run after onResume to avoid wrong empty-text
		} else {
			AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
			builder.setTitle(getText(R.string.location_denied_title));
			builder.setMessage(getText(R.string.location_denied_message));
			builder.setPositiveButton(android.R.string.ok, null);
			builder.show();
		}
	}

	private void updateScan(BluetoothDevice device) {
		if (scanState == ScanState.NONE)
			return;
		if (listItems.indexOf(device) < 0) {
			if ((device.getName() != null) && (device.getName().startsWith("DR-"))) {
				listItems.add(device);
				Collections.sort(listItems, DevicesFragment::compareTo);
				listAdapter.notifyDataSetChanged();
			}
		}
	}

	private void stopScan() {
		if (scanState == ScanState.NONE)
			return;
		setEmptyText(getString(R.string.devices_not_found_message));
		if (menu != null) {
			menu.findItem(R.id.ble_scan).setVisible(true);
			menu.findItem(R.id.ble_scan_stop).setVisible(false);
		}
		switch (scanState) {
			case LESCAN:
				leScanStopHandler.removeCallbacks(this::stopScan);
				bluetoothAdapter.stopLeScan(leScanCallback);
				break;
			case DISCOVERY:
				bluetoothAdapter.cancelDiscovery();
				break;
			default:
				// already canceled
		}
		scanState = ScanState.NONE;

	}

	@Override
	public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
		stopScan();
		BluetoothDevice device = listItems.get(position - 1);
		Bundle args = new Bundle();
		args.putString("device", device.getAddress());
		args.putString("name", device.getName());
		Fragment fragment = new TerminalFragment();
		fragment.setArguments(args);
		Objects.requireNonNull(getFragmentManager()).beginTransaction().replace(R.id.fragment, fragment, "terminal").addToBackStack(null).commit();
	}

	/**
	 * sort by name, then address. sort named devices first
	 */
	private static int compareTo(BluetoothDevice a, BluetoothDevice b) {
		boolean aValid = a.getName() != null && !a.getName().isEmpty();
		boolean bValid = b.getName() != null && !b.getName().isEmpty();
		if (aValid && bValid) {
			int ret = a.getName().compareTo(b.getName());
			if (ret != 0) return ret;
			return a.getAddress().compareTo(b.getAddress());
		}
		if (aValid) return -1;
		if (bValid) return +1;
		return a.getAddress().compareTo(b.getAddress());
	}
}
