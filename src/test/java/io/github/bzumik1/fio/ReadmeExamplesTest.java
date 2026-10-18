package io.github.bzumik1.fio;

import io.github.bzumik1.fio.StubFioServer.RecordedRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static io.github.bzumik1.fio.Fixtures.IMPORT_ACCEPTED;
import static io.github.bzumik1.fio.Fixtures.MERCHANT_DOCUMENTATION_EXAMPLE;
import static io.github.bzumik1.fio.Fixtures.PERIODS_STATEMENT;
import static io.github.bzumik1.fio.StubBank.SECRET;
import static org.assertj.core.api.Assertions.assertThat;

class ReadmeExamplesTest {

    private static final Path PROJECT = projectDirectory();
    private static final Path README = PROJECT.resolve("README.md");
    private static final Path CLIENT_SOURCE = PROJECT.resolve("src/main/java/io/github/bzumik1/fio/FioClient.java");
    private static final Pattern JAVA_BLOCK = Pattern.compile("```java\\R(.*?)```", Pattern.DOTALL);
    private static final Pattern JAVADOC_EXAMPLE = Pattern.compile("<pre>\\{@code\\R(.*?)\\R\\s*\\*\\s*}</pre>", Pattern.DOTALL);
    private static final Pattern JAVADOC_MARGIN = Pattern.compile("(?m)^\\s*\\* ?");
    private static final Pattern HEADING = Pattern.compile("(?m)^#+ (.+)$");
    private static final String PACKAGE = "readme";
    private static final String FROM_ENVIRONMENT = "FioToken.fromEnvironment(\"FIO_TOKEN\")";
    private static final String NEW_BUILDER = "FioClient.builder(";

    @RegisterExtension
    final StubBank bank = new StubBank();

    record Example(String name, String className, String code) {

        @Override
        public String toString() {
            return name;
        }

        boolean callsTheBank() {
            return code.contains("fio.");
        }

        String runnableCode() {
            return code.replace(FROM_ENVIRONMENT, "token").replace(NEW_BUILDER, "builder(");
        }
    }

    static List<Example> readmeExamples() {
        String readme = read(README);
        List<Example> examples = new ArrayList<>();
        Matcher block = JAVA_BLOCK.matcher(readme);
        while (block.find()) {
            int number = examples.size() + 1;
            examples.add(new Example(
                    "README example " + number + " (" + headingBefore(readme, block.start()) + ")",
                    "ReadmeExample" + number,
                    block.group(1)));
        }
        return examples;
    }

    static List<Example> javadocExamples() {
        Matcher example = JAVADOC_EXAMPLE.matcher(read(CLIENT_SOURCE));
        List<Example> examples = new ArrayList<>();
        while (example.find()) {
            int number = examples.size() + 1;
            examples.add(new Example(
                    "FioClient Javadoc example " + number,
                    "JavadocExample" + number,
                    JAVADOC_MARGIN.matcher(example.group(1)).replaceAll("")));
        }
        return examples;
    }

    static Stream<Example> documentedExamples() {
        return Stream.concat(readmeExamples().stream(), javadocExamples().stream());
    }

    static Stream<Example> examplesThatCallTheBank() {
        return documentedExamples().filter(Example::callsTheBank);
    }

    @Test
    void should_find_usage_and_payment_examples_when_extracted_given_readme() {
        assertThat(readmeExamples()).extracting(Example::name)
                .anySatisfy(name -> assertThat(name).endsWith("(Usage)"))
                .anySatisfy(name -> assertThat(name).endsWith("(Payment orders)"));
    }

