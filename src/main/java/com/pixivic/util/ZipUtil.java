package com.pixivic.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class ZipUtil {
    public void unzip(Path targetDirPath, String zipFilename) {
        try (ZipFile zipFile = new ZipFile(zipFilename)) {
            zipFile.stream()
                    .parallel()
                    .forEach(e -> unzipEntry(zipFile, e, targetDirPath));
        } catch (IOException e) {
            throw new RuntimeException("Error opening zip file '" + zipFilename + "': " + e, e);
        }
    }

    private void unzipEntry(ZipFile zipFile, ZipEntry entry, Path targetDir) {
        try {
            Path targetPath = targetDir.resolve(Paths.get(entry.getName()));
            if (Files.isDirectory(targetPath)) {
                Files.createDirectories(targetPath);
            } else {
                Files.createDirectories(targetPath.getParent());
                try (InputStream in = zipFile.getInputStream(entry)) {
                    Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Error processing zip entry '" + entry.getName() + "': " + e, e);
        }
    }

}