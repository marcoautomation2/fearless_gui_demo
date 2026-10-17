package demo;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.stream.IntStream;

@SuppressWarnings("restricted")
final class Buttons{
  static final String os= System.getProperty("os.name");

  static boolean down(){
    if (os.startsWith("Windows")){ return IntStream.of(1, 2, 4).anyMatch(vk -> (short)call(Win.state, vk) < 0); }
    if (os.startsWith("Mac")){ return IntStream.of(0, 1, 2).anyMatch(b -> (boolean)call(Mac.state, 0, b)); }
    var o= X11.out;
    call(X11.query, X11.display, X11.root, o, o.asSlice(8), o.asSlice(16), o.asSlice(20), o.asSlice(24), o.asSlice(28), o.asSlice(32));
    return (o.get(ValueLayout.JAVA_INT, 32) & 0x700) != 0;
    }
  private static MethodHandle fn(String lib, String name, FunctionDescriptor d){
    return Linker.nativeLinker().downcallHandle(SymbolLookup.libraryLookup(lib, Arena.global()).find(name).orElseThrow(), d);
    }
  private static Object call(MethodHandle h, Object... args){
    try{ return h.invokeWithArguments(args); }
    catch (Throwable t){ throw new Error(t); }
    }
  private static final class Win{
    static final MethodHandle state= fn("user32", "GetAsyncKeyState", FunctionDescriptor.of(ValueLayout.JAVA_SHORT, ValueLayout.JAVA_INT));
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
