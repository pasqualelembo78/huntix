package com.intelligame.huntix.legacy.Model;

import java.io.Serializable;

/**
 * Created by Lucas on 08/12/2016.
 */
public class Elemento implements Serializable {
    private int idElemento;
    private String nome;

    public Elemento() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getIdElemento() {
        return idElemento;
    }

    public void setIdElemento(int idElemento) {
        this.idElemento = idElemento;
    }
}
