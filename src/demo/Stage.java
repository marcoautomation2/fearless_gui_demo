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
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
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
  final int id;
  final Config c;
  final BufferedImage icon;
  final GraphicsDevice device;
  final GraphicsConfiguration gc;
  final JComponent surface;
  final Content content;
  final Rectangle screen;
  final Rectangle workArea;
  final boolean translucent;
  final Timer timer= new Timer(tickMillis, _ -> tick());
  final long t0= System.nanoTime();
  JFrame f;
  int canvasW;
  int canvasH;
  Mode mode;
  Dimension lastSeen= new Dimension();
  int quiet;
  boolean pending;
  boolean swapping;
  boolean closed;
  int requests;
  double lastAspect;
  String subtitle= "";
  Point pressAt;
  Point grip;
  Point anchor;
  Dimension natural;
  boolean resizing;
  boolean dragged;

  Stage(int id, Config c, BufferedImage icon, BufferedImage picture){
    this.id= id;
    this.c= c;
    this.icon= icon;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    device= GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    gc= device.getDefaultConfiguration();
    screen= gc.getBounds();
    var in= Toolkit.getDefaultToolkit().getScreenInsets(gc);
    workArea= new Rectangle(screen.x+in.left, screen.y+in.top, screen.width-in.left-in.right, screen.height-in.top-in.bottom);
    translucent= device.isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
    content= new Content(this, picture);
    surface= new JComponent(){ @Override protected void paintComponent(Graphics g){ paintSurface((Graphics2D)g); } };
    var m= new MouseAdapter(){
      @Override public void mousePressed(MouseEvent e){ press(e); }
      @Override public void mouseReleased(MouseEvent e){ pressAt= null; }
      @Override public void mouseDragged(MouseEvent e){ drag(e); }
      @Override public void mouseMoved(MouseEvent e){ move(e); }
      };
    surface.addMouseListener(m);
    surface.addMouseMotionListener(m);
    }
  private JFrame makeFrame(boolean undecorated){
    var w= new JFrame(gc);
    w.setUndecorated(undecorated);
    w.setContentPane(surface);
    w.setIconImage(icon);
    w.setTitle(title());
    w.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    w.addWindowListener(new WindowAdapter(){
      @Override public void windowClosing(WindowEvent e){ closeRequest("the close button of the desk decorations"); }
      @Override public void windowClosed(WindowEvent e){ desk(w, "closed"); }
      @Override public void windowIconified(WindowEvent e){ desk(w, "iconified"); }
      @Override public void windowDeiconified(WindowEvent e){ desk(w, "deiconified"); }
      });
    w.addComponentListener(new ComponentAdapter(){
      @Override public void componentHidden(ComponentEvent e){ desk(w, "hidden"); }
      @Override public void componentShown(ComponentEvent e){ desk(w, "shown"); }
      });
    w.addWindowStateListener(this::stateChanged);
    bind(w, c.closeKey(), "close", () -> closeRequest("the close key"));
    bind(w, c.fullScreenKey(), "fullScreen", this::toggleFullScreen);
    return w;
    }
  private static void bind(JFrame w, KeyStroke k, String name, Runnable r){
    w.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
    w.getRootPane().getActionMap().put(name, new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ r.run(); } });
    }
  private void desk(JFrame w, String what){ if (w == f && !swapping && !closed){ log("the desk reports the window "+what); } }
  void log(String s){ Main.log("#"+id+" "+s); }

  void open(){
    double w= Math.sqrt(c.screenArea()*screen.width*(double)screen.height*canvasW/canvasH);
    double cap= Math.min(1, Math.min(workArea.width/w, workArea.height*canvasW/(w*canvasH)));
    var d= size(w*cap);
    var r= new Rectangle(workArea.x+(workArea.width-d.width)/2, workArea.y+(workArea.height-d.height)/2, d.width, d.height);
    natural= d;
    realize(c.opensAs(), r, false, "open");
    timer.start();
    }

  private void realize(Mode m, Rectangle r, boolean maximized, String why){
    log((mode == null ? "" : mode+(maximized() ? " maximized" : "")+" -> ")+m+(maximized ? " maximized" : "")+" by "+why);
    swapping= true;
    var old= f;
    boolean reuse= old != null && old.isUndecorated() && m != Mode.decorated;
    if (reuse && mode == Mode.fullScreen){ device.setFullScreenWindow(null); }
    if (!reuse){ f= makeFrame(m != Mode.decorated); }
    mode= m;
    f.setOpacity(1f);
    switch (m){
      case fullScreen -> device.setFullScreenWindow(f);
      case fearless -> { f.setBounds(r); f.setVisible(true); }
      case decorated -> { decorate(r, maximized); f.setVisible(true); }
      }
    f.toFront();
    if (!reuse && old != null){ old.dispose(); }
    lastSeen= surface.getSize();
    pending= false;
    swapping= false;
    }
  private void decorate(Rectangle outer, boolean maximized){
    setContent(fit(outer));
    var in= f.getInsets();
    setContent(fit(new Rectangle(outer.x+in.left, outer.y+in.top, outer.width-in.left-in.right, outer.height-in.top-in.bottom)));
    if (maximized){ f.setExtendedState(Frame.MAXIMIZED_BOTH); }
    }
  private void setContent(Rectangle r){
    if (f.isUndecorated()){ f.setBounds(r); return; }
    surface.setPreferredSize(r.getSize());
    f.pack();
    Insets in= f.getInsets();
    f.setLocation(r.x-in.left, r.y-in.top);
    }
  Dimension size(double w){ return new Dimension((int)Math.round(w), (int)Math.round(w*canvasH/canvasW)); }
  Rectangle fit(Rectangle r){
    var d= size(Math.min(r.getWidth(), r.getHeight()*canvasW/canvasH));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y+(r.height-d.height)/2, d.width, d.height);
    }
  double minWidth(){ return Math.sqrt(c.minArea()*screen.width*(double)screen.height*canvasW/canvasH); }
  Rectangle contentRect(){ return new Rectangle(surface.getLocationOnScreen(), surface.getSize()); }
  boolean maximized(){ return f != null && (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0; }

  void request(double aspect){
    if (Math.abs(aspect*canvasH-canvasW) < 1 || Math.abs(aspect-lastAspect) < 1e-3){ return; }
    requests+= 1;
    lastAspect= aspect;
    log("aspect ratio request %.3f ".formatted(aspect)+(c.acceptAspect() ? "accepted" : "rejected"));
    if (c.acceptAspect()){ canvasW= Math.max(1, (int)Math.round(canvasH*aspect)); }
    }

  private void tick(){
    var now= surface.getSize();
    if (!now.equals(lastSeen)){ lastSeen= now; pending= true; quiet= 0; }
    else if (pending && ++quiet >= quietTicks){ pending= false; settle(); }
    if (mode == Mode.fearless && translucent && c.pulse()){ f.setOpacity(opacity()); }
    surface.repaint();
    }
  private float opacity(){
    double t= (System.nanoTime()-t0)/1e9;
    return (float)(c.opacityFloor()+(1-c.opacityFloor())*(0.5+0.5*Math.sin(t*2)));
    }
  private void settle(){
    var r= contentRect();
    request(r.getWidth()/r.getHeight());
    if (mode == Mode.fullScreen || maximized()){ return; }
    var fit= fit(r);
    if (fit.getSize().equals(r.getSize())){ return; }
    setContent(fit);
    lastSeen= surface.getSize();
    }
  private void stateChanged(WindowEvent e){
    boolean was= (e.getOldState() & Frame.MAXIMIZED_BOTH) != 0;
    boolean is= (e.getNewState() & (Frame.MAXIMIZED_BOTH|Frame.ICONIFIED)) != 0;
    if (swapping || mode != Mode.decorated || !was || is){ return; }
    realize(Mode.fullScreen, null, false, "unmaximize");
    }

  void toggleFullScreen(){
    if (mode == Mode.fullScreen){ natural= fit(workArea).getSize(); realize(Mode.fearless, fit(workArea), false, "the full screen key"); return; }
    realize(Mode.fullScreen, null, false, "the full screen key");
    }
  private void cornerDoubleClick(){
    if (mode == Mode.fullScreen){ realize(Mode.decorated, fit(workArea), true, "a corner double click"); return; }
    realize(Mode.decorated, f.getBounds(), false, "a corner double click");
    }
  void closeRequest(String why){
    log("close requested by "+why);
    if (c.askBeforeClose()){ content.confirm= true; return; }
    close();
    }
  void close(){
    log("closed by the program");
    closed= true;
    timer.stop();
    if (mode == Mode.fullScreen){ device.setFullScreenWindow(null); }
    f.dispose();
    }
  void subtitle(String s){
    subtitle= s;
    f.setTitle(title());
    }
  private String title(){ return subtitle.isEmpty() ? c.name() : c.name()+"    "+subtitle; }

  double scale(){ return Math.min(surface.getWidth()/(double)canvasW, surface.getHeight()/(double)canvasH); }
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

  private void move(MouseEvent e){
    content.mouse= canvasPoint(e.getPoint());
    int cursor= !inCorner(e.getPoint()) ? Cursor.DEFAULT_CURSOR : inTip(e.getPoint()) ? Cursor.NW_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
    surface.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  private void press(MouseEvent e){
    if (!inCorner(e.getPoint())){ content.press(canvasPoint(e.getPoint())); return; }
    if (e.getClickCount() == 2){ pressAt= null; cornerDoubleClick(); return; }
    pressAt= e.getLocationOnScreen();
    grip= e.getPoint();
    resizing= inTip(e.getPoint());
    if (mode == Mode.fullScreen){ natural= fit(workArea).getSize(); }
    anchor= new Point(f.getX()+f.getWidth(), f.getY()+f.getHeight());
    dragged= false;
    }
  private void drag(MouseEvent e){
    if (pressAt == null){ return; }
    var p= e.getLocationOnScreen();
    if (!dragged && p.distance(pressAt) <= 3){ return; }
    dragged= true;
    if (mode == Mode.fullScreen){ realize(Mode.fearless, place(p), false, "a corner drag"); return; }
    f.setBounds(resizing ? resized(p) : place(p));
    }
  private Rectangle place(Point p){
    int x= Math.max(workArea.x, p.x-grip.x);
    int y= Math.max(workArea.y, p.y-grip.y);
    double room= Math.min(workArea.x+workArea.width-x, (workArea.y+workArea.height-y)*canvasW/(double)canvasH);
    var d= size(Math.max(Math.min(natural.width, room), minWidth()));
    return new Rectangle(Math.min(x, workArea.x+workArea.width-d.width), Math.min(y, workArea.y+workArea.height-d.height), d.width, d.height);
    }
  private Rectangle resized(Point p){
    double a= canvasW/(double)canvasH;
    double w= Math.max(anchor.x-(p.x-grip.x), (anchor.y-(p.y-grip.y))*a);
    w= Math.min(w, Math.min(anchor.x-workArea.x, (anchor.y-workArea.y)*a));
    natural= size(Math.max(w, minWidth()));
    return new Rectangle(anchor.x-natural.width, anchor.y-natural.height, natural.width, natural.height);
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
    ig.drawImage(icon, at, at, side, side, null);
    ig.dispose();
    }

  List<String> status(){
    var r= surface.getSize();
    String max= maximized() ? " (maximized)" : "";
    String last= requests == 0 ? "" : ", last %.3f %s".formatted(lastAspect, c.acceptAspect() ? "accepted" : "rejected");
    String opacity= translucent ? "" : " (translucency not supported on this desk)";
    return List.of(
      "canvas "+canvasW+"x"+canvasH+" units, scale %.3f".formatted(scale()),
      "window content "+r.width+"x"+r.height+" px, "+mode+max+opacity,
      "aspect ratio requests: "+requests+last,
      "mouse "+content.mouse.x+", "+content.mouse.y+" (canvas units)");
    }
  String describe(){
    if (closed){ return "#"+id+" "+c.name()+": closed"; }
    var r= f.getBounds();
    String shown= f.isShowing() ? "showing" : "NOT showing";
    String focus= f.isFocused() ? ", focused" : "";
    return "#"+id+" "+c.name()+": "+mode+(maximized() ? " maximized" : "")+", window "+r.width+"x"+r.height+" at "+r.x+","+r.y+", "+shown+focus
      +", state "+f.getExtendedState()+", opacity %.2f".formatted(f.getOpacity());
    }
  }
