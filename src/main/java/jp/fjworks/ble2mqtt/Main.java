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

    Runnable printer = new Runnable() {
      @Override
      public void run() {
        try {
          while(true) {
            Adv adv = q.take();
            System.out.println(adv.toJsonString());
          }
        }
        catch (InterruptedException e) {
          //nop break
        }
      }
    };
    Thread printerTh = new Thread(printer);
    printerTh.setDaemon(true);
    printerTh.start();


    // 既存: HciMonitor や MQTT ブリッジの起動…
    HciMonitor hciMon = new HciMonitor(q);
    hciMon.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try { hciMon.close(); } catch (Exception ignore) {}
      try { if (scanCtrl != null) scanCtrl.close(); } catch (Exception ignore) {}
      try { sub.close(); } catch (Exception ignore) {}
      quit.countDown();
    }, "shutdown"));

    sub.start(cfg);
    hciMon.start();
    runPublisher(cfg); // 単発/周期は環境変数で制御（下の MqttCfg 参照）
    quit.await();
  }

  private static void runPublisher(MqttCfg cfg) throws Exception {
    String cid = cfg.clientIdPrefix + "-pub-" + UUID.randomUUID();
    MqttClient client = null;
    try {
      client = new MqttClient(cfg.broker, cid, new MemoryPersistence());
      var opt = new MqttConnectOptions();
      opt.setAutomaticReconnect(true);
      opt.setCleanSession(true);
      if (cfg.username != null) opt.setUserName(cfg.username);
      if (cfg.password != null) opt.setPassword(cfg.password.toCharArray());
      client.connect(opt);
      System.out.println("[PUB] connected to " + cfg.broker);

      int interval = cfg.publishIntervalSec;
      if (interval <= 0) publishOnce(client, cfg.topic);
      else {
        System.out.println("[PUB] interval=" + interval + "s (Ctrl+C to stop)");
        while (true) {
          publishOnce(client, cfg.topic);
          Thread.sleep(interval * 1000L);
        }
      }
    } finally {
      if (client != null) {
        try { if (client.isConnected()) client.disconnect(); } catch (Exception ignore) {}
        try { client.close(); } catch (Exception ignore) {}
      }
    }
  }

  private static void publishOnce(MqttClient client, String topic) throws MqttException {
    String payload = "hello from java @ " + Instant.now();
    client.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
    System.out.printf("[PUB] %s -> %s%n", topic, payload);
  }

  static class MqttCfg {
    final String broker, topic, username, password, clientIdPrefix; final int publishIntervalSec;
    MqttCfg(String b,String t,String u,String p,String c,int s){broker=b;topic=t;username=u;password=p;clientIdPrefix=c;publishIntervalSec=s;}
    static MqttCfg fromEnv() {
      String b = env("MQTT_BROKER","tcp://127.0.0.1:11883");
      String t = env("MQTT_TOPIC","test/echo");
      String u = env("MQTT_USER",null);
      String p = env("MQTT_PASS",null);
      String c = env("MQTT_CLIENT_ID_PREFIX","java-sample");
      int s = Integer.parseInt(env("MQTT_PUB_INTERVAL_SEC","0"));
      return new MqttCfg(b,t,u,p,c,s);
    }
    static String env(String k,String d){ var v=System.getenv(k); return (v==null||v.isBlank())?d:v; }
  }
}
