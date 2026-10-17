package demo;

import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.stream.Collectors;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
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
  static final ArrayList<Stage> stages= new ArrayList<>();
  static final JTextArea live= area(4);
  static final JTextArea logArea= area(10);
  static final DateTimeFormatter clock= DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

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
    SwingUtilities.invokeLater(() -> { logArea.append(line); logArea.setCaretPosition(logArea.getDocument().getLength()); });
    }
  private static JTextArea area(int rows){
    var t= new JTextArea(rows, 60);
    t.setEditable(false);
    t.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
    return t;
    }

  private static BufferedImage res(String name){
    try (var in= Main.class.getResourceAsStream("res/"+name)){ return ImageIO.read(in); }
    catch (IOException e){ throw new UncheckedIOException(e); }
    }
  private static JTextField field(JPanel p, String label, String value){
    p.add(new JLabel(label));
    var t= new JTextField(value, 12);
    p.add(t);
    return t;
    }
  private static JCheckBox check(JPanel p, String label, boolean value){
    p.add(new JLabel(label));
    var b= new JCheckBox("", value);
    p.add(b);
    return b;
    }
  private static <T> JComboBox<T> combo(JPanel p, String label, T[] values){
    p.add(new JLabel(label));
    var b= new JComboBox<>(values);
    p.add(b);
    return b;
    }

  private static void launcher(){
    var icon= res("icon.png");
    var picture= res("picture.png");
    var f= new JFrame("Fearless Gui demo: initial configuration");
    f.setIconImage(icon);
    f.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    var p= new JPanel(new GridLayout(0, 2, 8, 6));
    p.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
    var name= field(p, "Name (fixed, shown before the subtitle)", "Fearless App");
    var area= field(p, "Screen area at open, in (0, 1]", "0.3");
    var minArea= field(p, "Min screen area, in (0, screen area]", "0.02");
    var cw= field(p, "Canvas width, in units", "520");
    var ch= field(p, "Canvas height, in units", "340");
    var opens= combo(p, "Opens as", Stage.Mode.values());
    var floor= field(p, "Opacity floor, in [0, 1]", "0.4");
    var pulse= check(p, "Pulse the opacity (Fearless window only)", false);
    var closeKey= field(p, "Close key", "ESCAPE");
    var fsKey= field(p, "Full screen key", "F11");
    var accept= check(p, "Accept aspect ratio requests", true);
    var ask= check(p, "Ask before closing", false);
    var bands= combo(p, "Bands color", Config.Bands.values());
    var status= new JLabel(" ");
    status.setForeground(Color.red);
    var show= new JButton("Show");
    show.addActionListener(_ -> {
      try{
        var c= Config.parse(name.getText(), area.getText(), minArea.getText(), cw.getText(), ch.getText(), opens.getItemAt(opens.getSelectedIndex()),
          floor.getText(), pulse.isSelected(), closeKey.getText(), fsKey.getText(), accept.isSelected(), ask.isSelected(), bands.getItemAt(bands.getSelectedIndex()));
        var st= new Stage(stages.size()+1, c, icon, picture);
        stages.add(st);
        st.open();
        status.setText(" ");
        }
      catch (IllegalArgumentException e){ status.setText(e.getMessage()); }
      });
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
  private static JPanel titled(String title, java.awt.Component c){
    var p= new JPanel(new BorderLayout());
    p.add(new JLabel(title), BorderLayout.NORTH);
    p.add(c, BorderLayout.CENTER);
    return p;
    }
  }
