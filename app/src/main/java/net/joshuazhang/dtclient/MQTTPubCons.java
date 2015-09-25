package net.joshuazhang.dtclient;

/**
 * Created by voidmous on 2015/9/18.
 *
 */
public final class MQTTPubCons {
    public static String TCPADDR = "tcp://test.mosquitto.org:1883";
    public static final String CLIENTID = MainActivity.DEVICE_NAME;
    public static final int SLEEPTIMEOUT = 100;
    //0: The broker/client will deliver the message once, with no confirmation.
    //1: The broker/client will deliver the message at least once, with confirmation required.
    //2: The broker/client will deliver the message exactly once by using a four step handshake.
    public static final int QoS0 = 0;
    public static final int QoS1 = 1;
    public static final int QoS2 = 2;
    public static final boolean RETAINED = false;
    public static final String TOPIC_AUDIO_PUB = "audio/record/"+CLIENTID; // 录制音频流发布主题
    public static final String TOPIC_LOCATION_PUB = "audio/location/"+CLIENTID; // 位置坐标发布主题
    public static final String TOPIC_CONTROL_SUB = "audio/control"; // 录制状态控制订阅主题

}
