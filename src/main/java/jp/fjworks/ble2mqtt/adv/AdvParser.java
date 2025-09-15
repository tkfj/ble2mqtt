package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.util.Collection;

public interface AdvParser {
    public Collection<Adv> parse(ByteBuffer bbuf, int offset, int len);
    public int[] getSupportedTypes();
}
