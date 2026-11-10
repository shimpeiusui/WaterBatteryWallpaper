package jp.shimpeiusui.waterbattery;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.RectF;
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
  private Bitmap liquidArtwork;
  private SensorManager sensors;
  private Sensor sensor;
  private boolean visible;
  private boolean registered;
  private float battery = .65f, tilt = 0f, targetTilt = 0f, phase = 0f, gx = 0f, gy = 9.8f;
  private boolean charging = false;
  private long lastNs;
  private float ambientTime=0f;
  private final Random random=new Random();
  private final float[] bx=new float[48],by=new float[48],br=new float[48],bs=new float[48],bp=new float[48];
  private final boolean[] active=new boolean[48];
  private float downX=0f,downY=1f,smoothX=0f,smoothY=1f,slosh=0f,sloshSpeed=0f;
  private final float[] waveA=new float[4],waveV=new float[4];
  private long sensorNs=0;
  private float filteredSpeed=0f, previousAngular=0f;
  private final float[] depth=new float[48],drift=new float[48];
  private void ensureArtwork() {
    if(liquidArtwork==null) {
      liquidArtwork=BitmapFactory.decodeResource(getResources(),R.drawable.liquid_art);
    }
  }
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
    float alpha=Math.min(1f,dt*5f);
    filteredSpeed*=Math.exp(-dt*4.0f);
    smoothX+=(downX-smoothX)*alpha; smoothY+=(downY-smoothY)*alpha;
    float n=(float)Math.hypot(smoothX,smoothY);
    if(n>.001f){smoothX/=n;smoothY/=n;}
    sloshSpeed+=(-slosh*19f-sloshSpeed*6f)*dt;
    slosh+=sloshSpeed*dt;
    for(int k=0;k<waveA.length;k++){
      float omega=6.8f+k*2.8f;
      waveV[k]+=(-omega*omega*waveA[k]-(5.8f+k*.65f)*waveV[k])*dt;
      waveA[k]+=waveV[k]*dt;
    }
    for(int i=0;i<active.length;i++){
      if(!active[i]){
        if(random.nextFloat()<dt*(charging?1.5f:.8f)){
          active[i]=true;bx[i]=random.nextFloat();by[i]=1.05f+random.nextFloat()*.2f;
          br[i]=.002f+(float)Math.pow(random.nextFloat(),2)*.016f;
          bs[i]=.05f+random.nextFloat()*.18f;bp[i]=random.nextFloat()*6.28f;
          depth[i]=random.nextFloat();drift[i]=(random.nextFloat()-.5f)*.07f;
        }
      }else{
        by[i]-=bs[i]*dt;bx[i]+=drift[i]*dt;bp[i]+=dt*(1f+br[i]*80f);
        if(by[i]<-.2f)active[i]=false;
      }
    }
    phase += dt * 1.5f;
    ambientTime += dt;
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
     float seconds=sensorNs==0?.02f:Math.max(.008f,Math.min(.1f,(event.timestamp-sensorNs)*1e-9f));
     sensorNs=event.timestamp;
     float angularSpeed=change/seconds;
     // Only changes in motion should excite the liquid; do not inject
     // energy continuously while rotating at a steady speed.
     float acceleration=Math.abs(angularSpeed-previousAngular);
     previousAngular=angularSpeed;
     filteredSpeed=filteredSpeed*.78f+angularSpeed*.22f;
     if(acceleration>.45f && angularSpeed>.12f){
       float kick=Math.min(1.0f,acceleration*.10f);
       sloshSpeed=Math.max(-.4f,Math.min(.4f,sloshSpeed+kick*.055f));
       for(int k=0;k<waveV.length;k++)
         waveV[k]=Math.max(-.65f,Math.min(.65f,waveV[k]+(k%2==0?1f:-1f)*kick*.12f/(k+1)));
     }
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
    return surfaceLayer(nx,ny,t,w,h,filled,0);
  }
  private Path surfaceLayer(float nx,float ny,float t,float w,float h,boolean filled,int layer){
    Path p=new Path();float tx=-ny,ty=nx,span=(float)Math.hypot(w,h)*1.5f;
    if(filled){p.moveTo(nx*t-tx*span+nx*span,ny*t-ty*span+ny*span);p.lineTo(nx*t-tx*span,ny*t-ty*span);}
    for(int i=0;i<=160;i++){
      float q=-span+2*span*i/160f;
      float u=q/Math.max(1,w);
      float ripple=(float)Math.sin(u*(5.0f+layer*.7f)+phase*(.72f+layer*.08f)+layer*1.4f)*Math.min(w,h)*(.007f+layer*.0018f);
      ripple+=(float)Math.sin(u*(10.0f+layer)+phase*1.35f+layer*.9f)*Math.min(w,h)*.0032f;
      ripple+=Math.max(-.25f,Math.min(.25f,slosh))*(float)Math.sin(u*3f)*Math.min(w,h)*.012f;
      for(int k=0;k<waveA.length;k++){
        ripple+=Math.max(-.22f,Math.min(.22f,waveA[k]))*(float)Math.sin(u*(3.1f+k*2.5f)+k*.91f)*Math.min(w,h)*.018f/(1f+k*.3f);
      }
      ripple=Math.max(-Math.min(w,h)*.032f,Math.min(Math.min(w,h)*.032f,ripple));
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
    // Background is always present, independent of the liquid fill.
    ensureArtwork();
    canvas.drawColor(0xff041324);
    // Resolution-independent layered glass background: rendered at native
    // screen resolution rather than scaling the old 400x210 JPEG.
    float dx=(float)Math.sin(ambientTime*.095f)*w*.018f;
    float dy=(float)Math.cos(ambientTime*.071f)*h*.012f;
    canvas.save();canvas.translate(dx,dy);
    paint.setStyle(Paint.Style.FILL);paint.setAlpha(255);
    paint.setShader(new LinearGradient(0,0,w,h,
      new int[]{0xff020914,0xff06243b,0xff073f51,0xff0b2746,0xff020917},
      new float[]{0f,.26f,.53f,.76f,1f},Shader.TileMode.CLAMP));
    canvas.drawRect(-w*.1f,-h*.1f,w*1.1f,h*1.1f,paint);paint.setShader(null);
    for(int i=0;i<8;i++){
      float cx=w*(.15f+.11f*i),cy=h*(.18f+.11f*(i%5));
      paint.setShader(new RadialGradient(cx,cy,w*(.36f+.08f*(i%3)),
        new int[]{i%3==0?0x5541ddc3:i%3==1?0x443568b4:0x44e5a969,0x00203558},
        null,Shader.TileMode.CLAMP));
      canvas.drawRect(-w*.1f,-h*.1f,w*1.1f,h*1.1f,paint);paint.setShader(null);
    }
    // Smooth translucent glass folds and hairline specular highlights.
    for(int i=0;i<9;i++){
      float offset=i*.055f;
      Path fold=new Path();
      fold.moveTo(-w*.25f,h*(.60f+offset));
      fold.cubicTo(w*.25f,h*(.33f+offset),w*.45f,h*(.83f-offset),w*.78f,h*(.47f-offset));
      fold.cubicTo(w*1.04f,h*(.17f+offset),w*.82f,h*(.13f+offset),w*1.23f,h*(.03f+offset));
      paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*(.024f+i*.006f));
      paint.setShader(new LinearGradient(0,h*.7f,w,h*.2f,
        new int[]{0x0045d4d1,0x3341d7cb,0x33698dc2,0x00ffd7a2},
        null,Shader.TileMode.CLAMP));
      canvas.drawPath(fold,paint);paint.setShader(null);
      paint.setStrokeWidth(w*.0016f);
      paint.setColor(i%3==0?0x99f4c48a:0x7781e5de);canvas.drawPath(fold,paint);
    }
    paint.setStyle(Paint.Style.FILL);canvas.restore();
    // Slow, subtle ambient light undulates independently from the water.
    float glowX=w*(.55f+.22f*(float)Math.sin(ambientTime*.14f));
    float glowY=h*(.38f+.12f*(float)Math.cos(ambientTime*.11f));
    paint.setShader(new RadialGradient(glowX,glowY,w*.95f,
      new int[]{0x3349a8b5,0x17228f9c,0x00051229},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    // Dark vignette keeps icons legible while retaining clear detail.
    paint.setShader(new LinearGradient(0,0,0,h,
      new int[]{0x44020b20,0x0004142a,0x22010a20},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    float nx=smoothX,ny=smoothY,t=threshold(nx,ny,w,h);
    Path liquid=surface(nx,ny,t,w,h,true);
    canvas.save();canvas.clipPath(liquid);
    // Subtle underwater softening: translucent refractive layers over
    // the same gently moving background, without an expensive full blur.
    float refractionX=(float)Math.sin(ambientTime*.17f)*w*.008f;
    // Water gets its own soft optical tint; background remains sharp above it.
    paint.setShader(new RadialGradient(w*.64f,h*.69f,w*.75f,
      new int[]{0x2241d9cf,0x113e88a9,0x00051930},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    // Translucent depth tint keeps the photographic texture visible.
    paint.setShader(new LinearGradient(0,0,w,h,
      new int[]{0x44020b19,0x18003d45,0x18006c73,0x4403102b},
      null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    paint.setShader(new RadialGradient(w*.86f,h*.26f,w*.9f,
      new int[]{0xaa00d4b7,0x665e93ba,0x002a3568},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    // Volumetric illumination: overlapping pools of refracted light.
    for(int i=0;i<5;i++){
      float cx=w*(.14f+i*.2f),cy=h*(.35f+(i%3)*.19f);
      float radius=w*(.65f+(i%2)*.25f);
      paint.setShader(new RadialGradient(cx,cy,radius,
        new int[]{i%2==0?0x4478e8d6:0x44f7bd7c,0x1b285f91,0x00040b20},
        null,Shader.TileMode.CLAMP));
      canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    }
    // Smooth glass caustics instead of straight graphic ribbons.
    for(int i=0;i<7;i++){
      float shift=i*.075f;
      Path ribbon=new Path();
      ribbon.moveTo(-w*.38f,h*(.84f+shift));
      ribbon.cubicTo(w*.15f,h*(.69f+shift),w*.52f,h*(1.07f-shift),w*.79f,h*(.65f-shift));
      ribbon.cubicTo(w*1.02f,h*(.36f-shift),w*.91f,h*(.19f+shift),w*1.25f,h*(.08f+shift));
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(w*(.028f+i*.013f));
      paint.setShader(new LinearGradient(0,h*.86f,w,h*.14f,
        new int[]{0x002dd8bf,0x4465c6ce,0x554d66ab,0x00edb16d},null,Shader.TileMode.CLAMP));
      canvas.drawPath(ribbon,paint);paint.setShader(null);
      paint.setStrokeWidth(w*(.0015f+i*.0003f));
      paint.setColor(i%3==0?0x99ffd69c:0x5577e6db);
      canvas.drawPath(ribbon,paint);
    }
    paint.setStyle(Paint.Style.FILL);
    paint.setShader(new RadialGradient(w*.12f,h*.66f,w*.75f,
      new int[]{0x7740b4ad,0x22425c9a,0x00051025},null,Shader.TileMode.CLAMP));
    canvas.drawRect(0,0,w,h,paint);paint.setShader(null);
    paint.setStyle(Paint.Style.FILL);
    for(int i=0;i<active.length;i++)if(active[i]){
      float z=depth[i];
      float x=(bx[i]+.018f*(float)Math.sin(bp[i]))*w,y=by[i]*h;
      float radius=br[i]*w*(.55f+z*.9f);
      // Dark core, refracted teal rim and a warm specular glint create depth.
      paint.setStyle(Paint.Style.FILL);
      paint.setShader(new RadialGradient(x-radius*.25f,y-radius*.3f,Math.max(1f,radius*1.35f),
        new int[]{0x66ffffff,0x223ac8c5,0x99071b3a,0x006fe9d2},
        new float[]{0f,.32f,.72f,1f},Shader.TileMode.CLAMP));
      canvas.drawCircle(x,y,radius*1.35f,paint);paint.setShader(null);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(Math.max(.6f,radius*(.07f+.06f*z)));
      paint.setColor(z>.5f?0xaac5f5e7:0x5570b6c8);
      canvas.drawCircle(x,y,radius,paint);
      paint.setStrokeWidth(Math.max(.7f,radius*.15f));
      paint.setColor(z>.5f?0xbbffe4b3:0x6687f1e9);
      canvas.drawArc(x-radius*.83f,y-radius*.83f,x+radius*.83f,y+radius*.83f,205f,83f,false,paint);
      paint.setStyle(Paint.Style.FILL);
      paint.setColor(z>.5f?0xaafff7df:0x77bce4f6);
      canvas.drawCircle(x-radius*.29f,y-radius*.34f,Math.max(.6f,radius*.13f),paint);
    }
    canvas.restore();
    // Three displaced translucent sheets give the surface visible thickness.
    for(int layer=3;layer>=1;layer--){
      float offset=layer*w*.009f;
      Path sheet=surfaceLayer(nx,ny,t+offset,w,h,true,layer);
      paint.setStyle(Paint.Style.FILL);
      paint.setShader(new LinearGradient(0,0,w,h,
        layer==3?new int[]{0x6632c5cb,0x223c69ad,0x0068c6b9}:
        layer==2?new int[]{0x995ac7c3,0x44347d9d,0x006ac5d4}:
                 new int[]{0x887ce2d6,0x555c8b9e,0x00ffd49a},
        null,Shader.TileMode.CLAMP));
      canvas.drawPath(sheet,paint);paint.setShader(null);
      Path rim=surfaceLayer(nx,ny,t+offset,w,h,false,layer);
      paint.setStyle(Paint.Style.STROKE);
      paint.setStrokeWidth(w*(layer==1?.008f:.004f));
      paint.setColor(layer==1?0x88ffe2b4:0x5580e8e4);
      canvas.drawPath(rim,paint);
    }
    // Thin refracted highlight follows the true moving waterline.
    Path waterline=surface(nx,ny,t,w,h,false);
    paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(w*.018f);
    paint.setColor(0x77edb478);canvas.drawPath(waterline,paint);
    paint.setStrokeWidth(w*.0035f);paint.setColor(0xffd8fff2);
    canvas.drawPath(waterline,paint);paint.setStyle(Paint.Style.FILL);
   } catch (Exception ignored) { } finally { if(canvas!=null)holder.unlockCanvasAndPost(canvas); }
  }
 }
}
