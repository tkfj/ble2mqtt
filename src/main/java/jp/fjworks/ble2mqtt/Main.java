package jp.fjworks.ble2mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public class Main {
    public static void main(String[] args) throws Exception {
        var cfg = MqttCfg.fromEnv();

        // 別スレッド（デーモン）でサブスクライブ待機
        Thread subThread = new Thread(() -> runSubscriber(cfg), "mqtt-sub");
        subThread.setDaemon(true);
        subThread.start();

        // メインでパブリッシュ（1回 or 周期）
        Thread.sleep(1000);
        runPublisher(cfg);

        // 終了させたくなければ待機
        Thread.currentThread().join();
    }

    private static void runSubscriber(MqttCfg cfg) {
        String cid = cfg.clientIdPrefix + "-sub-" + UUID.randomUUID();
        try (var client = new MqttClient(cfg.broker, cid, new MemoryPersistence())) {
            var opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            opt.setCleanSession(true);
            if (cfg.username != null) opt.setUserName(cfg.username);
            if (cfg.password != null) opt.setPassword(cfg.password.toCharArray());

            client.setCallback(new MqttCallback() {
                public void connectionLost(Throwable cause) {
                    System.out.println("[SUB] connection lost: " + cause);
                }
                public void messageArrived(String topic, MqttMessage msg) {
                    System.out.printf("[SUB] %s | qos=%d | %s%n",
                        topic, msg.getQos(), new String(msg.getPayload(), StandardCharsets.UTF_8));
                }
                public void deliveryComplete(IMqttDeliveryToken token) {}
            });

            client.connect(opt);
            client.subscribe(cfg.topic, 0);
            System.out.println("[SUB] connected & subscribed to " + cfg.topic);

            // 永久待機（Pahoが内部スレッドで受信継続）
            while (true) Thread.sleep(60_000);
        } catch (Exception e) {
            System.err.println("[SUB] error: " + e);
        }
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
            if (interval <= 0) {
                publishOnce(client, cfg.topic);
            } else {
                System.out.println("[PUB] interval=" + interval + "s (Ctrl+C to stop)");
                while (true) {
                    publishOnce(client, cfg.topic);
                    Thread.sleep(interval * 1000L);
                }
            }
        } finally {
            if (client != null) {
                try {
                    if (client.isConnected()) client.disconnect(); // ← これが重要
                } catch (Exception ignore) {
                    // うまく切れない場合は最終手段:
                    try { client.disconnectForcibly(2000, 2000, true); } catch (Exception ignored) {}
                }
                try { client.close(); } catch (Exception ignored) {}
            }
        }
    }
    private static void publishOnce(MqttClient client, String topic) throws MqttException {
        String payload = "hello from java @ " + Instant.now();
        var msg = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        msg.setQos(0);
        client.publish(topic, msg);
        System.out.printf("[PUB] %s -> %s%n", topic, payload);
    }

    static class MqttCfg {
        final String broker, topic, username, password, clientIdPrefix;
        final int publishIntervalSec;
        private MqttCfg(String b, String t, String u, String p, String cid, int iv) {
            broker=b; topic=t; username=u; password=p; clientIdPrefix=cid; publishIntervalSec=iv;
        }
        static MqttCfg fromEnv() {
            String b = getenv("MQTT_BROKER", "tcp://127.0.0.1:11883");
            String t = getenv("MQTT_TOPIC",  "test/echo");
            String u = getenv("MQTT_USER",   null);
            String p = getenv("MQTT_PASS",   null);
            String c = getenv("MQTT_CLIENT_ID_PREFIX", "java-sample");
            int iv = Integer.parseInt(getenv("MQTT_PUB_INTERVAL_SEC", "0"));
            return new MqttCfg(b,t,u,p,c,iv);
        }
        private static String getenv(String k, String def) {
            String v = System.getenv(k);
            return (v == null || v.isBlank()) ? def : v;
        }
    }
}
