package jp.fjworks.ble2mqtt;

import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import jp.fjworks.ble2mqtt.Main.MqttCfg;
import jp.fjworks.ble2mqtt.adv.Adv;

public class MqttPublisher implements Runnable, AutoCloseable {
    private MqttClient client = null;
    private String cid;
    private String topic;
    private BlockingQueue<Adv> inQ;
    public MqttPublisher(MqttCfg cfg, BlockingQueue<Adv> inQ) throws IOException {
        this.cid = cfg.clientIdPrefix + "-pub-" + UUID.randomUUID();
        this.inQ = inQ;
        this.topic = cfg.topic;
        try {
            this.client = new MqttClient(cfg.broker, cid, new MemoryPersistence());
            var opt = new MqttConnectOptions();
            opt.setAutomaticReconnect(true);
            opt.setCleanSession(true);
            if (cfg.username != null) opt.setUserName(cfg.username);
            if (cfg.password != null) opt.setPassword(cfg.password.toCharArray());
            client.connect(opt);
            System.out.println("[PUB] connected to " + cfg.broker);
        } catch (MqttException e) {
            throw new IOException(e);
        }
    }
    
    private void publish(String payload) throws IOException {
        try {
            client.publish(topic, new MqttMessage(payload.getBytes(StandardCharsets.UTF_8)));
            // System.out.printf("[PUB] %s -> %s%n", topic, payload);
        }
        catch (MqttException e) {
            throw new IOException(e);
        }
    }

    @Override
    public void close() throws IOException {
        if (client != null) {
            try { if (client.isConnected()) client.disconnect(); } catch (Exception ignore) {}
            try { client.close(); } catch (Exception ignore) {}
        }
    }

    @Override
    public void run() {
        try {
            while(true) {
                Adv adv = inQ.take();
                try {
                    publish(adv.toJsonString());
                }
                catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (InterruptedException e) {
            //nop break
        }
    }
}