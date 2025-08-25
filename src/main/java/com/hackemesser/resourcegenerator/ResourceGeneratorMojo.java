package com.hackemesser.resourcegenerator;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templateresolver.FileTemplateResolver;

@Mojo(name = "generate", defaultPhase = LifecyclePhase.GENERATE_RESOURCES)
public class ResourceGeneratorMojo extends AbstractMojo {
  @Parameter(defaultValue = "${project}", required = true, readonly = true)
  MavenProject project;


  /**
   * Path to the Thymeleaf template file (relative to project root or absolute).
   */
  @Parameter(property = "templateFile", required = true)
  private File templateFile;

  /**
   * Path to the output file (relative to target/generated-resources or absolute).
   */
  @Parameter(property = "outputFile", required = true)
  private File outputFile;

  /**
   * Properties map for template processing.
   * Values can be strings or comma-separated lists.
   */
  @Parameter(property = "properties")
  private Map<String, String> properties = new HashMap<>();

  /**
   * Template mode (HTML, XML, TEXT, JAVASCRIPT, CSS, RAW).
   */
  @Parameter(property = "templateMode", defaultValue = "TEXT")
  private String templateMode;

  /**
   * Character encoding for input and output files.
   */
  @Parameter(property = "encoding", defaultValue = "UTF-8")
  private String encoding;

  public void execute() throws MojoExecutionException, MojoFailureException {
    getLog().info("Starting Resource generation...");

    validateInputs();

    try {
      TemplateEngine templateEngine = createTemplateEngine();
      String result = templateEngine.process(templateFile.getName(), createContext());
      writeOutput(result);

      getLog().info("Generation complete");

    } catch (Exception e) {
      throw new MojoExecutionException("Failed to generate resource from template", e);
    }
  }

  private void writeOutput(String content) throws IOException, MojoExecutionException {
    // Resolve output file path
    File resolvedOutputFile = resolveOutputFile();

    // Create output directory if needed

    File outputDir = resolvedOutputFile.getParentFile();
    if ( !outputDir.exists()) {
      if (!outputDir.mkdirs()) {
        throw new MojoExecutionException("Failed to create output directory: " + outputDir.getPath());
      }
    }

    // Write content to file
    try (FileWriter writer = new FileWriter(resolvedOutputFile, Charset.forName(encoding))) {
      writer.write(content);
    }

    getLog().info("Generated resource written to: " + resolvedOutputFile.getPath());
  }

  private void validateInputs() throws MojoExecutionException {
    if (!templateFile.exists()) {
      throw new MojoExecutionException("Template file does not exist: " + templateFile.getPath());
    }

    if (!templateFile.isFile()) {
      throw new MojoExecutionException("Template path is not a file: " + templateFile.getPath());
    }

    if (!templateFile.canRead()) {
      throw new MojoExecutionException("Cannot read template file: " + templateFile.getPath());
    }
  }

  private TemplateEngine createTemplateEngine() {
    TemplateEngine templateEngine = new TemplateEngine();

    // Configure file template resolver
    FileTemplateResolver templateResolver = new FileTemplateResolver();
    templateResolver.setTemplateMode(templateMode);
    templateResolver.setCharacterEncoding(encoding);
    templateResolver.setCacheable(false); // Disable caching for build-time processing

    // Set prefix to template file's parent directory
    String templateDir = templateFile.getParent();
    if (templateDir != null) {
      templateResolver.setPrefix(templateDir + File.separator);
    }

    templateEngine.setTemplateResolver(templateResolver);

    return templateEngine;
  }

  private Context createContext() {
    Context context = new Context();

    // Process configured properties
    Stream<Map.Entry<String, String>> env =
        System.getenv()
              .entrySet()
              .stream()
              .filter(entry -> !properties.containsKey(entry.getKey()));
    Map<String, Object> processedProps =
        Stream.concat(properties.entrySet().stream(), env)
              .collect(Collectors.toMap(
                  Map.Entry::getKey,
                  this::processPropertyValue));

    context.setVariables(processedProps);
    return context;
  }

  private Object processPropertyValue(Map.Entry<String, String> entry) {
    String key = entry.getKey();
    String value = Objects.requireNonNull(entry.getValue());

    // Check if value looks like a list (contains commas)
    if (value.contains(",")) {
      getLog().debug("Processing " + key + " as list");
      return Arrays.stream(value.split(",")).map(String::trim);
    } else {
      return value.trim();
    }
  }

  private File resolveOutputFile() {
    if (outputFile.isAbsolute()) {
      return outputFile;
    }

    // Relative to target/generated-resources
    return Path.of(project.getBuild().getDirectory(), "generated-resources", outputFile.getPath()).toFile();
  }
}
