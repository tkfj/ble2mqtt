package jp.fjworks.ble2mqtt;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;

import java.time.Instant;

public final class HciMonitor implements AutoCloseable, Runnable {
    // ---- libc bindings ----
public interface LibC {
        LibC INSTANCE = LibraryLoader.create(LibC.class).load("c");
        int socket(int domain, int type, int protocol);
        int bind(int sockfd, Pointer addr, int addrlen);
        int read(int fd, Pointer buf, int count);
        int close(int fd);
    }

    // constants (from hci(7) / bluez headers)
    private static final int AF_BLUETOOTH = 31;       // PF_BLUETOOTH
    private static final int SOCK_RAW     = 3;        // raw
    private static final int BTPROTO_HCI  = 1;
    private static final short HCI_DEV_NONE = (short)0xffff;
    private static final short HCI_CHANNEL_MONITOR = 0x02; // passive monitor
    // monitor header（hci_mon_hdr）
    private static final int MON_HDR_SIZE = 6;
    private static final int MON_OPCODE_EVENT = 3;    // HCI_MON_EVENT_PKT

    private final Thread th = new Thread(this, "hci-monitor");
    private volatile boolean running;
    private int fd = -1;

    public void start() {
        if (running) return;
        // sockaddr_hci
        jnr.ffi.Runtime rt = jnr.ffi.Runtime.getSystemRuntime(); // java.lang.Runtimeと名前がかぶるので完全修飾名
        Pointer sa = Memory.allocate(rt, 6); // sa_family(2) + dev(2) + channel(2)
        sa.putShort(0, (short)AF_BLUETOOTH);
        sa.putShort(2, HCI_DEV_NONE);
        sa.putShort(4, HCI_CHANNEL_MONITOR);

        fd = LibC.INSTANCE.socket(AF_BLUETOOTH, SOCK_RAW, BTPROTO_HCI);
        if (fd < 0) throw new RuntimeException("socket(AF_BLUETOOTH) failed");
        int rc = LibC.INSTANCE.bind(fd, sa, 6);
        if (rc < 0) throw new RuntimeException("bind(HCI_MONITOR) failed");

        running = true;
        th.setDaemon(true);
        th.start();
        System.out.println("[HCI] monitor started (no subprocess).");
    }

    @Override
    public void close() {
        running = false;
        try { th.join(300); } catch (InterruptedException ignore) {}
        if (fd >= 0) LibC.INSTANCE.close(fd);
        fd = -1;
    }

    @Override
    public void run() {
        jnr.ffi.Runtime rt = jnr.ffi.Runtime.getSystemRuntime();
        Pointer buf = Memory.allocate(rt, 4096);
        while (running) {
            int n = LibC.INSTANCE.read(fd, buf, 4096);
            if (n <= 0) continue;

            // parse monitor header
            int op   = Short.toUnsignedInt(buf.getShort(0));   // opcode
            int idx  = Short.toUnsignedInt(buf.getShort(2));   // index (unused here)
            int len  = Short.toUnsignedInt(buf.getShort(4));   // payload length
            if (MON_OPCODE_EVENT != op || n < MON_HDR_SIZE + len) continue;

            int off = MON_HDR_SIZE;
            // HCI Event packet begins here: evt(1), plen(1), params...
            int evt  = buf.getByte(off) & 0xFF;
            int plen = buf.getByte(off+1) & 0xFF;
            if (evt != 0x3E /* LE Meta */) continue;

            int subevt = buf.getByte(off+2) & 0xFF;
            if (subevt == 0x02) { // LE Advertising Report
                parseLegacyAdv(buf, off+3, plen-1);
            } else if (subevt == 0x0D) { // LE Extended Advertising Report
                parseExtAdv(buf, off+3, plen-1);
            } else if (subevt == 0x0F) { // LE Periodic Advertising Report
                parsePerAdv(buf, off+3, plen-1);
            }
        }
    }

