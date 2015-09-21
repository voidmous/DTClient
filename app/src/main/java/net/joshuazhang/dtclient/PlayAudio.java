package net.joshuazhang.dtclient;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.AsyncTask;
import android.util.Log;

import java.io.DataInputStream;
import java.io.IOException;

/**
 * 播放已录制音频的子线程AsyncTask类
 */
public class PlayAudio extends AsyncTask<Void, Void, Void> {

    private static final String LOG_TAG = MainActivity.LOG_TAG;
    private AudioSetting mPlayAS;

    public PlayAudio(AudioSetting as) {
        mPlayAS = as;
    }

    @Override
    protected Void doInBackground(Void... params) {
        MainActivity.isPlaying = true;

        int bufferSize = AudioTrack.getMinBufferSize(mPlayAS.frequency, mPlayAS.channelConfiguration, mPlayAS.audioEncoding);
        short[] audioData = new short[bufferSize/4];
        // bufferSize以byte为单位，audioData为了实现双缓冲推荐设置为bufferSize的1/2，
        // 由于audioData是short数组，因此应该除以4

        try {

            DataInputStream dis = mPlayAS.getDis();
            AudioTrack audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC, mPlayAS.frequency, mPlayAS.channelConfiguration,
                    mPlayAS.audioEncoding, bufferSize, AudioTrack.MODE_STREAM);
            // 注意这里不能再使用AudioFormat.CHANNEL_IN_MONO
            Log.i(LOG_TAG, "采样频率为：" + audioTrack.getSampleRate()+"Hz");
            Log.i(LOG_TAG, "声道设置为：" + audioTrack.getChannelCount());
            Log.i(LOG_TAG, "音频编码格式为：" + audioTrack.getAudioFormat());
            Log.i(LOG_TAG, "缓冲区大小为："+ bufferSize + "个字节");
            Log.i(LOG_TAG, "AudioTrack实例的状态为：" + audioTrack.getState());

            audioTrack.play();
            while( MainActivity.isPlaying && dis.available() > 0) {
                int i = 0;
                while(dis.available() > 0 && i < audioData.length) {
                    audioData[i] = dis.readShort();
                    i++;
                }
                audioTrack.write(audioData, 0, audioData.length);
                Log.i(LOG_TAG, "播放数据");
            }

            audioTrack.stop();
            Log.i(LOG_TAG, "AudioTrack实例已停止");
            dis.close();
            Log.i(LOG_TAG, "关闭播放流");
            audioTrack.release();
            Log.i(LOG_TAG, "AudioTrack实例已释放资源");
/*                recordButton.setEnabled(true);
                playButton.setEnabled(true);
                playButton.setText("Start Playing");*/

        } catch (IOException ioe) {
            Log.e(LOG_TAG, "打开音频文件失败");
        } catch (IllegalArgumentException iae) {
            Log.e(LOG_TAG, "参数错误");
        } catch (Throwable t) {
            Log.e(LOG_TAG, "播放音频失败");
        }
        return null;
    }
}
