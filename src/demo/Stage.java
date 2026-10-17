package demo;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;

final class Stage{
  enum Mode{ decorated, fearless, fullScreen }
  static final int tickMillis= 33;
  static final int quietTicks= 8;
  static final int stillTicks= 3;
  static final int dragTicks= 8;
  final int id;
  final Config c;
  final GraphicsDevice device= GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
  final GraphicsConfiguration gc= device.getDefaultConfiguration();
  final Rectangle screen= gc.getBounds();
  final Rectangle workArea= inset(screen, Toolkit.getDefaultToolkit().getScreenInsets(gc));
  final boolean translucent= device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
  final JComponent surface= new JComponent(){ @Override protected void paintComponent(Graphics g){ paintSurface((Graphics2D)g); } };
  final Content content;
  final Timer timer= new Timer(tickMillis, _ -> tick());
  final long t0= System.nanoTime();
  JFrame f;
  int canvasW;
  int canvasH;
  Mode mode;
  Dimension lastSeen= new Dimension();
  int quiet;
  int requests;
  String lastRequest= "none";
  String subtitle= "";
  Point pressAt;
  Rectangle pressBounds;
  boolean resizing;
  boolean dragged;
  int watch;
  int still;
  Point watched;

  Stage(int id, Config c){
    this.id= id;
    this.c= c;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    content= new Content(this);
    var m= new MouseAdapter(){
      @Override public void mousePressed(MouseEvent e){ press(e); }
      @Override public void mouseReleased(MouseEvent e){ release(e.getLocationOnScreen()); }
      @Override public void mouseDragged(MouseEvent e){ drag(e.getLocationOnScreen()); }
      @Override public void mouseMoved(MouseEvent e){ move(e.getPoint()); }
      };
    surface.addMouseListener(m);
    surface.addMouseMotionListener(m);
    }
  private JFrame makeFrame(boolean undecorated){
    var w= new JFrame(gc);
    w.setUndecorated(undecorated);
    w.setContentPane(surface);
    w.setIconImage(Main.icon);
    w.setTitle(title());
    w.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    var l= new WindowAdapter(){
      @Override public void windowClosing(WindowEvent e){ closeRequest("the close button of the desk decorations"); }
      @Override public void windowClosed(WindowEvent e){ desk(w, "closed"); }
      @Override public void windowIconified(WindowEvent e){ desk(w, "iconified"); }
      @Override public void windowDeiconified(WindowEvent e){ desk(w, "deiconified"); }
      @Override public void windowGainedFocus(WindowEvent e){ surface.requestFocusInWindow(); }
      };
    w.addWindowListener(l);
    w.addWindowFocusListener(l);
    w.addComponentListener(new ComponentAdapter(){
      @Override public void componentHidden(ComponentEvent e){ desk(w, "hidden"); }
      @Override public void componentShown(ComponentEvent e){ desk(w, "shown"); }
      });
    w.addWindowStateListener(this::stateChanged);
    bind(w, c.closeKey(), "close", () -> closeRequest("the close key"));
    bind(w, c.fullScreenKey(), "fullScreen", () -> realize(mode == Mode.fullScreen ? Mode.fearless : Mode.fullScreen, "the full screen key"));
    return w;
    }
  private static void bind(JFrame w, KeyStroke k, String name, Runnable r){
    w.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
    w.getRootPane().getActionMap().put(name, new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ r.run(); } });
    }
  private void desk(JFrame w, String what){ if (w == f){ log("the desk reports the window "+what); } }
  void log(String s){ Main.log("#"+id+" "+s); }

  void open(){
    realize(c.opensAs(), "open");
    timer.start();
    }
  private Rectangle window(){
    if (f != null && mode != Mode.fullScreen){ return f.getBounds(); }
    return fit(workArea, f == null ? width(c.screenArea()) : workArea.width);
    }
  private void realize(Mode m, String why){
    var r= window();
    var old= f;
    var left= mode;
    boolean maximize= m == Mode.decorated && left == Mode.fullScreen;
    log((left == null ? "" : state()+" -> ")+m+(maximize ? " maximized" : "")+" by "+why);
    boolean reuse= old != null && left != Mode.decorated && m != Mode.decorated;
    if (reuse && left == Mode.fullScreen){ device.setFullScreenWindow(null); }
    if (!reuse){ f= makeFrame(m != Mode.decorated); }
    mode= m;
    watch= 0;
    switch (m){
      case fullScreen -> device.setFullScreenWindow(f);
      case fearless -> { f.setBounds(r); f.setVisible(true); }
      case decorated -> { decorate(r, maximize); f.setVisible(true); }
      }
    f.toFront();
    surface.requestFocusInWindow();
    if (!reuse && old != null){ dispose(old, left); }
    }
  private void dispose(JFrame w, Mode was){
    if (was == Mode.fullScreen){ device.setFullScreenWindow(null); }
    w.dispose();
    }
  private void decorate(Rectangle outer, boolean maximize){
    f.pack();
    var in= f.getInsets();
    var r= fit(inset(outer, in), outer.width);
    surface.setPreferredSize(r.getSize());
    f.pack();
    f.setLocation(r.x-in.left, r.y-in.top);
    if (maximize){ f.setExtendedState(Frame.MAXIMIZED_BOTH); }
    }
  static Rectangle inset(Rectangle r, Insets in){ return new Rectangle(r.x+in.left, r.y+in.top, r.width-in.left-in.right, r.height-in.top-in.bottom); }
  Dimension size(double w){
    int iw= (int)w;
    return new Dimension(iw, (int)Math.round(iw*(double)canvasH/canvasW));
    }
  double fitWidth(double w, double h){ return Math.min(w, h*canvasW/canvasH); }
  Rectangle fit(Rectangle r, double w){
    var d= size(Math.min(w, fitWidth(r.width, r.height)));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y+(r.height-d.height)/2, d.width, d.height);
    }
  double width(double area){ return Math.sqrt(area*screen.width*(double)screen.height*canvasW/canvasH); }
  String state(){ return mode+((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0 ? " maximized" : ""); }

  void request(Dimension d){
    if (size(d.width).equals(d)){ return; }
    requests+= 1;
    double aspect= d.getWidth()/d.getHeight();
    lastRequest= "%.3f ".formatted(aspect)+(c.acceptAspect() ? "accepted" : "rejected");
    log("aspect ratio request "+lastRequest);
    if (c.acceptAspect()){ canvasW= Math.max(1, (int)Math.round(canvasH*aspect)); }
    }

  private void tick(){
    var now= surface.getSize();
    quiet= now.equals(lastSeen) ? quiet+1 : 0;
    lastSeen= now;
    if (quiet == quietTicks){ request(now); }
    if (watch > 0){ watch(); }
    if (mode == Mode.fearless && translucent && c.pulse()){ f.setOpacity(opacity()); }
    surface.repaint();
    }
  private float opacity(){
    double t= (System.nanoTime()-t0)/1e9;
    return (float)(c.opacityFloor()+(1-c.opacityFloor())*(0.5+0.5*Math.sin(t*2)));
    }
  private void stateChanged(WindowEvent e){
    boolean was= (e.getOldState() & Frame.MAXIMIZED_BOTH) != 0;
    boolean is= (e.getNewState() & (Frame.MAXIMIZED_BOTH|Frame.ICONIFIED)) != 0;
    if (mode != Mode.decorated || !was || is){ return; }
    log("unmaximized: watching for a title bar drag");
    watch= dragTicks;
    still= 0;
    watched= f.getLocation();
    }
  private void watch(){
    var at= f.getLocation();
    still= at.equals(watched) ? still+1 : 0;
    watched= at;
    watch-= 1;
    if (still == stillTicks){ realize(Mode.fullScreen, "unmaximize"); return; }
    if (watch == 0){ log("the desk is moving the window: a title bar drag, the window stays decorated"); }
    }

  void closeRequest(String why){
    log("close requested by "+why);
    if (c.askBeforeClose()){ content.confirm= true; return; }
    close();
    }
  void close(){
    log("closed by the program");
    timer.stop();
    dispose(f, mode);
    }
  void subtitle(String s){
    subtitle= s;
    f.setTitle(title());
    }
  private String title(){ return subtitle.isEmpty() ? c.name() : c.name()+"    "+subtitle; }

  double scale(){ return fitWidth(surface.getWidth(), surface.getHeight())/canvasW; }
  private Point offset(double s){ return new Point((int)((surface.getWidth()-canvasW*s)/2), (int)((surface.getHeight()-canvasH*s)/2)); }
  private Point canvasPoint(Point p){
    double s= scale();
    var o= offset(s);
    return new Point((int)Math.floor((p.x-o.x)/s), (int)Math.floor((p.y-o.y)/s));
    }
  private int legs(){ return Math.min(screen.height/16, surface.getHeight()/4); }
  private int tip(){ return legs()/5; }
  private boolean inCorner(Point p){ return mode != Mode.decorated && p.x+p.y <= legs(); }
  private boolean inTip(Point p){ return mode == Mode.fearless && p.x <= tip() && p.y <= tip(); }

  private void move(Point p){
    content.mouse= canvasPoint(p);
    int cursor= !inCorner(p) ? Cursor.DEFAULT_CURSOR : inTip(p) ? Cursor.NW_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
    surface.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  private void press(MouseEvent e){
    if (!inCorner(e.getPoint())){ content.press(canvasPoint(e.getPoint())); return; }
    if (e.getClickCount() == 2){ pressAt= null; realize(Mode.decorated, "a corner double click"); return; }
    pressAt= e.getLocationOnScreen();
    pressBounds= new Rectangle(surface.getLocationOnScreen(), surface.getSize());
    resizing= inTip(e.getPoint());
    dragged= false;
    }
  private void drag(Point p){
    if (pressAt == null){ return; }
    if (!dragged && p.distance(pressAt) <= 3){ return; }
    dragged= true;
    if (mode == Mode.fullScreen){ realize(Mode.fearless, "a corner drag"); pressBounds.setSize(f.getSize()); }
    var t= new Rectangle(pressBounds);
    t.translate(p.x-pressAt.x, p.y-pressAt.y);
    f.setBounds(resizing ? resized(t) : moved(t));
    }
  private void release(Point p){
    if (pressAt == null){ return; }
    pressAt= null;
    if (!dragged || mode != Mode.fearless){ return; }
    int snap= screen.height/16;
    log("corner drag ended with the pointer at "+p.x+","+p.y+", the work area at "+workArea.x+","+workArea.y+", snap within "+snap);
    if (p.distance(workArea.getLocation()) <= snap){ realize(Mode.fullScreen, "a corner drop at the top left"); }
    }
  private Rectangle moved(Rectangle t){
    int right= workArea.x+workArea.width;
    int bottom= workArea.y+workArea.height;
    int x= Math.max(workArea.x, t.x);
    int y= Math.max(workArea.y, t.y);
    var d= size(Math.max(Math.min(t.width, fitWidth(right-x, bottom-y)), width(c.minArea())));
    return new Rectangle(Math.min(x, right-d.width), Math.min(y, bottom-d.height), d.width, d.height);
    }
  private Rectangle resized(Rectangle t){
    int right= pressBounds.x+pressBounds.width;
    int bottom= pressBounds.y+pressBounds.height;
    double w= Math.max(right-t.x, (bottom-t.y)*canvasW/(double)canvasH);
    var d= size(Math.max(Math.min(w, fitWidth(right-workArea.x, bottom-workArea.y)), width(c.minArea())));
    return new Rectangle(right-d.width, bottom-d.height, d.width, d.height);
    }

  private void paintSurface(Graphics2D g){
    g.setColor(c.bands());
    g.fillRect(0, 0, surface.getWidth(), surface.getHeight());
    double s= scale();
    var o= offset(s);
    var cg= (Graphics2D)g.create();
    cg.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    cg.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    cg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    cg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    cg.translate(o.x, o.y);
    cg.scale(s, s);
    cg.clipRect(0, 0, canvasW, canvasH);
    content.paint(cg, canvasW, canvasH);
    cg.dispose();
    if (mode != Mode.decorated){ paintCorner(g); }
    }
  private void paintCorner(Graphics2D g){
    int l= legs();
    var tri= new Polygon(new int[]{0, l, 0}, new int[]{0, 0, l}, 3);
    g.setColor(new Color(28, 28, 32));
    g.fill(tri);
    if (mode == Mode.fearless){ g.setColor(new Color(64, 64, 70)); g.fillRect(0, 0, tip(), tip()); }
    var ig= (Graphics2D)g.create();
    ig.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    ig.clip(tri);
    int side= l/2;
    int at= (int)Math.round(l*(2-Math.sqrt(2))/2)-side/2;
    ig.drawImage(Main.icon, at, at, side, side, null);
    ig.dispose();
    }

  List<String> status(){
    var r= surface.getSize();
    String opacity= translucent ? "" : " (translucency not supported on this desk)";
    return List.of(
      "canvas "+canvasW+"x"+canvasH+" units, scale %.3f".formatted(scale()),
      "window content "+r.width+"x"+r.height+" px, "+state()+opacity,
      "aspect ratio requests: "+requests+", last "+lastRequest,
      "mouse "+content.mouse.x+", "+content.mouse.y+" (canvas units)");
    }
  String describe(){
    if (!f.isDisplayable()){ return "#"+id+" "+c.name()+": closed"; }
    var r= f.getBounds();
    String shown= f.isShowing() ? "showing" : "NOT showing";
    var owner= KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    String focus= !f.isFocused() ? "" : ", focused, keys to "+(owner == null ? "nobody" : owner.getClass().getSimpleName());
    return "#"+id+" "+c.name()+": "+state()+", window "+r.width+"x"+r.height+" at "+r.x+","+r.y+", "+shown+focus
      +", state "+f.getExtendedState()+", opacity %.2f".formatted(f.getOpacity());
    }
  }
