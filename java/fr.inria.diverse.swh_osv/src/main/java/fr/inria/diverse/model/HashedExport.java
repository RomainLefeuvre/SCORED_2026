package fr.inria.diverse.model;

import java.io.Serializable;

public class HashedExport implements Serializable{
    private static final long serialVersionUID = 1L;
    private int inputHash;

    public int getInputHash() {
        return inputHash;
    }

    public HashedExport(int inputHash) {
        this.inputHash = inputHash;
    }

  
}
