package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;

public class PeriodicAdvParser extends BaseAdvParser implements AdvParser {
    private int[] supportedTypes = new int[] { 0x0f };
    @Override
    public Collection<Adv> parse(ByteBuffer bbuf, int offset, int len) {
        int sync = Short.toUnsignedInt(bbuf.getShort(offset)); offset+=2;
        int tx   = (byte)bbuf.get(offset++); 
        int rssi = (byte)bbuf.get(offset++);
        int cte  = bbuf.get(offset++) & 0xFF;
        int st   = bbuf.get(offset++) & 0xFF;
        int dlen = bbuf.get(offset++) & 0xFF;
        byte[] data = new byte[dlen]; bbuf.get(offset, data, 0, dlen);offset += dlen;

        Collection<AdStructure> adStructures = AdStructure.parse(data);

        return Arrays.asList((Adv)new Adv() {
            @Override
            public int getTotalLength() {
                return 7+dlen;
            }
        });
    }

    @Override
    public int[] getSupportedTypes() {
        return supportedTypes;
    }
}
