package de.digitalcollections.core.model.api;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.google.common.base.Splitter;
import org.apache.commons.io.FilenameUtils;

public class MimeType {
  private static Map<String, MimeType> knownTypes;
  private static Map<String, String> extensionMapping;

  /** Regular Expression used for decoding a MIME type **/
  private static final Pattern MIME_PATTERN = Pattern.compile(
      "^(?<primaryType>[-a-z]+?)/(?<subType>[-\\\\.a-z0-9*]+?)(?:\\+(?<suffix>\\w+))?$");

  static {
    // Load list of known MIME types and their extensions from the IANA list in the
    // package resources (obtained from https://svn.apache.org/repos/asf/httpd/httpd/trunk/docs/conf/mime.types)
    InputStream mimeStream = MimeType.class
        .getClassLoader().getResourceAsStream("mime.types");
    BufferedReader mimeReader = new BufferedReader(new InputStreamReader(mimeStream));
    List<String> typeStrings = mimeReader.lines()
        .map(l -> l.replaceAll("^# ", ""))
        .filter(l -> MIME_PATTERN.matcher(Splitter.on('\t').trimResults().omitEmptyStrings().split(l).iterator().next()).matches())
        .collect(Collectors.toList());

    knownTypes = typeStrings.stream()
        // Strip comments
        .filter(l -> l.contains("\t"))
        // Normalize multiple tab-delimiters to a single one for easier parsing
        // and split into (type, extensions) pairs
        .map(l -> l.replaceAll("\\t+", "\t").split("\\t"))
        // From those pairs, make a list of the extensions and create MimeType instances
        .map(p -> new MimeType(p[0], Arrays.asList(p[1].split(" "))))
        .collect(Collectors.toMap(
            MimeType::getTypeName,
            Function.identity()));
    typeStrings.stream()
        .filter(l -> !l.contains("\t"))
        .map(t -> new MimeType(t, Collections.emptyList()))
        .forEach(m -> knownTypes.put(m.getTypeName(), m));

    // Some custom overrides to influence the order of file extensions
    // Since these are added to the end of the list, they take precedence over the
    // types from the `mime.types` file
    knownTypes.get("image/jpeg").setExtensions(Arrays.asList("jpg", "jpeg", "jpe"));
    knownTypes.get("image/tiff").setExtensions(Arrays.asList("tif", "tiff"));

    List<String> xmlExtensions = new ArrayList<>(knownTypes.get("application/xml").getExtensions());
    xmlExtensions.add("ent");
    knownTypes.get("application/xml").setExtensions(xmlExtensions);

    extensionMapping = new HashMap<>();
    for (Map.Entry<String, MimeType> entry : knownTypes.entrySet()) {
      String typeName = entry.getKey();
      for (String ext : entry.getValue().getExtensions()) {
        extensionMapping.put(ext, typeName);
      }
    }
  }

  /** Convenience definitions for commonly used MIME types */
  public static final MimeType MIME_WILDCARD = new MimeType("*", Collections.emptyList());
  public static final MimeType MIME_IMAGE = new MimeType("image/*", Collections.emptyList());
  public static final MimeType MIME_APPLICATION_JSON = knownTypes.get("application/json");
  public static final MimeType MIME_APPLICATION_XML = knownTypes.get("application/xml");
  public static final MimeType MIME_IMAGE_JPEG = knownTypes.get("image/jpeg");
  public static final MimeType MIME_IMAGE_TIF = knownTypes.get("image/tiff");
  public static final MimeType MIME_IMAGE_PNG = knownTypes.get("image/png");

  private final String primaryType;
  private final String subType;
  private final String suffix;
  private List<String> extensions;

  /** Determine MIME type for the given file extension */
  public static MimeType fromExtension(String ext) {
    final String extension;
    if (ext.startsWith(".")) {
      extension = ext.substring(1).toLowerCase();
    } else {
      extension = ext.toLowerCase();
    }
    String typeName = extensionMapping.get(extension);
    if (typeName != null) {
      return knownTypes.get(typeName);
    } else {
      return null;
    }
  }

  /**
   * Determine MIME type from filename string.
   * Returns null if no matching MIME type was found.
   */
  public static MimeType fromFilename(String filename) {
    return fromExtension(FilenameUtils.getExtension(filename));
  }

  /** Determine MIME type from URI. **/
  public static MimeType fromURI(URI uri) {
    try {
      return fromFilename(Paths.get(uri).toString());
    } catch (FileSystemNotFoundException e) {
      // For non-file URIs, try to guess the MIME type from the URL path, if possible
      return fromExtension(FilenameUtils.getExtension(uri.toString()));
    }
  }

  /** Given an existing MIME type name, look up the corresponding instance.
   *
   *  An exception is made for vendor-specific types or non-standard types.
   * **/
  public static MimeType fromTypename(String typeName) {
    MimeType knownType = knownTypes.get(typeName);
    if (knownType != null) {
      return knownType;
    }
    MimeType unknownType = new MimeType(typeName);
    if (!unknownType.getPrimaryType().startsWith("x-") ||
        !unknownType.getSubType().startsWith("vnd.") ||
        !unknownType.getSubType().startsWith("prs.")) {
      return null;
    } else {
      return unknownType;
    }
  }

  // NOTE: Constructors are private, since we want users to rely on the pre-defined MIME types
  private MimeType(String typeName) {
    this(typeName, Collections.emptyList());
  }

  private MimeType(String typeName, List<String> extensions) {
    if (typeName.equals("*")) {
      this.primaryType = "*";
      this.subType = "*";
      this.suffix = "";
    } else {
      Matcher matcher = MIME_PATTERN.matcher(typeName);
      if (!matcher.matches()) {
        throw new IllegalArgumentException(String.format("%s is not a valid MIME type!", typeName));
      }
      this.primaryType = matcher.group("primaryType");
      this.subType = matcher.group("subType");
      this.suffix = matcher.group("suffix");
      this.extensions = extensions;
    }
  }


  /** Get the MIME type's name (e.g. "application/json") */
  public String getTypeName() {
    StringBuilder sb = new StringBuilder(primaryType)
        .append("/")
        .append(subType);
    if (suffix != null) {
      sb.append("+").append(suffix);
    }
    return sb.toString();
  }

  /** Get the known file extensions for the MIME type */
  public List<String> getExtensions() {
    return extensions;
  }

  private void setExtensions(List<String> extensions) {
    this.extensions = extensions;
  }

  public String getPrimaryType() {
    return primaryType;
  }

  public String getSubType() {
    return subType;
  }

  public String getSuffix() {
    return suffix;
  }

  /** Check if the MIME type "matches" another MIME type.
   *
   * @param other   Other MIME type to compare against
   * @return Whether the other type matches this type
   */
  public boolean matches(Object other) {
    if (other instanceof MimeType) {
      MimeType mime = (MimeType) other;
      if (mime == MIME_WILDCARD || this == MIME_WILDCARD) {
        return true;
      } else if (((mime.getSubType().equals("*") || this.getSubType().equals("*")))
                  && this.getPrimaryType().equals(mime.getPrimaryType())) {
        return true;
      } else {
        return super.equals(other);
      }
    } else {
      return false;
    }
  }
}
