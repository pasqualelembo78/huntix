package com.intelligame.huntix.legacy.Model;

import android.database.Cursor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import com.intelligame.huntix.legacy.Util.DatabaseSingleton;

public class Caramella implements Serializable{
    private int idCaramella;
    private String nomeCreatura;
    private int quantita;

    public Caramella(){

    }

    public int getIdCaramella() {
        return idCaramella;
    }

    public void setIdCaramella(int idCaramella) {
        this.idCaramella = idCaramella;
    }

    public String getNomeCreatura() {
        return nomeCreatura;
    }

    public void setNomeCreatura(String nomeCreatura) {
        this.nomeCreatura = nomeCreatura;
    }

    public int getQuantita() {
        return quantita;
    }

    public void setQuantita(int quantita) {
        this.quantita = quantita;
    }

    protected Caramella(int idCaramella, String nomeCreatura, int quantita){
        this.idCaramella = idCaramella;
        this.nomeCreatura = nomeCreatura;
        this.quantita = quantita;
    }
}
