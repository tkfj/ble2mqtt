package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.util.Collection;

public class LegacyAdvParser extends BaseAdvParser implements AdvParser {
    private int[] supportedTypes = new int[] { 0x02 };
    @Override
    public void parse(ByteBuffer bbuf, int offset, int len, OnParsedCallback callback) {
        if (len < 1) throw new IllegalArgumentException(String.format("invalid len: %d",len));
        int num = bbuf.get(offset++) & 0xFF; len--;
        for (int i=0;i<num;i++) {
            Adv adv = parseOne(bbuf,offset,len-1);
            callback.onParsed(adv);
            offset+=adv.getTotalLength();
            len-=adv.getTotalLength();
        }
    }
    
    private static Adv parseOne(ByteBuffer bbuf, int offset, int len) {
        if (len < 10) throw new IllegalArgumentException(String.format("invalid len: %d",len));
        int evtType = bbuf.get(offset++) & 0xFF;
        int addrType= bbuf.get(offset++) & 0xFF;
        String addr = mac(bbuf, offset); offset += 6;
        int dlen    = bbuf.get(offset++) & 0xFF;
        byte[] data = new byte[dlen]; bbuf.get(offset, data, 0, dlen);offset += dlen;
        int rssi    = (byte)bbuf.get(offset++);
        int totallength = dlen + 10;
        len -= totallength;

        Collection<AdStructure> adStructures = AdStructure.parse(data);

        return new Adv() {
            @Override
            public int getTotalLength() {
                return totallength;
            }
            @Override
            public String toJsonString() {
                return String.format(
                    "{\"type\":\"legacy\",\"addr\":\"%s\",\"rssi\":%d,\"structure\":%s,\"raw\":\"%s\",\"ts\":\"%s\"}",
                    addr, rssi, AdStructure.toJsonString(adStructures), hex(data, 0, dlen), java.time.Instant.now());
            }
        };
    }

    @Override
    public int[] getSupportedTypes() {
        return supportedTypes;
    }
}
