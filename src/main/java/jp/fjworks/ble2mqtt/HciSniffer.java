package jp.fjworks.ble2mqtt;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class HciSniffer implements Runnable, AutoCloseable {
    private final Thread th = new Thread(this, "hci-sniffer");
    private volatile boolean running = false;
    private Process proc;

    // スキャン維持用（btmgmt / bluetoothctl）
    private Process scanProc;
    private Process btctlProc;
    private java.io.OutputStream btctlIn;
    private final String hciIndex = System.getenv().getOrDefault("BLE_HCI_INDEX", "0");

    // 任意フィルタ
    private final String addrRegex = System.getenv().getOrDefault("BLE_ADDR_REGEX", "");
    private final Integer minRssi = parseIntOrNull(System.getenv("BLE_MIN_RSSI"));
    private final boolean debug = "1".equals(System.getenv().getOrDefault("BLE_HCI_DEBUG", "0"));

    void start() {
        if (running) return;
        running = true;
        th.setDaemon(true);

        // スキャンON
        if (!startScanWithBtmgmt()) startScanWithBluetoothctl();

        th.start();
    }

    @Override public void close() {
        running = false;
        try { if (proc != null) proc.destroy(); } catch (Exception ignore) {}
        try { if (scanProc != null) scanProc.destroy(); } catch (Exception ignore) {}
        try {
            if (btctlIn != null) {
                btctlIn.write("scan off\nquit\n".getBytes());
                btctlIn.flush();
            }
            if (btctlProc != null) btctlProc.destroy();
        } catch (Exception ignore) {}
        try { th.join(500); } catch (InterruptedException ignore) {}
        System.out.println("[HCI] stopped");
    }

    @Override public void run() {
        try {
            // btmon 起動（stderr も取り込む）
            ProcessBuilder pb = new ProcessBuilder("stdbuf", "-oL", "btmon");
            pb.redirectErrorStream(true);
            try { proc = pb.start(); }
            catch (Exception e) {
                proc = new ProcessBuilder("btmon").redirectErrorStream(true).start();
            }

            System.out.println("[HCI] btmon started");

            try (var br = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                String line;
                // 現在のレコード
                String addr = null, rssi = null, mfgId = null, mfgData = null, svcUuid = null, svcData = null;
                String name = null, flags = null;
                StringBuilder raw = new StringBuilder();
                Integer tx = null;

                Pattern pStart = Pattern.compile("LE (Extended )?Advertising Report|LE Periodic Advertising Report",
                                                Pattern.CASE_INSENSITIVE);
                Pattern pAddr  = Pattern.compile("\\bAddress:\\s*([0-9A-F]{2}(?::[0-9A-F]{2}){5})", Pattern.CASE_INSENSITIVE);
                Pattern pRssi  = Pattern.compile("\\bRSSI:\\s*(-?\\d+)\\s*dBm", Pattern.CASE_INSENSITIVE);
                Pattern pMfg   = Pattern.compile("\\b(Company|Manufacturer):\\s.*\\((0x[0-9A-Fa-f]+)\\)");
                Pattern pSvc   = Pattern.compile("\\bService Data \\(UUID ([-0-9a-fA-F]+)\\):");
                Pattern pData  = Pattern.compile("\\bData:\\s*([0-9a-f ]+)");
                Pattern pFlags = Pattern.compile("\\bFlags:\\s*0x([0-9a-fA-F]+)");
                Pattern pName  = Pattern.compile("\\bName \\((?:complete|shortened)\\):\\s*(.+)", Pattern.CASE_INSENSITIVE);
                Pattern pTx    = Pattern.compile("\\bTX power:\\s*(-?\\d+)\\s*dBm", Pattern.CASE_INSENSITIVE);

                while (running && (line = br.readLine()) != null) {
                    if (debug) System.out.println("[RAW] " + line);

                    // 新しいレポート開始 → 直前のレコードをemitしてリセット
                    // ループ中の分岐に追加
                    if (pStart.matcher(line).find()) {
                        emit(addr, rssi, mfgId, mfgData, svcUuid, svcData, name, flags, tx, raw);
                        addr = rssi = mfgId = mfgData = svcUuid = svcData = name = flags = null;
                        tx = null; raw.setLength(0);
                        continue;
                    }
                    if (line.isEmpty()) {
                        emit(addr, rssi, mfgId, mfgData, svcUuid, svcData, name, flags, tx, raw);
                        addr = rssi = mfgId = mfgData = svcUuid = svcData = name = flags = null;
                        tx = null; raw.setLength(0);
                        continue;
                    }

                    Matcher m;
                    if ((m = pAddr.matcher(line)).find()) addr = m.group(1).toUpperCase();
                    else if ((m = pRssi.matcher(line)).find()) rssi = m.group(1);
                    else if ((m = pMfg.matcher(line)).find()) { mfgId = m.group(2).toUpperCase(); mfgData = null; }
                    else if ((m = pSvc.matcher(line)).find()) { svcUuid = m.group(1).toLowerCase(); svcData = null; }
                    else if ((m = pFlags.matcher(line)).find()) flags = "0x" + m.group(1).toUpperCase();
                    else if ((m = pName.matcher(line)).find())  name  = m.group(1);
                    else if ((m = pTx.matcher(line)).find())    tx    = Integer.parseInt(m.group(1));
                    else if ((m = pData.matcher(line)).find()) {
                        String hex = m.group(1).replace(" ", "");
                        // 既存の特定データへも割当
                        if (mfgId != null && mfgData == null) mfgData = hex;
                        else if (svcUuid != null && svcData == null) svcData = hex;
                        // ★ 生ペイロードとしても蓄積
                        if (hex.length() > 0) {
                            if (raw.length() > 0) raw.append('|'); // 複数ブロックを '|' 区切りで
                            raw.append(hex);
                        }
                    }
                }
                // 終了時に残りがあれば吐く
                emit(addr, rssi, mfgId, mfgData, svcUuid, svcData, name, flags, tx, raw);
            }
        } catch (Exception e) {
            System.err.println("[HCI] error: " + e.getMessage());
        }
    }

    // ---- scan control ----
    private boolean startScanWithBtmgmt() {
        try {
            String cmd = String.join(" ",
                "btmgmt", "--index", hciIndex, "power", "on", ">/dev/null","2>&1",";",
                "btmgmt", "--index", hciIndex, "le", "on", ">/dev/null","2>&1",";",
                "exec", "btmgmt", "--index", hciIndex, "find", "-l"
            );
            scanProc = new ProcessBuilder("bash", "-lc", cmd)
                .redirectErrorStream(true)
                .start();
            System.out.println("[HCI] scanning via btmgmt on hci" + hciIndex);
            return true;
        } catch (Exception e) {
            System.out.println("[HCI] btmgmt not available: " + e.getMessage());
            return false;
        }
    }

    private void startScanWithBluetoothctl() {
        try {
            btctlProc = new ProcessBuilder("bluetoothctl")
                .redirectErrorStream(true)
                .start();
            btctlIn = btctlProc.getOutputStream();
            btctlIn.write(("power on\nscan on\n").getBytes());
            btctlIn.flush();
            System.out.println("[HCI] scanning via bluetoothctl (interactive hold)");
        } catch (Exception e) {
            System.err.println("[HCI] failed to start scan via bluetoothctl: " + e.getMessage());
        }
    }

    // ---- emit & helpers ----
    // emit シグネチャと本文を拡張
    private void emit(String addr, String rssi, String mfgId, String mfgData,
                    String svcUuid, String svcData, String name, String flags,
                    Integer tx, StringBuilder raw) {
        if (addr == null && rssi == null && mfgId == null && svcUuid == null
            && (raw == null || raw.length() == 0) && name == null && flags == null && tx == null) return;

        if (!addrRegex.isBlank() && (addr == null || !addr.matches(addrRegex))) return;
        if (minRssi != null && rssi != null) {
            try { if (Integer.parseInt(rssi) < minRssi) return; } catch (NumberFormatException ignore) {}
        }

        StringBuilder sb = new StringBuilder(320);
        sb.append("{\"src\":\"hci\"");
        if (addr != null)  sb.append(",\"addr\":\"").append(addr).append("\"");
        if (rssi != null)  sb.append(",\"rssi\":").append(rssi);
        if (name != null)  sb.append(",\"name\":\"").append(escape(name)).append("\"");
        if (flags != null) sb.append(",\"flags\":\"").append(flags).append("\"");
        if (tx != null)    sb.append(",\"tx\":").append(tx);
        if (mfgId != null && mfgData != null)
            sb.append(",\"mfg\":{").append('"').append(mfgId).append('"').append(":\"").append(mfgData).append("\"}");
        if (svcUuid != null && svcData != null)
            sb.append(",\"svc\":{").append('"').append(svcUuid).append('"').append(":\"").append(svcData).append("\"}");
        if (raw != null && raw.length() > 0)
            sb.append(",\"raw\":\"").append(raw).append('"'); // ADブロックの hex を '|' 連結
        sb.append(",\"ts\":\"").append(Instant.now()).append("\"}");
        System.out.println(sb);
    }

private static String escape(String s){ return s.replace("\\","\\\\").replace("\"","\\\""); }

    private static Integer parseIntOrNull(String v) {
        if (v == null || v.isBlank()) return null;
        try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return null; }
    }
}
