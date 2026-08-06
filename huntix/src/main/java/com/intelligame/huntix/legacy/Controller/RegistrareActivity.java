package com.intelligame.huntix.legacy.Controller;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;

import com.intelligame.huntix.legacy.Model.GiocoSingleton;
import com.intelligame.huntix.legacy.R;

public class RegistrareActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_registrare);
    }

    public void clickIndietro(View v){
        Intent it = new Intent(this,LoginActivity.class);
        startActivity(it);
        finish();
    }

    public void clickRegistraUtente(View v){
        try {
            Log.i("REGISTRAZIONE", "Registrando l'utente nel sistema...");

            boolean cadOk = true;

            EditText edtNome = (EditText) findViewById(R.id.edtNomeRegistrazione);

            //TODO: verificare sul server se l'utente esiste già.
            EditText edtUtente = (EditText) findViewById(R.id.edtUtenteRegistrazione);

            EditText edtPassword = (EditText) findViewById(R.id.edtPasswordRegistrazione);
            EditText edtConfirmaPassword = (EditText) findViewById(R.id.edtConfirmacaoPasswordRegistrazione);
            RadioGroup sesso = (RadioGroup) findViewById(R.id.grupoSesso);

            //ottiene i dati forniti dall'utente
            String nome = edtNome.getText().toString();
            String utente = edtUtente.getText().toString();
            String password = edtPassword.getText().toString();
            String confPassword = edtConfirmaPassword.getText().toString();
            String nomeSesso = "";

            //ottiene l'informazione dal radio group
            if (sesso.getCheckedRadioButtonId() == R.id.sessoMaschile)
                nomeSesso = "M";
            else
                nomeSesso = "F";

            //Verifica la compilazione dei campi obbligatori e valida i dati
            if (nome.length() == 0 || nome.length() > 50) {
                Toast.makeText(this, "Inserisci un nome con massimo 50 caratteri!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (utente.length() == 0 || utente.length() > 45) {
                Toast.makeText(this, "Inserisci un utente con massimo 45 caratteri!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (password.length() == 0 || password.length() > 45) {
                Toast.makeText(this, "Inserisci una password con massimo 45 caratteri!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (confPassword.length() == 0) {
                Toast.makeText(this, "Inserisci la conferma della password!", Toast.LENGTH_SHORT).show();
                cadOk = false;
            } else if (!password.equals(confPassword)) {
                Toast.makeText(this, "Conferma password non valida!\nDigitala di nuovo.", Toast.LENGTH_SHORT).show();
                cadOk = false;
            }

            //Registra l'utente se i dati sono validi
            if (cadOk) {
                if (GiocoSingleton.getInstance().registraUtente(utente, password, nome, nomeSesso, "")) {
                    Toast.makeText(this, "Utente registrato!", Toast.LENGTH_SHORT).show();
                    Intent it = new Intent(this, MapActivity.class);
                    startActivity(it);
                    finish();
                } else {
                    Toast.makeText(this, "Problemi nel registrare l'utente.\nRiprova!", Toast.LENGTH_SHORT).show();
                }
            }

        }catch (Exception e){
            Log.e("REGISTRAZIONE", "ERRORE: " + e.getMessage());
        }
    }
}
