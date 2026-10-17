// java build/Build.java   (from the repository root)
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.spi.ToolProvider;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class Build{
  static final String appName= "fearless_gui_demo";
  static final Path root= Path.of("").toAbsolutePath();
  static final Path out= root.resolve("out");
  static final boolean windows= System.getProperty("os.name").toLowerCase().contains("windows");

  public static void main(String[] a) throws IOException, InterruptedException{
    if (!Files.isDirectory(root.resolve("src"))){ throw new IllegalStateException("Run from the repository root, not from "+root); }
    rmTree(out);
    var classes= out.resolve("classes");
    var mods= out.resolve("mods");
    var runtime= out.resolve("runtime");
    var app= out.resolve("app");
    var dist= out.resolve("dist");
    Files.createDirectories(mods);
    Files.createDirectories(dist);
    var args= new ArrayList<>(List.of("-encoding", "UTF-8", "-Xlint:all,-serial", "-Werror", "-d", classes.toString()));
    args.addAll(files(root.resolve("src"), ".java"));
    run("javac", args);
    for (var png : files(root.resolve("src"), ".png")){
      var rel= root.resolve("src").relativize(Path.of(png));
      Files.createDirectories(classes.resolve(rel).getParent());
      Files.copy(Path.of(png), classes.resolve(rel));
      }
    run("jar", List.of("--create", "--file", mods.resolve("demo.jar").toString(), "-C", classes.toString(), "."));
    run("jlink", List.of(
      "--module-path", Path.of(System.getProperty("java.home"), "jmods").toString(),
      "--add-modules", "java.desktop",
      "--output", runtime.toString(),
      "--no-header-files", "--no-man-pages", "--strip-debug", "--compress", "zip-0"));
    var icon= root.resolve("packaging").resolve(windows ? "windows/icon.ico" : "linux/icon.png");
    run("jpackage", List.of(
      "--type", "app-image",
      "--dest", app.toString(),
      "--name", appName,
      "--icon", icon.toString(),
      "--module-path", mods.toString(),
      "--module", "demo/demo.Main",
      "--runtime-image", runtime.toString()));
    if (windows){ zip(app.resolve(appName), dist.resolve(appName+"_windows.zip")); }
    else{ tar(app, dist.resolve(appName+"_linux.tar.gz")); }
    System.out.println("Built "+dist);
    }

  static void run(String tool, List<String> args){
    var t= ToolProvider.findFirst(tool).orElseThrow();
    int code= t.run(System.out, System.err, args.toArray(String[]::new));
    if (code != 0){ throw new IllegalStateException(tool+" failed with code "+code); }
    }
  static List<String> files(Path dir, String ext) throws IOException{
    try (Stream<Path> s= Files.walk(dir)){
      return s.filter(p -> p.toString().endsWith(ext)).map(Path::toString).sorted().toList();
      }
    }
  static void rmTree(Path p) throws IOException{
    if (!Files.exists(p)){ return; }
    try (Stream<Path> s= Files.walk(p)){
      s.sorted(Comparator.reverseOrder()).forEach(Build::delete);
      }
    }
  static void delete(Path p){
    try{ Files.delete(p); }
    catch (IOException e){ throw new UncheckedIOException(e); }
    }
  static void tar(Path parent, Path archive) throws IOException, InterruptedException{
    var pb= new ProcessBuilder("tar", "czf", archive.toString(), "-C", parent.toString(), appName).inheritIO();
    int code= pb.start().waitFor();
    if (code != 0){ throw new IllegalStateException("tar failed with code "+code); }
    }
  static void zip(Path dir, Path archive) throws IOException{
    try (var z= new ZipOutputStream(Files.newOutputStream(archive)); Stream<Path> s= Files.walk(dir)){
      for (var p : s.filter(Files::isRegularFile).sorted().toList()){
        z.putNextEntry(new ZipEntry(dir.getParent().relativize(p).toString().replace('\\', '/')));
        Files.copy(p, z);
        z.closeEntry();
        }
      }
    }
  }
