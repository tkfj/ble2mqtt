package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
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
            } else if(type==0x08||type==0x09) {
                adStructures.add(new Name(type, data));
            } else if(type==0x0a) {
                adStructures.add(new TxPowerLevel(type, data));
            } else if(type==0x16) {
                adStructures.add(new ServiceData(type, data));
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

    public static class ManufacuturarSpecific extends AdStructure {
        private int manufacturarId;
        private String manufacturarIdStr;
        private byte[] data;
        private ManufacuturarSpecific(int type, byte[] b) {
            super(type, b);
            this.manufacturarId = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(0);
            this.manufacturarIdStr = String.format("%04x",this.manufacturarId);
            this.data = Arrays.copyOfRange(b, 2,b.length);
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"manufacturar\":\"0x%s\",\"specific_value\":\"%s\"}",super.type, this.manufacturarIdStr, BaseAdvParser.hex(data,0,data.length));
        }
    }
    public static class Name extends AdStructure {
        private String nameType;
        private String name;
        private Name(int type, byte[] b) {
            super(type, b);
            this.nameType = type == 0x09 ? "full_name":"short_name";
            this.name = new String(b,StandardCharsets.US_ASCII);
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"%s\":\"%s\"}",super.type, this.nameType, this.name);
        }
    }
    public static class TxPowerLevel extends AdStructure {
        private int tx;
        private TxPowerLevel(int type, byte[] b) {
            super(type, b);
            this.tx = (byte)b[0];
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"tx\":%d}",super.type, this.tx);
        }
    }
    public static class ServiceData extends AdStructure {
        private String uuid;
        private byte[] data;
        private ServiceData(int type, byte[] b) {
            super(type, b);
            this.uuid = BaseAdvParser.hex(b, 0, 2);
            if(b.length>2) {
                this.data = Arrays.copyOfRange(b, 2, b.length);
            }
            else {
                this.data = new byte[0];
            }
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"service_uuid\":\"%s\",\"service_data\":\"%s\"}",super.type, this.uuid, BaseAdvParser.hex(this.data, 0,this.data.length));
        }
    }
}
