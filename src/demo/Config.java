package demo;

import java.awt.Color;
import javax.swing.KeyStroke;

record Config(String name, double screenArea, double minArea, int canvasW, int canvasH, Stage.Mode opensAs,
  float opacityFloor, boolean pulse, KeyStroke closeKey, KeyStroke fullScreenKey,
  boolean acceptAspect, boolean askBeforeClose, Color bands){
  enum Bands{
    black(Color.black), deepNapBlue(new Color(24, 48, 112)), boringGray(new Color(160, 160, 160));
    final Color color;
    Bands(Color color){ this.color= color; }
    }
  static Config parse(String name, String screenArea, String minArea, String canvasW, String canvasH, Stage.Mode opensAs,
    String opacityFloor, boolean pulse, String closeKey, String fullScreenKey,
    boolean acceptAspect, boolean askBeforeClose, Bands bands){
    if (name.isBlank()){ throw new IllegalArgumentException("The name can not be blank"); }
    double area= number(screenArea, "Screen area");
    if (!(area > 0 && area <= 1)){ throw new IllegalArgumentException("Screen area must be in (0, 1], got "+screenArea); }
    double min= number(minArea, "Min screen area");
    if (!(min > 0 && min <= area)){ throw new IllegalArgumentException("Min screen area must be in (0, screen area], got "+minArea); }
    int w= (int)number(canvasW, "Canvas width");
    int h= (int)number(canvasH, "Canvas height");
    if (w < 50 || h < 50){ throw new IllegalArgumentException("The canvas must be at least 50x50 units, got "+w+"x"+h); }
    double floor= number(opacityFloor, "Opacity floor");
    if (!(floor >= 0 && floor <= 1)){ throw new IllegalArgumentException("Opacity floor must be in [0, 1], got "+opacityFloor); }
    var close= key(closeKey);
    var full= key(fullScreenKey);
    if (close.equals(full)){ throw new IllegalArgumentException("The close key and the full screen key must differ, both are "+closeKey.trim()); }
    return new Config(name, area, min, w, h, opensAs, (float)floor, pulse, close, full, acceptAspect, askBeforeClose, bands.color);
    }
  private static double number(String s, String what){
    try{ return Double.parseDouble(s.trim()); }
    catch (NumberFormatException e){ throw new IllegalArgumentException(what+" must be a number, got "+s); }
    }
  private static KeyStroke key(String s){
    var k= KeyStroke.getKeyStroke(s.trim());
    if (k == null){ throw new IllegalArgumentException("Unknown key "+s+": use names like ESCAPE, F11, Q, ctrl Q"); }
    return k;
    }
  }
