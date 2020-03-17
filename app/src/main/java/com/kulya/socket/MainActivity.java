package com.kulya.socket;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.lang.reflect.Method;
import java.text.MessageFormat;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {


    private Button connection;
    private Button subs;
    private Button send;
    private Button disconnection;

    //我们自己新建的MQTT实体类
    private MQTTentity mqttentity;
    private MqttClient client;
    private MqttConnectOptions options;
    //以下两个声明目的是为了实现MQTT消息在testview刷新
    private ScheduledExecutorService scheduler;
    private TextView text;
    private EditText message;

    /**
     * 获取手机imei
     *
     * @param context
     * @param slotId
     * @return
     */
    public static String getIMEI(Context context, int slotId) {
        try {
            TelephonyManager manager = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            Method method = manager.getClass().getMethod("getImei", int.class);
            String imei = (String) method.invoke(manager, slotId);
            return imei;
        } catch (Exception e) {
            return "";
        }
    }

    Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            if (msg.what == 1) {
                Toast.makeText(MainActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
                text.setText((String) msg.obj);
            } else if (msg.what == 2) {
                try {
                    client.subscribe(mqttentity.getTimeTopic(), 1);//订阅主题“timeTopic”
                    Toast.makeText(getApplicationContext(), "连接成功", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else if (msg.what == 3) {
                Toast.makeText(getApplicationContext(), "连接失败，系统正在重连", Toast.LENGTH_SHORT).show();
            }
            return false;
        }
    });


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
        init(new MQTTentity("tcp://60.205.229.216:1883", "root", "1234", "huang"));

    }

    private void initView() {

        connection = (Button) findViewById(R.id.connection);

        send = (Button) findViewById(R.id.send);
        subs = (Button) findViewById(R.id.subs);
        disconnection = (Button) findViewById(R.id.disconnection);
        text = (TextView) findViewById(R.id.text);
        message = (EditText) findViewById(R.id.message);

        connection.setOnClickListener(this);
        send.setOnClickListener(this);
        subs.setOnClickListener(this);
        disconnection.setOnClickListener(this);
        text.setOnClickListener(this);
        message.setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.connection:
                startReconnect();
                break;
            case R.id.subs:
                try {
                    setSubs("huang");
                } catch (MqttException e) {
                    e.printStackTrace();
                }
                break;
            case R.id.disconnection:
                break;
            case R.id.send:
                String messageString = message.getText().toString().trim();
                if (TextUtils.isEmpty(messageString)) {
                    Toast.makeText(this, "messageString不能为空", Toast.LENGTH_SHORT).show();
                    return;
                }

                publish("huang", messageString);
                break;

        }
    }

    /**
     * MQTT初始化连接
     */
    private void init(MQTTentity mqttentity) {
        try {
            String imei = getIMEI(MainActivity.this, 0);
            //host为主机名，test为clientid即连接MQTT的客户端ID，一般以客户端唯一标识符表示，MemoryPersistence设置clientid的保存形式，默认为以内存保存
            client = new MqttClient(mqttentity.getHost(), imei, new MemoryPersistence());
            //MQTT的连接设置
            options = new MqttConnectOptions();
            //设置是否清空session,这里如果设置为false表示服务器会保留客户端的连接记录，这里设置为true表示每次连接到服务器都以新的身份连接
            options.setCleanSession(true);
            //设置连接的用户名
            options.setUserName(mqttentity.getTimeTopic());
            //设置连接的密码
            options.setPassword(mqttentity.getPassWord().toCharArray());
            // 设置超时时间 单位为秒
            options.setConnectionTimeout(10);
            // 设置会话心跳时间 单位为秒 服务器会每隔1.5*20秒的时间向客户端发送个消息判断客户端是否在线，但这个方法并没有重连的机制
            options.setKeepAliveInterval(20);
            //设置回调
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    //连接丢失后，一般在这里面进行重连
                    System.out.println("connectionLost----------");
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    //publish后会执行到这里
                    System.out.println("deliveryComplete---------"
                            + token.isComplete());
                }

                @Override
                public void messageArrived(String topicName, MqttMessage message) {
                    //subscribe后得到的消息会执行到这里面
                    System.out.println("messageArrived----------");
                    Message msg = new Message();
                    msg.what = 1;   //收到消息标志位
                    msg.obj = topicName + "_" + message.toString();
                    //handler.sendMessage(msg);    // hander 回传
                }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * MQTT建立连接及重连
     */
    private void startReconnect() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                if (!client.isConnected()) {
                    connect();
                }
            }
        }, 0, 10 * 1000, TimeUnit.MILLISECONDS);
    }

    /**
     * MQTT连接状态鉴别
     */
    private void connect() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    client.connect(options);
                    Message msg = new Message();
                    msg.what = 2;
                    handler.sendMessage(msg);
                } catch (Exception e) {
                    e.printStackTrace();
                    Message msg = new Message();
                    msg.what = 3;
                    handler.sendMessage(msg);
                }
            }
        }).start();
    }

    private void setSubs(String topic) throws MqttException {
        System.out.println("Subscribe to topic:" + topic);
        client.subscribe(topic);
        client.setCallback(new MqttCallback() {
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String theMsg = MessageFormat.format("{0} is arrived for topic {1}.", new String(message.getPayload()), topic);
                System.out.println(theMsg);
            }

            public void deliveryComplete(IMqttDeliveryToken token) {
            }

            public void connectionLost(Throwable throwable) {
            }
        });
    }
    /**
     * 向Topic发送消息
     *
     * @param topic
     * @param sendMessage
     */
    public void publish(String topic, String sendMessage) {
        Integer qos = 0;
        Boolean retained = false;
        try {
            if (client != null) {
                client.publish(topic, sendMessage.getBytes(), qos.intValue(), retained.booleanValue());
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            scheduler.shutdown();
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }


}
