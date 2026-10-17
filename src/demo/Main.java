package demo;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;

public final class Main{
  static final BufferedImage icon= res("icon.png");
  static final BufferedImage picture= res("picture.png");
  static final ArrayList<Stage> stages= new ArrayList<>();
  static final DateTimeFormatter clock= DateTimeFormatter.ofPattern("HH:mm:ss.SSS");
  static JTextArea logArea;

  public static void main(String[] a){
    Thread.setDefaultUncaughtExceptionHandler(Main::uncaught);
    SwingUtilities.invokeLater(Main::launcher);
    }
  private static void uncaught(Thread t, Throwable e){
    var sw= new StringWriter();
    e.printStackTrace(new PrintWriter(sw));
    log("EXCEPTION on thread "+t.getName()+": "+sw);
    }
  static void log(String s){
    var line= clock.format(LocalTime.now())+" "+s.stripTrailing()+"\n";
    System.err.print(line);
    SwingUtilities.invokeLater(() -> logArea.append(line));
    }
  private static JTextArea textArea(int rows){
    var t= new JTextArea(rows, 60);
    t.setEditable(false);
    t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    return t;
    }

  private static BufferedImage res(String name){
    try (var in= Main.class.getResourceAsStream("res/"+name)){ return ImageIO.read(in); }
    catch (IOException e){ throw new UncheckedIOException(e); }
    }
  private static <T extends JComponent> T row(JPanel p, String label, T c){
    p.add(new JLabel(label));
    p.add(c);
    return c;
    }

  private static void launcher(){
    logArea= textArea(10);
    var live= textArea(4);
    var f= new JFrame("Fearless Gui demo: initial configuration");
    f.setIconImage(icon);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    var p= new JPanel(new GridLayout(0, 2, 8, 6));
    p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    var name= row(p, "Name (fixed, shown before the subtitle)", new JTextField("Fearless App"));
    var screenArea= row(p, "Screen area at open, in (0, 1]", new JTextField("0.3"));
    var minArea= row(p, "Min screen area, in (0, screen area]", new JTextField("0.02"));
    var canvasW= row(p, "Virtual pixel space width", new JTextField("520"));
    var canvasH= row(p, "Virtual pixel space height", new JTextField("340"));
    var opensAs= row(p, "Opens as", new JComboBox<>(Stage.Mode.values()));
    var opacityFloor= row(p, "Opacity floor, in [0, 1]", new JTextField("0.4"));
    var pulse= row(p, "Pulse the opacity (Fearless window only)", new JCheckBox());
    var closeKey= row(p, "Close key", new JTextField("ESCAPE"));
    var fullScreenKey= row(p, "Full screen key", new JTextField("F11"));
    var acceptAspect= row(p, "Accept aspect ratio requests", new JCheckBox("", true));
    var askBeforeClose= row(p, "Ask before closing", new JCheckBox());
    var bands= row(p, "Bands color", new JComboBox<>(Config.Bands.values()));
    var status= new JLabel(" ");
    status.setForeground(Color.red);
    var show= new JButton("Show");
    show.addActionListener(_ -> open(status, () -> new Config(name.getText(), Config.number(screenArea.getText(), "Screen area"),
      Config.number(minArea.getText(), "Min screen area"), Config.whole(canvasW.getText(), "Virtual pixel space width"), Config.whole(canvasH.getText(), "Virtual pixel space height"),
      opensAs.getItemAt(opensAs.getSelectedIndex()), Config.number(opacityFloor.getText(), "Opacity floor"), pulse.isSelected(),
      Config.key(closeKey.getText()), Config.key(fullScreenKey.getText()),
      acceptAspect.isSelected(), askBeforeClose.isSelected(), bands.getItemAt(bands.getSelectedIndex()).color)));
    p.add(status);
    p.add(show);
    var all= new JPanel(new BorderLayout(0, 8));
    all.add(p, BorderLayout.NORTH);
    var bottom= new JPanel(new BorderLayout(0, 8));
    bottom.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
    bottom.add(titled("Windows, as the program sees them", live), BorderLayout.NORTH);
    bottom.add(titled("Log (transitions, requests, desk reports, exceptions)", new JScrollPane(logArea)), BorderLayout.CENTER);
    all.add(bottom, BorderLayout.CENTER);
    new Timer(500, _ -> live.setText(stages.stream().map(Stage::describe).collect(Collectors.joining("\n")))).start();
    f.setContentPane(all);
    f.pack();
    f.setLocationRelativeTo(null);
    f.setVisible(true);
    }
  private static void open(JLabel status, Supplier<Config> read){
    Config c;
    try{ c= read.get(); }
    catch (IllegalArgumentException e){ status.setText(e.getMessage()); return; }
    status.setText(" ");
    stages.add(new Stage(stages.size()+1, c));
    }
  private static JPanel titled(String title, Component c){
    var p= new JPanel(new BorderLayout());
    p.add(new JLabel(title), BorderLayout.NORTH);
    p.add(c, BorderLayout.CENTER);
    return p;
    }
  }
