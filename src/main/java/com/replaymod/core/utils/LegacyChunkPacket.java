package com.replaymod.core.utils;

import java.io.IOException;

/** 1.7.10 chunk framing; WGCore widens section masks without changing compressed terrain. */
public final class LegacyChunkPacket {
    private LegacyChunkPacket() { }
    private static int integer(byte[] p,int i) { return (p[i]&255)<<24|(p[i+1]&255)<<16|(p[i+2]&255)<<8|p[i+3]&255; }
    private static int word(byte[] p,int i) { return (p[i]&255)<<8|p[i+1]&255; }
    private static void integer(byte[] p,int i,int n) { p[i]=(byte)(n>>>24); p[i+1]=(byte)(n>>>16); p[i+2]=(byte)(n>>>8); p[i+3]=(byte)n; }
    private static void word(byte[] p,int i,int n) throws IOException {
        if ((n&0xffff0000)!=0) throw new IOException("Unsupported chunk section mask: "+n);
        p[i]=(byte)(n>>>8); p[i+1]=(byte)n;
    }
    private static boolean lengthAt(byte[] p,int offset) {
        return p.length>=offset+4 && integer(p,offset)>=0 && integer(p,offset)==p.length-offset-4;
    }
    public static boolean extendedSingle(byte[] p) { return lengthAt(p,17); }
    public static byte[] normalizeSingle(byte[] p) throws IOException {
        if (lengthAt(p,17) || lengthAt(p,13)) return p;
        for (int offset:new int[]{13,17}) {
            int value=0;
            for (int n=0;n<3 && offset+n<p.length;n++) {
                int b=p[offset+n]&255; value|=(b&127)<<(n*7);
                if ((b&128)==0) {
                    if (value==p.length-offset-n-1) {
                        byte[] fixed=new byte[offset+4+value];
                        System.arraycopy(p,0,fixed,0,offset); integer(fixed,offset,value);
                        System.arraycopy(p,offset+n+1,fixed,offset+4,value); return fixed;
                    }
                    break;
                }
            }
        }
        throw new IOException("Invalid 1.7.10 chunk packet length: "+p.length+" bytes");
    }
    public static byte[] standardSingle(byte[] original) throws IOException {
        byte[] p=normalizeSingle(original);
        if (!extendedSingle(p)) return p;
        byte[] result=new byte[p.length-4]; System.arraycopy(p,0,result,0,9);
        word(result,9,integer(p,9)); word(result,11,integer(p,13));
        System.arraycopy(p,17,result,13,p.length-17); return result;
    }
    public static byte[] widenSingle(byte[] original) throws IOException {
        byte[] p=normalizeSingle(original);
        if (extendedSingle(p)) return p;
        byte[] result=new byte[p.length+4]; System.arraycopy(p,0,result,0,9);
        integer(result,9,word(p,9)); integer(result,13,word(p,11));
        System.arraycopy(p,13,result,17,p.length-13); return result;
    }
    public static boolean extendedBulk(byte[] p) throws IOException {
        if (p.length<7) throw new IOException("Truncated bulk chunk packet");
        int count=word(p,0),compressed=integer(p,2);
        long remaining=p.length-7L-compressed;
        if (compressed<0 || (remaining!=count*12L && remaining!=count*16L)) throw new IOException("Invalid bulk chunk header length");
        return count>0 && remaining==count*16L;
    }
    public static byte[] standardBulk(byte[] p) throws IOException {
        if (!extendedBulk(p)) return p;
        int count=word(p,0),start=7+integer(p,2);
        byte[] result=new byte[p.length-count*4]; System.arraycopy(p,0,result,0,start);
        for (int i=0;i<count;i++) {
            int from=start+i*16,to=start+i*12;
            System.arraycopy(p,from,result,to,8);
            word(result,to+8,integer(p,from+8)); word(result,to+10,integer(p,from+12));
        }
        return result;
    }
}
