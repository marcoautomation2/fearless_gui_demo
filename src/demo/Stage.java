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
import java.awt.dnd.DragSource;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;

final class Stage extends MouseAdapter{
  enum Mode{ decorated, fearless, fullScreen }
  static final int tickMillis= 33;
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
  Dimension chosen= new Dimension();
  boolean deskResized;
  Rectangle normal;
  int requests;
  String lastRequest= "none";
  String subtitle= "";
  Point pressAt;
  Rectangle pressBounds;
  boolean resizing;
  boolean dragged;

  Stage(int id, Config c){
    this.id= id;
    this.c= c;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    content= new Content(this);
    surface.addMouseListener(this);
    surface.addMouseMotionListener(this);
    surface.setOpaque(true);
    realize(c.opensAs(), "the program");
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
      @Override public void windowClosing(WindowEvent e){ closeRequest("the desk"); }
      @Override public void windowClosed(WindowEvent e){ desk(e, "closed"); }
      @Override public void windowIconified(WindowEvent e){ desk(e, "iconified"); }
      @Override public void windowDeiconified(WindowEvent e){ desk(e, "deiconified"); }
      @Override public void windowGainedFocus(WindowEvent e){ surface.requestFocusInWindow(); }
      };
    frame.addWindowListener(l);
    frame.addWindowFocusListener(l);
    frame.addComponentListener(new ComponentAdapter(){
      @Override public void componentHidden(ComponentEvent e){ desk(e, "hidden"); }
      @Override public void componentShown(ComponentEvent e){ desk(e, "shown"); }
      });
    frame.addWindowStateListener(e -> desk(e, "in state "+e.getNewState()));
    bind(frame, c.closeKey(), () -> closeRequest("the close key"));
    bind(frame, c.fullScreenKey(), () -> realize(mode == Mode.fullScreen ? Mode.fearless : Mode.fullScreen, "the full screen key"));
    return frame;
    }
  private static void bind(JFrame frame, KeyStroke k, Runnable r){
    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, k);
    frame.getRootPane().getActionMap().put(k, new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ r.run(); } });
    }
  private void desk(ComponentEvent e, String what){ if (e.getComponent() == f){ log("the desk reports the window "+what); } }
  private void log(String s){ Main.log("#"+id+" "+s); }

  private void realize(Mode m, String why){
    var old= f;
    var r= old != null && mode != Mode.fullScreen ? old.getBounds() : fit(workArea, old == null ? areaWidth(c.screenArea()) : workArea.width);
    boolean undecorated= m != Mode.decorated;
    log((old == null ? "" : state()+" -> ")+m+" by "+why);
    if (old == null || old.isUndecorated() != undecorated){ f= makeFrame(undecorated); }
    mode= m;
    deskResized= false;
    switch (m){
      case fullScreen -> {
        Main.stages.stream().filter(s -> s.f == device.getFullScreenWindow()).forEach(s -> s.realize(Mode.fearless, "#"+id+" taking full screen"));
        device.setFullScreenWindow(f);
        }
      case fearless -> {
        if (device.getFullScreenWindow() == f){ device.setFullScreenWindow(null); }
        place(r);
        }
      case decorated -> {
        f.pack();
        place(compacted(inset(r, f.getInsets())));
        normal= f.getBounds();
        }
      }
    f.setVisible(true);
    f.toFront();
    surface.requestFocusInWindow();
    if (old != null && old != f){ old.dispose(); }
    }
  private static Rectangle inset(Rectangle r, Insets in){ return new Rectangle(r.x+in.left, r.y+in.top, r.width-in.left-in.right, r.height-in.top-in.bottom); }
  private static int within(int v, int low, int high){ return Math.max(low, Math.min(v, high)); }
  private void place(Rectangle content){
    chosen= content.getSize();
    var in= f.getInsets();
    f.setBounds(content.x-in.left, content.y-in.top, content.width+in.left+in.right, content.height+in.top+in.bottom);
    }
  private Rectangle compacted(Rectangle r){
    var d= size(fitWidth(r.width, r.height));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y, d.width, d.height);
    }
  private Rectangle rescaled(Rectangle r){
    var room= inset(workArea, f.getInsets());
    var d= clamped(Math.sqrt(r.width*(double)r.height*canvasW/canvasH), room.width, room.height);
    int x= within(r.x+(r.width-d.width)/2, room.x, room.x+room.width-d.width);
    return new Rectangle(x, within(r.y+(r.height-d.height)/2, room.y, room.y+room.height-d.height), d.width, d.height);
    }
  private Dimension size(double w){
    int iw= (int)w;
    return new Dimension(iw, (int)Math.round(iw*(double)canvasH/canvasW));
    }
  private double fitWidth(double w, double h){ return Math.min(w, h*canvasW/canvasH); }
  private Rectangle fit(Rectangle r, double w){
    var d= size(Math.min(w, fitWidth(r.width, r.height)));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y+(r.height-d.height)/2, d.width, d.height);
    }
  private double areaWidth(double area){ return Math.min(Math.sqrt(area*screen.width*screen.height*canvasW/canvasH), fitWidth(workArea.width, workArea.height)); }
  private Dimension clamped(double w, int roomW, int roomH){ return size(Math.max(Math.min(w, fitWidth(roomW, roomH)), areaWidth(c.minArea()))); }
  private boolean maximized(){ return (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0; }
  private String state(){ return mode+(maximized() ? " maximized" : ""); }

  private void request(Dimension d){
    if (mode == Mode.fearless || d.width == 0 || d.height == 0){ return; }
    requests+= 1;
    double aspect= d.getWidth()/d.getHeight();
    lastRequest= String.format(Locale.ROOT, "%.3f %s", aspect, c.acceptAspect() ? "accepted" : "rejected");
    log("aspect ratio request "+lastRequest);
    if (c.acceptAspect()){ canvas(Math.max(1, (int)Math.round(canvasH*aspect)), canvasH); }
    }
  void canvas(int w, int h){
    if (w == canvasW && h == canvasH){ return; }
    boolean max= maximized();
    var old= inset(max ? normal : f.getBounds(), f.getInsets());
    canvasW= w;
    canvasH= h;
    log("virtual pixel space "+w+"x"+h);
    if (mode == Mode.fullScreen || deskResized){ return; }
    place(rescaled(old));
    if (!max){ return; }
    normal= f.getBounds();
    f.setExtendedState(Frame.MAXIMIZED_BOTH);
    }

  private void tick(){
    var now= surface.getSize();
    boolean deskWindow= mode == Mode.decorated && !maximized();
    if (deskWindow){ normal= f.getBounds(); }
    deskResized&= deskWindow;
    boolean deskChose= Math.abs(now.width-chosen.width) > 1 || Math.abs(now.height-chosen.height) > 1;
    if (!now.equals(lastSize) && deskChose){ deskResized|= deskWindow; request(now); }
    lastSize= now;
    if (deskResized && !Buttons.down()){ compact(); }
    if (mode == Mode.fearless && translucent && c.pulse()){ f.setOpacity((float)(c.opacityFloor()+(1-c.opacityFloor())*(0.5+0.5*Math.cos((System.nanoTime()-t0)/0.5e9)))); }
    surface.repaint();
    }
  private void compact(){
    deskResized= false;
    log("the desk has finished resizing: compacting");
    place(compacted(inset(f.getBounds(), f.getInsets())));
    }

  private void closeRequest(String why){
    log("close requested by "+why);
    content.closeRequest();
    }
  void close(){
    log("closed by the program");
    timer.stop();
    f.dispose();
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

  @Override public void mouseMoved(MouseEvent e){
    var p= e.getPoint();
    if (!inCorner(p)){ mouse= canvasPoint(p); }
    int cursor= !inCorner(p) ? Cursor.DEFAULT_CURSOR : inTip(p) ? Cursor.NW_RESIZE_CURSOR : Cursor.MOVE_CURSOR;
    surface.setCursor(Cursor.getPredefinedCursor(cursor));
    }
  @Override public void mousePressed(MouseEvent e){
    if (e.getButton() != MouseEvent.BUTTON1){ return; }
    var p= canvasPoint(e.getPoint());
    if (!inCorner(e.getPoint())){ if (new Rectangle(canvasW, canvasH).contains(p)){ content.press(p); } return; }
    if (e.getClickCount() == 2){ realize(Mode.decorated, "a corner double click"); return; }
    pressAt= e.getLocationOnScreen();
    pressBounds= f.getBounds();
    resizing= inTip(e.getPoint());
    dragged= false;
    }
  @Override public void mouseDragged(MouseEvent e){
    if (pressAt == null){ mouseMoved(e); return; }
    var p= e.getLocationOnScreen();
    if (!dragged && p.distance(pressAt) <= DragSource.getDragThreshold()){ return; }
    dragged= true;
    if (mode == Mode.fullScreen){ realize(Mode.fearless, "a corner drag"); }
    var at= new Point(pressBounds.x+p.x-pressAt.x, pressBounds.y+p.y-pressAt.y);
    place(resizing ? resized(at) : moved(at));
    }
  @Override public void mouseReleased(MouseEvent e){
    if (e.getButton() != MouseEvent.BUTTON1 || pressAt == null){ return; }
    pressAt= null;
    if (!dragged || mode != Mode.fearless){ return; }
    if (e.getLocationOnScreen().distance(workArea.getLocation()) <= screen.height/16){ realize(Mode.fullScreen, "a corner drop at the top left"); }
    }
  private Rectangle moved(Point at){
    int right= workArea.x+workArea.width;
    int bottom= workArea.y+workArea.height;
    int x= Math.max(workArea.x, at.x);
    int y= Math.max(workArea.y, at.y);
    var d= clamped(f.getWidth(), right-x, bottom-y);
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
    if (mode == Mode.decorated){ return; }
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
    var d= surface.getSize();
    String translucency= translucent ? "" : " (translucency not supported on this desk)";
    return List.of(
      String.format(Locale.ROOT, "virtual pixel space %dx%d, scale %.3f", canvasW, canvasH, scale()),
      "window content "+d.width+"x"+d.height+" px, "+state()+translucency,
      "aspect ratio requests: "+requests+", last "+lastRequest,
      "mouse "+mouse.x+", "+mouse.y+" (virtual pixels)");
    }
  String describe(){
    if (!f.isDisplayable()){ return "#"+id+" "+c.name()+": closed"; }
    var r= f.getBounds();
    String shown= f.isShowing() ? "showing" : "NOT showing";
    var owner= KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
    String focus= !f.isFocused() ? "" : ", focused, keys to "+(owner == null ? "nobody" : owner.getClass().getSimpleName());
    return "#"+id+" "+c.name()+": "+state()+", window "+r.width+"x"+r.height+" at "+r.x+","+r.y+", "+shown+focus
      +", state "+f.getExtendedState()+String.format(Locale.ROOT, ", opacity %.2f", f.getOpacity());
    }
  }
