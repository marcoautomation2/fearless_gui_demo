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
  Point mouse= new Point(-1, -1);
  Dimension lastSize= new Dimension();
  int quiet;
  int requests;
  String lastRequest= "none";
  String subtitle= "";
  Point pressAt;
  Rectangle pressBounds;
  boolean resizing;
  boolean dragged;
  int watching;
  int still;
  Point lastLocation;

  Stage(int id, Config c){
    this.id= id;
    this.c= c;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    content= new Content(this);
    var m= new MouseAdapter(){
      @Override public void mousePressed(MouseEvent e){ press(e); }
      @Override public void mouseReleased(MouseEvent e){ release(e.getLocationOnScreen()); }
      @Override public void mouseDragged(MouseEvent e){ drag(e); }
      @Override public void mouseMoved(MouseEvent e){ move(e.getPoint()); }
      };
    surface.addMouseListener(m);
    surface.addMouseMotionListener(m);
    surface.setOpaque(true);
    realize(c.opensAs(), "open");
    timer.start();
    }
  private JFrame makeFrame(boolean undecorated){
    var frame= new JFrame(gc);
    frame.setUndecorated(undecorated);
    frame.setBackground(Color.black);
    frame.setContentPane(surface);
    frame.setIconImage(Main.icon);
    frame.setTitle(title());
    frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    var l= new WindowAdapter(){
      @Override public void windowClosing(WindowEvent e){ closeRequest("the close button of the desk decorations"); }
      @Override public void windowClosed(WindowEvent e){ desk(frame, "closed"); }
      @Override public void windowIconified(WindowEvent e){ desk(frame, "iconified"); }
      @Override public void windowDeiconified(WindowEvent e){ desk(frame, "deiconified"); }
      @Override public void windowGainedFocus(WindowEvent e){ surface.requestFocusInWindow(); }
      };
    frame.addWindowListener(l);
    frame.addWindowFocusListener(l);
    frame.addComponentListener(new ComponentAdapter(){
      @Override public void componentHidden(ComponentEvent e){ desk(frame, "hidden"); }
      @Override public void componentShown(ComponentEvent e){ desk(frame, "shown"); }
      });
    frame.addWindowStateListener(this::stateChanged);
    bind(frame, c.closeKey(), "close", () -> closeRequest("the close key"));
    bind(frame, c.fullScreenKey(), "fullScreen", () -> realize(mode == Mode.fullScreen ? Mode.fearless : Mode.fullScreen, "the full screen key"));
    return frame;
    }
  private static void bind(JFrame frame, KeyStroke k, String name, Runnable r){
    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
    frame.getRootPane().getActionMap().put(name, new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ r.run(); } });
    }
  private void desk(JFrame frame, String what){ if (frame == f){ log("the desk reports the window "+what); } }
  private void log(String s){ Main.log("#"+id+" "+s); }

  private Rectangle window(){
    if (f != null && mode != Mode.fullScreen){ return f.getBounds(); }
    return fit(workArea, f == null ? areaWidth(c.screenArea()) : workArea.width);
    }
  private void realize(Mode m, String why){
    var r= window();
    var old= f;
    boolean maximize= m == Mode.decorated && mode == Mode.fullScreen;
    log((mode == null ? "" : state()+" -> ")+m+(maximize ? " maximized" : "")+" by "+why);
    if (old == null || mode == Mode.decorated || m == Mode.decorated){ f= makeFrame(m != Mode.decorated); }
    mode= m;
    watching= 0;
    switch (m){
      case fullScreen -> device.setFullScreenWindow(f);
      case fearless -> { windowed(f); f.setBounds(r); f.setVisible(true); }
      case decorated -> {
        f.setBounds(wrapped(r));
        if (maximize){ f.setExtendedState(Frame.MAXIMIZED_BOTH); }
        f.setVisible(true);
        }
      }
    f.toFront();
    surface.requestFocusInWindow();
    if (old != null && old != f){ dispose(old); }
    }
  private void windowed(JFrame frame){ if (device.getFullScreenWindow() == frame){ device.setFullScreenWindow(null); } }
  private void dispose(JFrame frame){
    windowed(frame);
    frame.dispose();
    }
  private Rectangle wrapped(Rectangle r){
    f.pack();
    var in= f.getInsets();
    var d= fit(inset(r, in), r.width);
    return new Rectangle(d.x-in.left, d.y-in.top, d.width+in.left+in.right, d.height+in.top+in.bottom);
    }
  private static Rectangle inset(Rectangle r, Insets in){ return new Rectangle(r.x+in.left, r.y+in.top, r.width-in.left-in.right, r.height-in.top-in.bottom); }
  private Dimension size(double w){
    int iw= (int)w;
    return new Dimension(iw, (int)Math.round(iw*(double)canvasH/canvasW));
    }
  private double fitWidth(double w, double h){ return Math.min(w, h*canvasW/canvasH); }
  private Rectangle fit(Rectangle r, double w){
    var d= size(Math.min(w, fitWidth(r.width, r.height)));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y+(r.height-d.height)/2, d.width, d.height);
    }
  private double areaWidth(double area){ return Math.min(Math.sqrt(area*screen.width*(double)screen.height*canvasW/canvasH), fitWidth(workArea.width, workArea.height)); }
  private Dimension clamped(double w, int roomW, int roomH){ return size(Math.max(Math.min(w, fitWidth(roomW, roomH)), areaWidth(c.minArea()))); }
  private String state(){ return mode+((f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0 ? " maximized" : ""); }

  private void request(Dimension d){
    if (d.width == 0 || d.height == 0 || size(d.width).equals(d)){ return; }
    requests+= 1;
    double aspect= d.getWidth()/d.getHeight();
    lastRequest= "%.3f ".formatted(aspect)+(c.acceptAspect() ? "accepted" : "rejected");
    log("aspect ratio request "+lastRequest);
    if (c.acceptAspect()){ canvasW= Math.max(1, (int)Math.round(canvasH*aspect)); }
    }

  private void tick(){
    var now= surface.getSize();
    quiet= now.equals(lastSize) ? quiet+1 : 0;
    lastSize= now;
    if (quiet == quietTicks){ request(now); }
    if (watching > 0){ watch(); }
    if (mode == Mode.fearless && translucent && c.pulse()){ f.setOpacity(opacity()); }
    surface.repaint();
    }
  private float opacity(){
    double t= (System.nanoTime()-t0)/1e9;
    return (float)(c.opacityFloor()+(1-c.opacityFloor())*(0.5+0.5*Math.sin(t*2)));
    }
  private void stateChanged(WindowEvent e){
    boolean unmaximized= (e.getOldState() & Frame.MAXIMIZED_BOTH) != 0 && (e.getNewState() & (Frame.MAXIMIZED_BOTH|Frame.ICONIFIED)) == 0;
    if (e.getWindow() != f || mode != Mode.decorated || !unmaximized){ return; }
    log("unmaximized: watching for a title bar drag");
    watching= dragTicks;
    still= 0;
    lastLocation= f.getLocation();
    }
  private void watch(){
    var at= f.getLocation();
    still= at.equals(lastLocation) ? still+1 : 0;
    lastLocation= at;
    watching-= 1;
    if (still == stillTicks){ realize(Mode.fullScreen, "unmaximize"); return; }
    if (watching == 0){ log("the desk is moving the window: a title bar drag, the window stays decorated"); }
    }

  private void closeRequest(String why){
    log("close requested by "+why);
    if (c.askBeforeClose()){ content.confirm= true; return; }
    close();
    }
  void close(){
    log("closed by the program");
    timer.stop();
    dispose(f);
    }
  void subtitle(String s){
    subtitle= s;
    f.setTitle(title());
    }
  private String title(){ return subtitle.isEmpty() ? c.name() : c.name()+"    "+subtitle; }

  private double scale(){ return fitWidth(surface.getWidth(), surface.getHeight())/canvasW; }
  private Point offset(double s){ return new Point((int)((surface.getWidth()-canvasW*s)/2), (int)((surface.getHeight()-canvasH*s)/2)); }
  private Point canvasPoint(Point p){
    double s= scale();
    var o= offset(s);
    return new Point((int)Math.floor((p.x-o.x)/s), (int)Math.floor((p.y-o.y)/s));
    }
  private int legs(){ return Math.min(screen.height/16, surface.getHeight()/4); }
  private int tip(){ return legs()/5; }
  private boolean inCorner(Point p){ return mode != Mode.decorated && p.x+p.y < legs(); }
  private boolean inTip(Point p){ return mode == Mode.fearless && p.x < tip() && p.y < tip(); }

  private void move(Point p){
    if (!inCorner(p)){ mouse= canvasPoint(p); }
    int cursor= !inCorner(p) ? Cursor.DEFAULT_CURSOR : inTip(p) ? Cursor.NW_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
    surface.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  private void press(MouseEvent e){
    if (!inCorner(e.getPoint())){ content.press(canvasPoint(e.getPoint())); return; }
    if (e.getClickCount() == 2){ realize(Mode.decorated, "a corner double click"); return; }
    pressAt= e.getLocationOnScreen();
    pressBounds= f.getBounds();
    resizing= inTip(e.getPoint());
    dragged= false;
    }
  private void drag(MouseEvent e){
    if (pressAt == null){ move(e.getPoint()); return; }
    var p= e.getLocationOnScreen();
    if (!dragged && p.distance(pressAt) <= 3){ return; }
    dragged= true;
    if (mode == Mode.fullScreen){ realize(Mode.fearless, "a corner drag"); pressBounds.setSize(f.getSize()); }
    var at= new Point(pressBounds.x+p.x-pressAt.x, pressBounds.y+p.y-pressAt.y);
    f.setBounds(resizing ? resized(at) : moved(at));
    }
  private void release(Point p){
    if (pressAt == null){ return; }
    pressAt= null;
    if (!dragged || mode != Mode.fearless){ return; }
    if (p.distance(workArea.getLocation()) <= screen.height/16){ realize(Mode.fullScreen, "a corner drop at the top left"); }
    }
  private Rectangle moved(Point at){
    int right= workArea.x+workArea.width;
    int bottom= workArea.y+workArea.height;
    int x= Math.max(workArea.x, at.x);
    int y= Math.max(workArea.y, at.y);
    var d= clamped(pressBounds.width, right-x, bottom-y);
    return new Rectangle(Math.min(x, right-d.width), Math.min(y, bottom-d.height), d.width, d.height);
    }
  private Rectangle resized(Point at){
    int right= pressBounds.x+pressBounds.width;
    int bottom= pressBounds.y+pressBounds.height;
    var d= clamped(Math.max(right-at.x, (bottom-at.y)*(double)canvasW/canvasH), right-workArea.x, bottom-workArea.y);
    return new Rectangle(right-d.width, bottom-d.height, d.width, d.height);
    }

  private void paintSurface(Graphics2D g){
    g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
    g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    g.setColor(c.bands());
    g.fillRect(0, 0, surface.getWidth(), surface.getHeight());
    double s= scale();
    var o= offset(s);
    var cg= (Graphics2D)g.create();
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
    var ig= (Graphics2D)g.create();
    ig.clip(tri);
    int side= l/2;
    int at= (int)Math.round(l*(2-Math.sqrt(2))/2)-side/2;
    ig.drawImage(Main.icon, at, at, side, side, null);
    ig.dispose();
    if (mode == Mode.fearless){ g.setColor(new Color(64, 64, 70)); g.fillRect(0, 0, tip(), tip()); }
    }

  List<String> status(){
    var r= surface.getSize();
    String opacity= translucent ? "" : " (translucency not supported on this desk)";
    return List.of(
      "canvas "+canvasW+"x"+canvasH+" units, scale %.3f".formatted(scale()),
      "window content "+r.width+"x"+r.height+" px, "+state()+opacity,
      "aspect ratio requests: "+requests+", last "+lastRequest,
      "mouse "+mouse.x+", "+mouse.y+" (canvas units)");
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
