package ru.ifmo.rain.shaposhnikov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import static ru.ifmo.rain.shaposhnikov.implementor.Util.*;

/**
 * Implementation of {@link Impler} and {@link JarImpler} interfaces.
 * Generates a file with the class by the transmitted {@link Class} token.
 * When launched with the <var>-jar</var> key generates a <var>.jar</var> file.
 *
 * @author Boris Shaposhnikov
 */
public class JarImplementor implements JarImpler {
    @Override
    public void implement(final Class<?> token, final Path root) throws ImplerException {
        new Implementor().implement(token, root);
    }

    /**
     * Compiles implementation class and stores <var>.class</var> file in given <var>path</var>.
     *
     * @param token the {@link Class} object of a parent class or an interface that is being implemented
     * @param path  where to save <var>.class</var> files
     * @throws ImplerException if the java compiler wasn't found or an error occurred trying to compile classes
     */
    private void compileClass(final Class<?> token, final Path path) throws ImplerException {
        final JavaCompiler javaCompiler = ToolProvider.getSystemJavaCompiler();
        final String codeSource;
        try {
            codeSource = Path.of(token.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
        } catch (final URISyntaxException e) {
            throw new ImplerException("Error during getting source code uri: " + e.getMessage(), e);
        }
        final String[] args = new String[]{
                "-cp",
                path + File.pathSeparator + codeSource,
                getPath(token, path, ".java").toString()
        };

        if (javaCompiler == null) {
            throw new ImplerException("No java compiler found");
        }
        if (javaCompiler.run(null, null, null, args) != 0) {
            throw new ImplerException("Error during compiling classes");
        }
    }

    /**
     * Create a <var>.jar</var> file containing <var>.class</var> files
     * compiling by {@link #compileClass(Class, Path)}.
     *
     * @param token   the {@link Class} object of a parent class or an interface that is being implemented
     * @param tmpDir  where to get <var>.class</var> files
     * @param jarFile where to save the <var>.jar</var> file
     * @throws ImplerException if an error occurred trying to write in <var>.jar</var> file or to copy files
     */
    private void createJarFile(final Class<?> token, final Path tmpDir, final Path jarFile) throws ImplerException {
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

        try (final JarOutputStream writer = new JarOutputStream(Files.newOutputStream(jarFile), manifest)) {
            final String className = getPackageDir(token, "/") + String.format("/%s.class", getImplSimpleName(token));
            writer.putNextEntry(new ZipEntry(className));
            Files.copy(tmpDir.resolve(className), writer);
        } catch (final IOException e) {
            throw new ImplerException("Error during a jar file writing: " + e.getMessage(), e);
        }
    }

    /**
     * Class is used to clean directory for temporary files, after creating <var>.jar</var>-file.
     * Recursively deletes directory, all its subdirectories and files inside.
     *
     * @see Files#walkFileTree(Path, FileVisitor)
     */
    private static class TmpDirCleaner extends SimpleFileVisitor<Path> {

        /**
         * Deletes file
         *
         * @param file file to delete
         * @see Files#delete(Path)
         */
        @Override
        public FileVisitResult visitFile(final Path file, final BasicFileAttributes avarrs) throws IOException {
            Files.delete(file);
            return FileVisitResult.CONTINUE;
        }

        /**
         * Deletes directory after all of files inside it and subdirectories are already deleted.
         *
         * @param dir directory to delete
         * @param exc thrown exception during deleting
         * @return value indicating continuation of walking
         * @throws IOException if an error occurred trying to create directories
         */
        @Override
        public FileVisitResult postVisitDirectory(final Path dir, final IOException exc) throws IOException {
            Files.delete(dir);
            return FileVisitResult.CONTINUE;
        }
    }

    /**
     * Static object of {@link TmpDirCleaner} class.
     */
    private static final FileVisitor<Path> TMP_DIR_CLEANER = new TmpDirCleaner();


    @Override
    public void implementJar(final Class<?> token, final Path jarFile) throws ImplerException {
        nullAssertion(token, jarFile);
        createDirectories(jarFile);
        final Path tmpDir = createTempDirectory(jarFile);

        try {
            implement(token, tmpDir);
            compileClass(token, tmpDir);
            createJarFile(token, tmpDir, jarFile);
        } finally {
            try {
                Files.walkFileTree(tmpDir, TMP_DIR_CLEANER);
            } catch (final IOException e) {
                System.err.println(String.format("Error during deleting temporary files in '%s' directory: %s",
                        tmpDir, e.getMessage()));
            }
        }
    }

    /**
     * The main function for implementing the class.
     * Supports two operating modes.
     * <ul>
     *     <li>2 arguments. <br>
     *          The first argument is a class object that needs to be expanded or implemented.
     *          The second argument is the path where you need to put the implementation class.
     *     </li>
     *
     *     <li>
     *         Jar mode. <br>
     *         The first argument is the <var>-jar</var> key.
     *         The first argument is a class object that needs to be expanded or implemented and archived.
     *         The second argument is the path where you need to put the <var>.jar</var>.
     *     </li>
     * </ul>
     *
     * @param args command line arguments {@code [-jar] class path}
     * @see #implement(Class, Path)
     * @see #implementJar(Class, Path)
     */
    public static void main(final String[] args) {
        Objects.requireNonNull(args, "Expected non null arguments");
        if (args.length != 2 && args.length != 3) {
            System.err.println("Expected 2 arguments for class implementing or 3 arguments for jar implementing");
            return;
        }
        for (int i = 0; i < args.length; i++) {
            Objects.requireNonNull(args[i], i + " argument is null");
        }
        try {
            if (args.length == 2) {
                new JarImplementor().implement(Class.forName(args[0]), Paths.get(args[1]));
            } else if (args[0].equals("-jar")) {
                new JarImplementor().implementJar(Class.forName(args[1]), Paths.get(args[2]));
            } else {
                System.err.println("Use '-jar' as first argument for jar implementing");
            }
        } catch (final ClassNotFoundException e) {
            System.err.println("Invalid class name: " + e.getMessage());
        } catch (final ImplerException e) {
            System.err.println(e.getMessage());
        } catch (final InvalidPathException e) {
            System.err.println("Invalid path: " + e.getMessage());
        }
    }
}
