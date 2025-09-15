package jp.fjworks.ble2mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;

import jp.fjworks.ble2mqtt.adv.Adv;

public class Main {
  public static void main(String[] args) throws Exception {
    var cfg = MqttCfg.fromEnv();

    CountDownLatch quit = new CountDownLatch(1);
    MqttSubscriber sub = new MqttSubscriber(cfg);

    String hciIndex = System.getenv().getOrDefault("BLE_HCI_INDEX", "0");
    boolean manageScan = "1".equals(System.getenv().getOrDefault("BLE_MANAGE_SCAN", "1"));

    BluezScanControl scanCtrl = manageScan ?  BluezScanControl.withDefaults(hciIndex) : null;
    if (scanCtrl != null) {
      try { scanCtrl.ensureScanning(); }
      catch (Exception e) {
        System.err.println("[DBus] ensureScanning failed: " + e.getMessage());
      }
    }

    BlockingQueue<Adv> q = new ArrayBlockingQueue<>(1);

    MqttPublisher publisher = new MqttPublisher(cfg, q);
    Thread publishTh = new Thread(publisher);
    publishTh.setDaemon(true);

    // 既存: HciMonitor や MQTT ブリッジの起動…
    HciMonitor hciMon = new HciMonitor(q);
    hciMon.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try { hciMon.close(); } catch (Exception ignore) {}
      try { if (scanCtrl != null) scanCtrl.close(); } catch (Exception ignore) {}
      try { sub.close(); } catch (Exception ignore) {}
      try { publisher.close(); } catch (Exception ignore) {}
      quit.countDown();
    }, "shutdown"));

    sub.start(cfg);
    publishTh.start();
    hciMon.start();
    quit.await();
  }


  static class MqttCfg {
    final String broker, topic, username, password, clientIdPrefix;
    MqttCfg(String b,String t,String u,String p,String c){broker=b;topic=t;username=u;password=p;clientIdPrefix=c;}
    static MqttCfg fromEnv() {
      String b = env("MQTT_BROKER","tcp://127.0.0.1:11883");
      String t = env("MQTT_TOPIC","test/echo");
      String u = env("MQTT_USER",null);
      String p = env("MQTT_PASS",null);
      String c = env("MQTT_CLIENT_ID_PREFIX","java-sample");
      return new MqttCfg(b,t,u,p,c);
    }
    static String env(String k,String d){ var v=System.getenv(k); return (v==null||v.isBlank())?d:v; }
  }
}
