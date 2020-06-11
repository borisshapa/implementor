package ru.ifmo.rain.shaposhnikov.implementor;

import info.kgeorgiy.java.advanced.implementor.ImplerException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.Objects;

/**
 * Helper functions for {@link Implementor and {@link JarImplementor}
 *
 * @author Boris Shaposhnikov
 */
public class Util {

    /**
     * Returns {@link String} a new class name with <var>Impl</var> suffix.
     *
     * @param name of a parent class or an interface that is being implemented
     * @return a concatenation of a given class name and <var>Impl</var>
     */
    public static String getNewClassName(final String name) {
        return name + "Impl";
    }

    /**
     * Method retrieves implemented class name by {@link Class} token.
     *
     * @param token the {@link Class} object of a parent class or an interface that is being implemented
     * @return the simple name of a class that is to be implemented
     * @see #getNewClassName(String)
     */
    public static String getImplSimpleName(final Class<?> token) {
        return getNewClassName(token.getSimpleName());
    }

    /**
     * Returns the absolute pathname string of this abstract pathname's parent,
     * or null if this pathname does not name a parent directory.
     *
     * @param path the path whose pathname's parent must be returned
     * @return the absolute pathname string of the parent directory, or null if this pathname does not name a parent
     * @see Path#getParent()
     */
    public static Path getParent(final Path path) {
        return path.toAbsolutePath().getParent();
    }

    /**
     * Creates the directories for the given file pathname if the don't exist.
     *
     * @param path the pathname of the file, the necessary directories for which needs to create
     * @throws ImplerException if an error occurred trying to create directories
     * @see Files#createDirectories(Path, FileAttribute[])
     */
    public static void createDirectories(final Path path) throws ImplerException {
        final Path parent = getParent(path);
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (final IOException e) {
                throw new ImplerException("Error during creating directories: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Creates a temporary directory in the specified location.
     * The name of the temporary folder has a "tmp" prefix.
     *
     * @param path the pathname where to create the temporary directory
     * @return the path to the newly created directory that did not exist before this method was invoked
     * @throws ImplerException if an error occurred trying to create temporary directory
     * @see Files#createTempDirectory(Path, String, FileAttribute[])
     */
    public static Path createTempDirectory(final Path path) throws ImplerException {
        try {
            return Files.createTempDirectory(getParent(path), "tmp");
        } catch (final IOException e) {
            throw new ImplerException("Error during creating a temporary directory: " + e.getMessage(), e);
        }
    }

    /**
     * Returns a package name {@link String} with replacement of dots by the specified <var>separator</var>.
     *
     * @param token     the {@link Class} object of a parent class or an interface that is being implemented
     * @param separator what to replace the dots with.
     * @return a {@link String} that contains the changed package name.
     */
    public static String getPackageDir(final Class<?> token, final String separator) {
        return token.getPackageName().replace(".", separator);
    }

    /**
     * Returns a path where an implementation class should be considering its package.
     * The file name ends with a suffix denoting the file format (<var>.class</var> or <var>.java</var>).
     * If there is no directory in which the implementation file is located, directories are created.
     *
     * @param token  the {@link Class} object of a parent class or an interface that is being implemented
     * @param root   the path to the directory where the file with the full name should be
     * @param format file format added as a suffix to the file name (<var>.class</var> or <var>.java</var>)
     * @return the path to the file with the name containing the package name.
     * @throws ImplerException if an error occurred trying to create directories
     * @see #createDirectories(Path)
     */
    public static Path getPath(final Class<?> token, final Path root, final String format) throws ImplerException {
        final Path path = root.resolve(Path.of(getPackageDir(token, File.separator),
                getImplSimpleName(token) + format));
        createDirectories(path);
        return path;
    }

    /**
     * Checks if all arguments passed are non-null.
     *
     * @param args arguments to check
     * @throws ImplerException if at least one argument is null
     */
    public static void nullAssertion(final Object... args) throws ImplerException {
        for (final Object o : args) {
            if (Objects.isNull(o)) {
                throw new ImplerException("Non null arguments expected");
            }
        }
    }
}
