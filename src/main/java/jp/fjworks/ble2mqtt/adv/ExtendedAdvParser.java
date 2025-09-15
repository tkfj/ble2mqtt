package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.util.Collection;

public class ExtendedAdvParser extends BaseAdvParser implements AdvParser {
    private int[] supportedTypes = new int[] { 0x0d };
    
    @Override
    public void parse(ByteBuffer bbuf, int offset, int len, OnParsedCallback callback) {
        if (len < 1) throw new IllegalArgumentException(String.format("invalid len: %d",len));
        int num = bbuf.get(offset++) & 0xFF; len--;
        // System.out.printf("num: %d",num);
        for (int i=0;i<num;i++) {
            Adv adv = parseOne(bbuf,offset,len-1);
            callback.onParsed(adv);
            offset+=adv.getTotalLength();
            len-=adv.getTotalLength();
        }
    }
    private static Adv parseOne(ByteBuffer bbuf, int offset, int len) {
        if (len < 24) throw new IllegalArgumentException(); //TODO message
        int type     = Short.toUnsignedInt(bbuf.getShort(offset)); offset+=2;
        int addrType = bbuf.get(offset++) & 0xFF;
        String addr  = mac(bbuf, offset); offset+=6;
        int priPhy   = bbuf.get(offset++) & 0xFF;
        int secPhy   = bbuf.get(offset++) & 0xFF;
        int sid      = bbuf.get(offset++) & 0xFF;
        int tx       = (byte)bbuf.get(offset++); // dBm
        int rssi     = (byte)bbuf.get(offset++);
        int interval = Short.toUnsignedInt(bbuf.getShort(offset)); offset+=2;
        int daddrT   = bbuf.get(offset++) & 0xFF;
        String daddr = mac(bbuf, offset); offset+=6;
        int dlen     = bbuf.get(offset++) & 0xFF;
        byte[] data = new byte[dlen]; bbuf.get(offset, data, 0, dlen);offset += dlen;
        int totallength = dlen + 24;

        Collection<AdStructure> adStructures = AdStructure.parse(data);

        return new Adv() {
            @Override
            public int getTotalLength() {
                return totallength;
            }

            @Override
            public String toJsonString() {
                return String.format(
                    "{\"type\":\"extended\",\"addr\":\"%s\",\"rssi\":%d,\"structure\":%s,\"raw\":\"%s\",\"ts\":\"%s\"}",
                    addr, rssi, AdStructure.toJsonString(adStructures), hex(data, 0, dlen), java.time.Instant.now());
            }
        };
    }
    @Override
    public int[] getSupportedTypes() {
        return supportedTypes;
    }
}
