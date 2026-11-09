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
  private boolean charging = false;
  private long lastNs;
  private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
   @Override public void onReceive(Context context, Intent intent) {
    int value = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
    int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
    if (value >= 0 && scale > 0) battery = Math.max(0f, Math.min(1f, value / (float)scale));
    int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
    charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
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
   return base + tilt*(x-w*.5f) + Math.min(8f, w*.012f)*(float)Math.sin(x/Math.max(1f,w)*9f+phase);
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
    // Keep the surface visible even when the battery is full.
    float left=w*.055f, right=w*.945f, top=h*.06f, bottom=h*.96f;
    float surface = bottom - (bottom-top)*(.035f + .81f*battery);
    path.reset(); path.addRoundRect(left,top,right,bottom,w*.065f,w*.065f,Path.Direction.CW);
    canvas.save(); canvas.clipPath(path);
    path.reset(); path.moveTo(0,h); path.lineTo(0,wave(0,w,surface));
    for(int i=1;i<=120;i++){ float x=w*i/120f; path.lineTo(x,wave(x,w,surface)); }
    path.lineTo(w,h); path.close();
    int[] colors = battery<=.20f ? new int[]{0xffe6b178,0xffa56d59,0xff372642}
                                    : new int[]{0xff72d9c4,0xff16878d,0xff11294f};
    paint.setShader(new LinearGradient(0,surface,w,bottom,colors,null,Shader.TileMode.CLAMP));
    canvas.drawPath(path,paint); paint.setShader(null);
    paint.setShader(new RadialGradient(w*.8f,surface+h*.12f,w*.85f,
        new int[]{0x8895f8d6,0x2248c7b7,0x00203857},null,Shader.TileMode.CLAMP));
    canvas.drawPath(path,paint); paint.setShader(null);
    paint.setShader(new LinearGradient(0,surface,0,bottom,
        new int[]{0x33f4e4b7,0x00123d54,0x66100c32},null,Shader.TileMode.CLAMP));
    canvas.drawPath(path,paint); paint.setShader(null);
    path.reset();
    for(int i=0;i<=120;i++){float x=w*i/120f; if(i==0)path.moveTo(x,wave(x,w,surface));else path.lineTo(x,wave(x,w,surface));}
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(w*.006f);
    paint.setColor(0x99e4f9e4); canvas.drawPath(path,paint);
    paint.setStyle(Paint.Style.FILL);
    for(int i=0;i<30;i++){
      float bx=left+(right-left)*((i*73%101)/101f);
      float progress=((i*47%103)/103f + phase*(charging?.08f:.012f))%1f;
      float by=bottom-(bottom-top)*progress;
      if(by>wave(bx,w,surface)+6f){
        paint.setColor(charging?0x99e5f8d8:0x447ac6cf);
        canvas.drawCircle(bx,by,w*(.002f+.003f*(i%4)/4f),paint);
      }
    }
    paint.setShader(new LinearGradient(left,0,right,0,
        new int[]{0x449cdacb,0x00162e3b,0x1049c6ae,0x66e7c89c},null,Shader.TileMode.CLAMP));
    canvas.drawRect(left,top,right,bottom,paint); paint.setShader(null);
    canvas.restore();
    paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(w*.003f);
    paint.setColor(0x88b4d9d0);
    canvas.drawRoundRect(left,top,right,bottom,w*.065f,w*.065f,paint);
    paint.setStyle(Paint.Style.FILL); paint.setTextAlign(Paint.Align.CENTER);
    paint.setTypeface(android.graphics.Typeface.create("sans-serif-light",0));
    paint.setColor(0xfff5eadb); paint.setTextSize(Math.min(w*.14f,70f));
    canvas.drawText(Math.round(battery*100f)+"%",w*.5f,h*.22f,paint);
    paint.setTextSize(Math.min(w*.027f,15f)); paint.setColor(0xffa4b8c9);
    canvas.drawText("L I Q U I D   /   E N E R G Y",w*.5f,h*.22f+34f,paint);
   } catch (Exception ignored) { } finally { if(canvas!=null)holder.unlockCanvasAndPost(canvas); }
  }
 }
}
