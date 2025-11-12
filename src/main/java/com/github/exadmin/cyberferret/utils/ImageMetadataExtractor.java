package com.github.exadmin.cyberferret.utils;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Utility class for extracting metadata from image files (PNG, JPEG, etc.)
 * Extracts EXIF, IPTC, XMP, and other metadata that can be searched with regex patterns.
 */
public class ImageMetadataExtractor {
    private static final Logger LOGGER = LoggerFactory.getLogger(ImageMetadataExtractor.class);

    /**
     * Extracts all metadata from an image file and returns it as formatted text.
     * The text format allows regex pattern matching on metadata fields.
     *
     * @param imagePath Path to the image file
     * @return Formatted string containing all metadata, or empty string if extraction fails
     */
    public static String extractMetadataAsText(Path imagePath) {
        if (imagePath == null) {
            LOGGER.warn("Image path is null");
            return "";
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(imagePath.toFile());
            return formatMetadataAsText(metadata);
        } catch (ImageProcessingException e) {
            LOGGER.debug("Failed to process image metadata for {}: {}", imagePath, e.getMessage());
            return "";
        } catch (IOException e) {
            LOGGER.debug("Failed to read image file {}: {}", imagePath, e.getMessage());
            return "";
        } catch (Exception e) {
            LOGGER.warn("Unexpected error extracting metadata from {}: {}", imagePath, e.getMessage());
            return "";
        }
    }

    /**
     * Formats extracted metadata as searchable text.
     * Each metadata tag is formatted as "DirectoryName.TagName: value"
     *
     * Example output:
     * <pre>
     * PNG-IHDR.Image Width: 1920
     * PNG-IHDR.Image Height: 1080
     * PNG-tEXt.Author: John Doe
     * Exif IFD0.Software: Adobe Photoshop
     * Exif IFD0.Artist: Jane Smith
     * </pre>
     *
     * @param metadata Metadata object from metadata-extractor library
     * @return Formatted string representation
     */
    private static String formatMetadataAsText(Metadata metadata) {
        StringBuilder sb = new StringBuilder();

        for (Directory directory : metadata.getDirectories()) {
            String directoryName = directory.getName();

            // Add each tag in the directory
            for (Tag tag : directory.getTags()) {
                String tagName = tag.getTagName();
                String tagValue = tag.getDescription();

                if (tagValue != null && !tagValue.trim().isEmpty()) {
                    sb.append(directoryName)
                      .append(".")
                      .append(tagName)
                      .append(": ")
                      .append(tagValue)
                      .append("\n");
                }
            }

            // Add any errors encountered
            if (directory.hasErrors()) {
                for (String error : directory.getErrors()) {
                    LOGGER.debug("Metadata directory '{}' error: {}", directoryName, error);
                }
            }
        }

        return sb.toString();
    }

    /**
     * Checks if a file extension indicates an image file that may contain metadata.
     *
     * @param extension File extension (without dot)
     * @return true if the extension is for a supported image format
     */
    public static boolean isSupportedImageFormat(String extension) {
        if (extension == null) {
            return false;
        }

        String ext = extension.toLowerCase();
        return ext.equals("png") ||
               ext.equals("jpg") ||
               ext.equals("jpeg") ||
               ext.equals("gif") ||
               ext.equals("bmp") ||
               ext.equals("tiff") ||
               ext.equals("tif") ||
               ext.equals("webp") ||
               ext.equals("ico");
    }
}
