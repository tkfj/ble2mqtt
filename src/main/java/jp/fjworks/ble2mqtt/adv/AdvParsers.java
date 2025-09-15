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
        if (evt != 0x3E /* LE Meta */) return null; //TODO もうちょっとマシな返しを
        int len = bbuf.get(offset++) & 0xFF;
        int subevt = bbuf.get(offset++) & 0xFF;
        if(parserMap.containsKey(Integer.valueOf(subevt))) {
            return parserMap.get(subevt).parse(bbuf, offset, len);
        }
        else {
            return null;//TODO もうちょっとマシな返しを
        }
    }
}
