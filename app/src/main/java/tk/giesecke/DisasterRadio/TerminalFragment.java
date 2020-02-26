package tk.giesecke.DisasterRadio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import android.preference.PreferenceManager;
import android.text.util.Linkify;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.modules.ArchiveFileFactory;
import org.osmdroid.tileprovider.modules.IArchiveFile;
import org.osmdroid.tileprovider.modules.OfflineTileProvider;
import org.osmdroid.tileprovider.tilesource.FileBasedTileSource;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.tileprovider.util.SimpleRegisterReceiver;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import tk.giesecke.DisasterRadio.msg.MemberAdapter;
import tk.giesecke.DisasterRadio.msg.MemberData;
import tk.giesecke.DisasterRadio.msg.Message;
import tk.giesecke.DisasterRadio.msg.MessageAdapter;
import tk.giesecke.DisasterRadio.nodes.Nodes;
import tk.giesecke.DisasterRadio.nodes.NodesAdapter;

import static tk.giesecke.DisasterRadio.MainActivity.LOCATION_UPDATE;
import static tk.giesecke.DisasterRadio.MainActivity.appContext;
import static tk.giesecke.DisasterRadio.MainActivity.latDouble;
import static tk.giesecke.DisasterRadio.MainActivity.longDouble;
import static tk.giesecke.DisasterRadio.MainActivity.mPrefs;
import static tk.giesecke.DisasterRadio.MainActivity.userName;

/**
 * Terminal fragment holds the chat box and the members map.
 * Switching between chat box and members map needs improvement as it is not obvious
 * that it is not the back or home button going back from the map to the chatbox, but
 * a button at the bottom of the screen.
 */
