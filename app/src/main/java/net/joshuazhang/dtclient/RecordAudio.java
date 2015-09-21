package net.joshuazhang.dtclient;

import android.graphics.Color;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataOutputStream;

/**
 * 录制音频的子线程AsyncTask类
 *
 */
public class RecordAudio extends AsyncTask<Void, double[], Void> {
    /**
     * 音频录制类，使用后台进程录制并返回录制数据到前台UI绘制图形
     */

    private static final String LOG_TAG = MainActivity.LOG_TAG;
    private AudioSetting mRecordAS;

    // 使用构造器传递录制参数
    public RecordAudio(AudioSetting as) {
        mRecordAS = as;
    }

    @Override
    protected Void doInBackground(Void... params) {
        try {
            // 创建临时文件以保存音频数据
            DataOutputStream dos = mRecordAS.getDos();
            int bufferSize = AudioRecord.getMinBufferSize(mRecordAS.frequency,
                    mRecordAS.channelConfiguration, mRecordAS.audioEncoding); // 最小缓冲区大小

            AudioRecord audioRecord = new AudioRecord(
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
            double[] audioData = new double[blockSize]; // 一般应设置blockSize小于bufferSize？

            audioRecord.startRecording();

            while (MainActivity.isRecording) { // isRecording控制着循环
                int bufferReadResult = audioRecord.read(buffer, 0,
                        blockSize); //读取blockSize长度的数据
                MainActivity.totalDataSize = MainActivity.totalDataSize + bufferReadResult; //统计采样点数
                Log.i(LOG_TAG, "采样了" + bufferReadResult + "点数据");

                for (int i = 0; i < bufferReadResult; i++) {
                    dos.writeShort(buffer[i]); //写入两个字节到文件,big-endian
                    //if (i < blockSize) { //图形部分每次只处理blockSize个数据
                    audioData[i] = (double) buffer[i] / 32768.0;
                    // 将16位short类型的buffer数据转为double类型，取值范围为[-1.0, 1.0)
                    //}
                }

                publishProgress(audioData);
            }
            audioRecord.stop(); // isRecording为false后停止录制
            Log.i(LOG_TAG, "AudioRecord实例已停止");
            dos.close();
            Log.i(LOG_TAG, "关闭录制流");
            audioRecord.release(); // 释放对象
            audioRecord = null; // 对象释放后必须设置引用为null
            Log.i(LOG_TAG, "AudioRecord实例已释放资源");

        } catch (Throwable t) {
            Log.e(LOG_TAG, "录制失败");
        }
        return null;
    }

    @Override
    protected void onProgressUpdate(double[]... progress) {
        MainActivity.canvasPCM.drawColor(Color.BLACK); //清空画布

        //绘制PCM波形和分贝图形
        for (int i = 0; i < progress[0].length; i++) {
            int downy = (int) (100 - (progress[0][i] * 100));
            int upy = 100;
            MainActivity.canvasPCM.drawLine(i, downy, i, upy, MainActivity.paintPCM);
        }
        MainActivity.imageViewPCM.invalidate(); //更新PCM波形图
    }

    /**
     * 把short转换为两个bytes，使用big-endian编码
     * 参考<a href="https://stackoverflow.com/questions/2188660/convert-short-to-byte-in-java">Convert short to byte[] in Java - Stack Overflow</a>
     * @param s
     * @return
     */
    private byte[] shortToByteArray(Short s) {
        return new byte[]{(byte)(s>>8), (byte) (s&0xFF)};
    }
}
