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
import java.awt.MouseInfo;
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
  boolean unmaximized;
  Rectangle restore;
  boolean placing;
  int requests;
  String lastRequest= "none";
  String subtitle= "";
  Point pressAt;
  Rectangle pressBounds;
  boolean resizing;
  boolean dragged;
  boolean opensFullScreen;
  boolean handed;
  Rectangle pending;
  Point pressPoint;
  double moved;

  Stage(int id, Config c){
    this.id= id;
    this.c= c;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    content= new Content(this);
    surface.addMouseListener(this);
    surface.addMouseMotionListener(this);
    surface.setOpaque(true);
    if (Desk.managed){ open(); }
    else{ realize(c.opensAs(), "the program"); }
    timer.start();
    }
  private JFrame makeFrame(boolean undecorated){
    var frame= new JFrame(gc){
      @Override public Dimension getMinimumSize(){
        var d= super.getMinimumSize();
        // The Windows peer gives the desk the minimum size in user space, and the desk reads it in device pixels
        double k= placing || !Desk.windows ? 1 : gc.getDefaultTransform().getScaleX();
        return new Dimension((int)Math.ceil(d.width*k), (int)Math.ceil(d.height*k));
        }
      };
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
    frame.addWindowStateListener(this::stateChanged);
    bind(frame, c.closeKey(), () -> closeRequest("the close key"));
    bind(frame, c.fullScreenKey(), this::fullScreenKey);
    return frame;
    }
  private static void bind(JFrame frame, KeyStroke k, Runnable r){
    frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, k);
    frame.getRootPane().getActionMap().put(k, new AbstractAction(){ @Override public void actionPerformed(ActionEvent e){ r.run(); } });
    }
  private void desk(ComponentEvent e, String what){ if (e.getComponent() == f){ log("the desk reports the window "+what); } }
  private void log(String s){ Main.log("#"+id+" "+s); }

  private void fullScreenKey(){
    if (Desk.managed){ Desk.fullScreen(f, mode != Mode.fullScreen); return; }
    realize(mode == Mode.fullScreen ? Mode.fearless : Mode.fullScreen, "the full screen key");
    }
  private void open(){
    f= makeFrame(false);
    mode= Mode.decorated;
    log("managed by the program");
    f.pack();
    place(compacted(inset(fit(workArea(), areaWidth(c.screenArea())), f.getInsets())));
    minimum();
    opensFullScreen= c.opensAs() == Mode.fullScreen;
    f.setVisible(true);
    f.toFront();
    }
  private void realize(Mode m, String why){
    var old= f;
    var r= old != null && mode != Mode.fullScreen ? old.getBounds() : fit(workArea(), old == null ? areaWidth(c.screenArea()) : leavingWidth());
    boolean undecorated= m != Mode.decorated;
    log((old == null ? "" : state()+" -> ")+m+" by "+why);
    if (old == null || old.isUndecorated() != undecorated){ f= makeFrame(undecorated); }
    boolean replaced= old != null && old != f;
    if (replaced && !Desk.windows){ old.dispose(); }
    mode= m;
    deskResized= false;
    unmaximized= false;
    switch (m){
      case fullScreen -> {
        Main.stages.stream().filter(s -> s.f == device.getFullScreenWindow()).forEach(s -> s.realize(Mode.fearless, "#"+id+" taking full screen"));
        if (!Desk.windows && !f.isShowing()){ place(fit(workArea(), leavingWidth())); f.setVisible(true); }
        device.setFullScreenWindow(f);
        }
      case fearless -> {
        if (device.getFullScreenWindow() == f){ device.setFullScreenWindow(null); }
        place(r);
        }
      case decorated -> {
        f.pack();
        place(compacted(inset(r, f.getInsets())));
        }
      }
    minimum();
    f.setVisible(true);
    f.toFront();
    surface.requestFocusInWindow();
    if (replaced && !Desk.windows){ Desk.activate(f); }
    if (replaced && Desk.windows){ old.dispose(); }
    }
  private Rectangle workArea(){ return Desk.cosmic ? Desk.cosmicArea : inset(screen, Toolkit.getDefaultToolkit().getScreenInsets(gc)); }
  private static Rectangle inset(Rectangle r, Insets in){ return new Rectangle(r.x+in.left, r.y+in.top, r.width-in.left-in.right, r.height-in.top-in.bottom); }
  private static int within(int v, int low, int high){ return Math.max(low, Math.min(v, high)); }
  private Rectangle outset(Rectangle content){
    var in= f.getInsets();
    return new Rectangle(content.x-in.left, content.y-in.top, content.width+in.left+in.right, content.height+in.top+in.bottom);
    }
  private void place(Rectangle content){
    chosen= content.getSize();
    placing= true;
    f.setBounds(outset(content));
    placing= false;
    restore= f.getBounds();
    if (Desk.cosmic){ pending= restore; }
    }
  private void minimum(){
    var in= f.getInsets();
    var d= size(areaWidth(c.minArea()));
    f.setMinimumSize(new Dimension(d.width+in.left+in.right, d.height+in.top+in.bottom));
    }
  private Rectangle compacted(Rectangle r){
    var d= size(fitWidth(r.width, r.height));
    return new Rectangle(r.x+(r.width-d.width)/2, r.y, d.width, d.height);
    }
  private Rectangle rescaled(Rectangle r){
    var room= inset(workArea(), f.getInsets());
    if (Desk.cosmic){
      var d= clamped(Math.sqrt(r.width*(double)r.height*canvasW/canvasH), room.x+room.width-r.x, room.y+room.height-r.y);
      return new Rectangle(r.x, r.y, d.width, d.height);
      }
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
  private double leavingWidth(){
    var w= workArea();
    return 0.77*fitWidth(w.width, w.height);
    }
  private double areaWidth(double area){
    var w= workArea();
    return Math.min(Math.sqrt(area*screen.width*screen.height*canvasW/canvasH), fitWidth(w.width, w.height));
    }
  private Dimension clamped(double w, int roomW, int roomH){ return size(Math.max(Math.min(w, fitWidth(roomW, roomH)), areaWidth(c.minArea()))); }
  private boolean maximized(){ return mode == Mode.decorated && !Desk.managed && (f.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH; }
  private String state(){ return (Desk.managed && mode == Mode.decorated ? "managed" : mode.toString())+(maximized() ? " maximized" : ""); }

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
    var old= inset(bounds(), f.getInsets());
    canvasW= w;
    canvasH= h;
    log("virtual pixel space "+w+"x"+h);
    minimum();
    if (mode == Mode.fullScreen || maximized() || deskResized || Desk.managed){ return; }
    place(rescaled(old));
    }

  private void tick(){
    if (Desk.managed){ managedState(); }
    if (!Desk.windows && !Desk.cosmic){ trackPress(); }
    var now= surface.getSize();
    boolean deskWindow= mode == Mode.decorated && !maximized() && !Desk.managed;
    deskResized&= deskWindow;
    if (Desk.cosmic){ cosmicTick(deskWindow); }
    else if (deskWindow && !unmaximized && (Desk.resizeUnseen || f.getSize().equals(restore.getSize()))){ restore= f.getBounds(); }
    boolean deskChose= !near(now.width, chosen.width) || !near(now.height, chosen.height);
    if (!now.equals(lastSize) && deskChose){ deskResized|= deskWindow && !Desk.resizeUnseen; request(now); }
    lastSize= now;
    if (unmaximized && (Desk.cosmic ? sameSize(f.getBounds(), restore) : !Desk.down())){ unmaximized(); return; }
    if (deskResized && !Desk.down()){ compact(); }
    if (mode == Mode.fearless && translucent && c.pulse()){ f.setOpacity((float)(c.opacityFloor()+(1-c.opacityFloor())*(0.5+0.5*Math.cos((System.nanoTime()-t0)/0.5e9)))); }
    surface.repaint();
    }
  private void cosmicTick(boolean deskWindow){
    if (maximized()){
      var m= Desk.bounds(f);
      int top= Desk.cosmicArea.y;
      Desk.cosmicArea= new Rectangle(m.x, top, m.width, m.y+m.height-top);
      }
    if (deskWindow && !unmaximized && pending == null){ restore= f.getBounds(); }
    if (pending == null || !Desk.managing(f) || Desk.fullScreen(f)){ return; }
    var real= Desk.bounds(f);
    if (real.equals(screen)){ return; }
    var in= f.getInsets();
    var room= inset(workArea(), in);
    var d= clamped(pending.width-in.left-in.right, room.x+room.width-real.x, room.y+room.height-real.y);
    pending= null;
    if (real.getSize().equals(d)){ return; }
    chosen= d;
    log("fitting the window in the work area");
    Desk.resize(f, d);
    }
  private Rectangle bounds(){ return Desk.cosmic ? outset(Desk.bounds(f)) : f.getBounds(); }
  private static boolean sameSize(Rectangle a, Rectangle b){ return near(a.width, b.width) && near(a.height, b.height); }
  private void managedState(){
    if (opensFullScreen && Desk.managing(f)){ opensFullScreen= false; Desk.fullScreen(f, true); }
    var m= Desk.fullScreen(f) ? Mode.fullScreen : Mode.decorated;
    if (m == mode){ return; }
    var was= state();
    mode= m;
    log(was+" -> "+state()+" reported by the desk");
    }
  private void stateChanged(WindowEvent e){
    if (e.getComponent() != f || mode != Mode.decorated || Desk.managed){ return; }
    desk(e, "in state "+e.getNewState());
    boolean was= (e.getOldState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    boolean is= (e.getNewState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
    if (!was && is && Desk.windows){ Desk.restoreSize(f, restore, true); return; }
    unmaximized= was && (e.getNewState() & (Frame.MAXIMIZED_BOTH|Frame.ICONIFIED)) == 0;
    }
  private static boolean near(int a, int b){ return Math.abs(a-b) <= 1; }
  private void trackPress(){
    if (!Desk.down()){ if (!unmaximized){ pressPoint= null; moved= 0; } return; }
    var p= MouseInfo.getPointerInfo().getLocation();
    if (pressPoint == null){ pressPoint= p; }
    moved= Math.max(moved, p.distance(pressPoint));
    }
  private void unmaximized(){
    unmaximized= false;
    var b= f.getBounds();
    boolean restored= Desk.windows || Desk.cosmic
      ? near(b.x, restore.x) && near(b.y, restore.y) && near(b.width, restore.width) && near(b.height, restore.height)
      : moved <= DragSource.getDragThreshold();
    pressPoint= null;
    moved= 0;
    if (restored){ realize(Mode.fullScreen, "the desk's unmaximize"); return; }
    log("a title bar drag or a desk arrangement: the window stays a desk window");
    compact();
    }
  private void compact(){
    deskResized= false;
    log("the desk has finished resizing: compacting");
    place(compacted(inset(f.getBounds(), f.getInsets())));
    if (Desk.windows){ Desk.restoreSize(f, f.getBounds(), false); }
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
  private boolean inCorner(Point p){ return (mode != Mode.decorated || Desk.managed) && p.x+p.y < legs(); }
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
    if (e.getClickCount() == 2 || Desk.managed){ return; }
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
    if (Desk.cosmic){ handOver(p); return; }
    if (mode == Mode.fullScreen){ realize(Mode.fearless, "a corner drag"); }
    var at= new Point(pressBounds.x+p.x-pressAt.x, pressBounds.y+p.y-pressAt.y);
    place(resizing ? resized(at) : moved(at));
    }
  @Override public void mouseReleased(MouseEvent e){
    if (e.getButton() != MouseEvent.BUTTON1){ return; }
    if (e.getClickCount() == 2 && inCorner(e.getPoint()) && Desk.managed){ Desk.fullScreen(f, mode != Mode.fullScreen); return; }
    if (e.getClickCount() == 2 && inCorner(e.getPoint())){ realize(Mode.decorated, "a corner double click"); return; }
    if (pressAt == null){ return; }
    pressAt= null;
    if (handed){ handed= false; log("the drag has ended"); }
    if (Desk.cosmic && dragged && mode == Mode.fearless){ pending= resizing ? compacted(bounds()) : bounds(); }
    if (!dragged || mode != Mode.fearless){ return; }
    var b= bounds();
    if (new Point(b.x+e.getX(), b.y+e.getY()).distance(workArea().getLocation()) <= screen.height/16){ realize(Mode.fullScreen, "a corner drop at the top left"); }
    }
  private void handOver(Point p){
    if (handed){ return; }
    handed= true;
    if (mode == Mode.fullScreen){ realize(Mode.fearless, "a corner drag"); return; }
    log("the drag goes on as the desk's "+(resizing ? "resize" : "move"));
    Desk.moveResize(f, p, resizing ? 0 : 8);
    }
  private Rectangle moved(Point at){
    var w= workArea();
    int right= w.x+w.width;
    int bottom= w.y+w.height;
    int x= Math.max(w.x, at.x);
    int y= Math.max(w.y, at.y);
    var d= clamped(restore.width, right-x, bottom-y);
    return new Rectangle(Math.min(x, right-d.width), Math.min(y, bottom-d.height), d.width, d.height);
    }
  private Rectangle resized(Point at){
    int right= pressBounds.x+pressBounds.width;
    int bottom= pressBounds.y+pressBounds.height;
    var w= workArea();
    var d= clamped(Math.max(right-at.x, (bottom-at.y)*(double)canvasW/canvasH), right-w.x, bottom-w.y);
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
    if (mode == Mode.decorated && !Desk.managed){ return; }
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
    String translucency= (Desk.managed ? ", the desk manages every window" : "")+(translucent ? "" : " (translucency not supported on this desk)");
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