    @Test
    void should_find_class_example_without_javadoc_margin_when_extracted_given_client_source() {
        assertThat(javadocExamples()).singleElement()
                .satisfies(example -> assertThat(example.code()).startsWith("FioClient fio = ").doesNotContain("*"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("documentedExamples")
    void should_compile_against_the_library_when_compiled_given_documented_example(Example example) {
        compile(example.className(), example.code());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("examplesThatCallTheBank")
    void should_run_against_the_stub_bank_when_executed_given_documented_example(Example example) throws Exception {
        answerEveryDocumentedRequest();

        run(example);

        assertThat(bank.server().requests()).isNotEmpty();
    }

    @Test
    void should_submit_the_batch_when_executed_given_payment_example() throws Exception {
        answerEveryDocumentedRequest();
        Example payment = readmeExamples().stream()
                .filter(example -> example.name().endsWith("(Payment orders)"))
                .findFirst()
                .orElseThrow();

        run(payment);

        assertThat(bank.server().requests()).containsExactly(new RecordedRequest("POST", "/v1/rest/import/", "application/xml"));
        assertThat(bank.server().bodies()).singleElement().extracting(StubFioServer.RecordedBody::text).asString()
                .contains("<DomesticTransaction>", "<T2Transaction>", "<amount>1250.00</amount>", "<amount>99.90</amount>");
    }

    private void run(Example example) throws Exception {
        Map<String, byte[]> classes = compile(example.className(), example.runnableCode());
        Callable<?> runnable = (Callable<?>) new InMemoryClassLoader(classes)
                .loadClass(PACKAGE + "." + example.className())
                .getConstructor(FioClient.Builder.class)
                .newInstance(bank.builder());
        runnable.call();
    }

    private void answerEveryDocumentedRequest() {
        StubFioServer server = bank.server();
        server.respondWith(404, "No stub answer for this path");
        server.route("/lastStatement/", 200, "text/plain", utf8("2026,10"));
        server.route("/merchant/", 200, "application/xml", utf8(Fixtures.read(MERCHANT_DOCUMENTATION_EXAMPLE)));
        server.route("transactions.json", 200, StubFioServer.JSON, utf8(Fixtures.read(PERIODS_STATEMENT)));
        server.route("transactions.csv", 200, "text/csv", utf8("Datum;Objem"));
        server.route("transactions.pdf", 200, "application/pdf", utf8("%PDF"));
        server.route("/set-last-", 200, "text/plain", new byte[0]);
        server.route("/import/", 200, "application/xml", utf8(Fixtures.read(IMPORT_ACCEPTED)));
    }

    private static Map<String, byte[]> compile(String className, String code) {
        String preamble = preamble(className);
        String source = preamble + code + "\n        return null;\n    }\n}\n";
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        Map<String, ByteArrayOutputStream> classes = new ConcurrentHashMap<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(diagnostics, Locale.ENGLISH, StandardCharsets.UTF_8)) {
            JavaFileManager inMemory = new ForwardingJavaFileManager<>(files) {
                @Override
                public JavaFileObject getJavaFileForOutput(
                        Location location, String name, JavaFileObject.Kind kind, FileObject sibling) {
                    return new SimpleJavaFileObject(URI.create("memory:///" + name.replace('.', '/') + kind.extension), kind) {
                        @Override
                        public OutputStream openOutputStream() {
                            return classes.computeIfAbsent(name, ignored -> new ByteArrayOutputStream());
                        }
                    };
                }
            };
            JavaFileObject unit = new SimpleJavaFileObject(
                    URI.create("string:///" + PACKAGE + "/" + className + ".java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            List<String> options = List.of(
                    "--release", "17", "-proc:none", "-Xlint:all", "-Werror", "-classpath", System.getProperty("java.class.path"));
            boolean compiled = compiler.getTask(null, inMemory, diagnostics, options, null, List.of(unit)).call();
            assertThat(compiled)
                    .withFailMessage(() -> className + " does not compile:\n" + describe(diagnostics, preamble) + "\n" + code)
                    .isTrue();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return classes.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().toByteArray()));
    }

    private static String preamble(String className) {
        return String.join("\n",
                "package " + PACKAGE + ";",
                "",
                "import io.github.bzumik1.fio.*;",
                "import java.math.BigDecimal;",
                "import java.net.URI;",
                "import java.net.http.HttpClient;",
                "import java.time.*;",
                "import java.util.*;",
                "",
                "public final class " + className + " implements java.util.concurrent.Callable<Void> {",
                "",
                "    private final FioClient.Builder stubbed;",
                "    private final FioToken token = FioToken.of(\"" + SECRET + "\");",
                "    private final FioClient fio;",
                "    private final LocalDate from = LocalDate.of(2026, 1, 5);",
                "    private final LocalDate to = LocalDate.of(2026, 2, 9);",
                "    private final HttpClient myHttpClient = HttpClient.newBuilder().build();",
                "    private final long lastProcessedTransactionId = 1147608196L;",
                "    private final LocalDate firstDayThatFailed = LocalDate.of(2026, 1, 20);",
                "",
                "    public " + className + "(FioClient.Builder stubbed) {",
                "        this.stubbed = stubbed;",
                "        this.fio = stubbed.build();",
                "    }",
                "",
                "    private FioClient.Builder builder(FioToken ignored) {",
                "        return stubbed;",
                "    }",
                "",
                "    @Override",
                "    public Void call() throws Exception {",
                "");
    }

    private static String describe(DiagnosticCollector<JavaFileObject> diagnostics, String preamble) {
        long preambleLines = preamble.lines().count();
        return diagnostics.getDiagnostics().stream()
                .map(diagnostic -> diagnostic.getKind() + " at line " + lineInExample(diagnostic, preambleLines) + ": "
                        + diagnostic.getMessage(Locale.ENGLISH))
                .collect(Collectors.joining("\n"));
    }

    private static long lineInExample(Diagnostic<? extends JavaFileObject> diagnostic, long preambleLines) {
        return diagnostic.getLineNumber() - preambleLines;
    }

    private static String headingBefore(String text, int position) {
        Matcher heading = HEADING.matcher(text.substring(0, position));
        String last = "no heading";
        while (heading.find()) {
            last = heading.group(1);
        }
        return last;
    }

    private static Path projectDirectory() {
        Path start = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path directory = start; directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("README.md")) && Files.isRegularFile(directory.resolve("build.gradle.kts"))) {
                return directory;
            }
        }
        throw new IllegalStateException("No directory holding README.md and build.gradle.kts above " + start);
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] utf8(String text) {
        return text.getBytes(StandardCharsets.UTF_8);
    }

    private static final class InMemoryClassLoader extends ClassLoader {

        private final Map<String, byte[]> classes;

        InMemoryClassLoader(Map<String, byte[]> classes) {
            super(ReadmeExamplesTest.class.getClassLoader());
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            byte[] bytes = classes.get(name);
            if (bytes == null) {
                throw new ClassNotFoundException(name);
            }
            return defineClass(name, bytes, 0, bytes.length);
        }
    }
}
