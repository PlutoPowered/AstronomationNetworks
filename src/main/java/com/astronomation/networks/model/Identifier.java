package com.astronomation.networks.model;

public interface Identifier {

    java.lang.String id();

    record String(java.lang.String id) implements Identifier {

    }

}