public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

	private static final String TAG = "TerminalFragment";

	private enum Connected {False, Pending, True}

	private Context thisContext;

	private String deviceAddress;

	private SerialSocket socket;
	private SerialService service;
	private boolean initialStart = true;
	private Connected connected = Connected.False;

	private ListView messagesView;
	private MessageAdapter messageAdapter;
	private MemberAdapter memberAdapter;

	static MemberData meEntry = null;
	private MemberData noUserName;

	@SuppressWarnings("FieldCanBeLocal")
	private ListView nodesView;
	private NodesAdapter nodesAdapter;

	private View fragmentView;
	private EditText sendText;
	private TextView nodeInfo;


	private LinearLayout mapLayout;
	private LinearLayout chatLayout;

	private final double defaultZoom = 14.0;

	public static MapView mapView;

	private static final String PREFS_TILE_SOURCE = "tilesource";
	private static final String PREFS_LATITUDE_STRING = "latitudeString";
	private static final String PREFS_LONGITUDE_STRING = "longitudeString";
	private static final String PREFS_ORIENTATION = "orientation";
	private static final String PREFS_ZOOM_LEVEL_DOUBLE = "zoomLevelDouble";

	private Menu thisMenu;

	/*
	 * Lifecycle
	 */
	@Override
	public void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		thisContext = getContext();
		setHasOptionsMenu(true);
		setRetainInstance(true);
		deviceAddress = Objects.requireNonNull(getArguments()).getString("device");
		messageAdapter = new MessageAdapter(thisContext);
		memberAdapter = new MemberAdapter();
		nodesAdapter = new NodesAdapter(thisContext);
	}

	@Override
	public void onDestroy() {

		if (connected != Connected.False) {
			if (userName != null) {
				send("~" + userName + " " + getString(R.string.chat_leave), "c");
			}
			disconnect();
		}
		Objects.requireNonNull(getActivity()).stopService(new Intent(getActivity(), SerialService.class));
		LocalBroadcastManager.getInstance(thisContext).unregisterReceiver(MyLocationUpdate);
		super.onDestroy();
	}

	@Override
	public void onStart() {
		super.onStart();
		if (service != null) {
			service.attach(this);
		} else {
			Objects.requireNonNull(getActivity()).startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
		}
	}

	@Override
	public void onStop() {
		if (service != null && !Objects.requireNonNull(getActivity()).isChangingConfigurations()) {
			service.detach();
		}
		super.onStop();
	}

	@SuppressWarnings("deprecation")
	// onAttach(context) was added with API 23. onAttach(activity) works for all API versions
	@Override
	public void onAttach(@NonNull Activity activity) {
		super.onAttach(activity);
		Objects.requireNonNull(getActivity()).bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
	}

	@Override
	public void onDetach() {
		try {
			Objects.requireNonNull(getActivity()).unbindService(this);
		} catch (Exception ignored) {
		}
		super.onDetach();
	}

	@Override
	public void onResume() {
		super.onResume();
		if (initialStart && service != null) {
			initialStart = false;
			Objects.requireNonNull(getActivity()).runOnUiThread(this::connect);
		}
		mapView.onResume();
	}

	@Override
	public void onServiceConnected(ComponentName name, IBinder binder) {
		service = ((SerialService.SerialBinder) binder).getService();
		if (initialStart && isResumed()) {
			initialStart = false;
			Objects.requireNonNull(getActivity()).runOnUiThread(this::connect);
		}
	}

	@Override
	public void onServiceDisconnected(ComponentName name) {
		service = null;
	}

	@SuppressLint("ApplySharedPref")
	@Override
	public void onPause() {
		super.onPause();

		//save the current map view settings
		final SharedPreferences.Editor edit = mPrefs.edit();
		edit.putString(PREFS_TILE_SOURCE, mapView.getTileProvider().getTileSource().name());
		edit.putFloat(PREFS_ORIENTATION, mapView.getMapOrientation());
		edit.putString(PREFS_LATITUDE_STRING, String.valueOf(mapView.getMapCenter().getLatitude()));
		edit.putString(PREFS_LONGITUDE_STRING, String.valueOf(mapView.getMapCenter().getLongitude()));
		edit.putFloat(PREFS_ZOOM_LEVEL_DOUBLE, (float) mapView.getZoomLevelDouble());
		edit.commit();
		mapView.onPause();
	}

	/*
	 * UI
	 */
	private void setInfo() {
		@SuppressLint("DefaultLocale") String deviceInfo = "Device: " + userName + " Loc: lat " + String.format("%.3f long ", latDouble) + String.format("%.3f", longDouble);
		nodeInfo.setText(deviceInfo);
	}

	@SuppressLint("DefaultLocale")
	@Override
	public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		// For the map
		Configuration.getInstance().load(thisContext, PreferenceManager.getDefaultSharedPreferences(thisContext));

		fragmentView = inflater.inflate(R.layout.fragment_terminal, container, false);

		sendText = fragmentView.findViewById(R.id.send_text);
		nodeInfo = fragmentView.findViewById(R.id.node_info);
		nodeInfo = fragmentView.findViewById(R.id.node_info);

		// Create message view
		messagesView = fragmentView.findViewById(R.id.messages_view);
		messagesView.setAdapter(messageAdapter);

		// Create nodes list
		nodesView = fragmentView.findViewById(R.id.nodes_view);
		nodesView.setAdapter(nodesAdapter);
		nodesView.setOnItemClickListener((parent, view, position, id) -> {
			// TODO map nodesID's and usernames somehow
			//Nodes selectedNode = nodesAdapter.getItem(position);
			//sendText.append("@" + selectedNode.get() + " ");
		});

		mapLayout = fragmentView.findViewById(R.id.map_view);
		chatLayout = fragmentView.findViewById(R.id.chat_view);

		View sendBtn = fragmentView.findViewById(R.id.send_btn);
		sendBtn.setOnClickListener(v -> {
			send(sendText.getText().toString(), "c");
			sendText.getText().clear();
		});

		View sendMapBtn = fragmentView.findViewById(R.id.send_map_btn);
		sendMapBtn.setOnClickListener(v -> {
			if (connected != Connected.True) {
				showToast(getString(R.string.chat_disconnected), true);
				return;
			}
			sendCoords();
		});

		View showMapBtn = fragmentView.findViewById(R.id.show_map_btn);
		showMapBtn.setOnClickListener(v -> {
			if ((!meEntry.isCoordValid()) || (mapView.getOverlays().isEmpty())) {
				chatLayout.setVisibility(View.GONE);
				mapLayout.setVisibility(View.VISIBLE);
				mapView.getController().setCenter(new GeoPoint(14.4726767, 121.0011358));
				mapView.getController().setZoom(defaultZoom);
			} else {
				chatLayout.setVisibility(View.GONE);
				mapLayout.setVisibility(View.VISIBLE);
				Handler handler = new Handler();
				final Runnable stopAlarm = () -> {
					BoundingBox newBox = createBoundingBox();
					if (newBox != null) {
						mapView.zoomToBoundingBox(newBox.increaseByScale(1.3f), true);
					} else {
						mapView.getController().setCenter(meEntry.getCoords());
						mapView.getController().setZoom(defaultZoom);
					}
					mapView.invalidate();
				};
				handler.postDelayed(stopAlarm, 100);
				mapView.invalidate();
			}
		});
		View showChatBtn = fragmentView.findViewById(R.id.show_chat_btn);
		showChatBtn.setOnClickListener(v -> {
			chatLayout.setVisibility(View.VISIBLE);
			mapLayout.setVisibility(View.INVISIBLE);
		});

		return fragmentView;
	}


	@Override
	public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
		super.onViewCreated(view, savedInstanceState);

		mapView = fragmentView.findViewById(R.id.map);

		// Setup the map
		setupMap();

		// Register the receiver for location changes
		final IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(LOCATION_UPDATE);
		LocalBroadcastManager.getInstance(thisContext).registerReceiver(MyLocationUpdate, intentFilter);
	}

	@Override
	public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {
		thisMenu = menu;
		inflater.inflate(R.menu.menu_terminal, menu);
	}

	@SuppressLint("ApplySharedPref")
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		int id = item.getItemId();
		switch (id) {
			case R.id.clear:
				messageAdapter.clear();
				return true;
			case R.id.user_name:
				androidx.appcompat.app.AlertDialog.Builder userAlert = new androidx.appcompat.app.AlertDialog.Builder(thisContext);

				userAlert.setTitle(getString(R.string.chat_settings));
				userAlert.setMessage(getString(R.string.settings_username));

				// Set an EditText view to get user input
				final EditText input = new EditText(thisContext);
				if (userName != null) {
					input.setText(userName);
				}
				input.setHint(getString(R.string.chat_username_hint));
				userAlert.setView(input);

				userAlert.setPositiveButton(getString(android.R.string.ok), (dialog, whichButton) -> {
					userName = input.getText().toString();
					// Save the username
					mPrefs.edit().putString("userName", userName).commit();
					send(userName, "u");
				});

				userAlert.setNegativeButton(getString(R.string.string_delete), (dialog, whichButton) -> {
					mPrefs.edit().remove("userName").commit();
					userName = null;
					send("", "u");
				});

				userAlert.setNeutralButton(getString(android.R.string.cancel), (dialog, whichButton) -> {
					// Canceled.
				});
				userAlert.show();
				return true;
			case R.id.ui_switch:
				send("Switching UI", "i");
				connected = Connected.False;
				service.disconnect();
				socket.disconnect();
				socket = null;
				Fragment fragment = new DevicesFragment();
				Objects.requireNonNull(getFragmentManager()).beginTransaction().replace(R.id.fragment, fragment, "devices").addToBackStack(null).commit();
				return true;
			case R.id.chat_about:
				androidx.appcompat.app.AlertDialog.Builder aboutAlert = new androidx.appcompat.app.AlertDialog.Builder(thisContext);

				aboutAlert.setTitle(getString(R.string.menu_about));
				aboutAlert.setMessage(getString(R.string.about_text));

				aboutAlert.setPositiveButton(getString(android.R.string.ok), (dialog, whichButton) -> {
					// Canceled.
				});

				AlertDialog aboutDialog = aboutAlert.create();
				aboutDialog.show();

				TextView alertTextView = aboutDialog.findViewById(android.R.id.message);
				assert alertTextView != null;
				Linkify.addLinks(alertTextView, Linkify.WEB_URLS);

				return true;
			case R.id.reconnect:
				connect();
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}

	private static void showToast(String errorMsg, boolean isError) {
		Toast toast;
		toast = Toast.makeText(appContext, errorMsg, Toast.LENGTH_SHORT);
		toast.setGravity(Gravity.CENTER, 0, 0);
		ViewGroup group = (ViewGroup) toast.getView();
		TextView v = (TextView) group.getChildAt(0);
		v.setTextSize(25);
		if (isError) {
			v.setTextColor(Color.WHITE);
			v.setBackgroundColor(Color.RED);
		} else {
			v.setTextColor(Color.BLACK);
			v.setBackgroundColor(Color.GREEN);
		}
		v.setGravity(Gravity.CENTER);
		toast.setDuration(Toast.LENGTH_LONG);
		toast.show();
	}

	/*
	 * Map stuff
	 */
	private void setupMap() {
		// Check if offline maps are available
		File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/osmdroid/");
		boolean foundMap = false;
		if (f.exists()) {

			File[] list = f.listFiles();
			if (list != null) {
				for (File file : list) {
					if (file.isDirectory()) {
						continue;
					}
					String name = file.getName().toLowerCase();
					if (!name.contains(".")) {
						continue; //skip files without an extension
					}
					name = name.substring(name.lastIndexOf(".") + 1);
					if (name.length() == 0) {
						continue;
					}
					if (ArchiveFileFactory.isFileExtensionRegistered(name)) {
						try {

							//ok found a file we support and have a driver for the format, for this demo, we'll just use the first one

							//create the offline tile provider, it will only do offline file archives
							//again using the first file
							OfflineTileProvider tileProvider = new OfflineTileProvider(new SimpleRegisterReceiver(getActivity()),
									new File[]{file});

							//tell osmdroid to use that provider instead of the default rig which is (asserts, cache, files/archives, online
							mapView.setTileProvider(tileProvider);

							//this bit enables us to find out what tiles sources are available. note, that this action may take some time to run
							//and should be ran asynchronously. we've put it inline for simplicity

							String source = "MAPNIK";

							IArchiveFile[] archives = tileProvider.getArchives();
							if (archives.length > 0) {
								//cheating a bit here, get the first archive file and ask for the tile sources names it contains
								Set<String> tileSources = archives[0].getTileSources();
								//presumably, this would be a great place to tell your users which tiles sources are available
								if (!tileSources.isEmpty()) {
									//ok good, we found at least one tile source, create a basic file based tile source using that name
									//and set it. If we don't set it, osmdroid will attempt to use the default source, which is "MAPNIK",
									//which probably won't match your offline tile source, unless it's MAPNIK
									source = tileSources.iterator().next();
									mapView.setTileSource(FileBasedTileSource.getSource(source));
									mapView.invalidate();
									foundMap = true;
								} else {
									mapView.setTileSource(TileSourceFactory.MAPNIK);
									mapView.invalidate();
								}
							} else {
								mapView.setTileSource(TileSourceFactory.MAPNIK);
								mapView.invalidate();
							}
//							showToast("Using " + file.getAbsolutePath() + " " + source, false);
							Snackbar snackbar = Snackbar
									.make(fragmentView, "Using " + file.getAbsolutePath() + " " + source, Snackbar.LENGTH_SHORT);
							snackbar.show();
							mapView.setUseDataConnection(false);
							mapView.invalidate();
						} catch (Exception ex) {
							ex.printStackTrace();
						}
					}
				}
			}
		}

		if (!foundMap) {
//			showToast(f.getAbsolutePath() + " did not find any map files, using online MAPNIK", true);
			Snackbar snackbar = Snackbar
					.make(fragmentView, f.getAbsolutePath() + " did not find any map files, using online MAPNIK", Snackbar.LENGTH_SHORT);
			snackbar.show();
			mapView.setUseDataConnection(true);
			mapView.setTileSource(TileSourceFactory.MAPNIK);
			mapView.invalidate();
		}

		// Initialize the map parameters

		//scales tiles to the current screen's DPI, helps with readability of labels
		mapView.setTilesScaledToDpi(true);

		//the rest of this is restoring the last map location the user looked at
		float zoomLevel = mPrefs.getFloat(PREFS_ZOOM_LEVEL_DOUBLE, 5.0f);
		if (zoomLevel < 15.0f) {
			zoomLevel = 15.0f;
		}

		mapView.setMaxZoomLevel(16.0);
		mapView.setMinZoomLevel(0.0);
		mapView.getController().setZoom(zoomLevel);
		final float orientation = mPrefs.getFloat(PREFS_ORIENTATION, 0);
		mapView.setMapOrientation(orientation, false);
		final String latitudeString = mPrefs.getString(PREFS_LATITUDE_STRING, "14.0");
		final String longitudeString = mPrefs.getString(PREFS_LONGITUDE_STRING, "121.0");
		double latitude = 0.0;
		if (latitudeString != null) {
			latitude = Double.parseDouble(latitudeString);
		}
		double longitude = 0.0;
		if (longitudeString != null) {
			longitude = Double.parseDouble(longitudeString);
		}
		mapView.getController().setCenter(new GeoPoint(latitude, longitude));

		// Add yourself to the member data
		meEntry = new MemberData(getContext(), "me", "#B2EBF2");
		memberAdapter.add(meEntry);
		if ((userName != null) && (!userName.equalsIgnoreCase("me"))) {
			meEntry.setData(userName, "#B2EBF2");
		}
		if ((latDouble != 0.0) && (longDouble != 0.0)) {
			Marker memberMarker = meEntry.setCoords(new GeoPoint(latDouble, longDouble));
			mapView.getOverlays().add(memberMarker);
			BoundingBox newBox = createBoundingBox();
			if (newBox != null) {
				mapView.zoomToBoundingBox(newBox.increaseByScale(1.3f), true);
			} else {
				mapView.getController().setCenter(new GeoPoint(latDouble, longDouble));
				mapView.getController().setZoom(defaultZoom);
			}
		}
		mapView.invalidate();

		// Add a member for messages that do not contain a member name. They will be shown as "emy_chat"
		noUserName = new MemberData(getContext(), getString(R.string.default_rcvd_name), "#F06262");
		memberAdapter.add(noUserName);
	}

	private BoundingBox createBoundingBox() {
		List<GeoPoint> memberMapCoords = new ArrayList<>();
		for (int idx = 0; idx < memberAdapter.getCount(); idx++) {
			if (memberAdapter.getItem(idx).isCoordValid()) {
				memberMapCoords.add(memberAdapter.getItem(idx).getCoords());
			}
		}
		if (memberMapCoords.size() <= 1) {
			return null;
		}

		return BoundingBox.fromGeoPointsSafe(memberMapCoords);
	}

	@SuppressLint("DefaultLocale")
	private void sendCoords() {
		if (meEntry.isCoordValid()) {
			// TODO how does final location message look like?
			// 04m|<user>{"pos":[48.75608,2.302038]}
			String currentPos;
			if (userName != null) {
				currentPos = "{\"pos\":[" +
						String.format("%.6f", latDouble) + "," +
						String.format("%.6f", longDouble) + "]}";
				send(currentPos, "m");
			}
		}
	}

	private final BroadcastReceiver MyLocationUpdate = new BroadcastReceiver() {
		@SuppressLint("SimpleDateFormat")
		@Override
		public void onReceive(final Context context, final Intent intent) {
			final String action = intent.getAction();
			if (LOCATION_UPDATE.equals(action)) {
				if ((intent.hasExtra("lat")) && (intent.hasExtra("long"))) {
					double newLat = intent.getDoubleExtra("lat", 0.0);
					double newLong = intent.getDoubleExtra("long", 0.0);
					if ((newLat != 0.0) && (newLong != 0.0)) {
						meEntry.setCoords(new GeoPoint(newLat, newLong));
						if (mapView.getOverlays().indexOf(meEntry.getMarker()) == -1) {
							mapView.getOverlays().add(meEntry.getMarker());
						} else {
							mapView.getOverlays().remove(meEntry.getMarker());
							mapView.getOverlays().add(meEntry.getMarker());
						}
						BoundingBox newBox = createBoundingBox();
						if (newBox != null) {
							mapView.zoomToBoundingBox(newBox.increaseByScale(1.3f), true);
						} else {
							mapView.getController().setCenter(meEntry.getCoords());
							mapView.getController().setZoom(defaultZoom);
						}
						mapView.invalidate();
						if (intent.hasExtra("hasChanged")) {
							sendCoords();
						}
						setInfo();
					}
				}
			}
		}
	};

	/*
	 * Serial + UI
	 */
	private void connect() {
		try {
			BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
			BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
			String deviceName = device.getName() != null ? device.getName() : device.getAddress();
			connected = Connected.Pending;
			socket = new SerialSocket();
			service.connect(this, "Connected to " + deviceName);
			socket.connect(thisContext, service, device);
		} catch (Exception e) {
			onSerialConnectError(e);
		}
	}

	private void disconnect() {
		connected = Connected.False;
		service.disconnect();
		socket.disconnect();
		socket = null;
	}

	private void send(String str, String type) {
		if (connected != Connected.True) {
			showToast(getString(R.string.chat_disconnected), true);
			return;
		}
		String toBeSent = str;
		try {
			if ((toBeSent.length() == 0) && (type.equalsIgnoreCase("u"))) {
				// Send delete username command
				toBeSent = "00u|~";
				byte[] data = toBeSent.getBytes();
				socket.write(data);
				return;
			}
			if (toBeSent.length() > 0) {
				final Message message = new Message(toBeSent, meEntry, true);
				if ((!toBeSent.startsWith("~")) && (type.equalsIgnoreCase("c"))) {
					messageAdapter.add(message);
					// scroll the ListView to the last added element
					messagesView.setSelection(messagesView.getCount() - 1);
				}
				if ((userName != null) && !type.equalsIgnoreCase("u")) {
					toBeSent = "00" + type + "|<" + userName + ">" + toBeSent;
				} else {
					toBeSent = "00" + type + "|" + toBeSent;
				}

				byte[] data = toBeSent.getBytes();
				socket.write(data);
			}
		} catch (Exception e) {
			onSerialIoError(e);
		}
	}

	@SuppressLint("DefaultLocale")
	@SuppressWarnings("StringConcatenationInLoop")
	private void receive(byte[] rcvdBytes) {
		if (rcvdBytes.length < 2) {
			Log.e(TAG, "Received data with length < 2");
			return;
		}
		if (rcvdBytes[2] == 'c') {
			// We got a chat message
			String rcvd = new String(rcvdBytes);
			rcvd = rcvd.substring(4);
			String sender;
			MemberData thisUser = noUserName;
			boolean myHistory = false;
			if (rcvd.substring(0, 1).equalsIgnoreCase("<")) {
				// Message has a sender name
				int lastIndex = rcvd.indexOf(">");
				if (lastIndex > 1) {
					sender = rcvd.substring(1, lastIndex);
					rcvd = rcvd.substring(lastIndex + 1);

					Log.d(TAG, "Received chat message from " + sender);
					if (sender.equalsIgnoreCase(userName)) {
						myHistory = true;
						thisUser = meEntry;
					} else {
						// Check if we know the member already
						int memberId = memberAdapter.hasMember(sender);
						if (memberId != -1) {
							thisUser = memberAdapter.getItem(memberId);
						} else {
							thisUser = new MemberData(getContext(), sender, memberAdapter.getRandomColor());
							memberAdapter.add(thisUser);
						}
						if (BuildConfig.DEBUG) {
							// TODO for map testing only
							if ((sender.equalsIgnoreCase("Display") || (sender.equalsIgnoreCase("WiFi")))
									&& !thisUser.isCoordValid()) {

								GeoPoint wifiLatLong;
								if (sender.equalsIgnoreCase("WiFi")) {
									wifiLatLong = new GeoPoint(14.483, 120.993); // SM Sucat
								} else {
									wifiLatLong = new GeoPoint(14.515, 121.016); // Airport
								}
								thisUser.setCoords(wifiLatLong);
								mapView.getOverlays().add(thisUser.getMarker());
								BoundingBox newBox = createBoundingBox();
								if (newBox != null) {
									mapView.zoomToBoundingBox(newBox.increaseByScale(1.3f), true);
								} else {
									mapView.getController().setCenter(wifiLatLong);
									mapView.getController().setZoom(defaultZoom);
								}
								mapView.invalidate();
							}
						}
					}
				}
			} else if (rcvd.substring(0, 1).equalsIgnoreCase("~")) {
				// TODO user joined message, how to handle it
				showToast(rcvd.substring(1), false);
				return;
			}
			final Message message = new Message(rcvd, thisUser, myHistory);
			messageAdapter.add(message);
			// scroll the ListView to the last added element
			messagesView.setSelection(messagesView.getCount() - 1);

			if (userName != null) {
				String mention = "@" + userName;
				if (rcvd.contains(mention)) {
					// TODO make notification tone user selectable
					try {
						Uri path = Uri.parse("android.resource://" + thisContext.getPackageName() + "/raw/dingdong.mp3");
						RingtoneManager.setActualDefaultRingtoneUri(
								thisContext, RingtoneManager.TYPE_RINGTONE, path
						);
						Ringtone r = RingtoneManager.getRingtone(thisContext, path);
						r.play();
//						Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
//						Ringtone r = RingtoneManager.getRingtone(thisContext, notification);
//						r.play();
					} catch (Exception exp) {
						exp.printStackTrace();
					}
				}
			}
		} else if (rcvdBytes[2] == 'u') {
			String newUserName = new String(rcvdBytes);
			userName = newUserName.substring(4);
			Log.d(TAG, "Got username " + newUserName);
//			if (userName != null) {
				send("~" + userName + " " + getString(R.string.chat_joined), "c");
//				Handler handler = new Handler();
//				final Runnable sendUserName = () -> send(userName, "u");
//				handler.postDelayed(sendUserName, 500);
//			}
		} else if (rcvdBytes[2] == 'm') {
			// we got coordinates of a user
			// TODO how does final location message look like?
			// 04m|<user>{"pos":[48.75608,2.302038]}
			String rcvd = new String(rcvdBytes);
			// We got a map message
			rcvd = rcvd.substring(4);

			Log.d(TAG, "Got location msg: " + rcvd);
			String mapSender;
			if (rcvd.substring(0, 1).equalsIgnoreCase("<")) {
				// Message has a sender name
				int lastIndex = rcvd.indexOf(">");
				if (lastIndex > 1) {
					mapSender = rcvd.substring(1, lastIndex);
					rcvd = rcvd.substring(lastIndex + 1);
					JSONObject jObject;
					try {
						jObject = new JSONObject(rcvd);
						JSONArray posData = jObject.getJSONArray("pos");
						double gotLat = posData.getDouble(0);
						double gotLong = posData.getDouble(1);
						MemberData mapSenderData;

						int senderMemberId = memberAdapter.hasMember(mapSender);
						if (senderMemberId == -1) {
							mapSenderData = new MemberData(getContext(), mapSender, memberAdapter.getRandomColor());
							memberAdapter.add(mapSenderData);
						} else {
							mapSenderData = memberAdapter.getItem(senderMemberId);
						}
						GeoPoint userLatLong = new GeoPoint(gotLat, gotLong);
						mapSenderData.setCoords(userLatLong);
						if (mapView.getOverlays().indexOf(mapSenderData.getMarker()) == -1) {
							mapView.getOverlays().add(mapSenderData.getMarker());
						} else {
							mapView.getOverlays().remove(mapSenderData.getMarker());
							mapView.getOverlays().add(mapSenderData.getMarker());
						}

						BoundingBox newBox = createBoundingBox();
						if (newBox != null) {
							mapView.zoomToBoundingBox(newBox.increaseByScale(1.3f), true);
						} else {
							mapView.getController().setCenter(mapSenderData.getCoords());
							mapView.getController().setZoom(defaultZoom);
						}
						mapView.invalidate();

					} catch (JSONException e) {
						e.printStackTrace();
					}
				} else {
					Log.e(TAG, "Missing name in the map data");
				}
			}
		} else if (rcvdBytes[2] == 'r') {
			// We got a routing table, first clear old content
			nodesAdapter.clear();

			int idx = 4;
			int nodeNum = 0;
			int dataLen = rcvdBytes.length - 4;
			dataLen = (dataLen / 8) * 8;

			String nodeIdAsString = "";
			while (idx < dataLen) {
				for (int nodeIdx = 0; nodeIdx < 6; nodeIdx++) {
					nodeIdAsString += String.format("%02X", rcvdBytes[(nodeNum * 8) + 4 + nodeIdx]);
				}
				nodeNum++;
				idx += 8;
				int nodesIndex = nodesAdapter.hasNode(nodeIdAsString);
				if (nodesIndex == -1) {
					nodesAdapter.add(new Nodes(nodeIdAsString));
					nodesAdapter.notifyDataSetChanged();
				}
				nodeIdAsString = "";
			}
			setInfo();
		} else {
			int size = rcvdBytes.length;
			Log.d(TAG, "Received binary data with length " + size);

			String resultString = "";
			for (byte output : rcvdBytes) {
				resultString += String.format("%02X ", output);
			}
			Log.d(TAG, resultString);
		}
	}

	/*
	 * SerialListener
	 */
	@Override
	public void onSerialConnect() {
		showToast(getString(R.string.chat_connected), false);
		connected = Connected.True;
//		if (userName != null) {
//			send("~" + userName + " " + getString(R.string.chat_joined), "c");
//			Handler handler = new Handler();
//			final Runnable sendUserName = () -> send(userName, "u");
//			handler.postDelayed(sendUserName, 500);
//		}
		thisMenu.findItem(R.id.reconnect).setVisible(false);
	}

	@Override
	public void onSerialConnectError(Exception e) {
		showToast(getString(R.string.chat_connection_failed), true);
		disconnect();
		thisMenu.findItem(R.id.reconnect).setVisible(true);
	}

	@Override
	public void onSerialRead(byte[] data) {
		receive(data);
	}

	@Override
	public void onSerialIoError(Exception e) {
		// TODO make alarm user selectable
		try {
			Uri path = Uri.parse("android.resource://" + thisContext.getPackageName() + "/raw/signal.mp3");
			RingtoneManager.setActualDefaultRingtoneUri(
					thisContext, RingtoneManager.TYPE_RINGTONE, path
			);
			Ringtone r = RingtoneManager.getRingtone(thisContext, path);
			r.play();
//			Uri notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
//			Ringtone alarmTone = RingtoneManager.getRingtone(thisContext, notification);
//			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//				alarmTone.setLooping(false);
//			} else {
//				Handler handler = new Handler();
//				final Runnable stopAlarm = alarmTone::stop;
//				handler.postDelayed(stopAlarm, 3000);
//			}
//			alarmTone.play();
		} catch (Exception exp) {
			exp.printStackTrace();
		}
		showToast(getString(R.string.chat_connection_error), true);
		disconnect();
		thisMenu.findItem(R.id.reconnect).setVisible(true);
	}
}
