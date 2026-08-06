package com.intelligame.huntix.legacy.Model;

import java.io.Serializable;

/**
 * Created by Lucas on 08/12/2016.
 */
public class Apparizione implements Serializable {
    private double latitude;
    private double longitude;
    private Creatura creatura;

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

    public Creatura getCreatura() {
        return creatura;
    }

    public void setCreatura(Creatura creatura) {
        this.creatura = creatura;
    }
}
