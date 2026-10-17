package demo;

import java.awt.Color;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

public final class Main{
  public static void main(String[] a){ SwingUtilities.invokeLater(Main::launcher); }

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
    var cw= field(p, "Canvas width, in units", "520");
    var ch= field(p, "Canvas height, in units", "340");
    var opens= combo(p, "Opens as", Stage.Mode.values());
    var floor= field(p, "Opacity floor, in [0, 1]", "0.4");
    var pulse= check(p, "Pulse the opacity (Fearless decorations only)", false);
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
        var c= Config.parse(name.getText(), area.getText(), cw.getText(), ch.getText(), opens.getItemAt(opens.getSelectedIndex()),
          floor.getText(), pulse.isSelected(), closeKey.getText(), fsKey.getText(), accept.isSelected(), ask.isSelected(), bands.getItemAt(bands.getSelectedIndex()));
        new Stage(c, icon, picture).open();
        status.setText(" ");
        }
      catch (IllegalArgumentException e){ status.setText(e.getMessage()); }
      });
    p.add(status);
    p.add(show);
    f.setContentPane(p);
    f.pack();
    f.setLocationRelativeTo(null);
    f.setVisible(true);
    }
  }
