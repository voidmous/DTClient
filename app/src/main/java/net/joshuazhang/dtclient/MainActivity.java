package net.joshuazhang.dtclient;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
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
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity implements OnClickListener, OnItemSelectedListener {

    // 日志标签
    public static final String LOG_TAG = "DTClient";
    // logcat进程的命令行参数
    public static final String logcatProc = "logcat -b main -v brief -d " + LOG_TAG + ":D *:S";

    // 自动识别的设备名称，不包含空格，例如：LGE-LG-SU640，HTC-Vision
    public static final String DEVICE_NAME = DeviceName.getDeviceName();

    public static boolean sendDataWithMQTT = true;  //是否发送MQTT数据
    public static boolean saveDataToFile = true;    //是否保存数据到本地

    public static MQTTPubAudio pubThread; //MQTT数据发送线程

    private static int frequency = 8000;

    protected static Long totalDataSize = 0L; // 用于记录总的采样点数

    private AudioSetting recordAS;        //录音设置实例
    private AudioSetting playAS;          //播放设置实例
    private Button recordButton;          // 开始、停止录制按钮
    private Button playButton;            // 开始、停止播放按钮
    protected static boolean isRecording = false;
    protected static boolean isPlaying = false;
    private TextView appLogTextView;      // logcat输出TextView，滚动日志信息文本框
    private EditText mqttAddrEditText;    // MQTT地址文本
    private EditText mqttPortEditText;    // MQTT端口文本

    private RecordAudio recordTask;       // 录制音频的实例
    private PlayAudio playTask;           // 播放音频的实例
    private File path;
    protected static File recordingFile;  // 保存音频文件，路径为/storage/sdcard/DTClient/

    protected static ImageView imageViewPCM;
    protected static Bitmap bitmapPCM;
    protected static Canvas canvasPCM;
    protected static Paint paintPCM;

    private static PowerManager.WakeLock wl;     // 不关闭CPU、允许关闭屏幕和键盘灯

    private Long startTimeStamp;          // 开始录制的时间

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        recordButton = (Button) findViewById(R.id.record_button);
        recordButton.setOnClickListener(this);

        playButton = (Button) findViewById(R.id.play_button);
        playButton.setOnClickListener(this);
        playButton.setEnabled(false); //初始情况下播放按钮不可用

        appLogTextView = (TextView) findViewById(R.id.appLog);
        appLogTextView.setMovementMethod(new ScrollingMovementMethod()); //可滚动更新的文本

        mqttAddrEditText = (EditText) findViewById(R.id.mqtt_addr);
        mqttPortEditText = (EditText) findViewById(R.id.mqtt_port);

        Spinner sp;  // 设置Spinner用于选择采样频率
        sp = (Spinner) findViewById(R.id.spinnerSampleRate);
        sp.setOnItemSelectedListener(this);
        List<Integer> spinnerList = new ArrayList<>();
        // 44100Hz is currently the only rate that is guaranteed to work on all devices, but other
        // rates such as 22050, 16000, and 11025 may work on some devices.
        spinnerList.add(8000);  // 默认采样率
        spinnerList.add(11250);
        spinnerList.add(16000);
        spinnerList.add(22050);
        spinnerList.add(44100);
        ArrayAdapter<Integer> dataAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item,spinnerList);
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sp.setAdapter(dataAdapter);
        frequency = (int) sp.getSelectedItem();
        Log.i(LOG_TAG, "采样频率初始化为：" + frequency + "Hz");

        //初始化PCM图形
        int imageWidth = 512;
        imageViewPCM = (ImageView) findViewById(R.id.ImageViewPCM);
        bitmapPCM = Bitmap.createBitmap(imageWidth, 200, Bitmap.Config.ARGB_8888);
        canvasPCM = new Canvas(bitmapPCM);
        paintPCM = new Paint();
        paintPCM.setColor(Color.GREEN);
        imageViewPCM.setImageBitmap(bitmapPCM);
        Log.i(LOG_TAG, "初始化ImageView成功");

        path = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DTClient/");
        boolean isDirCreated = path.mkdirs();
        if (isDirCreated) {
            Log.i(LOG_TAG, "成功创建DTClient文件夹" + path);
        } else if (path.isDirectory()){
            Log.i(LOG_TAG, path + "文件夹已存在");
        } else {
            Log.w(LOG_TAG, "创建DTClient文件夹失败");
        }
        int audioEncoding = AudioFormat.ENCODING_PCM_16BIT;
        // TODO 8bit会显著地提高性能吗？如果不能，不用改
        // 注意CHANNEL_IN_MONO和CHANNEL_OUT_MONO有区别，只能分开设置
        recordAS = new AudioSetting(frequency, AudioFormat.CHANNEL_IN_MONO, audioEncoding);
        playAS = new AudioSetting(frequency, AudioFormat.CHANNEL_OUT_MONO, audioEncoding);

        // 为了实验时不会因为手机锁屏关闭程序，需要控制电源参数
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "不关闭CPU，可以关闭屏幕和键盘灯");
        // 查看是否已经开启了权限
        if (wl.isHeld()) {
            Log.w(LOG_TAG, "WakeLock已经开启，请检查是否上次获取未被释放");
        } else {
            wl.acquire();
            Log.i(LOG_TAG, "获取了WakeLock，CPU不会被关闭，屏幕和键盘灯可以关闭");
        }

    }

    @Override
    protected void onDestroy() {
        super.onDestroy(); // TODO 父类方法应该放前面还是后面？
        // 释放各类占用的系统资源
        if (recordTask != null && recordTask.getAudioRecord() != null) {
            recordTask.getAudioRecord().release();
            Log.i(LOG_TAG, "AudioRecord实例已释放资源");
        }
        if (playTask != null && playTask.getAudioTrack() != null) {
            playTask.getAudioTrack().release();
            Log.i(LOG_TAG, "AudioTrack实例已释放资源");
        }
        Log.i(LOG_TAG, "释放WakeLock");
        if (wl != null) {
            wl.release();
            Log.i(LOG_TAG, "onDestry释放WakeLock");
        }
    }

    @Override
    public void onClick(View v) {
        /**
         * OnClickListener接口必须实现onClick()方法
         */
        switch (v.getId()) {
            case R.id.record_button: // 录制按钮
                if (isRecording) {
                    // 录制状态下按“Stop”按钮则停止录制并改recordButton按钮文字为“Start Recording”
                    isRecording = false; // 通过更改isRecording状态结束录制
                    if (saveDataToFile) {
                        playButton.setEnabled(true); // 如果保存了文件则播放录音按钮激活
                    }
                    Log.i(LOG_TAG, "停止录制");
                    recordButton.setText(R.string.start_recording);
                    recordTask.cancel(true); //手动停止，对应onCancelled方法
                    recordTask = null;
                    long stopTimeStamp = System.currentTimeMillis(); //获取采样停止时刻的时间戳
                    double timeElapsed = (stopTimeStamp - startTimeStamp) / 1000.0;
                    Log.i(LOG_TAG, "共采样" + totalDataSize + "点，大约耗时" +
                            timeElapsed + "秒，平均采样率为" + (totalDataSize / timeElapsed) + "Hz");
                    if (saveDataToFile) {
                        Log.i(LOG_TAG, "数据已存储到" + recordingFile);
                    }
                    Log.d(LOG_TAG, "这里是录制按钮按下Stop的代码块");
                    printAppLog(); // 打印当前过滤日志到appLogTextView
                } else {
                    // 非录制状态按“Start”按钮开始录制并改recordButton按钮文字为“Stop Recording”
                    isRecording = true;
                    recordButton.setText(R.string.stop_recording);
                    playButton.setEnabled(false); //录制状态下播放按钮不可用
                    totalDataSize = 0L; //计数归零
                    Log.i(LOG_TAG, "开始录制");
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
                                Log.e(LOG_TAG, "文件创建失败，请检查原因\n");
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
                        Log.i(LOG_TAG, "数据发布进程启动...");
                    }
                    recordTask = new RecordAudio(MainActivity.this, recordAS); // 传入录制参数并创建录制子线程
                    startTimeStamp = System.currentTimeMillis(); // 获取采样开始时刻的时间戳
                    recordTask.execute(); // 开始调用后台进程
                    Log.i(LOG_TAG, "采集进程开始运行...");
                }
                break;
            case R.id.play_button: // 播放按钮
                if(isPlaying) {
                    // 播放状态下按“Stop Playing”按钮则停止播放并改按钮文字为"Start Playing"
                    isPlaying = false;
                    recordButton.setEnabled(true);
                    Log.i(LOG_TAG, "停止播放");
                    playButton.setText(R.string.start_playing);
                    playTask.cancel(true);
                    playTask=null;
                    Log.d(LOG_TAG, "这里是播放按钮按下Stop的代码块");
                    printAppLog();
                } else {
                    // 非播放状态下按"Start Playing"按钮则开始播放并改按钮文字为"Stop Playing"
                    recordButton.setEnabled(false); // 播放状态下录制按钮不可用
                    Log.i(LOG_TAG, "开始播放文件" + recordingFile);
                    playButton.setText(R.string.stop_playing);
                    try {
                        DataInputStream dis = new DataInputStream(new BufferedInputStream(new FileInputStream(recordingFile)));
                        playAS.setDis(dis); // 设置播放输入文件流
                    } catch (Throwable t) {
                        Log.e(LOG_TAG, "创建播放文件流失败\n");
                    }
                    playTask = new PlayAudio(playAS); // 传入播放参数并创建播放子线程
                    playTask.execute();
                    Log.i(LOG_TAG, "播放进程开始运行...");
                }
                break;
            default:
                break;
        }
    }

    public void onCheckboxClicked(View view) {
        boolean checked = ((CheckBox) view).isChecked();

        switch (view.getId()) {
            case R.id.checkbox_savefile:
                if (checked) {
                    MainActivity.saveDataToFile = true;
                    Toast.makeText(this, "保存音频到文件", Toast.LENGTH_SHORT).show();
                    Log.i(LOG_TAG, "保存音频到文件");
                } else {
                    MainActivity.saveDataToFile = false;
                    Toast.makeText(this, "不保存音频到文件", Toast.LENGTH_SHORT).show();
                    Log.i(LOG_TAG, "不保存音频到文件");
                }
                break;
            case R.id.checkbox_sendmqtt:
                if (checked) {
                    MainActivity.sendDataWithMQTT = true;
                    Toast.makeText(this, "设置通过MQTT发送数据", Toast.LENGTH_SHORT).show();
                    Log.i(LOG_TAG, "设置通过MQTT发送数据");
                } else {
                    MainActivity.sendDataWithMQTT = false;
                    Toast.makeText(this, "设置不通过MQTT发送数据", Toast.LENGTH_SHORT).show();
                    Log.i(LOG_TAG, "设置不通过MQTT发送数据");
                }
                break;
            default:
                break;
        }

    }

    public int printAppLog() {
        // TODO 还是不能在RecordAudio或者PlayAudio类中调用，会产生异常
        // 在应用内调用`logcat`打印过滤的日志到TextView
        // 临时启动logcat进程，记录当前App的日志并输出到appLogTextView
        // 注意这相当于运行一次logcat进程并取得过滤的日志再在appLogTextView里打印出来
        // 因为是一次性而非常驻进程，所以必须放到所有日志都产生之后的地方，否则后面的日志只能等下次输出
        String separator = System.getProperty("line.separator");
        try {
            // logcat -d OptionMenu:D *:S
            // Output only logs of TAG OptionMenu above Debug
            Process mProcess = Runtime.getRuntime().exec(logcatProc);
            // 将logcat进程的输出连接到BufferedReader上
            BufferedReader reader =  new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
            StringBuilder builder = new StringBuilder(); // 临时存储日志
            //logBuilder.append(separator);
            String line;

            while ((line = reader.readLine())!= null) {
                // 此循环结束后出现的日志都不会在appLogTextView里打印出来
                // 当然仍然可以使用远程adb logcat看到
                builder.append(line);
                builder.append(separator);
            }
            Log.i(LOG_TAG, "调用logcat输出日志完成"); //这条日志就不会在App里被打印出来
            appLogTextView.setText(builder.toString());
        } catch (IOException e) {
            Log.e(LOG_TAG, "调用logcat产生了IO异常");
            e.printStackTrace();
            return 1;
        } catch (Exception e) {
            e.printStackTrace();
            Log.e(LOG_TAG, "调用logcat产生了其它异常");
            Log.e(LOG_TAG, "原因：" + e.getCause());
            Log.e(LOG_TAG, "说明：" + e.getLocalizedMessage());
            Log.e(LOG_TAG, "详细说明：" + e.getMessage());
            return 2;
        }
        return 0;

    }
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        frequency = (int) parent.getSelectedItem();
        recordAS.setFrequency(frequency); // 修改录制与播放线程参数设置
        playAS.setFrequency(frequency);
        Log.i(LOG_TAG, "采样频率修改为：" + MainActivity.frequency + "Hz");
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
        Log.e(LOG_TAG, "强制退出App");
        Log.e(LOG_TAG, "释放WakeLock");
        if (wl!=null && wl.isHeld()) {
            wl.release();
        }
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(1);
        // 使用System.exit()退出App是由争议的，这里不理会
    }

}
