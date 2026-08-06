package com.intelligame.huntix.legacy.Model;

import java.util.ArrayList;

/**
 * Created by Lucas on 08/12/2016.
 */
public class CreaturaCatturata {
    private double latitude;
    private double longitude;
    private String dataCattura;
    private int bloccato;

    protected CreaturaCatturata(double latitude, double longitude, String dataCattura){
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataCattura = dataCattura;
    }

    public CreaturaCatturata() {
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public String getDataCattura() {
        return dataCattura;
    }

    public void setDataCattura(String dataCattura) {
        this.dataCattura = dataCattura;
    }

    public int getBloccato() {
        return bloccato;
    }

    public void setBloccato(int bloccato) {
        this.bloccato = bloccato;
    }
}
