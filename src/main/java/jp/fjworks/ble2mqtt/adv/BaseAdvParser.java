package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;

public abstract class BaseAdvParser {

    // 16進 → byte[]
    static byte[] hexToBytes(String hex) {
        int n = hex.length();
        byte[] out = new byte[n/2];
        for (int i = 0; i < n; i += 2) {
            out[i/2] = (byte) Integer.parseInt(hex.substring(i, i+2), 16);
        }
        return out;
    }

    static String hex(byte[] b, int off, int len) {
        StringBuilder sb = new StringBuilder(len*2);
        for (int k=0;k<len;k++) sb.append(String.format("%02x", b[off+k]));
        return sb.toString();
    }

    static String hex(ByteBuffer bbuf, int pos, int len) {
        StringBuilder sb = new StringBuilder(len*2);
        for (int i=0;i<len;i++) sb.append(String.format("%02x", bbuf.get(pos+i)));
        return sb.toString();
    }
    
    static String mac(ByteBuffer bbuf, int pos) { // little-endian
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                bbuf.get(pos+5)&0xFF, bbuf.get(pos+4)&0xFF, bbuf.get(pos+3)&0xFF,
                bbuf.get(pos+2)&0xFF, bbuf.get(pos+1)&0xFF, bbuf.get(pos)&0xFF);
    }
}
