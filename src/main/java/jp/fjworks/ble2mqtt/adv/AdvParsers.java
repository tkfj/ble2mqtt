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

    public Collection<Adv> parse(ByteBuffer bbuf, int offset) {

        int evt  = bbuf.get(offset++) & 0xFF;
        // System.out.printf("evt: 0x%02x\n",evt);
        if (evt != 0x3E /* LE Meta */) return null; //TODO もうちょっとマシな返しを
        int len = bbuf.get(offset++) & 0xFF;
        // System.out.printf("len: 0x%02x\n",len);
        int subevt = bbuf.get(offset++) & 0xFF;
        // System.out.printf("subevt: 0x%02x\n",subevt);
        if(parserMap.containsKey(Integer.valueOf(subevt))) {
            return parserMap.get(subevt).parse(bbuf, offset, len);
        }
        else {
            System.out.println("not supported.");
            return null;//TODO もうちょっとマシな返しを
        }
    }
}
