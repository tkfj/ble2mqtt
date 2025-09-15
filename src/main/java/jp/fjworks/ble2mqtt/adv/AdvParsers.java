package jp.fjworks.ble2mqtt.adv;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AdvParsers {
    private Map<Integer,AdvParser> parserMap;
    private AdvParsers() {
        Collection<AdvParser> parsers = Arrays.asList(
            (AdvParser)new LegacyAdvParser(),
            (AdvParser)new ExtendedAdvParser(),
            (AdvParser)new PeriodicAdvParser()
        );
        parserMap= new HashMap<>();
        for (AdvParser parser: parsers) {
            for (int i: parser.getSupportedTypes()) {
                parserMap.put(i,parser);
            }
        }
    }
    private class SingletonHolder {
        private static final AdvParsers SINGLETON = new AdvParsers();

    }
    public static AdvParsers getInstance() {
        return SingletonHolder.SINGLETON;
    }

    public void parse(ByteBuffer bbuf, int offset, AdvParser.OnParsedCallback callback) {

        int evt  = bbuf.get(offset++) & 0xFF;
        // System.out.printf("evt: 0x%02x\n",evt);
        if (evt != 0x3E /* LE Meta */) return; //TODO もうちょっとマシな返しを
        int len = bbuf.get(offset++) & 0xFF;
        // System.out.printf("len: 0x%02x\n",len);
        int subevt = bbuf.get(offset++) & 0xFF;
        // System.out.printf("subevt: 0x%02x\n",subevt);
        if(parserMap.containsKey(Integer.valueOf(subevt))) {
            parserMap.get(subevt).parse(bbuf, offset, len, callback);
        }
        else {
            System.out.println("not supported.");
            return;//TODO もうちょっとマシな返しを
        }
    }
}
