package io.immutables.ecs.gen;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedList;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.base.Joiner;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.immutables.generator.PostprocessingMachine;
import org.immutables.generator.Templates;
import org.immutables.generator.Templates.Invokable;
import org.immutables.generator.Templates.Invokation;

final class Output {
  private static final String NO_REWRITE_IMPORTS = "//-no-import-rewrite";

  @Nullable String zip;
  @Nullable String out;
	boolean schema;

	private final ListMultimap<String, String> fileContent = ArrayListMultimap.create();

  void finalizeResources() throws IOException {
    if (out != null) writeFiles(out);
    if (zip != null) writeZip(zip);
  }

  private void writeZip(String zipPath) throws IOException {
    File outFile = new File(zipPath);

    if (!outFile.getParentFile().exists()
        && !outFile.getParentFile().mkdirs()) {
      throw new IOException("Cannot create directories for " + outFile);
    }

    var sink = Files.asByteSink(outFile);

    try (var os = sink.openBufferedStream();
        var zip = new ZipOutputStream(os, StandardCharsets.UTF_8)) {
      zip.setMethod(ZipOutputStream.STORED);

      for (var e : fileContent.asMap().entrySet()) {
        var byteStream = new ByteArrayOutputStream();
        var checksum = new CRC32();

        var checkedStream = new CheckedOutputStream(byteStream, checksum);
        for (String s : e.getValue()) {
          ByteStreams.copy(new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8)), checkedStream);
        }
        checkedStream.close();

        byte[] data = byteStream.toByteArray();

        var entry = new ZipEntry(e.getKey());
        entry.setTime(0); // reproducible build
        entry.setSize(data.length);
        entry.setCrc(checksum.getValue());

        zip.putNextEntry(entry);
        zip.write(data);
        zip.closeEntry();
      }
    }
  }

  private void writeFiles(String outPath) throws IOException {
    var outDir = new File(outPath);

    for (var e : fileContent.asMap().entrySet()) {
      try {
        var outFile = new File(outDir, e.getKey());
        if (!outFile.getParentFile().exists()) {
          outFile.getParentFile().mkdirs();
          // if this will fail we will have exception thrown below anyway
        }
        var sink = Files.asCharSink(outFile, StandardCharsets.UTF_8);
        for (String s : e.getValue()) {
          sink.write(s);
        }
      } catch (IOException ex) {
        throw new IOException("Cannot write: " + e.getKey(), ex);
      }
    }
  }

  public final Templates.Invokable system = new Templates.Invokable() {
    @Override
    @Nullable
    public Invokable invoke(Invokation invokation, Object... parameters) {
      String message = CharMatcher.whitespace().trimFrom(parameters[0].toString());
      // we use out potentially to write zip file so can report smth only err
      System.err.println(message);
      return null;
    }
  };

  public final Templates.Invokable append = new Templates.Invokable() {
    @Override
    @Nullable
    public Invokable invoke(Invokation invokation, Object... parameters) {
      writeContent(parameters[0].toString(), parameters[1], Functions.identity(), true);
      return null;
    }
  };

  public final Templates.Invokable write = new Templates.Invokable() {
    @Override
    @Nullable
    public Invokable invoke(Invokation invokation, Object... parameters) {
      var ps = new LinkedList<>();
      Collections.addAll(ps, parameters);
      if (ps.isEmpty()) return null;
      Object content = ps.removeLast();
      var path = Joiner.on("").join(ps);
      writeContent(path, content, Functions.identity(), false);
      return null;
    }
  };

  public final Templates.Invokable java = new Templates.Invokable() {
    @Override
    @Nullable
    public Invokable invoke(Invokation invokation, Object... parameters) {
      String packageName = parameters[0].toString();
      String filenameNoExt = parameters[1].toString();
      Object body = parameters[2];

      String path = toFilePath(packageName, filenameNoExt, ".java");
      writeContent(path, body, this::rewriteImports, false);
      return null;
    }

    private CharSequence rewriteImports(CharSequence input) {
      String content = input.toString();
      return content.startsWith(NO_REWRITE_IMPORTS) ? content : PostprocessingMachine.rewrite(content);
    }
  };

  public final Templates.Invokable kt = new Templates.Invokable() {
    @Override
    @Nullable
    public Invokable invoke(Invokation invokation, Object... parameters) {
      String packageName = parameters[0].toString();
      String filenameNoExt = parameters[1].toString();
      Object body = parameters[2];

      String path = toFilePath(packageName, filenameNoExt, ".kt");
      writeContent(path, body, this::rewriteImports, false);
      return null;
    }

    private CharSequence rewriteImports(CharSequence input) {
      String content = input.toString();
      return content.startsWith(NO_REWRITE_IMPORTS) ? content : PostprocessingMachine.rewrite(content, false);
    }
  };

  private String toFilePath(String packageName, String filename, String extension) {
    return (!packageName.isEmpty() ? (packageName.replace('.', '/') + '/') : "") + filename + extension;
  }

  private void writeContent(
      String path,
      Object body,
      Function<CharSequence, CharSequence> transform,
      boolean append) {

    String content = (body instanceof Invokable ? new Templates.Fragment(0) {
      @Override
      public void run(Invokation invokation) {
        ((Invokable) body).invoke(invokation);
      }
    } : body).toString();

    if (!append) {
      fileContent.get(path).clear();
    }

    fileContent.put(path, transform.apply(content).toString());
  }
}
