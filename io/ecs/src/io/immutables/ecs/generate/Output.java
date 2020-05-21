package io.immutables.ecs.generate;

import com.google.common.base.CharMatcher;
import com.google.common.base.Functions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.io.ByteStreams;
import com.google.common.io.CharSink;
import com.google.common.io.Files;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.annotation.Nullable;
import org.immutables.generator.Templates;
import org.immutables.generator.Templates.Invokable;
import org.immutables.generator.Templates.Invokation;

public final class Output {
	private static final String NO_REWRITE_IMPORTS = "//-no-import-rewrite";

	public @Nullable String zip;
	public @Nullable String out;

	private final ListMultimap<String, String> fileContent = ArrayListMultimap.create();

	void finalizeResources() {
		if (out != null) writeFiles(out);
		if (zip != null) writeZip(zip);
	}

	private void writeZip(String zipPath) {
		File outFile = new File(zipPath);
		if (!outFile.getParentFile().exists()) {
			outFile.getParentFile().mkdirs();
			// if this will fail we will have exception thrown below anyway
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
		} catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private void writeFiles(String outPath) {
		File outDir = new File(outPath);

		for (Entry<String, Collection<String>> e : fileContent.asMap().entrySet()) {
			File outFile = new File(outDir, e.getKey());
			try {
				if (!outFile.getParentFile().exists()) {
					outFile.getParentFile().mkdirs();
					// if this will fail we will have exception thrown below anyway
				}
				CharSink sink = Files.asCharSink(outFile, StandardCharsets.UTF_8);
				for (String s : e.getValue()) {
					sink.write(s);
				}
			} catch (IOException ex) {
				throw new UncheckedIOException(ex);
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
			writeToFile(parameters[0].toString(), (Invokable) parameters[1], Functions.identity(), true);
			return null;
		}
	};

	public final Templates.Invokable write = new Templates.Invokable() {
		@Override
		@Nullable
		public Invokable invoke(Invokation invokation, Object... parameters) {
			writeToFile(parameters[0].toString(), (Invokable) parameters[1], Functions.identity(), false);
			return null;
		}
	};

	public final Templates.Invokable java = new Templates.Invokable() {
		@Override
		@Nullable
		public Invokable invoke(Invokation invokation, Object... parameters) {
			String packageName = parameters[0].toString();
			String filenameNoExt = parameters[1].toString();
			Invokable body = (Invokable) parameters[2];
			
			String path = toFilePath(packageName, filenameNoExt, ".java");
			writeToFile(path, body, this::rewriteImports, false);
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
			Invokable body = (Invokable) parameters[2];

			String path = toFilePath(packageName, filenameNoExt, ".kt");
			writeToFile(path, body, this::rewriteImports, false);
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
	
	private void writeToFile(
			String path,
			Invokable body,
			Function<CharSequence, CharSequence> transform,
			boolean append) {

		String content = new Templates.Fragment(0) {
			@Override
			public void run(Invokation invokation) {
				body.invoke(invokation);
			}
		}.toString();

		if (!append) {
			fileContent.get(path).clear();
		}
		fileContent.put(path, transform.apply(content).toString());
	}
}
