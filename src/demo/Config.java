package demo;

import java.awt.Color;
import javax.swing.KeyStroke;

record Config(String name, double screenArea, double minArea, int canvasW, int canvasH, Stage.Mode opensAs,
  double opacityFloor, boolean pulse, KeyStroke closeKey, KeyStroke fullScreenKey,
  boolean acceptAspect, boolean askBeforeClose, Color bands){
  enum Bands{
    black(Color.black), deepNapBlue(new Color(24, 48, 112)), boringGray(new Color(160, 160, 160));
    final Color color;
    Bands(Color color){ this.color= color; }
    }
  Config{
    if (name.isBlank()){ throw new IllegalArgumentException("The name can not be blank"); }
    if (!(screenArea > 0 && screenArea <= 1)){ throw new IllegalArgumentException("Screen area must be in (0, 1], got "+screenArea); }
    if (!(minArea > 0 && minArea <= screenArea)){ throw new IllegalArgumentException("Min screen area must be in (0, screen area], got "+minArea); }
    if (canvasW < 50 || canvasH < 50){ throw new IllegalArgumentException("The virtual pixel space must be at least 50x50 virtual pixels, got "+canvasW+"x"+canvasH); }
    if (!(opacityFloor >= 0 && opacityFloor <= 1)){ throw new IllegalArgumentException("Opacity floor must be in [0, 1], got "+opacityFloor); }
    if (closeKey.equals(fullScreenKey)){ throw new IllegalArgumentException("The close key and the full screen key must differ, both are "+closeKey); }
    }
  static double number(String s, String what){
    try{ return Double.parseDouble(s.trim()); }
    catch (NumberFormatException e){ throw new IllegalArgumentException(what+" must be a number, got "+s); }
    }
  static int whole(String s, String what){
    try{ return Integer.parseInt(s.trim()); }
    catch (NumberFormatException e){ throw new IllegalArgumentException(what+" must be a whole number, got "+s); }
    }
  static KeyStroke key(String s){
    var k= KeyStroke.getKeyStroke(s.trim());
    if (k == null){ throw new IllegalArgumentException("Unknown key "+s+": use names like ESCAPE, F11, Q, ctrl Q"); }
    return k;
    }
  }
