package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.location.Criteria;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.Image;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

import com.intelligame.huntix.legacy.Model.Apparizione;
import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.Model.Creatura;
import com.intelligame.huntix.legacy.Model.CreaturaCatturata;
import com.intelligame.huntix.legacy.R;
import com.intelligame.huntix.legacy.Util.DatabaseSingleton;
import com.intelligame.huntix.legacy.Util.MyApp;
import com.intelligame.huntix.legacy.View.AdapterBestiario;
import com.intelligame.huntix.legacy.View.AdapterScambioCreatureList;

public class ScambioListaCreaturaActivity extends Activity implements AdapterView.OnItemClickListener{

    private List<Creatura> creature;

    static final int REQUEST_ENABLE_BT = 1;

    protected static final UUID uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

    private BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private ConnectedThread connectedThread;
    private static final String TAG = "SCAMBIO_CREATURA";

    // Defines several constants used when transmitting messages between the
    // service and the UI.
    private interface MessageConstants {
        public static final int MESSAGE_READ = 0;
        public static final int MESSAGE_WRITE = 1;
        public static final int MESSAGE_TOAST = 2;
    }
    private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MessageConstants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    return true;
                case MessageConstants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    Toast.makeText(MyApp.getAppContext(), readMessage,
                            Toast.LENGTH_LONG).show();
                    processMessage(readMessage);
                    return true;
                case MessageConstants.MESSAGE_TOAST:
                    Toast.makeText(MyApp.getAppContext(), msg.getData().toString(),
                            Toast.LENGTH_SHORT).show();
                    break;
            }

            return false;
        }
    });

    private ListView listView;

    private Creatura ofertado = null;
    private Creatura recebido = null;
    private boolean eu_aceitei = false;
    private boolean outro_aceitou = false;

    private Button accetta;
    private Button rifiuta;
    private ImageView euAceitei;
    private ImageView outroAceitou;


    private AdapterScambioCreatureList adapterBestiario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scambio_lista_creature);

        //ottiene i riferimenti delle view

        try {
            //Prepara la listview personalizzata del bestiario
            creature = GiocoSingleton.getInstance().getCreature();
            listView = (ListView) findViewById(R.id.listaScambioCreature);

            adapterBestiario = new AdapterScambioCreatureList(creature, this);
            listView.setAdapter(adapterBestiario);
            listView.setOnItemClickListener(this);

            accetta  = (Button) findViewById(R.id.bottoneAccetta);
            rifiuta = (Button) findViewById(R.id.bottoneRifiuta);
            euAceitei = (ImageView) findViewById(R.id.euAceitei);
            outroAceitou = (ImageView) findViewById(R.id.outroAceitou);
            //rifiuta.setEnabled(false);


        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE: " + e.getMessage());
        }

        if (!bluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }

        socket = MyApp.getBluetoothSocket();

        if(socket != null) {
            try {
                Log.e("SCAMBIO", "Socket trovato");
                connectedThread = new ConnectedThread(socket);
                connectedThread.start();
            } catch (SecurityException e) {
                Log.e("SCAMBIO", "Permesso bluetooth mancante per la connessione", e);
            }
        }
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        try {

            //Click su un elemento della listView personalizzata
            ofertado = (Creatura) parent.getAdapter().getItem(position);

            ImageView creatura_selezionato = (ImageView) findViewById(R.id.mia_creatura_selezionata);
            creatura_selezionato.setImageResource(ofertado.getIcona());
            adapterBestiario.setSelected(position);

            byte[] msg = ("CHANGE " + Integer.toString(ofertado.getNumero())).getBytes();
            connectedThread.write(msg);

            rifiutaScambio(false);

        }catch (Exception e){
            Log.e("BESTIARIO", "ERRORE al click: " + e.getMessage());
        }

    }

    public void accettaScambio(View v){
        accettaScambio();
    }

    public void accettaScambio(){
        if(ofertado == null) {
            Context context = getApplicationContext();
            CharSequence text = "Non puoi fare uno scambio senza offrire una creatura! Offri una creatura della tua collezione.";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return;
        }
        if(recebido == null) {
            Context context = getApplicationContext();
            CharSequence text = "Non puoi fare uno scambio senza ricevere una creatura! Aspetta che l'altro allenatore faccia la sua offerta.";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return;
        }

        eu_aceitei = true;
        accetta.setEnabled(false);
        rifiuta.setEnabled(true);
        adapterBestiario.setAreAllEnabled(false);
        euAceitei.setImageResource(android.R.drawable.checkbox_on_background);

        byte[] msg = "ACCEPT".getBytes();
        connectedThread.write(msg);

        if(outro_aceitou) {
            faiScambio();
        }
    }

    public void rifiutaScambio(View v){
        rifiutaScambio(true);
    }

    public void rifiutaScambio(boolean sendMsg){
        eu_aceitei = false;
        outro_aceitou = false;
        accetta.setEnabled(true);
        rifiuta.setEnabled(false);
        euAceitei.setImageResource(android.R.drawable.checkbox_off_background);
        outroAceitou.setImageResource(android.R.drawable.checkbox_off_background);
        adapterBestiario.setAreAllEnabled(true);
        adapterBestiario.notifyDataSetChanged();

        if(sendMsg) {
            byte[] msg = "REJECT".getBytes();
            connectedThread.write(msg);
        }
    }

    public void faiScambio(){
        if(eu_aceitei && outro_aceitou){
            Toast.makeText(this, "L'altro ha accettato!", Toast.LENGTH_LONG);

            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            Criteria criteria = new Criteria();

            PackageManager packageManager = getPackageManager();
            boolean hasGPS = packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);

            if (hasGPS) {
                criteria.setAccuracy(Criteria.ACCURACY_FINE);
                Log.i("LOCATION", "usando GPS");
            } else {
                criteria.setAccuracy(Criteria.ACCURACY_COARSE);
                Log.i("LOCATION", "usando WI-FI o dati");
            }

            String provider = lm.getBestProvider(criteria, true);

            if (provider == null) {
                Log.e("SCAMBIO", "Nessun provider trovato");

                Context context = getApplicationContext();
                CharSequence text = "Non siamo riusciti a ottenere la tua posizione geografica. Riprova.";
                int duration = Toast.LENGTH_SHORT;

                Toast toast = Toast.makeText(context, text, duration);
                toast.show();
                return;
            } else {
                Log.i("SCAMBIO", "È in uso il provider " + provider);

            }

            double lat = lm.getLastKnownLocation(provider).getLatitude();
            double lon = lm.getLastKnownLocation(provider).getLongitude();
            Apparizione ap = new Apparizione();
            ap.setLatitude(lat); ap.setLongitude(lon);
            ap.setCreatura(recebido);
            GiocoSingleton.getInstance().getUtente().catturare(ap);

            CreaturaCatturata paraEditar = null;
            for (CreaturaCatturata capt: GiocoSingleton.getInstance().getUtente().getCreature().get(ofertado) ) {
                if(capt.getBloccato() == 0) {
                    capt.setBloccato(1);
                    paraEditar = capt;
                    break;
                }
            }

            if(paraEditar != null) {
                ContentValues valores = new ContentValues();
                valores.put("bloccato", 1);
                String where = "login = '" + GiocoSingleton.getInstance().getUtente().getLogin() + "' AND " +
                        "idCreatura = " + String.valueOf(ofertado.getNumero()) + " AND " +
                        "dataCattura = '" + paraEditar.getDataCattura() + "'";
                DatabaseSingleton.getInstance().aggiorna("creatura_utente", valores, where);

                Log.d("SCAMBIO", "Rimozione");
            }



            Context context = getApplicationContext();
            CharSequence text = "Scambio realizada com sucesso!";
            int duration = Toast.LENGTH_SHORT;

            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            finish();
        }
        else{
            rifiutaScambio(false);
        }
    }

    public void clickIndietro(View v){
        finish();
    }


    private class ConnectedThread extends Thread {
        private final BluetoothSocket mmSocket;
        private final InputStream mmInStream;
        private final OutputStream mmOutStream;
        private byte[] mmBuffer; // mmBuffer store è the stream

        public ConnectedThread(BluetoothSocket socket) {
            mmSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            // Get the input and output streams; using temp objects because
            // member streams are final.
            try {
                tmpIn = socket.getInputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating input stream", e);
            }
            try {
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
                Log.e(TAG, "Error occurred when creating output stream", e);
            }

            mmInStream = tmpIn;
            mmOutStream = tmpOut;
        }

        public void run() {
            mmBuffer = new byte[1024];
            int numBytes; // bytes returned from read()

            Log.e("SCAMBIO", "Running Connection");

            // Keep listening to the InputStream until an exception occurs.
            while (true) {
                try {
                    // Read from the InputStream.
                    numBytes = mmInStream.read(mmBuffer);
                    // Send the obtained bytes to the UI activity.
                    Message readMsg = handler.obtainMessage(
                            MessageConstants.MESSAGE_READ, numBytes, -1,
                            mmBuffer);
                    readMsg.sendToTarget();
                    Log.e("SCAMBIO", "Ricezione: " + mmBuffer.toString());
                } catch (IOException e) {
                    Log.d(TAG, "Input stream was disconnected", e);
                    break;
                }
            }
        }

        // Call this from the main activity to send data to the remote device.
        public void write(byte[] bytes) {
            try {
                mmOutStream.write(bytes);

                Log.e("SCAMBIO", "Invio: " + bytes.toString());
                // Share the sent message with the UI activity.
                Message writtenMsg = handler.obtainMessage(
                        MessageConstants.MESSAGE_WRITE, -1, -1, mmBuffer);
                writtenMsg.sendToTarget();

            } catch (IOException e) {
                Log.e(TAG, "Error occurred when sending data", e);

                // Send a failure message back to the activity.
                Message writeErrorMsg =
                        handler.obtainMessage(MessageConstants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString("toast",
                        "Couldn't send data to the other device");
                writeErrorMsg.setData(bundle);
                handler.sendMessage(writeErrorMsg);
            }
        }

        // Call this method from the main activity to shut down the connection.
        public void cancel() {
            try {
                mmSocket.close();
            } catch (IOException e) {
                Log.e(TAG, "Could not close the connect socket", e);
            }
        }

    }

    public void processMessage(String msg){
        String[] flags = msg.split(" ");

        switch(flags[0]){
            case "CHANGE":

                Integer position = Integer.parseInt(flags[1]) - 1;

                recebido = (Creatura) creature.get(position);

                ImageView creatura_selezionato = (ImageView) findViewById(R.id.altra_creatura_selezionata);
                creatura_selezionato.setImageResource(recebido.getIcona());

                rifiutaScambio(false);

                break;

            case "ACCEPT":
                outro_aceitou = true;
                outroAceitou.setImageResource(android.R.drawable.checkbox_on_background);

                if(eu_aceitei && outro_aceitou)
                    faiScambio();

                break;

            case "REJECT":
                rifiutaScambio(false);
                break;
        }
    }

}
