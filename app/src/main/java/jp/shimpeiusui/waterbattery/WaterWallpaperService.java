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
import java.util.Random;
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
  private final Random random=new Random();
  private final float[] bx=new float[48],by=new float[48],br=new float[48],bs=new float[48],bp=new float[48];
  private final boolean[] active=new boolean[48];
  private float downX=0f,downY=1f,smoothX=0f,smoothY=1f,slosh=0f,sloshSpeed=0f;
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
    float alpha=Math.min(1f,dt*7f);
    smoothX+=(downX-smoothX)*alpha; smoothY+=(downY-smoothY)*alpha;
    float n=(float)Math.hypot(smoothX,smoothY);
    if(n>.001f){smoothX/=n;smoothY/=n;}
    sloshSpeed+=(-slosh*19f-sloshSpeed*6f)*dt;
    slosh+=sloshSpeed*dt;
    for(int i=0;i<active.length;i++){
      if(!active[i]){
        if(random.nextFloat()<dt*(charging?1.5f:.8f)){
          active[i]=true;bx[i]=random.nextFloat();by[i]=1.05f+random.nextFloat()*.2f;
          br[i]=.002f+(float)Math.pow(random.nextFloat(),2)*.016f;
          bs[i]=.05f+random.nextFloat()*.18f;bp[i]=random.nextFloat()*6.28f;
        }
      }else{
        by[i]-=bs[i]*dt;bp[i]+=dt*(1f+br[i]*80f);
        if(by[i]<-.2f)active[i]=false;
      }
    }
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
   float magnitude=(float)Math.hypot(gx,gy);
   if(magnitude>1.3f){
     // Android sensor Y points up the device, canvas Y points down.
     float nx=-gx/magnitude,ny=gy/magnitude;
     float change=(float)Math.hypot(nx-downX,ny-downY);
     if(change>.025f)sloshSpeed+=Math.min(1.5f,change)*.25f;
     downX=nx;downY=ny;
   }
  }
  @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
  private float area(float nx,float ny,float t,float w,float h){
    float[] x={0,w,w,0},y={0,0,h,h},px=new float[8],py=new float[8];
    int count=0;
    for(int i=0;i<4;i++){
      int j=(i+1)%4;float a=nx*x[i]+ny*y[i]-t,c=nx*x[j]+ny*y[j]-t;
      if(a>=0){px[count]=x[i];py[count++]=y[i];}
      if((a<0&&c>0)||(a>0&&c<0)){
        float f=a/(a-c);px[count]=x[i]+f*(x[j]-x[i]);py[count++]=y[i]+f*(y[j]-y[i]);
      }
    }
    float sum=0;
    for(int i=0;i<count;i++){int j=(i+1)%count;sum+=px[i]*py[j]-px[j]*py[i];}
    return Math.abs(sum)*.5f;
  }
  private float threshold(float nx,float ny,float w,float h){
    float lo=Math.min(0,nx*w)+Math.min(0,ny*h)-1;
    float hi=Math.max(0,nx*w)+Math.max(0,ny*h)+1;
    float fraction=.025f+.80f*battery;
    for(int i=0;i<23;i++){
      float mid=(lo+hi)*.5f;
      if(area(nx,ny,mid,w,h)>w*h*fraction)lo=mid;else hi=mid;
    }
    return (lo+hi)*.5f;
  }
  private Path surface(float nx,float ny,float t,float w,float h,boolean filled){
    Path p=new Path();float tx=-ny,ty=nx,span=(float)Math.hypot(w,h)*1.5f;
    if(filled){p.moveTo(nx*t-tx*span+nx*span,ny*t-ty*span+ny*span);p.lineTo(nx*t-tx*span,ny*t-ty*span);}
    for(int i=0;i<=160;i++){
      float q=-span+2*span*i/160f;
      float ripple=(float)Math.sin(q/Math.max(1,w)*11f+phase)*Math.min(w,h)*.003f;
      ripple+=slosh*(float)Math.sin(q/Math.max(1,w)*3f)*Math.min(w,h)*.03f;
      float x=nx*(t+ripple)+tx*q,y=ny*(t+ripple)+ty*q;
      if(i==0&&!filled)p.moveTo(x,y);else p.lineTo(x,y);
    }
    if(filled){p.lineTo(nx*t+tx*span+nx*span,ny*t+ty*span+ny*span);p.close();}
    return p;
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
    float nx=smoothX,ny=smoothY,t=threshold(nx,ny,w,h);
    Path liquid=surface(nx,ny,t,w,h,true);
    canvas.save();canvas.clipPath(liquid);
    paint.setShader(new LinearGradient(0,0,w,h,
      new int[]{0xff041020,0xff063a4d,0xff007b77,0xff182d63,0xff050b19},
      new float[]{0f,.24f,.49f,.76f,1f},Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    paint.setShader(new RadialGradient(w*.86f,h*.26f,w*.9f,
      new int[]{0xaa00d4b7,0x665e93ba,0x002a3568},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    for(int i=0;i<5;i++){
      Path ribbon=new Path();float off=i*.11f;
      ribbon.moveTo(-w*.35f,h*(.84f+off));
      ribbon.cubicTo(w*.22f,h*(.54f+off),w*.72f,h*(.95f-off),w*1.25f,h*(.12f+off));
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*(.07f+i*.009f));
      paint.setShader(new LinearGradient(0,h*.8f,w,h*.2f,
        new int[]{0x0066dfca,0x7750d8d0,0x88586bb0,0x00edb16d},null,Shader.TileMode.CLAMP));
      canvas.drawPath(ribbon,paint);paint.setShader(null);
      paint.setStrokeWidth(w*.005f);paint.setColor(i%2==0?0xccf8bf79:0x99b5f8e9);
      canvas.drawPath(ribbon,paint);
    }
    paint.setStyle(Paint.Style.FILL);
    for(int i=0;i<active.length;i++)if(active[i]){
      float x=(bx[i]+.018f*(float)Math.sin(bp[i]))*w,y=by[i]*h,r=br[i]*w;
      paint.setColor(0x99b8f5ea);paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Math.max(.7f,r*.17f));canvas.drawCircle(x,y,r,paint);
      paint.setStyle(Paint.Style.FILL);paint.setColor(0x66ffffff);
      canvas.drawCircle(x-r*.2f,y-r*.2f,Math.max(.6f,r*.15f),paint);
    }
    canvas.restore();
    Path waterline=surface(nx,ny,t,w,h,false);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*.013f);
    paint.setColor(0x77edb478);canvas.drawPath(waterline,paint);
    paint.setStrokeWidth(w*.004f);paint.setColor(0xffb8f5e8);
    canvas.drawPath(waterline,paint);paint.setStyle(Paint.Style.FILL);
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
