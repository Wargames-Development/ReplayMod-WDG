package com.replaymod.replay.compat;

import java.io.*;
import java.util.*;

/** First full terrain snapshots, for explicit export-time recovery outside recorded coverage. */
public final class RecordedTerrain {
    public static final class Snapshot {
        public final int time, index;
        public final byte[] packet;
        Snapshot(int time, byte[] packet, int index) {
            this.time=time; this.packet=packet; this.index=index;
        }
    }
    private final Map<Integer, Map<Long, Snapshot>> dimensions = new HashMap<>();
    private static long key(int x, int z) { return ((long)x << 32) ^ (z & 0xffffffffL); }
    public Snapshot at(int dimension, int x, int z) {
        Map<Long,Snapshot> chunks=dimensions.get(dimension);
        return chunks==null?null:chunks.get(key(x,z));
    }
    public int size() { int n=0; for (Map<?,?> chunks:dimensions.values()) n+=chunks.size(); return n; }
    private void add(int dimension,int x,int z,int time,byte[] packet,int index) {
        dimensions.computeIfAbsent(dimension,unused->new HashMap<>()).putIfAbsent(key(x,z),new Snapshot(time,packet,index));
    }
    private static int integer(byte[] p,int i) {
        return (p[i]&255)<<24|(p[i+1]&255)<<16|(p[i+2]&255)<<8|p[i+3]&255;
    }
    private static int unsignedShort(byte[] p,int i) { return (p[i]&255)<<8|p[i+1]&255; }
    public static RecordedTerrain read(InputStream stream) throws IOException {
        RecordedTerrain result=new RecordedTerrain();
        DataInputStream in=new DataInputStream(new BufferedInputStream(stream));
        int dimension=0; boolean joined=false;
        while (true) {
            int first=in.read(); if (first<0) break;
            int time=first<<24|in.readUnsignedByte()<<16|in.readUnsignedByte()<<8|in.readUnsignedByte();
            int length=in.readInt();
            if (length<1 || length>64*1024*1024) throw new IOException("Invalid recorded terrain packet length: "+length);
            byte[] p=new byte[length]; in.readFully(p);
            int id=p[0]&255;
            if (id==1 && length>=10 && length<64) { dimension=p[6]; joined=true; }
            if (!joined) continue;
            if (id==7 && length>=5) dimension=integer(p,1);
            if (id==0x26 && length>=8) {
                int count=unsignedShort(p,1), compressed=integer(p,3);
                long start=8L+compressed, remainder=length-start;
                // WGCore's extended block IDs widen both section masks to ints.
                if (count==0 || compressed<0 || start>length || (remainder!=12L*count && remainder!=16L*count))
                    throw new IOException("Invalid bulk terrain packet at "+time);
                int stride=(int)remainder/count, offset=(int)start;
                for (int i=0;i<count;i++,offset+=stride) {
                    int mask=stride==16?integer(p,offset+8):unsignedShort(p,offset+8);
                    if (mask!=0) result.add(dimension,integer(p,offset),integer(p,offset+4),time,p,i);
                }
            } else if (id==0x21 && length>=18 && p[9]!=0) {
                // Detect the actual wire layout without assuming the installed ID extension.
                int mask;
                if (integer(p,14)>=0 && (long)integer(p,14)+18==length) mask=unsignedShort(p,10);
                else if (length>=22 && integer(p,18)>=0 && (long)integer(p,18)+22==length) mask=integer(p,10);
                else throw new IOException("Invalid full terrain packet at "+time);
                if (mask!=0) result.add(dimension,integer(p,1),integer(p,5),time,p,-1);
            }
        }
        return result;
    }
}
