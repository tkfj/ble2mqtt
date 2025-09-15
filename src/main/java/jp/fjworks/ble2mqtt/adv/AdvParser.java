package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;

public interface AdvParser {
    public void parse(ByteBuffer bbuf, int offset, int len, OnParsedCallback callback);
    public int[] getSupportedTypes();
    public interface OnParsedCallback {
        public void onParsed(Adv adv);
    }
}