    private static String hex(Pointer p, int pos, int len) {
        StringBuilder sb = new StringBuilder(len*2);
        for (int i=0;i<len;i++) sb.append(String.format("%02x", p.getByte(pos+i)));
        return sb.toString();
    }
    private static String mac(Pointer p, int pos) { // little-endian in HCI
        return String.format("%02X:%02X:%02X:%02X:%02X:%02X",
                p.getByte(pos+5)&0xFF, p.getByte(pos+4)&0xFF, p.getByte(pos+3)&0xFF,
                p.getByte(pos+2)&0xFF, p.getByte(pos+1)&0xFF, p.getByte(pos)&0xFF);
    }

    // --- parsers (最小限: addr / rssi / AD生データ) ---
    private void parseLegacyAdv(Pointer p, int pos, int len) {
        int num = p.getByte(pos) & 0xFF; pos++; len--;
        for (int i=0;i<num;i++) {
            if (len < 10) return;
            int evtType = p.getByte(pos) & 0xFF; pos++;
            int addrType= p.getByte(pos) & 0xFF; pos++;
            String addr = mac(p, pos); pos += 6;
            int dlen    = p.getByte(pos) & 0xFF; pos++;
            String data = hex(p, pos, dlen); pos += dlen;
            int rssi    = (byte)p.getByte(pos); pos++;
            len -= (1+1+6+1+dlen+1);

            System.out.printf("{\"src\":\"hci\",\"addr\":\"%s\",\"rssi\":%d,\"raw\":\"%s\",\"ts\":\"%s\"}%n",
                    addr, rssi, data, Instant.now());
        }
    }

    private void parseExtAdv(Pointer p, int pos, int len) {
        int num = p.getByte(pos) & 0xFF; pos++; len--;
        for (int i=0;i<num;i++) {
            if (len < 24) return;
            int type     = Short.toUnsignedInt(p.getShort(pos)); pos+=2;
            int addrType = p.getByte(pos++) & 0xFF;
            String addr  = mac(p, pos); pos+=6;
            int priPhy   = p.getByte(pos++) & 0xFF;
            int secPhy   = p.getByte(pos++) & 0xFF;
            int sid      = p.getByte(pos++) & 0xFF;
            int tx       = (byte)p.getByte(pos++); // dBm
            int rssi     = (byte)p.getByte(pos++);
            int interval = Short.toUnsignedInt(p.getShort(pos)); pos+=2;
            int daddrT   = p.getByte(pos++) & 0xFF;
            String daddr = mac(p, pos); pos+=6;
            int dlen     = p.getByte(pos++) & 0xFF;
            String data  = hex(p, pos, dlen); pos+=dlen;
            len -= (2+1+6+1+1+1+1+1+2+1+6+1+dlen);

            System.out.printf("{\"src\":\"hci\",\"addr\":\"%s\",\"rssi\":%d,\"tx\":%d,"
                    + "\"etype\":%d,\"sid\":%d,\"raw\":\"%s\",\"ts\":\"%s\"}%n",
                    addr, rssi, tx, type, sid, data, Instant.now());
        }
    }

    private void parsePerAdv(Pointer p, int pos, int len) {
        if (len < 7) return;
        int sync = Short.toUnsignedInt(p.getShort(pos)); pos+=2;
        int tx   = (byte)p.getByte(pos++); 
        int rssi = (byte)p.getByte(pos++);
        int cte  = p.getByte(pos++) & 0xFF;
        int st   = p.getByte(pos++) & 0xFF;
        int dlen = p.getByte(pos++) & 0xFF;
        String data = hex(p, pos, dlen);
        System.out.printf("{\"src\":\"hci\",\"per\":%d,\"rssi\":%d,\"tx\":%d,\"raw\":\"%s\",\"ts\":\"%s\"}%n",
                sync, rssi, tx, data, Instant.now());
    }
}
