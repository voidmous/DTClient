package net.joshuazhang.dtclient;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
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
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setCleanSession(true);
            pubClient.connect(connOpts);
            Log.i(LOG_TAG, "连接到MQTT broker " + MQTTCons.TCPADDR);
            pubTopic = pubClient.getTopic(MQTTCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "发布主题" + MQTTCons.TOPIC_AUDIO_PUB);
            Log.i(LOG_TAG, "音频采集发布线程成功创建");
        } catch (MqttException me) {
            Log.e(LOG_TAG, "创建MQTT连接出错");
            Log.e(LOG_TAG, "Reason: " + me.getReasonCode());
            Log.e(LOG_TAG, "Msg: " + me.getMessage());
            Log.e(LOG_TAG, "Loc: " + me.getLocalizedMessage());
            Log.e(LOG_TAG, "Cause: " + me.getCause());
            Log.e(LOG_TAG, "Exception: " + me);
            me.printStackTrace();
            MainActivity.forceExitApp(); // 如果创建连接失败，直接退出App，异常退出
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
                    // TODO 选择哪种QoS比较合适？
                    //QoS0可能导致部分采集数据丢失？
                    //QoS2可能导致传输中断、网络阻塞？
                    pubToken = pubTopic.publish(pubMsg);
                    //Log.i(LOG_TAG, "发布数据");
                    // TODO 需要等待数据发送完成吗？
                    // 好像和数据发送中断无关
                    pubToken.waitForCompletion(MQTTCons.SLEEPTIMEOUT);
                    Log.v(LOG_TAG, "第" + msgUpdateCNT + "段数据发布完成");
                    oldMsgUpdateCNT = msgUpdateCNT;
                }
            }
        } catch (MqttException me) {
            Log.e(LOG_TAG, "Reason: " + me.getReasonCode());
            Log.e(LOG_TAG, "Msg: " + me.getMessage());
            Log.e(LOG_TAG, "Loc: " + me.getLocalizedMessage());
            Log.e(LOG_TAG, "Cause: " + me.getCause());
            Log.e(LOG_TAG, "Exception: " + me);
            me.printStackTrace();
            Log.e(LOG_TAG, "MQTT发布数据出错");
        }

    }
}
