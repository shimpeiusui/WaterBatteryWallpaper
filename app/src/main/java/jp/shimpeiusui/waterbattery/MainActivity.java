package jp.shimpeiusui.waterbattery;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
 @Override public void onCreate(Bundle savedInstanceState) {
  super.onCreate(savedInstanceState);
  LinearLayout layout = new LinearLayout(this);
  layout.setOrientation(LinearLayout.VERTICAL);
  layout.setPadding(48, 80, 48, 48);
  layout.setBackgroundColor(0xff0b1021);
  TextView title = new TextView(this);
  title.setText("WATER / BATTERY"); title.setTextColor(0xffe4faff); title.setTextSize(26); layout.addView(title);
  TextView info = new TextView(this);
  info.setText("\nBattery charge becomes a luminous liquid. Tilt your phone to move the waterline.\n");
  info.setTextColor(0xffa4c6d5); info.setTextSize(17); layout.addView(info);
  Button apply = new Button(this); apply.setText("Set live wallpaper");
  apply.setOnClickListener(v -> {
   Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
   intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, new ComponentName(this, WaterWallpaperService.class));
   startActivity(intent);
  });
  layout.addView(apply); setContentView(layout);
 }
}
