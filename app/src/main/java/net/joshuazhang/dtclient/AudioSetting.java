package net.joshuazhang.dtclient;

import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * 音频录制及播放参数设置类
 *
 * 用于创建RecordAudio和PlayAudio实例
 */
public class AudioSetting {
    public int frequency;
    public int channelConfiguration;
    public int audioEncoding;
    public DataOutputStream dos = null;
    public DataInputStream dis = null;

    /**
     * 构造参数设置对象
     * @param frequency 采样/播放频率，
     * @param channelConfiguration 单/双声道，采样要使用AudioFormat.CHANNEL_IN_MONO，
     *                             播放要使用AudioFormat.CHANNEL_OUT_MONO
     * @param audioEncoding 8/16位PCM
     */
    public AudioSetting(int frequency, int channelConfiguration, int audioEncoding) {
        this.frequency = frequency;
        this.channelConfiguration = channelConfiguration;
        this.audioEncoding = audioEncoding;
    }

    public int getFrequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        this.frequency = frequency;
    }

    public int getChannelConfiguration() {
        return channelConfiguration;
    }

    public void setChannelConfiguration(int channelConfiguration) {
        this.channelConfiguration = channelConfiguration;
    }

    public int getAudioEncoding() {
        return audioEncoding;
    }

    public void setAudioEncoding(int audioEncoding) {
        this.audioEncoding = audioEncoding;
    }

    public DataOutputStream getDos() {
        return dos;
    }

    public void setDos(DataOutputStream dos) {
        this.dos = dos;
    }

    public DataInputStream getDis() {
        return dis;
    }

    public void setDis(DataInputStream dis) {
        this.dis = dis;
    }
}
