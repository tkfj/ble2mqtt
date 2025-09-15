package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public class AdStructure {
    private int type;
    private byte[] data;
    private AdStructure(int type, byte[] data) {
        this.type = type;
        this.data = data;
    }
    public static Collection<AdStructure> parse(byte[] b) {
        if (b == null || b.length==0) return null;
        List<AdStructure> adStructures = new ArrayList<>(2);
        for (int i = 0; i < b.length; ) {
            int len = b[i++] & 0xFF;             // L
            if (len == 0) break;
            if (i + len > b.length) break;       // 破損ガード
            int type = b[i++] & 0xFF;            // T
            int dlen = len - 1;                  // V長
            byte[] data = Arrays.copyOfRange(b, i, i+dlen);
            // String dataHex = BaseAdvParser.toHex(b, i, dlen);
            if(type==0xff) {
                adStructures.add(new ManufacuturarSpecific(type, data));
            } else {
                adStructures.add(new AdStructure(type, data));
            }
            i += dlen; // 次のADへ
        }
        return adStructures;
    }
    public static String toJsonString(Collection<AdStructure> adStructures) {
        StringBuilder sb = new StringBuilder("[");
        if(adStructures != null){
            for (AdStructure adStructure: adStructures) {
                if(sb.length()>1) sb.append(",");
                sb.append(adStructure.toJsonString());
            }
        }
        sb.append("]");
        return sb.toString();
    }
    public String toJsonString() {
        return String.format("{\"type\":\"0x%02x\",\"value\":\"%s\"}",type,BaseAdvParser.hex(data, 0, data.length));
    }

    static class ManufacuturarSpecific extends AdStructure {
        private byte[] manufacturarIdRaw;
        private int manufacturarId;
        private String manufacturarIdStr;
        private byte[] data;
        ManufacuturarSpecific(int type, byte[] b) {
            super(type, b);
            this.manufacturarIdRaw = Arrays.copyOfRange(b, 0,2);
            this.manufacturarId = ByteBuffer.wrap(this.manufacturarIdRaw).order(ByteOrder.LITTLE_ENDIAN).getShort();
            this.manufacturarIdStr = String.format("%04x",this.manufacturarId);
            this.data = Arrays.copyOfRange(b, 2,b.length);
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"manufacturar\":\"0x%s\",\"value\":\"%s\"}",super.type, this.manufacturarIdStr, BaseAdvParser.hex(data,0,data.length));
        }
    }
}
