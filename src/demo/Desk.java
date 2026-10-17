package demo;

import java.awt.Component;
import java.awt.Frame;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.stream.IntStream;

@SuppressWarnings("restricted")
final class Desk{
  static final boolean windows= System.getProperty("os.name").startsWith("Windows");
  static final boolean mac= System.getProperty("os.name").startsWith("Mac");

  static boolean down(){
    if (windows){ return IntStream.of(1, 2, 4).anyMatch(vk -> (short)call(Win.state, vk) < 0); }
    if (mac){ return IntStream.of(0, 1, 2).anyMatch(b -> (boolean)call(Mac.state, 0, b)); }
    var o= X11.out;
    call(X11.query, X11.display, X11.root, o, o.asSlice(8), o.asSlice(16), o.asSlice(20), o.asSlice(24), o.asSlice(28), o.asSlice(32));
    return (o.get(ValueLayout.JAVA_INT, 32) & 0x700) != 0;
    }
  static void restoreSize(Frame f, Rectangle r, boolean maximized){
    var gc= f.getGraphicsConfiguration();
    double k= gc.getDefaultTransform().getScaleX();
    var in= Toolkit.getDefaultToolkit().getScreenInsets(gc);
    var s= gc.getBounds();
    int x= (int)Math.round((r.x-s.x-in.left)*k);
    int y= (int)Math.round((r.y-s.y-in.top)*k);
    var p= Win.placement;
    var h= MemorySegment.ofAddress((long)reflect(() -> Win.hwnd.invoke(reflect(() -> Win.peer.get(f)))));
    p.set(ValueLayout.JAVA_INT, 0, 44);
    call(Win.getPlacement, h, p);
    p.set(ValueLayout.JAVA_INT, 8, maximized ? 3 : 1);
    p.set(ValueLayout.JAVA_INT, 28, x);
    p.set(ValueLayout.JAVA_INT, 32, y);
    p.set(ValueLayout.JAVA_INT, 36, x+(int)Math.round(r.width*k));
    p.set(ValueLayout.JAVA_INT, 40, y+(int)Math.round(r.height*k));
    call(Win.setPlacement, h, p);
    }
  private static MethodHandle fn(String lib, String name, FunctionDescriptor d){
    return Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup(lib, Arena.global()).find(name).orElseThrow(), d);
    }
  private static Object call(MethodHandle h, Object... args){
    try{ return h.invokeWithArguments(args); }
    catch (Throwable t){ throw new Error(t); }
    }
  private interface Reflective<T>{ T get() throws ReflectiveOperationException; }
  private static <T> T reflect(Reflective<T> r){
    try{ return r.get(); }
    catch (ReflectiveOperationException e){ throw new Error(e); }
    }
  private static <T extends AccessibleObject> T open(T o){
    o.setAccessible(true);
    return o;
    }
  private static final class Win{
    static final MethodHandle state= fn("user32", "GetAsyncKeyState", FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
    static final FunctionDescriptor placementCall= FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS);
    static final MethodHandle getPlacement= fn("user32", "GetWindowPlacement", placementCall);
    static final MethodHandle setPlacement= fn("user32", "SetWindowPlacement", placementCall);
    static final MemorySegment placement= Arena.global().allocate(44);
    static final Field peer= open(reflect(() -> Component.class.getDeclaredField("peer")));
    static final Method hwnd= open(reflect(() -> Class.forName("sun.awt.windows.WComponentPeer").getMethod("getHWnd")));
    }
  private static final class Mac{
    static final MethodHandle state= fn("/System/Library/Frameworks/CoreGraphics.framework/CoreGraphics", "CGEventSourceButtonState",
      FunctionDescriptor.of(ValueLayout.JAVA_BOOLEAN, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    }
  private static final class X11{
    static final String lib= "libX11.so.6";
    static final MemorySegment display= (MemorySegment)call(fn(lib, "XOpenDisplay", FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS)), MemorySegment.NULL);
    static final long root= (long)call(fn(lib, "XDefaultRootWindow", FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS)), display);
    static final MethodHandle query= fn(lib, "XQueryPointer", FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_LONG,
      ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    static final MemorySegment out= Arena.global().allocate(36);
    }
  }
