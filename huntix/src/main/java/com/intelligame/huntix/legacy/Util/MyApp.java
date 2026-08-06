package com.intelligame.huntix.legacy.Util;

import android.app.Application;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.util.Log;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

/**
 * Created by Lucas on 12/12/2016.
 */
public class MyApp extends Application {

    private static Context context;
    private static BluetoothSocket bluetoothSocket;

    public void onCreate() {
        super.onCreate();
        MyApp.context = getApplicationContext();
    }

    // Hook chiamato dall'Application dell'app host (EggHuntApplication) dato che
    // questa libreria non è l'Application del processo.
    public static void initContext(Context ctx) {
        MyApp.context = ctx.getApplicationContext();
    }

    public static Context getAppContext() {
        //metodo usato per recuperare il context dell'app
        //da qualsiasi parte del programma
        return MyApp.context;
    }

    public static BluetoothSocket getBluetoothSocket() {
        return MyApp.bluetoothSocket;
    }

    public static void setBluetoothSocket(BluetoothSocket socket){
        MyApp.bluetoothSocket = socket;
    }
}
