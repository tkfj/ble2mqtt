package jp.fjworks.ble2mqtt;

import org.freedesktop.dbus.annotations.DBusInterfaceName;
import org.freedesktop.dbus.interfaces.DBusInterface;
import org.freedesktop.dbus.interfaces.Properties;
import org.freedesktop.dbus.types.Variant;
import org.freedesktop.dbus.connections.impl.DBusConnection;
import org.freedesktop.dbus.connections.impl.DBusConnectionBuilder;

import java.util.Map;
import java.util.HashMap;

public final class BluezScanControl implements AutoCloseable {
  @DBusInterfaceName("org.bluez.Adapter1")
  public interface Adapter1 extends DBusInterface {
    void StartDiscovery();
    void StopDiscovery();
    void SetDiscoveryFilter(Map<String, Variant<?>> filter);
  }

  private final String adapterPath;
  private final boolean autoPowerOn;               // Powered=false のときに起こすか
  private final Map<String, Variant<?>> filter;    // 適用したいフィルタ（任意）
  private DBusConnection sys;
  private Adapter1 a1;
  private Properties props;
  private boolean startedByUs = false;

  public BluezScanControl(String hciIndex, boolean autoPowerOn, Map<String, Variant<?>> filter) {
    this.adapterPath = "/org/bluez/hci" + hciIndex;
    this.autoPowerOn = autoPowerOn;
    this.filter = (filter == null) ? new HashMap<>() : filter;
  }

  /** D-Bus 接続とリモートオブジェクトを準備（多重呼び出し可） */
  private void ensureConn() throws Exception {
    if (sys != null) return;
    sys = DBusConnectionBuilder.forSystemBus().build();
    a1 = sys.getRemoteObject("org.bluez", adapterPath, Adapter1.class);
    props = sys.getRemoteObject("org.bluez", adapterPath, Properties.class);
  }

  /** 現在スキャン中かどうかを返す（Discovering プロパティ） */
  public boolean isScanning() {
    try {
      ensureConn();
      Object v = props.Get("org.bluez.Adapter1", "Discovering");
      boolean on = toBool(v);
      System.out.println("[DBus] Discovering=" + on + " on " + adapterPath);
      return on;
    } catch (Exception e) {
      System.err.println("[DBus] isScanning failed: " + e.getMessage());
      return false;
    }
  }

  /** 必要ならスキャンを開始（既に動いていれば何もしない） */
  public void ensureScanning() throws Exception {
    ensureConn();

    // 必要に応じて電源ON（Powered=false の環境対策）
    if (autoPowerOn) {
      try {
        Object pv = props.Get("org.bluez.Adapter1", "Powered");
        boolean powered = toBool(pv);
        if (!powered) {
          props.Set("org.bluez.Adapter1", "Powered", new Variant<>(Boolean.TRUE));
          System.out.println("[DBus] Powered -> true");
        }
      } catch (Exception e) {
        System.out.println("[DBus] Power-on skipped: " + e.getMessage());
      }
    }

    if (isScanning()) return;

    // 任意フィルタ（未対応キーは無視）
    if (!filter.isEmpty()) {
      try { a1.SetDiscoveryFilter(filter); }
      catch (Exception e) { System.out.println("[DBus] SetDiscoveryFilter ignored: " + e.getMessage()); }
    }

    a1.StartDiscovery();
    startedByUs = true;
    System.out.println("[DBus] StartDiscovery (by us) on " + adapterPath);
  }

  /** 自分が起動した場合のみ停止 */
  @Override public void close() {
    try {
      if (startedByUs) {
        a1.StopDiscovery();
        System.out.println("[DBus] StopDiscovery (by us) on " + adapterPath);
      } else {
        System.out.println("[DBus] scan stop skipped (was not started by us)");
      }
    } catch (Exception e) {
      System.out.println("[DBus] StopDiscovery skipped: " + e.getMessage());
    }
    try { if (sys != null) sys.close(); } catch (Exception ignore) {}
    sys = null; a1 = null; props = null; startedByUs = false;
  }

  /** お手軽なデフォルト生成ヘルパ（LE/弱RSSI/重複許可） */
  public static BluezScanControl withDefaults(String hciIndex) {
    Map<String, Variant<?>> f = new HashMap<>();
    f.put("Transport",     new Variant<>("le"));
    f.put("RSSI",          new Variant<>((short) -127));
    // DuplicateData は BlueZ バージョンにより未対応の場合あり
    try { f.put("DuplicateData", new Variant<>(Boolean.TRUE)); } catch (Exception ignored) {}
    return new BluezScanControl(hciIndex, /*autoPowerOn*/ true, f);
  }
  private static boolean toBool(Object o) {
    if (o instanceof Boolean) return (Boolean) o;
    if (o instanceof org.freedesktop.dbus.types.Variant)
      return toBool(((org.freedesktop.dbus.types.Variant<?>) o).getValue());
    return false;
  }
}
