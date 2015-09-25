package net.joshuazhang.dtclient;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.MqttTopic;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * 发布音频采集数据到MQTT broker的线程
 */
public class MQTTPubAudio extends Thread {
    private static final String LOG_TAG = MainActivity.LOG_TAG;
    MqttClient pubClient;
    MqttTopic pubTopic;
    MqttMessage pubMsg;
    MqttDeliveryToken pubToken;
    int msgUpdateCNT=0;

    public MQTTPubAudio () {
        try {
            Log.i(LOG_TAG, "Broker地址为：" + MQTTPubCons.TCPADDR);
            Log.i(LOG_TAG, "ClientID为：" + MQTTPubCons.CLIENTID);
            pubClient = new MqttClient(MQTTPubCons.TCPADDR, MQTTPubCons.CLIENTID, new MemoryPersistence());
            // TODO 为什么这里不加 new MemoryPersistence() 会出错？
            pubClient.connect();
            Log.i(LOG_TAG, "连接到MQTT broker" + MQTTPubCons.TCPADDR);
            pubTopic = pubClient.getTopic(MQTTPubCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "发布主题" + MQTTPubCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "音频采集发布线程成功创建");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void run() {
        try {
            int oldMsgUpdateCNT = msgUpdateCNT;
            Log.i(LOG_TAG, "音频采集发布线程开始运行——发布数据");
            while (MainActivity.isRecording) {
                if (oldMsgUpdateCNT != msgUpdateCNT) { // 来了新数据则重新发布
                    pubMsg.setQos(MQTTPubCons.QoS0);
                    pubToken = pubTopic.publish(pubMsg);
                    Log.i(LOG_TAG, "发布数据");
                    pubToken.waitForCompletion(MQTTPubCons.SLEEPTIMEOUT);
                    oldMsgUpdateCNT = msgUpdateCNT;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
