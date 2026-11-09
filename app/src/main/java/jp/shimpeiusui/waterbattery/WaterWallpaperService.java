package jp.shimpeiusui.waterbattery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.service.wallpaper.WallpaperService;
import android.view.SurfaceHolder;

public class WaterWallpaperService extends WallpaperService {
 @Override public Engine onCreateEngine() { return new WaterEngine(); }
 private final class WaterEngine extends Engine implements SensorEventListener {
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
  private final Path path = new Path();
  private SensorManager sensors;
  private Sensor sensor;
  private boolean visible;
  private boolean registered;
  private float battery = .65f, tilt = 0f, targetTilt = 0f, phase = 0f, gx = 0f, gy = 9.8f;
  private long lastNs;
  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
   @Override public void onReceive(Context context, Intent intent) {
    int value = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
    if (value >= 0 && scale > 0) battery = Math.max(0f, Math.min(1f, value / (float)scale));
   }
  };
  private final Runnable animate = new Runnable() {
   @Override public void run() {
    if (!visible) return;
    long now = System.nanoTime();
    float dt = lastNs == 0 ? .033f : Math.min(.05f, (now-lastNs)/1000000000f);
    lastNs = now;
    tilt += (targetTilt - tilt) * Math.min(1f, dt * 5f);
    phase += dt * 1.5f;
    drawFrame();
    handler.postDelayed(this, 33);
   }
  };
  @Override public void onCreate(SurfaceHolder holder) {
   super.onCreate(holder);
   sensors = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
   sensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY);
   if (sensor == null) sensor = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
  }
  @Override public void onVisibilityChanged(boolean isVisible) {
   visible = isVisible; handler.removeCallbacks(animate);
   if (visible) {
    Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
    registered = true;
    if (sticky != null) batteryReceiver.onReceive(WaterWallpaperService.this, sticky);
    if (sensor != null) sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
    lastNs = 0; handler.post(animate);
   } else {
    if (sensors != null) sensors.unregisterListener(this);
    if (registered) { unregisterReceiver(batteryReceiver); registered = false; }
   }
  }
  @Override public void onDestroy() {
   visible = false; handler.removeCallbacks(animate);
   if (sensors != null) sensors.unregisterListener(this);
   if (registered) { unregisterReceiver(batteryReceiver); registered = false; }
   super.onDestroy();
  }
  @Override public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
   super.onSurfaceChanged(holder, format, width, height);
   if (visible) drawFrame();
  }
  @Override public void onSensorChanged(SensorEvent event) {
   float x = event.values[0], y = event.values[1];
   if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
    gx = gx*.88f + x*.12f; gy = gy*.88f + y*.12f;
   } else { gx = x; gy = y; }
   float magnitude = (float)Math.hypot(gx, gy);
   if (magnitude > 1.3f) targetTilt = Math.max(-.7f, Math.min(.7f, gx/Math.max(2.5f, Math.abs(gy))));
  }
  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
  private float wave(float x, float w, float base) {
   return base - tilt*(x-w*.5f) + Math.min(8f, w*.012f)*(float)Math.sin(x/Math.max(1f,w)*9f+phase);
  }
  private void drawFrame() {
   SurfaceHolder holder = getSurfaceHolder(); Canvas canvas = null;
   try {
    canvas = holder.lockCanvas(); if (canvas == null) return;
    float w = canvas.getWidth(), h = canvas.getHeight();
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xff111b39,0xff071426,0xff040a1c},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);
    paint.setShader(new RadialGradient(w*.75f,h*.23f,w*.9f,new int[]{0x3357b8db,0x002a7599},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint); paint.setShader(null);
    float surface = h*(1f-battery);
    path.reset(); path.moveTo(0,h); path.lineTo(0,wave(0,w,surface));
    for(int i=1;i<=120;i++){ float x=w*i/120f; path.lineTo(x,wave(x,w,surface)); }
    path.lineTo(w,h); path.close();
    paint.setShader(new LinearGradient(0,0,0,h,new int[]{0xff6ce9e6,0xff258db6,0xff132f6a},new float[]{0f,.52f,1f},Shader.TileMode.CLAMP));
    canvas.drawPath(path,paint); paint.setShader(null);
    path.reset();
    for(int i=0;i<=120;i++){float x=w*i/120f; if(i==0)path.moveTo(x,wave(x,w,surface));else path.lineTo(x,wave(x,w,surface));}
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5); paint.setColor(0x66c6ffff); canvas.drawPath(path,paint);
    paint.setStrokeWidth(1.7f); paint.setColor(0xffd8ffff); canvas.drawPath(path,paint);
    paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
    paint.setColor(0xffd4faff); paint.setTextSize(Math.min(w*.14f,70f));
    canvas.drawText(Math.round(battery*100f)+"%",w*.5f,h*.22f,paint);
    paint.setTextSize(Math.min(w*.027f,15f)); paint.setColor(0xffa9dbe8);
    canvas.drawText("W A T E R   /   B A T T E R Y",w*.5f,h*.22f+34f,paint);
   } catch (Exception ignored) { } finally { if(canvas!=null)holder.unlockCanvasAndPost(canvas); }
  }
 }
}
