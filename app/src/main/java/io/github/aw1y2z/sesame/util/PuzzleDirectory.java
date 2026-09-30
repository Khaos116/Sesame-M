package io.github.aw1y2z.sesame.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/** Keeps captcha screenshots outside Android's media scan, including screenshots from older installs. */
final class PuzzleDirectory {
    private PuzzleDirectory() {}

    static File prepare(File mainDirectory) throws IOException {
        File legacy = new File(mainDirectory, "puzzle");
        File hiddenParent = new File(mainDirectory, ".nomedia");
        File hidden = new File(hiddenParent, "puzzle");
        if (Files.isSymbolicLink(legacy.toPath()) || Files.isSymbolicLink(hiddenParent.toPath())
                || Files.isSymbolicLink(hidden.toPath())) {
            throw new IOException("Puzzle directory is a symbolic link");
        }
        if (legacy.isDirectory()) {
            noMedia(legacy);
        }
        Files.createDirectories(hiddenParent.toPath());
        if (legacy.isDirectory()) {
            if (!hidden.exists() && legacy.renameTo(hidden)) {
                return hidden;
            }
        }
        Files.createDirectories(hidden.toPath());
        noMedia(hidden);
        if (legacy.isDirectory()) {
            merge(legacy, hidden);
        }
        return hidden;
    }

    private static void noMedia(File directory) throws IOException {
        File marker = new File(directory, ".nomedia");
        if (!marker.exists()) {
            Files.createFile(marker.toPath());
        } else if (Files.isSymbolicLink(marker.toPath()) || !marker.isFile()) {
            throw new IOException("Invalid .nomedia marker: " + marker);
        }
    }

    private static void merge(File source, File target) throws IOException {
        File[] entries = source.listFiles();
        if (entries == null) {
            throw new IOException("Cannot list puzzle directory: " + source);
        }
        for (File entry : entries) {
            if (entry.getName().equals(".nomedia")) continue;
            if (Files.isSymbolicLink(entry.toPath())) {
                throw new IOException("Puzzle entry is a symbolic link: " + entry);
            }
            File destination = new File(target, entry.getName());
            if (Files.isSymbolicLink(destination.toPath())) {
                throw new IOException("Puzzle target is a symbolic link: " + destination);
            }
            if (entry.isDirectory() && destination.isDirectory()) {
                merge(entry, destination);
                continue;
            }
            if (destination.exists()) {
                String name = entry.getName();
                int dot = name.lastIndexOf('.');
                String stem = dot > 0 ? name.substring(0, dot) : name;
                String extension = dot > 0 ? name.substring(dot) : "";
                for (int n = 1; destination.exists(); n++) {
                    destination = new File(target, stem + "-old-" + n + extension);
                }
            }
            try {
                Files.move(entry.toPath(), destination.toPath());
            } catch (IOException moveError) {
                if (!entry.isDirectory()) throw moveError;
                Files.createDirectories(destination.toPath());
                merge(entry, destination);
            }
        }
        Files.deleteIfExists(new File(source, ".nomedia").toPath());
        try {
            Files.delete(source.toPath());
        } catch (IOException e) {
            noMedia(source);
            throw e;
        }
    }
}
