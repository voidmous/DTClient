package net.joshuazhang.dtclient;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttException;
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

    /**
     * 构造MQTT发布音频采集数据线程
     */
    public MQTTPubAudio () {
        try {
            Log.i(LOG_TAG, "Broker地址为：" + MQTTCons.TCPADDR);
            Log.i(LOG_TAG, "ClientID为：" + MQTTCons.CLIENTID);
            pubClient = new MqttClient(MQTTCons.TCPADDR, MQTTCons.CLIENTID, new MemoryPersistence());
            // TODO 为什么这里不加 new MemoryPersistence() 会出错？
            // TODO 显示连接过程，并提示是否正常连接
            pubClient.connect();
            Log.i(LOG_TAG, "连接到MQTT broker " + MQTTCons.TCPADDR);
            pubTopic = pubClient.getTopic(MQTTCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "发布主题" + MQTTCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "音频采集发布线程成功创建");
        } catch (MqttException mqtte) {
            mqtte.printStackTrace();
            Log.e(LOG_TAG, "创建MQTT连接出错");
        }
    }

    public void run() {
        try {
            // msgUpdateCNT由音频采集线程更新（自增一）
            int oldMsgUpdateCNT = msgUpdateCNT;
            Log.i(LOG_TAG, "音频采集发布线程开始运行——发布数据");
            while (MainActivity.isRecording) {
                if (oldMsgUpdateCNT != msgUpdateCNT) { // 来了新数据则重新发布
                    pubMsg.setQos(MQTTCons.QoS0);
                    pubToken = pubTopic.publish(pubMsg);
                    Log.i(LOG_TAG, "发布数据");
                    pubToken.waitForCompletion(MQTTCons.SLEEPTIMEOUT);
                    oldMsgUpdateCNT = msgUpdateCNT;
                }
            }
        } catch (MqttException mqtte) {
            mqtte.printStackTrace();
            Log.e(LOG_TAG, "MQTT发布数据出错");
        }

    }
}
