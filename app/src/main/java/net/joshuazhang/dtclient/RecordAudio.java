package net.joshuazhang.dtclient;

import android.content.Context;
import android.graphics.Color;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * 音频录制子线程AsyncTask类，使用后台进程录制并返回录制数据到前台UI绘制图形
 * TODO 把此类改写为服务？
 */
public class RecordAudio extends AsyncTask<Void, short[], Void> {

    private static final String LOG_TAG = MainActivity.LOG_TAG;
    private AudioSetting mRecordAS = null;
    private DataOutputStream dos = null;
    private AudioRecord audioRecord = null;
    private MainActivity activity = null;

    /**
     * 使用构造器传递录制参数
     * @param context TODO 为了引用MainActivity的实例？
     * @param as 音频采样参数设置对象
     */
    public RecordAudio(Context context, AudioSetting as) {
        mRecordAS = as;
        activity = (MainActivity) context;
        // 创建文件以保存音频数据
        if (MainActivity.saveDataToFile) {
            this.dos = mRecordAS.getDos();
        }
    }

    /**
     * 音频录制子线程任务，后台开始音频采集，并将采集数据push到UI进行绘制
     * 以及通过MQTT协议发送到broker
     * @param params void
     * @return void
     */
    @Override
    protected Void doInBackground (Void... params) {
        try {
            // 获取系统允许的最小缓冲区长度
            int bufferSize = AudioRecord.getMinBufferSize(
                    mRecordAS.frequency,
                    mRecordAS.channelConfiguration,
                    mRecordAS.audioEncoding);

            //创建AudioRecord实例
            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    mRecordAS.frequency,
                    mRecordAS.channelConfiguration,
                    mRecordAS.audioEncoding,
                    bufferSize);
            Log.i(LOG_TAG, "========采样设置参数========");
            Log.i(LOG_TAG, "音源为：" + audioRecord.getAudioSource());
            Log.i(LOG_TAG, "采样频率为：" + audioRecord.getSampleRate()+"Hz");
            Log.i(LOG_TAG, "声道设置为：" + audioRecord.getChannelCount() + "个声道");
            Log.i(LOG_TAG, "音频编码格式为：" + audioRecord.getAudioFormat() + "个字节");
            Log.i(LOG_TAG, "缓冲区大小为："+ bufferSize + "个字节");
            Log.i(LOG_TAG, "AudioRecord实例状态为：" + audioRecord.getState() + "，1为正常");
            Log.i(LOG_TAG, "========采样设置参数========");

            // TODO buffersize 和 blockSize需要满足什么大小关系吗？
            // buffer数据用作存储或传输
            //int blockSize=512;
            int blockSize = bufferSize;
            short[] buffer = new short[blockSize]; //16位short signed int，取值范围-32768~+32767
            Log.i(LOG_TAG, "数据读取缓冲short数组大小为：" + blockSize);


            // 开始录制音频
            audioRecord.startRecording();
            Log.i(LOG_TAG, "开始采集音频数据...");

            while (MainActivity.isRecording) { // isRecording控制录制状态
                //读取blockSize长度的数据
                int bufferReadResultLen = audioRecord.read(buffer, 0, blockSize);
                MainActivity.totalDataSize += bufferReadResultLen; //统计采样点数
                Log.v(LOG_TAG, "采样了" + bufferReadResultLen + "点数据");

                if (MainActivity.saveDataToFile) {
                    for (int i = 0; i < bufferReadResultLen; i++) {
                        dos.writeShort(buffer[i]); //写入两个字节到文件,big-endian
                    }
                }
                // 发布采集数据到主界面线程，由于采集不稳定，发布的数据长度也有可能变化
                // 这里会分配新的内存空间用于复制，效率有待观察
                // https://stackoverflow.com/questions/11001720/get-only-part-of-an-array-in-java
                publishProgress(Arrays.copyOfRange(buffer, 0, bufferReadResultLen));
            }

        } catch (IOException ioe) {
            Log.e(LOG_TAG, "IO异常，请检查");
        } catch (Exception e) {
            Log.e(LOG_TAG, "录制失败");
        } finally {
            audioRecord.stop(); // isRecording为false后停止录制
            Log.i(LOG_TAG, "AudioRecord实例已停止");
            audioRecord.release(); // 释放对象
            if ( audioRecord != null) {
                audioRecord = null; // 对象释放后必须设置引用为null
            }
            Log.i(LOG_TAG, "AudioRecord实例已释放资源");
            if (MainActivity.saveDataToFile) {
                if (this.dos != null) {
                    try {
                        this.dos.close();
                        Log.i(LOG_TAG, "关闭录制流成功");
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                        Log.e(MainActivity.LOG_TAG, "关闭文件流错误");
                    }
                }
            }
            Log.d(LOG_TAG, "RecordAudio->doInBackground()->finally");
            activity.printAppLog();
        }
        return null;
    }

    /**
     * 主UI动态绘制采样数据图形并转换数据到网络格式，
     * 数据格式转换参考：
     * <a href="https://stackoverflow.com/questions/2188660/convert-short-to-byte-in-java">
     *     Convert short to byte[] in Java - Stack Overflow</a>
     * @param progress 录音线程发布的short[]数据
     */
    @Override
    protected void onProgressUpdate(short[]... progress) {

        // 背景绘制，清空画布
        MainActivity.canvasPCM.drawColor(Color.WHITE);

        // 将16位short类型的buffer数据转为double类型，
        // audioData是归一化的buffer数据，取值范围为[-1.0, 1.0)，用于绘图
        double[] audioData=new double[progress[0].length];

        // data保存转换后的byte[]，用于 MQTT 发送
        byte[] data = new byte[progress[0].length * 2];

        //绘制PCM波形和分贝图形
        for (int i = 0; i < progress[0].length; i++) {
            audioData[i]=(double) progress[0][i] / 32768.0;
            // 将一个short转为网络传输的两个byte，big-endian编码
            data[i*2]=(byte)(progress[0][i]>>8);
            data[i*2+1]=(byte) (progress[0][i]&0xFF);
            int downy = (int) (100 - (audioData[i] * 100));
            int upy = 100;
            MainActivity.canvasPCM.drawLine(i, downy, i, upy, MainActivity.paintPCM);
        }
        MainActivity.imageViewPCM.invalidate(); //更新PCM波形图

        if (MainActivity.sendDataWithMQTT) {
            // 发送采集的数据到 MQTT broker
            MainActivity.pubThread.pubMsg=new MqttMessage(data);
            MainActivity.pubThread.msgUpdateCNT += 1; // 更新数据更新FLAG
            Log.v(LOG_TAG, "第" + MainActivity.pubThread.msgUpdateCNT + "段新数据更新完成");
            //MainActivity.pubThread.start();
        }
    }

    @Override
    protected void onPostExecute (Void result) {
        Log.d(LOG_TAG, "RecordAudio->onPostExecute()");
        activity.printAppLog();
    }
}
