package tk.giesecke.DisasterRadio;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.IBinder;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Objects;

import tk.giesecke.DisasterRadio.msg.Message;
import tk.giesecke.DisasterRadio.msg.MessageAdapter;

import static tk.giesecke.DisasterRadio.MainActivity.appContext;
import static tk.giesecke.DisasterRadio.MainActivity.mPrefs;
import static tk.giesecke.DisasterRadio.MainActivity.userName;

public class TerminalFragment extends Fragment implements ServiceConnection, SerialListener {

    private static final String TAG = "TerminalFragment";

    private enum Connected { False, Pending, True }

    private String deviceAddress;

    private SerialSocket socket;
    private SerialService service;
    private boolean initialStart = true;
    private Connected connected = Connected.False;

    private MessageAdapter messageAdapter;
    private ListView messagesView;

    private EditText sendText;

    private Menu thisMenu;

    /*
     * Lifecycle
     */
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        setRetainInstance(true);
        deviceAddress = Objects.requireNonNull(getArguments()).getString("device");
    }

    @Override
    public void onDestroy() {

        if (connected != Connected.False) {
            if (userName != null) {
                send("~" + userName + " " + getString(R.string.chat_leave));
            }
            disconnect();
        }
        Objects.requireNonNull(getActivity()).stopService(new Intent(getActivity(), SerialService.class));
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        if(service != null)
            service.attach(this);
        else
            Objects.requireNonNull(getActivity()).startService(new Intent(getActivity(), SerialService.class)); // prevents service destroy on unbind from recreated activity caused by orientation change
    }

    @Override
    public void onStop() {
        if(service != null && !Objects.requireNonNull(getActivity()).isChangingConfigurations())
            service.detach();
        super.onStop();
    }

    @SuppressWarnings("deprecation") // onAttach(context) was added with API 23. onAttach(activity) works for all API versions
    @Override
    public void onAttach(@NonNull Activity activity) {
        super.onAttach(activity);
        Objects.requireNonNull(getActivity()).bindService(new Intent(getActivity(), SerialService.class), this, Context.BIND_AUTO_CREATE);
    }

    @Override
    public void onDetach() {
        try { Objects.requireNonNull(getActivity()).unbindService(this); } catch(Exception ignored) {}
        super.onDetach();
    }

    @Override
    public void onResume() {
        super.onResume();
        if(initialStart && service !=null) {
            initialStart = false;
            Objects.requireNonNull(getActivity()).runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceConnected(ComponentName name, IBinder binder) {
        service = ((SerialService.SerialBinder) binder).getService();
        if(initialStart && isResumed()) {
            initialStart = false;
            Objects.requireNonNull(getActivity()).runOnUiThread(this::connect);
        }
    }

    @Override
    public void onServiceDisconnected(ComponentName name) {
        service = null;
    }

    /*
     * UI
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_terminal, container, false);
        sendText = view.findViewById(R.id.send_text);
        View sendBtn = view.findViewById(R.id.send_btn);
        sendBtn.setOnClickListener(v -> send(sendText.getText().toString()));
        messageAdapter = new MessageAdapter(getContext());
        messagesView = view.findViewById(R.id.messages_view);
        messagesView.setAdapter(messageAdapter);

        return view;
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
            case R.id.setup:
                androidx.appcompat.app.AlertDialog.Builder alert = new androidx.appcompat.app.AlertDialog.Builder(Objects.requireNonNull(getContext()));

                alert.setTitle(getString(R.string.chat_settings));
                alert.setMessage(getString(R.string.settings_username_title));

                // Set an EditText view to get user input
                final EditText input = new EditText(getContext());
                if (userName != null) {
                    input.setText(userName);
                }
                input.setHint(getString(R.string.chat_username_hint));
                alert.setView(input);

                alert.setPositiveButton(getString(android.R.string.ok), (dialog, whichButton) -> {
                    userName = input.getText().toString();
                    // TODO save the username
                    mPrefs.edit().putString("userName", userName).commit();
                });

                alert.setNegativeButton(getString(R.string.string_delete), (dialog, whichButton) -> {
                    mPrefs.edit().remove("userName").commit();
                    userName = null;
                });

                alert.setNeutralButton(getString(android.R.string.cancel), (dialog, whichButton) -> {
                    // Canceled.
                });
                alert.show();
                return true;
            case R.id.chat_about:
                androidx.appcompat.app.AlertDialog.Builder aboutAlert = new androidx.appcompat.app.AlertDialog.Builder(Objects.requireNonNull(getContext()));

                aboutAlert.setTitle(getString(R.string.menu_about));
                aboutAlert.setMessage(getString(R.string.about_text));

                aboutAlert.setPositiveButton(getString(android.R.string.ok), (dialog, whichButton) -> {
                    // Canceled.
                });
                aboutAlert.show();
                return true;
            case R.id.reconnect:
                connect();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

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
            socket.connect(getContext(), service, device);
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

    private void send(String str) {
        if(connected != Connected.True) {
            showToast(getString(R.string.chat_disconnected), true);
            return;
        }
        String toBeSent = str;
        try {
            if (toBeSent.length() > 0) {
                final Message message = new Message(toBeSent, "Me", true);
                if (!toBeSent.startsWith("~")) {
                    messageAdapter.add(message);
                    // scroll the ListView to the last added element
                    messagesView.setSelection(messagesView.getCount() - 1);
                }
                if (userName != null) {
                    toBeSent = "00c|<" + userName + ">" + toBeSent;
                } else {
                    toBeSent = "00c|" + toBeSent;
                }

                byte[] data = toBeSent.getBytes();
                socket.write(data);
                sendText.getText().clear();
            }
        } catch (Exception e) {
            onSerialIoError(e);
        }
    }

    @SuppressWarnings("StringConcatenationInLoop")
    private void receive(byte[] rcvdBytes) {

        if (rcvdBytes[2] == 'c') {
            String rcvd = new String(rcvdBytes);
            // We got a chat message
            rcvd = rcvd.substring(4);
            String sender = getString(R.string.default_rcvd_name);
            if (rcvd.substring(0, 1).equalsIgnoreCase("<")) {
                // Message has a sender name
                int lastIndex = rcvd.indexOf(">");
                if (lastIndex > 1) {
                    sender = rcvd.substring(1, lastIndex);
                    rcvd = rcvd.substring(lastIndex + 1);
                }
            }
            final Message message = new Message(rcvd, sender, false);
            messageAdapter.add(message);
            // scroll the ListView to the last added element
            messagesView.setSelection(messagesView.getCount() - 1);
            Log.d(TAG, "Received: " + rcvd);
        } else {
            // We got a routing table
            int size = rcvdBytes.length;
            Log.d(TAG, "Received binary data with length " + size);

            String resultString = "";
            for (byte output : rcvdBytes) {
                resultString += String.format("%02X ", output);
            }
            Log.d(TAG, resultString);
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
     * SerialListener
     */
    @Override
    public void onSerialConnect() {
        showToast(getString(R.string.chat_connected), false);
        connected = Connected.True;
        if (userName != null) {
            send("~" + userName + " " + getString(R.string.chat_joined));
        }
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
        showToast(getString(R.string.chat_connection_error), true);
        disconnect();
        thisMenu.findItem(R.id.reconnect).setVisible(true);
    }

}
