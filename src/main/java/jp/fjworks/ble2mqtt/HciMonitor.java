package jp.fjworks.ble2mqtt;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Collection;

import jnr.ffi.LibraryLoader;
import jnr.ffi.Memory;
import jnr.ffi.Pointer;
import jp.fjworks.ble2mqtt.adv.Adv;
import jp.fjworks.ble2mqtt.adv.AdvParsers;

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
        System.out.println("[HCI] monitor started.");
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
        final int MEM_SIZE = 4096;
        jnr.ffi.Runtime rt = jnr.ffi.Runtime.getSystemRuntime();
        Pointer buf = Memory.allocate(rt, MEM_SIZE);
        AdvParsers parser = AdvParsers.getInstance();
        while (running) {
            int n = LibC.INSTANCE.read(fd, buf, MEM_SIZE);
            if (n <= 0) continue; //TODO NIO的な待ちかたをさせたい。たぶんサイズ１のブロッキングキューに突っ込むが正解。（でも空ループは止まらないのでは。）
            ByteBuffer bbuf;
            if(buf.hasArray()) {
                bbuf = ByteBuffer.wrap((byte[])buf.array()).order(ByteOrder.LITTLE_ENDIAN);
            }else {
                bbuf = ByteBuffer.wrap(new byte[(int)buf.size()]).order(ByteOrder.LITTLE_ENDIAN);
                buf.get(0, bbuf.array(), 0,(int)buf.size());
            }

            // parse monitor header
            int op   = Short.toUnsignedInt(bbuf.getShort(0));   // opcode
            int idx  = Short.toUnsignedInt(bbuf.getShort(2));   // index (unused here)
            int len  = Short.toUnsignedInt(bbuf.getShort(4));   // payload length
            if (MON_OPCODE_EVENT != op || n < MON_HDR_SIZE + len) continue;

            int off = MON_HDR_SIZE;
            // HCI Event packet begins here: evt(1), plen(1), params...

            Collection<Adv> advs = parser.parse(bbuf, off);
        }
    }

}
