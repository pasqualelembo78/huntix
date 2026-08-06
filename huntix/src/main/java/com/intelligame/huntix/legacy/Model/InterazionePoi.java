package com.intelligame.huntix.legacy.Model;

import java.io.Serializable;
import java.util.Date;

public class InterazionePoi implements Serializable {
    private Poi p;
    private Utente utente;
    private Date ultimoAccesso;

    public InterazionePoi(Poi p, Utente utente, Date ultimoAccesso){
        this.p = p;
        this.utente = utente;
        this.ultimoAccesso = ultimoAccesso;
    }

    public Poi getPoi() {
        return p;
    }

    public void setPoi(Poi p) {
        this.p = p;
    }

    public Utente getUtente() {
        return utente;
    }

    public void setUtente(Utente utente) {
        this.utente = utente;
    }

    public Date getUltimoAccesso() {
        return ultimoAccesso;
    }

    public void setUltimoAccesso(Date ultimoAccesso) {
        this.ultimoAccesso = ultimoAccesso;
    }
}
