package net.joshuazhang.dtclient;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnClickListener, OnItemSelectedListener {

    public static final String LOG_TAG = "DTClient";
    public static final String DEVICE_NAME = DeviceName.getDeviceName();
    // TODO 为useMQTT和saveRecordingFile提供按钮开关
    public static boolean sendDataWithMQTT = true; //是否发送MQTT数据
    public static boolean saveDataToFile = true;  //是否保存数据到本地

    public static MQTTPubAudio pubThread; //MQTT数据发送线程

    private static int frequency;
    protected static Long totalDataSize = 0L; // 用于记录总的采样点数

    private AudioSetting recordAS = null; //录音设置实例
    private AudioSetting playAS = null;   //播放设置实例
    private Button recordButton;          // 开始、停止录制按钮
    private Button playButton;            // 开始、停止播放按钮
    protected static boolean isRecording = false;
    protected static boolean isPlaying = false;
    private TextView statusString;        // 滚动日志信息文本框
    private EditText mqttAddrEditText;    // MQTT地址文本
    private EditText mqttPortEditText;    // MQTT端口文本

    private RecordAudio recordTask = null; // 录制音频的实例
    private PlayAudio playTask = null;     // 播放音频的实例
    private File path;
    protected static File recordingFile;   // 保存音频文件，路径为/storage/sdcard/DTClient/

    protected static ImageView imageViewPCM;
    protected static Bitmap bitmapPCM;
    protected static Canvas canvasPCM;
    protected static Paint paintPCM;

    private Long startTimeStamp=null;
    private Long stopTimeStamp=null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 隐藏标题栏
        //requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.activity_main);

        recordButton = (Button) findViewById(R.id.record_button);
        recordButton.setOnClickListener(this);

        playButton = (Button) findViewById(R.id.play_button);
        playButton.setOnClickListener(this);
        playButton.setEnabled(false); //初始情况下播放按钮不可用

        statusString = (TextView) findViewById(R.id.status);
        statusString.setMovementMethod(new ScrollingMovementMethod()); //可滚动更新的文本

        mqttAddrEditText = (EditText) findViewById(R.id.mqtt_addr);
        mqttPortEditText = (EditText) findViewById(R.id.mqtt_port);

        Spinner sp;
        sp = (Spinner) findViewById(R.id.spinnerSampleRate); // 设置Spinner用于选择采样频率
        sp.setOnItemSelectedListener(this);
        List<Integer> spinnerList = new ArrayList<>();
        spinnerList.add(16000);
        spinnerList.add(8000);
        spinnerList.add(11250);
        spinnerList.add(22050);
        spinnerList.add(44100);
        ArrayAdapter<Integer> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,spinnerList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(dataAdapter);
        frequency = (int) sp.getSelectedItem();

        //初始化PCM图形
        int imageWidth = 512;
        imageViewPCM = (ImageView) findViewById(R.id.ImageViewPCM);
        bitmapPCM = Bitmap.createBitmap(imageWidth, 200, Bitmap.Config.ARGB_8888);
        canvasPCM = new Canvas(bitmapPCM);
        paintPCM = new Paint();
        paintPCM.setColor(Color.GREEN);
        imageViewPCM.setImageBitmap(bitmapPCM);

        path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DTClient/");
        boolean isDirCreated = path.mkdirs();
        if (isDirCreated) {
            Log.i(LOG_TAG, "成功创建DTClient文件夹" + path);
        } else if (path.isDirectory()){
            Log.i(LOG_TAG, path + "DTClient文件夹已存在");
        } else {
            Log.i(LOG_TAG, "创建DTClient文件夹失败");
        }

        // 注意CHANNEL_IN_MONO和CHANNEL_OUT_MONO有区别，只能分开设置
        recordAS = new AudioSetting(frequency, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        playAS = new AudioSetting(frequency, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);

    }

    @Override
    public void onClick(View v) {
        /**
         * OnClickListener接口必须实现onClick()方法
         */
        switch (v.getId()) {
            case R.id.record_button: // 录制按钮
                if (isRecording) {
                    // 录制状态下按“Stop”按钮则停止录制并改按钮文字为“Start Recording”
                    isRecording = false; // 通过更改isRecording状态结束录制
                    if (saveDataToFile) {
                        playButton.setEnabled(true); // 播放录音按钮激活
                    }
                    statusString.append("停止录制\n");
                    recordButton.setText(R.string.start_recording);
                    recordTask.cancel(true); //手动停止，对应onCancelled方法
                    recordTask = null;
                    stopTimeStamp = System.currentTimeMillis(); //获取采样停止时刻的时间戳
                    statusString.append("共采样" + totalDataSize + "点，大约耗时" +
                            ((stopTimeStamp - startTimeStamp) / 1000.0) + "秒\n");
                    if (saveDataToFile) {
                        statusString.append("数据已存储到" + recordingFile);
                    }
                } else {
                    // 非录制状态按“Start”按钮开始录制并改按钮文字为“Stop”
                    isRecording = true;
                    recordButton.setText(R.string.stop_recording);
                    playButton.setEnabled(false); //录制状态下播放按钮不可用
                    totalDataSize = 0L; //计数归零
                    statusString.append("开始录制\n");
                    if (saveDataToFile) {
                        try {
                            String timeStr = (new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US)).format(new Date());
                            recordingFile = new File(path, DEVICE_NAME + "_"
                                    + timeStr + ".pcm");
                            // 文件名示例：LGE-LG-SU640_2015-04-04_22-28-21.pcm
                            boolean isFileCreated = recordingFile.createNewFile();
                            if (isFileCreated) {
                                Log.i(LOG_TAG, "成功创建文件" + recordingFile + "\n");
                            } else {
                                Log.i(LOG_TAG, "文件创建失败，请检查原因\n");
                            }
                            DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(recordingFile)));
                            recordAS.setDos(dos); // 设置录制输出文件流
                        } catch (IOException ioe) {
                            throw new RuntimeException("无法在SD卡上创建文件", ioe);
                        } catch (Throwable t) {
                            Log.e(LOG_TAG, "创建文件输出流失败");
                        }
                    }
                    if (sendDataWithMQTT) {
                        // Read MQTT Address，为简单起见，不验证IPv4地址或者域名
                        MQTTCons.TCPADDR = "tcp://" +
                                mqttAddrEditText.getText().toString() + ":" +
                                mqttPortEditText.getText().toString();
                        Log.i(LOG_TAG, "MQTT broker 设置为：" + MQTTCons.TCPADDR);
                        // 启动数据发布子线程
                        pubThread = new MQTTPubAudio();
                        pubThread.start();
                    }
                    recordTask = new RecordAudio(recordAS); // 传入录制参数并创建录制子线程
                    startTimeStamp = System.currentTimeMillis(); // 获取采样开始时刻的时间戳
                    recordTask.execute(); // 开始调用后台进程
                }
                break;
            case R.id.play_button: // 播放按钮
                if(isPlaying) {
                    // 播放状态下按“Stop Playing”按钮则停止播放并改按钮文字为"Start Playing"
                    isPlaying = false;
                    recordButton.setEnabled(true);
                    statusString.append("停止播放\n");
                    playButton.setText(R.string.start_playing);
                    playTask.cancel(true);
                    playTask=null;

                } else {
                    // 非播放状态下按"Start Playing"按钮则开始播放并改按钮文字为"Stop Playing"
                    recordButton.setEnabled(false); // 播放状态下录制按钮不可用
                    statusString.append("开始播放文件" + recordingFile + "\n");
                    playButton.setText(R.string.stop_playing);
                    try {
                        DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(recordingFile)));
                        playAS.setDis(dis); // 设置播放输入文件流
                    } catch (Throwable t) {
                        Log.e(LOG_TAG, "创建播放文件流失败\n");
                    }
                    playTask = new PlayAudio(playAS); // 传入播放参数并创建播放子线程
                    playTask.execute();

                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        frequency = (int) parent.getSelectedItem();
        recordAS.setFrequency(frequency); // 修改录制与播放线程参数设置
        playAS.setFrequency(frequency);
        Log.i(LOG_TAG, "采样频率修改为：" + MainActivity.frequency);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.set_freq:
                Toast.makeText(this, "Set sampling frequence", Toast.LENGTH_SHORT).show();
                // TODO move set freq function into menu
                break;
            case R.id.help:
                Toast.makeText(this, "Help info for DTClient", Toast.LENGTH_SHORT).show();
                break;
            case R.id.exit:
                Toast.makeText(this, "Exit App Now!", Toast.LENGTH_SHORT).show();
                // 这样做可以方便调试时重启App而不需要手动杀掉已有的App进程
                forceExitApp();
                break;
            default:
        }
        return true;
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // Nothing to do here
    }

    /**
     * 强制退出App
     * 使用场景：
     * 1、MQTT broker连接失败
     * 2、菜单里，用于调试退出
     */
    public static void forceExitApp() {
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
        // 使用System.exit()退出App是由争议的，这里不理会
    }

}
