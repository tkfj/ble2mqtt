package jp.fjworks.ble2mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

final class MqttSubscriber implements AutoCloseable, Runnable {
    private final MqttClient client;
    private final String topic;
    private final Thread thread;
    private volatile boolean running = false;

    MqttSubscriber(Main.MqttCfg cfg) throws MqttException {
        this.topic = cfg.topic;
        this.client = new MqttClient(cfg.broker, cfg.clientIdPrefix + "-sub-" + UUID.randomUUID(), new MemoryPersistence());
        this.thread = new Thread(this, "mqtt-sub");
        this.thread.setDaemon(false); // 前面プロセスで管理
        client.setCallback(new MqttCallback() {
            public void connectionLost(Throwable cause) { System.out.println("[SUB] lost: " + cause); }
            public void messageArrived(String t, MqttMessage msg) {
                System.out.printf("[SUB] %s | qos=%d | %s%n", t, msg.getQos(),
                        new String(msg.getPayload(), StandardCharsets.UTF_8));
            }
            public void deliveryComplete(IMqttDeliveryToken token) { }
        });
    }

    void start(Main.MqttCfg cfg) throws MqttException {
        var opt = new MqttConnectOptions();
        opt.setAutomaticReconnect(true);
        opt.setCleanSession(true);
        if (cfg.username != null) opt.setUserName(cfg.username);
        if (cfg.password != null) opt.setPassword(cfg.password.toCharArray());
        client.connect(opt);
        client.subscribe(topic, 0);
        System.out.println("[SUB] connected & subscribed to " + topic);
        running = true;
        thread.start();
    }

    @Override public void run() {
        // Paho は内部で受信スレッドが動くのでここでは待機だけ
        try {
            while (running) Thread.sleep(60_000);
        } catch (InterruptedException ignored) { }
    }

    @Override public void close() {
        running = false;
        thread.interrupt();
        try { if (client.isConnected()) client.unsubscribe(topic); } catch (Exception ignore) {}
        try { if (client.isConnected()) client.disconnect(); } catch (Exception ignore) {}
        try { client.close(); } catch (Exception ignore) {}
        System.out.println("[SUB] closed");
    }
}
