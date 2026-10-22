package com.replaymod.simplepathing.properties;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.replaymod.replaystudio.pathing.property.AbstractProperty;
import com.replaymod.replaystudio.pathing.property.PropertyPart;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;

/** Editor-only grouping travels with camera keys through saving, retiming and undo. */
public final class ShotGroupProperty extends AbstractProperty<String> {
    public static final ShotGroupProperty PROPERTY=new ShotGroupProperty();
    private ShotGroupProperty() { super("wdgShotGroup","<internal>",null,""); }
    @Override public Collection<PropertyPart<String>> getParts() { return Collections.emptyList(); }
    @Override public void applyToGame(String value,Object replay) { }
    @Override public void toJson(JsonWriter writer,String value) throws IOException { writer.value(value); }
    @Override public String fromJson(JsonReader reader) throws IOException {
        String value=reader.nextString(); if(value.length()>100) throw new IOException("Invalid shot group."); return value;
    }
}
