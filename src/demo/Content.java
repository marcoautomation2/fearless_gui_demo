package demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.util.List;

final class Content{
  final Stage st;
  final BufferedImage picture;
  final List<String> hints;
  final Rectangle button= new Rectangle();
  final Rectangle yes= new Rectangle();
  final Rectangle no= new Rectangle();
  int w;
  int h;
  int clicks;
  boolean confirm;
  Point mouse= new Point(-1, -1);

  Content(Stage st, BufferedImage picture){
    this.st= st;
    this.picture= picture;
    hints= List.of(
      KeyEvent.getKeyText(st.c.closeKey().getKeyCode())+" asks to close, "+KeyEvent.getKeyText(st.c.fullScreenKey().getKeyCode())+" toggles full screen.",
      "Desk window: maximize as usual; unmaximize gives full screen; a title bar drag is the desk's.",
      "Full screen: drag the corner for a Fearless window, double click it to decorate.",
      "Fearless window: corner drags (shrinks at the edge, full screen at the top left), tip resizes, double click decorates.",
      "Drag an edge of the desk window: when it settles, a rejected request leaves bands.");
    }
  private void layout(int w, int h){
    this.w= w;
    this.h= h;
    yes.setBounds(w/2-110, h/2+50, 100, 32);
    no.setBounds(w/2+10, h/2+50, 100, 32);
    }

  void paint(Graphics2D g, int w, int h){
    layout(w, h);
    g.setPaint(new GradientPaint(0, 0, new Color(250, 250, 245), 0, h, new Color(215, 225, 240)));
    g.fillRect(0, 0, w, h);
    g.setColor(new Color(60, 60, 80));
    g.setStroke(new BasicStroke(2));
    g.drawRect(1, 1, w-2, h-2);
    int ph= h-130;
    int pw= picture.getWidth()*ph/picture.getHeight();
    if (pw > w/3){ pw= w/3; ph= picture.getHeight()*pw/picture.getWidth(); }
    g.drawImage(picture, 20, 20, pw, ph, null);
    int x= 40+pw;
    int y= 40;
    g.setColor(new Color(30, 30, 50));
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 24));
    g.drawString("Fearless Gui demo", x, y);
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    for (var line : st.status()){ y+= 18; g.drawString(line, x, y); }
    button.setBounds(x, y+14, 160, 36);
    box(g, button, "Clicked "+clicks+" times", new Color(72, 120, 210));
    y= h-22-16*hints.size();
    g.setColor(new Color(90, 90, 110));
    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 9));
    for (var line : hints){ y+= 16; g.drawString(line, 20, y); }
    if (!confirm){ return; }
    g.setColor(new Color(0, 0, 0, 120));
    g.fillRect(0, 0, w, h);
    g.setColor(Color.white);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
    center(g, "Close this window?", new Rectangle(0, h/2, w, 30));
    box(g, yes, "Yes", new Color(230, 64, 48));
    box(g, no, "No", new Color(40, 170, 64));
    }
  private void box(Graphics2D g, Rectangle r, String text, Color bg){
    g.setColor(bg);
    g.fillRoundRect(r.x, r.y, r.width, r.height, 10, 10);
    g.setColor(Color.white);
    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 14));
    center(g, text, r);
    }
  private void center(Graphics2D g, String text, Rectangle r){
    var fm= g.getFontMetrics();
    g.drawString(text, r.x+(r.width-fm.stringWidth(text))/2, r.y+(r.height-fm.getHeight())/2+fm.getAscent());
    }

  void press(Point p){
    if (p.x < 0 || p.y < 0 || p.x >= w || p.y >= h){ return; }
    if (confirm){
      if (yes.contains(p)){ st.close(); }
      if (no.contains(p)){ confirm= false; }
      return;
      }
    if (!button.contains(p)){ return; }
    clicks+= 1;
    st.subtitle("clicks: "+clicks);
    }
  }
