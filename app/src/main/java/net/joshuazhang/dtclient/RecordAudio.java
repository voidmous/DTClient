package net.joshuazhang.dtclient;

import android.graphics.Color;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * 录制音频的子线程AsyncTask类
 * TODO 把此类改写为服务
 *
 */
public class RecordAudio extends AsyncTask<Void, short[], Void> {
    /**
     * 音频录制类，使用后台进程录制并返回录制数据到前台UI绘制图形
     */

    private static final String LOG_TAG = MainActivity.LOG_TAG;
    private AudioSetting mRecordAS;
    private DataOutputStream dos;
    private AudioRecord audioRecord;

    // 使用构造器传递录制参数
    public RecordAudio(AudioSetting as) {
        mRecordAS = as;
    }

    @Override
    protected Void doInBackground (Void... params) {
        try {
            // 创建临时文件以保存音频数据
            if (MainActivity.saveRecordingFile) {
                this.dos = mRecordAS.getDos();
            }
            int bufferSize = AudioRecord.getMinBufferSize(mRecordAS.frequency,
                    mRecordAS.channelConfiguration, mRecordAS.audioEncoding); // 最小缓冲区大小

            audioRecord = new AudioRecord(
                    MediaRecorder.AudioSource.MIC, mRecordAS.frequency,
                    mRecordAS.channelConfiguration, mRecordAS.audioEncoding, bufferSize);//创建AudioRecord实例
            Log.i(LOG_TAG, "音源为：" + audioRecord.getAudioSource());
            Log.i(LOG_TAG, "采样频率为：" + audioRecord.getSampleRate()+"Hz");
            Log.i(LOG_TAG, "声道设置为：" + audioRecord.getChannelCount());
            Log.i(LOG_TAG, "音频编码格式为：" + audioRecord.getAudioFormat());
            Log.i(LOG_TAG, "缓冲区大小为："+ bufferSize + "个字节");
            Log.i(LOG_TAG, "AudioRecord实例状态为：" + audioRecord.getState());

            // buffer数据用作存储或传输
            // audioData是归一化的buffer数据，用于绘图
            int blockSize=512;
            //每次读取数据、FFT转换的点数为256，可以设为其它值吗？
            // 可以设为其它值，但要考虑计算效率
            short[] buffer = new short[blockSize]; //16位short数组，取值范围-32768~+32767

            audioRecord.startRecording();
            Log.i(LOG_TAG, "开始采集音频数据");

            while (MainActivity.isRecording) { // isRecording控制着循环
                int bufferReadResult = audioRecord.read(buffer, 0,
                        blockSize); //读取blockSize长度的数据
                MainActivity.totalDataSize = MainActivity.totalDataSize + bufferReadResult; //统计采样点数
                Log.v(LOG_TAG, "采样了" + bufferReadResult + "点数据");

                if (MainActivity.saveRecordingFile) {
                    for (int i = 0; i < bufferReadResult; i++) {
                        dos.writeShort(buffer[i]); //写入两个字节到文件,big-endian
                    }
                }
                publishProgress(buffer); // 发布采集数据到主界面线程
            }
            if (MainActivity.saveRecordingFile) {
                dos.close();
                Log.i(LOG_TAG, "关闭录制流");
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
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(short[]... progress) {
        MainActivity.canvasPCM.drawColor(Color.BLACK); //清空画布

        double[] audioData=new double[progress[0].length];
        // 将16位short类型的buffer数据转为double类型，取值范围为[-1.0, 1.0)

        byte[] data = new byte[progress[0].length * 2];

        //绘制PCM波形和分贝图形
        for (int i = 0; i < progress[0].length; i++) {
            audioData[i]=(double) progress[0][i] / 32768.0;
            data[i*2]=(byte)(progress[0][i]>>8);
            data[i*2+1]=(byte) (progress[0][i]&0xFF);
            int downy = (int) (100 - (audioData[i] * 100));
            int upy = 100;
            MainActivity.canvasPCM.drawLine(i, downy, i, upy, MainActivity.paintPCM);
        }
        MainActivity.imageViewPCM.invalidate(); //更新PCM波形图

        // TODO 发送采集的数据到MQTT broker
        MainActivity.pubThread.pubMsg=new MqttMessage(data);
        MainActivity.pubThread.msgUpdateCNT += 1;
        Log.v(LOG_TAG, "第" + MainActivity.pubThread.msgUpdateCNT + "段新数据更新完成");
        //MainActivity.pubThread.start();

    }

    /**
     * 把short转换为两个bytes，使用big-endian编码
     * 参考<a href="https://stackoverflow.com/questions/2188660/convert-short-to-byte-in-java">Convert short to byte[] in Java - Stack Overflow</a>
     * @param s to be converted
     * @return byte[] covnerted in big-endian
     */
    private byte[] shortToByteArray(Short s) {
        return new byte[]{(byte)(s>>8), (byte) (s&0xFF)};
    }
}
