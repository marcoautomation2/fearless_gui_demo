package demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Toolkit;
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
  final Config c;
  final BufferedImage icon;
  final JFrame f= new JFrame();
  final JComponent surface;
  final Content content;
  final GraphicsDevice device;
  final Rectangle screen;
  final Rectangle workArea;
  final boolean translucent;
  final Timer timer= new Timer(tickMillis, _ -> tick());
  final long t0= System.nanoTime();
  int canvasW;
  int canvasH;
  Mode mode= Mode.decorated;
  Mode beforeFullScreen= Mode.decorated;
  Rectangle recorded;
  Dimension lastSeen= new Dimension();
  int quiet;
  boolean pending;
  boolean fearlessOnSettle;
  boolean realizing;
  int requests;
  double lastAspect;
  String subtitle= "";
  Point dragFrom;
  Point windowFrom;
  boolean dragged;

  Stage(Config c, BufferedImage icon, BufferedImage picture){
    this.c= c;
    this.icon= icon;
    canvasW= c.canvasW();
    canvasH= c.canvasH();
    device= GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
    var gc= device.getDefaultConfiguration();
    screen= gc.getBounds();
    var in= Toolkit.getDefaultToolkit().getScreenInsets(gc);
    workArea= new Rectangle(screen.x+in.left, screen.y+in.top, screen.width-in.left-in.right, screen.height-in.top-in.bottom);
    translucent= gc.getDevice().isWindowTranslucencySupported(GraphicsDevice.WindowTranslucency.TRANSLUCENT);
    content= new Content(this, picture);
    surface= new JComponent(){ @Override protected void paintComponent(Graphics g){ paintSurface((Graphics2D)g); } };
    surface.setOpaque(true);
    f.setContentPane(surface);
    f.setIconImage(icon);
    f.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    f.addWindowListener(new WindowAdapter(){ @Override public void windowClosing(WindowEvent e){ closeRequest(); } });
    f.addWindowStateListener(this::stateChanged);
    var m= new MouseAdapter(){
      @Override public void mousePressed(MouseEvent e){ press(e); }
      @Override public void mouseReleased(MouseEvent e){ release(); }
      @Override public void mouseDragged(MouseEvent e){ drag(e); }
      @Override public void mouseMoved(MouseEvent e){ content.mouse= canvasPoint(e.getPoint()); }
      };
    surface.addMouseListener(m);
    surface.addMouseMotionListener(m);
    bind(c.closeKey(), "close", this::closeRequest);
    bind(c.fullScreenKey(), "fullScreen", this::toggleFullScreen);
    title();
    }
  private void bind(KeyStroke k, String name, Runnable r){
    f.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(k, name);
    f.getRootPane().getActionMap().put(name, new AbstractAction(){ @Override public void actionPerformed(java.awt.event.ActionEvent e){ r.run(); } });
    }

  void open(){
    double area= c.screenArea()*screen.width*(double)screen.height;
    double w= Math.sqrt(area*canvasW/canvasH);
    double h= w*canvasH/canvasW;
    double cap= Math.min(1, Math.min(workArea.width/w, workArea.height/h));
    int cw= (int)Math.round(w*cap);
    int ch= (int)Math.round(h*cap);
    var r= new Rectangle(workArea.x+(workArea.width-cw)/2, workArea.y+(workArea.height-ch)/2, cw, ch);
    if (c.opensAs() == Mode.fullScreen){ recorded= r; }
    realize(c.opensAs(), r);
    timer.start();
    }

  void realize(Mode m, Rectangle r){
    realizing= true;
    if (mode == Mode.fullScreen){ device.setFullScreenWindow(null); }
    f.dispose();
    mode= m;
    f.setOpacity(1f);
    f.setUndecorated(m != Mode.decorated);
    f.setExtendedState(Frame.NORMAL);
    if (m == Mode.fullScreen){ device.setFullScreenWindow(f); request(screen.getWidth()/screen.getHeight()); }
    else{ request(r.getWidth()/r.getHeight()); setContent(fit(r)); f.setVisible(true); }
    f.toFront();
    lastSeen= surface.getSize();
    pending= false;
    realizing= false;
    }
  private void setContent(Rectangle r){
    surface.setPreferredSize(r.getSize());
    f.pack();
    var in= f.getInsets();
    f.setLocation(r.x-in.left, r.y-in.top);
    }
  Rectangle fit(Rectangle r){
    double s= Math.min(r.getWidth()/canvasW, r.getHeight()/canvasH);
    int w= (int)Math.round(canvasW*s);
    int h= (int)Math.round(canvasH*s);
    return new Rectangle(r.x+(r.width-w)/2, r.y+(r.height-h)/2, w, h);
    }
  Rectangle contentRect(){ return new Rectangle(surface.getLocationOnScreen(), surface.getSize()); }
  boolean maximized(){ return (f.getExtendedState() & Frame.MAXIMIZED_BOTH) != 0; }

  void request(double aspect){
    if (Math.abs(aspect*canvasH-canvasW) < 1){ return; }
    requests+= 1;
    lastAspect= aspect;
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
    if (fearlessOnSettle){ fearlessOnSettle= false; realize(Mode.fearless, contentRect()); return; }
    var r= contentRect();
    request(r.getWidth()/r.getHeight());
    if (mode == Mode.fullScreen || maximized()){ return; }
    var fit= fit(r);
    if (fit.getSize().equals(r.getSize())){ return; }
    setContent(fit);
    lastSeen= surface.getSize();
    }
  private void stateChanged(WindowEvent e){
    if (realizing){ return; }
    boolean was= (e.getOldState() & Frame.MAXIMIZED_BOTH) != 0;
    boolean is= (e.getNewState() & Frame.MAXIMIZED_BOTH) != 0;
    if (was && !is && mode != Mode.fullScreen){ fearlessOnSettle= true; pending= true; quiet= 0; }
    }

  void toggleFullScreen(){
    if (mode == Mode.fullScreen){ realize(beforeFullScreen, recorded); return; }
    beforeFullScreen= mode;
    recorded= contentRect();
    realize(Mode.fullScreen, null);
    }
  private void cornerClick(){ realize(Mode.decorated, mode == Mode.fullScreen ? recorded : contentRect()); }
  void closeRequest(){
    if (c.askBeforeClose()){ content.confirm= true; return; }
    close();
    }
  void close(){
    timer.stop();
    if (mode == Mode.fullScreen){ device.setFullScreenWindow(null); }
    f.dispose();
    }
  void subtitle(String s){
    subtitle= s;
    title();
    }
  private void title(){ f.setTitle(subtitle.isEmpty() ? c.name() : c.name()+"    "+subtitle); }

  double scale(){ return Math.min(surface.getWidth()/(double)canvasW, surface.getHeight()/(double)canvasH); }
  private Point offset(double s){ return new Point((int)((surface.getWidth()-canvasW*s)/2), (int)((surface.getHeight()-canvasH*s)/2)); }
  private Point canvasPoint(Point p){
    double s= scale();
    var o= offset(s);
    return new Point((int)Math.floor((p.x-o.x)/s), (int)Math.floor((p.y-o.y)/s));
    }
  private int legs(){ return Math.min(screen.height/16, surface.getHeight()/4); }
  private boolean inCorner(Point p){ return mode != Mode.decorated && p.x+p.y <= legs(); }

  private void press(MouseEvent e){
    if (!inCorner(e.getPoint())){ content.press(canvasPoint(e.getPoint())); return; }
    dragFrom= e.getLocationOnScreen();
    windowFrom= f.getLocation();
    dragged= false;
    }
  private void drag(MouseEvent e){
    if (dragFrom == null){ return; }
    var p= e.getLocationOnScreen();
    dragged|= p.distance(dragFrom) > 3;
    if (mode == Mode.fearless){ f.setLocation(windowFrom.x+p.x-dragFrom.x, windowFrom.y+p.y-dragFrom.y); }
    }
  private void release(){
    if (dragFrom == null){ return; }
    dragFrom= null;
    if (!dragged){ cornerClick(); }
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
    g.setColor(new Color(20, 20, 30));
    g.fill(tri);
    g.setColor(new Color(255, 220, 40));
    g.setStroke(new BasicStroke(2));
    g.drawLine(l, 0, 0, l);
    var ig= (Graphics2D)g.create();
    ig.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
    ig.clip(tri);
    int side= l/2-4;
    ig.drawImage(icon, 3, 3, side, side, null);
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
  }
