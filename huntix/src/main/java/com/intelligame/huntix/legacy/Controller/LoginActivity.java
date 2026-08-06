package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.R;

public class LoginActivity extends Activity {


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_login);

    }

    public void clickLogin(View v){
        try {
            Log.i("LOGIN", "Autenticando l'accesso al sistema...");

            EditText edtUtente = (EditText) findViewById(R.id.edtUtenteLogin);
            EditText edtPassword = (EditText) findViewById(R.id.edtPasswordLogin);

            //Ottiene i dati dell'utente
            String utente = edtUtente.getText().toString();
            String password = edtPassword.getText().toString();

            if (GiocoSingleton.getInstance().accedi(utente, password)) {
                Intent it = new Intent(this, MapActivity.class);
                startActivity(it);
                finish();
            } else {
                Toast.makeText(this, "Utente e/o password non validi!", Toast.LENGTH_SHORT).show();
            }
        }catch (Exception e){
            Log.e("LOGIN", "ERRORE: " + e.getMessage());
        }

    }

    public void clickRegistrare(View v){
        Intent it = new Intent(this, RegistrareActivity.class);
        startActivity(it);
        finish();
    }
}
