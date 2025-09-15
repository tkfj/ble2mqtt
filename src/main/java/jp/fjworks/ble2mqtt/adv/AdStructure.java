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
            if(type==0x01) {
                adStructures.add(new Flags(type, data));
            } else if(type==0x02||type==0x03||type==0x04||type==0x05||type==0x06||type==0x07) {
                adStructures.add(new Service(type, data));
            } else if(type==0x08||type==0x09) {
                adStructures.add(new Name(type, data));
            } else if(type==0x0a) {
                adStructures.add(new TxPowerLevel(type, data));
            } else if(type==0x16) {
                adStructures.add(new ServiceData(type, data));
            } else if(type==0xff) {
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
        return String.format("{\"UNKNOWN_TYPE\":\"0x%02x\",\"value\":\"%s\"}",type,BaseAdvParser.hex(data, 0, data.length));
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
    public static class Service extends AdStructure {
        private String uuidType;
        private String uuid;
        private Service(int type, byte[] b) {
            super(type, b);
            this.uuidType = type == 0x03 ? "more_16bit_uuids":
                type == 0x04 ? "complete_16bit_uuids":
                type == 0x05 ? "more_32bit_uuids":
                type == 0x06 ? "complete_32bit_uuids":
                type == 0x07 ? "more_128bit_uuids":"complete_128bit_uuids";
            this.uuid = BaseAdvParser.hex(b, 0, b.length);
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"%s\":\"%s\"}",super.type, this.uuidType, this.uuid);
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
    public static class Flags extends AdStructure {
        private static int BLE_GAP_ADV_FLAG_LE_LIMITED_DISC_MODE = 0x01;
        private static int BLE_GAP_ADV_FLAG_LE_GENERAL_DISC_MODE = 0x02;
        private static int BLE_GAP_ADV_FLAG_BR_EDR_NOT_SUPPORTED = 0x04;
        private static int BLE_GAP_ADV_FLAG_LE_BR_EDR_CONTROLLER = 0x08;
        private static int BLE_GAP_ADV_FLAG_LE_BR_EDR_HOST = 0x10;
        private int flags;
        private boolean flag_le_limited_disc_mode;
        private boolean flag_le_general_disc_mode;
        private boolean flag_br_edr_not_supported;
        private boolean flag_le_br_edr_controller;
        private boolean flag_le_br_edr_host;
        private String str;
        private Flags(int type, byte[] b) {
            super(type, b);
            this.flags = (byte)b[0];
            this.flag_le_limited_disc_mode = (this.flags & BLE_GAP_ADV_FLAG_LE_LIMITED_DISC_MODE)!=0;
            this.flag_le_general_disc_mode = (this.flags & BLE_GAP_ADV_FLAG_LE_GENERAL_DISC_MODE)!=0;
            this.flag_br_edr_not_supported = (this.flags & BLE_GAP_ADV_FLAG_BR_EDR_NOT_SUPPORTED)!=0;
            this.flag_le_br_edr_controller = (this.flags & BLE_GAP_ADV_FLAG_LE_BR_EDR_CONTROLLER)!=0;
            this.flag_le_br_edr_host = (this.flags & BLE_GAP_ADV_FLAG_LE_BR_EDR_HOST)!=0;
            List<String> flgstrs = new ArrayList<>();
            if (flag_le_limited_disc_mode) flgstrs.add("BLE_GAP_ADV_FLAG_LE_LIMITED_DISC_MODE");
            if (flag_le_general_disc_mode) flgstrs.add("BLE_GAP_ADV_FLAG_LE_GENERAL_DISC_MODE");
            if (flag_br_edr_not_supported) flgstrs.add("BLE_GAP_ADV_FLAG_BR_EDR_NOT_SUPPORTED");
            if (flag_le_br_edr_controller) flgstrs.add("BLE_GAP_ADV_FLAG_LE_BR_EDR_CONTROLLER");
            if (flag_le_br_edr_host) flgstrs.add("BLE_GAP_ADV_FLAG_LE_BR_EDR_HOST");
            this.str = String.join("|", flgstrs);
        }
        @Override
        public String toJsonString() {
            return String.format("{\"type\":\"0x%02x\",\"flags\":\"0x%02x\",\"flags_str\":\"%s\"}",super.type, this.flags, this.str);
        }
    }
}
