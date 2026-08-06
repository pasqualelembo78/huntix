package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.Button;
import android.widget.Adapter;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.MyApp;
import com.intelligame.huntix.legacy.View.AdapterBestiario;
import com.intelligame.huntix.legacy.View.AdapterScambio;

import static com.intelligame.huntix.legacy.Controller.ProfiloActivity.PERFIL_SCAMBIO;

public class ScambioListaUtentiActivity extends Activity implements AdapterView.OnItemClickListener{

    static final int REQUEST_ENABLE_BT = 1;
    static final int REQUEST_BT_PERM = 2;
    protected static final String NAME = "SERVIDOR_SCAMBI";
    protected static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    List<BluetoothDevice> bluetoothDevices = null;

    protected AcceptThread acceptThread;
    protected ConnectThread connectThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scambio_lista_utenti);

        // Register è broadcasts when a device is discovered.
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        registerReceiver(receiver, filter);

         if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        //Inizializza la lista
        bluetoothDevices = new ArrayList<BluetoothDevice>();

        //avvia il server (serve il permesso BLUETOOTH_CONNECT su Android 12+)
        if (android.os.Build.VERSION.SDK_INT >= 31 &&
                (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                 ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN},
                    REQUEST_BT_PERM);
        } else {
            avviaServer();
        }
    }

    protected void avviaServer(){
        try {
            acceptThread = new AcceptThread();
            acceptThread.start();
        } catch (Exception e) {
            Log.e("SCAMBIO", "Impossibile avviare il server bluetooth: " + e.getMessage());
            Toast.makeText(this, "Bluetooth non disponibile per la scambio", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_BT_PERM) {
            avviaServer();
        }
    }

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {

                // Discovery has found a device. Get the BluetoothDevice
                // object and its info from the Intent.
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);

                //Aggiunge il dispositivo alla lista
                if(device.getName() != null)
                bluetoothDevices.add(device);

            }
            Button btn = (Button) findViewById(R.id.cerca);
            if (BluetoothAdapter.ACTION_DISCOVERY_STARTED.equals(action)) {
                //All'inizio della ricerca, avvisa l'utente per il periodo di attesa
                int duration = Toast.LENGTH_LONG;
                Toast toast = Toast.makeText(context, "Attendi...", duration);
                toast.show();
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {
                //Abilita di nuovo il pulsante e ripristina il colore originale
                btn.setEnabled(true);
                btn.setTextColor(Color.parseColor("#000000"));
                elencaUtenti();
            }
        }
    };

    public void aggiornaBluetooth(View v) {

        Button btn = (Button) findViewById(R.id.cerca);
        btn.setTextColor(Color.parseColor("#808080"));
        btn.setEnabled(false);

        bluetoothDevices.clear();

        //Filtro di controllo per il metodo asincrono startDiscovery
        IntentFilter filter = new IntentFilter();

        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

        registerReceiver(receiver, filter);
        try {
            bluetoothAdapter.startDiscovery();
        } catch (SecurityException e) {
            Log.e("SCAMBIO", "Permesso bluetooth mancante per startDiscovery", e);
            Toast.makeText(this, "Permesso bluetooth non concesso", Toast.LENGTH_LONG).show();
        }
    }

    public void elencaUtenti() {

        try {

            //Prepara la ListView personalizzata dello scambio di utenti
            ListView listView = (ListView) findViewById(R.id.bluetooth_user_list);
            AdapterScambio adapterScambio = new AdapterScambio(bluetoothDevices, this);

            listView.setAdapter(adapterScambio);
            listView.setOnItemClickListener(this);

        }catch (Exception e){
            Log.e("SCAMBIO", "ERRORE NEL ELENCARE: " + e.getMessage());
        }

    }

    public void clickIndietro(View v){
        finish();
    }

    @Override
    protected void onDestroy() {

        // Don't forget to unregister the ACTION_FOUND receiver.
        unregisterReceiver(receiver);

        if(acceptThread != null)
            acceptThread.cancel();

        super.onDestroy();
    }

    @Override
    public void onItemClick(AdapterView<?> adapterView, View view, int idx, long id) {
        //Recupera il device selezionato
        BluetoothDevice device = bluetoothDevices.get(idx);

        //Chiude la connessione precedente
        if(connectThread != null)
            connectThread.cancel();

        //Avvia una nuova
        connectThread = new ConnectThread(device);
        connectThread.start();

    }

    private class AcceptThread extends Thread {
        private final BluetoothServerSocket mmServerSocket;

        public AcceptThread() {
            // Use a temporary object that is later assigned to mmServerSocket
            // because mmServerSocket is final.
            BluetoothServerSocket tmp = null;
            try {
                // MY_UUID is the app's UUID string, also used by the client code.
                tmp = bluetoothAdapter.listenUsingRfcommWithServiceRecord(NAME, uuid);
            } catch (IOException e) {
                Log.e("Socket Fail", "Socket's listen() method failed", e);
            }
            mmServerSocket = tmp;
        }

        public void run() {
            BluetoothSocket socket = null;
            // Keep listening until exception occurs or a socket is returned.

            Log.e("SCAMBIO", "Running Server");

            while (true) {
                try {
                    socket = mmServerSocket.accept();

                    if (socket != null) {
                        // A connection was accepted. Perform work associated with
                        // the connection in a separate thread.
                        manageMyConnectedSocket(socket);
                        mmServerSocket.close();
                        break;
                    }
                } catch (IOException e) {
                    Log.e("SCAMBIO", "Socket's accept() method failed", e);
                    break;
                }
            }
        }

        // Closes the connect socket and causes the thread to finish.
        public void cancel() {

            Log.e("SCAMBIO", "Closing Server");

            try {
                mmServerSocket.close();
            } catch (IOException e) {
                Log.e("SCAMBIO", "Could not close the connect socket", e);
            }
        }
    }

    private class ConnectThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final BluetoothDevice mmDevice;

        public ConnectThread(BluetoothDevice device) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            mmDevice = device;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                tmp = device.createRfcommSocketToServiceRecord(uuid);
            } catch (IOException e) {
                Log.e("SCAMBIO", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            bluetoothAdapter.cancelDiscovery();

            Log.e("SCAMBIO", "Running Client");

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("SCAMBIO", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.
            manageMyConnectedSocket(mmSocket);
        }

        // Closes the client socket and causes the thread to finish.
        public void cancel() {

            Log.e("Scambio", "Closing Client");

            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e("SCAMBIO", "Could not close the client socket", e);
            }
        }
    }

    public void manageMyConnectedSocket(BluetoothSocket socket){

        Log.e("SCAMBIO", "Connessione stabilita");

        if(acceptThread != null)
            acceptThread.cancel();

        MyApp.setBluetoothSocket(socket);

        Intent it = new Intent(this, ScambioListaCreaturaActivity.class);
        startActivity(it);
    }
}
