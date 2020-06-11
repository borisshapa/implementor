package ru.ifmo.rain.shaposhnikov.implementor;

import info.kgeorgiy.java.advanced.implementor.Impler;
import info.kgeorgiy.java.advanced.implementor.ImplerException;
import info.kgeorgiy.java.advanced.implementor.JarImpler;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.lang.reflect.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;

import static ru.ifmo.rain.shaposhnikov.implementor.Util.*;

/**
 * Implementation of {@link Impler} and {@link JarImpler} interfaces.
 * Generates a file with the class by the transmitted {@link Class} token.
 * When launched with the <var>-jar</var> key generates a <var>.jar</var> file.
 *
 * @author Boris Shaposhnikov
 */
public class Implementor implements Impler, JarImpler {
    /**
     * Returns a {@link String} contains package line in generated java class.
     * If a parent class or an interface is not placed in a package, an empty string is returned.
     *
     * @param token the {@link Class} object of a parent class or an interface that is being implemented
     * @return a {@link String} containing a package line if it exist, and empty string otherwise
     */
    private static String getPackageLine(final Class<?> token) {
        final String packageName = token.getPackageName();
        return packageName.isEmpty() ? "" : String.format("package %s;", packageName);
    }

    /**
     * Returns a string containing declaration of implementation class.
     * Format: {@code public class implementation_class_name extends class_that_is_being_implemented}.
     * If given {@link Class} object is interface, instead of {@code extends} will be {@code implements}, respectively.
     *
     * @param token the {@link Class} object of a parent class or an interface that is being implemented
     * @return a declaration of implementation class
     * @see Util#getImplSimpleName(Class)
     */
    private String getDeclarationLine(final Class<?> token) {
        return String.format("public class %s %s %s",
                getImplSimpleName(token),
                token.isInterface() ? "implements" : "extends",
                token.getCanonicalName());
    }

    /**
     * Returns a string containing separated collection elements with prefix and suffix.
     * All objects in the {@link Collection} are converted to a string by <var>mapper</var> function.
     * The strings are separated by the passed string. Also at the beginning of the sequence is added a prefix,
     * at the end of the suffix.
     *
     * @param elements  the {@link Collection} of elements to enumerate
     * @param mapper    the function of converting elements to strings
     * @param separator sequence elements will be separated by this string
     * @param prefix    this string will be added to the beginning of the sequence
     * @param suffix    this string will be added to the end of the sequence
     * @param <T>       type of elements contained in the collection
     * @return the string in which elements are listed through a separator with prefix and suffix added
     */
    private static <T> String join(final Collection<T> elements,
                                   final Function<T, String> mapper,
                                   final String separator, final String prefix, final String suffix) {
        return elements.stream().map(mapper).collect(Collectors.joining(separator, prefix, suffix));
    }

    /**
     * Returns a string containing separated array elements with prefix and suffix.
     * Does the same as {@link #join(Collection, Function, String, String, String)},
     * but takes an array of elements of type <var>T</var>.
     *
     * @param elements  the array of elements to enumerate
     * @param mapper    the function of converting elements to strings
     * @param separator sequence elements will be separated by this string
     * @param prefix    this string will be added to the beginning of the sequence
     * @param suffix    this string will be added to the end of the sequence
     * @param <T>       type of elements contained in the array
     * @return the string in which elements are listed through a separator with prefix and suffix added
     * @see #join(Collection, Function, String, String, String)
     */
    private static <T> String join(final T[] elements,
                                   final Function<T, String> mapper,
                                   final String separator, final String prefix, final String suffix) {
        return join(Arrays.asList(elements), mapper, separator, prefix, suffix);
    }

    /**
     * Returns a string containing separated array elements without prefix and suffix.
     * Does the same as {@link #join(Object[], Function, String, String, String)},
     * but does not add prefix and suffix to the enumeration string.
     *
     * @param elements  the array of elements to enumerate
     * @param mapper    the function of converting elements to strings
     * @param separator sequence elements will be separated by this string
     * @param <T>       type of elements contained in the array
     * @return the string in which elements are listed through a separator
     */
    private static <T> String join(final T[] elements,
                                   final Function<T, String> mapper,
                                   final String separator) {
        return join(elements, mapper, separator, "", "");
    }

    /**
     * Writes any one constructor implementation of class that is to being using a given <var>writer</var>.
     * If the passed class object is an interface, the function does nothing.
     *
     * @param token  the {@link Class} object of a parent class or an interface that is being implemented
     * @param writer where to write the constructor
     * @throws ImplerException if there are no appropriate constructors in a class given
     * @throws IOException     if an error occurred trying to write with a given writer
     * @see #getConstructor(Constructor)
     */
    private void writeConstructor(final Class<?> token, final Writer writer) throws ImplerException, IOException {
        if (token.isInterface()) {
            return;
        }
        write(writer, Arrays.stream(token.getDeclaredConstructors())
                .filter(constructor -> !Modifier.isPrivate(constructor.getModifiers()))
                .findAny().map(this::getConstructor)
                .orElseThrow(() -> new ImplerException("No non-private constructors found")));
    }

    /**
     * Writes the implementation of all abstract superclass classes using writer.
     *
     * @param token  the {@link Class} object of a parent class or an interface that is being implemented
     * @param writer where to write the methods
     * @throws IOException if an error occurred trying to write with a given writer
     * @see #getMethod(Method)
     */
    private void writeMethods(final Class<?> token, final Writer writer) throws IOException {
        final Set<MethodWrapper> methods = getMethodsSet(token);
        for (final MethodWrapper methodWrapper : methods) {
            write(writer, getMethod(methodWrapper.getMethod()));
        }
    }

    /**
     * Returns a string containing implementation of <var>constructor</var>.
     * The result of function {@link Util#getImplSimpleName(Class)}. is used as the name of the constructor.
     * The result of function {@link #getConstructorBody(Constructor)} is used as the constructor body.
     *
     * @param constructor the {@link Constructor} whose implementation must be obtained
     * @return implementation constructor {@link String}
     * @see #getExecutable(Executable, String, String)
     */
    private String getConstructor(final Constructor<?> constructor) {
        return getExecutable(constructor,
                getImplSimpleName(constructor.getDeclaringClass()),
                getConstructorBody(constructor));
    }

    /**
     * Returns a string containing implementation of method.
     * The result of function {@link #getMethodBody(Method)} is used as the method body.
     *
     * @param method the {@link Method} whose implementation must be obtained
     * @return implementation method string
     * @see #getExecutable(Executable, String, String)
     */
    private String getMethod(final Method method) {
        return getExecutable(method,
                method.getReturnType().getCanonicalName() + " " + method.getName(),
                getMethodBody(method));
    }

    /**
     * Returns an enumeration of arguments.
     * Return a {@link String} of type {@code (Argumenvarype0 arg0, Argumenvarype1 arg1, ... , Argumenvarypen argn)}
     * by {@link Parameter} array
     *
     * @param parameters the arguments array
     * @return enumeration of arguments separated by commas with a space with bracket at the beginning and at the end.
     */
    private String getArgs(final Parameter[] parameters) {
        return join(parameters,
                parameter -> parameter.getType().getCanonicalName() + " " + parameter.getName(),
                ", ", "(", ")");
    }

    /**
     * Returns a string about thrown exceptions.
     * Return a {@link String} of type {@code throws Exception0, Exception1, ... , Exceptionn} by exception class array
     * If exception class array is empty, returns an empty string.
     *
     * @param exceptions exception class array
     * @return exception names separated by commas with a space with "throws " prefix
     * if exception class array isn't empty, and and empty string otherwise
     */
    private static String getExceptions(final Class<?>[] exceptions) {
        return exceptions.length == 0 ? "" : "throws " + join(exceptions, Class::getCanonicalName, ", ");
    }

    /**
     * Returns a constructor body implementation.
     * The constructor implementation is a {@code super} method call with the arguments passed.
     * The returned string does not contain a semicolon at the end.
     *
     * @param constructor the {@link Constructor} whose body implementation must be obtained
     * @return enumeration of the arguments passed, separated by commas with a space with "super(" prefix ans ")" suffix
     */
    private String getConstructorBody(final Constructor<?> constructor) {
        return join(constructor.getParameters(), Parameter::getName, ", ", "super(", ")");
    }

    /**
     * Returns method or constructor implementation.
     * Format: {@code modifiers returnType name(parameters) {
     * method_or_constructor_body;
     * }}
     *
     * @param executable        {@link Executable} class of method or constructor that is being implemented
     * @param returnTypeAndName a {@link String} containing returnType and name separated by space
     *                          if executable is method, and name otherwise
     * @param body              a {@link String} containing constructor or method body
     * @return a string containing method or constructor implementation
     */
    private String getExecutable(final Executable executable,
                                 final String returnTypeAndName,
                                 final String body) {
        return String.format("\t%s %s%s %s {%n\t\t%s;%n\t}%n",
                Modifier.toString(executable.getModifiers()
                        & ~Modifier.ABSTRACT & ~Modifier.NATIVE & ~Modifier.TRANSIENT),
                returnTypeAndName,
                getArgs(executable.getParameters()),
                getExceptions(executable.getExceptionTypes()),
                body);
    }

    /**
     * Returns a method body implementation.
     * The method implementation is a {@code return} the required parameter,
     * depending on the type obtained from the {@link Method#getReturnType()} method.
     * The returned string does not contain a semicolon at the end.
     *
     * @param method the {@link Method} whose body implementation must be obtained
     * @return {@link String} containing the required value with "return " prefix
     */
    private String getMethodBody(final Method method) {
        final Class<?> returnValueType = method.getReturnType();
        final String returnValue;
        if (returnValueType.equals(void.class)) {
            returnValue = "";
        } else if (returnValueType.equals(boolean.class)) {
            returnValue = "false";
        } else if (returnValueType.isPrimitive()) {
            returnValue = "0";
        } else {
            returnValue = "null";
        }
        return "return " + returnValue;
    }

    /**
     * Wraps the required methods in {@link MethodWrapper}.
     * Gets the required methods defined by the function <var>getMethods</var> ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()},
     * Then filters them according to the <var>predicate</var> and return {@link Stream} of {@link MethodWrapper}.
     *
     * @param token      the {@link Class} whose methods you want to get
     * @param getMethods {@code Function<Class<?>, Method[]>} which imposes restrictions on return methods
     *                   ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()})
     * @param predicate  how to filter
     * @return {@link Stream} of {@link MethodWrapper}
     */
    private Stream<MethodWrapper> getMethodWrapperSet(final Class<?> token,
                                                      final Function<Class<?>, Method[]> getMethods,
                                                      final Predicate<Method> predicate) {
        return Arrays.stream(getMethods.apply(token))
                .filter(predicate)
                .map(MethodWrapper::new);
    }

    /**
     * Helper method for obtaining the necessary methods.
     * Adds the abstract methods defined by the function
     * <var>getMethods</var> ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()})
     * to the {@link Set} <var>set</var>
     * All methods are cast to {@link MethodWrapper} class using {@link #getMethodWrapperSet(Class, Function, Predicate)}.
     *
     * @param token      the {@link Class} whose methods you want to get
     * @param getMethods {@code Function<Class<?>, Method[]>} which imposes restrictions on return methods
     *                   ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()})
     * @param set        where to collect
     */
    private void getMethodsSet(final Class<?> token,
                               final Function<Class<?>, Method[]> getMethods,
                               final Set<MethodWrapper> set) {
        getMethodWrapperSet(token, getMethods, method -> Modifier.isAbstract(method.getModifiers()))
                .collect(Collectors.toCollection(() -> set));
    }

    /**
     * Deletes final methods in super classes.
     * Needed to remove abstract methods that cannot be implemented in the generated class.
     * Deletes the final methods defined by the function {@code getMethods} ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()})
     *
     * @param token      the {@link Class} object of a parent class or an interface that is being implemented
     * @param getMethods {@code Function<Class<?>, Method[]>} which imposes restrictions on return methods
     *                   ({@link Class#getMethods()} or {@link Class#getDeclaredMethods()})
     * @param set        where to remove unnecessary methods
     */
    private void removeFinalMethods(final Class<?> token,
                                    final Function<Class<?>, Method[]> getMethods,
                                    final Set<MethodWrapper> set) {
        getMethodWrapperSet(token, getMethods, method -> Modifier.isFinal(method.getModifiers()))
                .forEach(set::remove);
    }

    /**
     * Collects a set of all abstract methods to implement of a class.
     *
     * @param token the {@link Class} object of a parent class or an interface that is being implemented
     * @return set of methods that need to be implemented
     */
    private Set<MethodWrapper> getMethodsSet(final Class<?> token) {
        final Set<MethodWrapper> methods = new HashSet<>();
        getMethodsSet(token, Class::getMethods, methods);
        for (Class<?> curToken = token; curToken != null; curToken = curToken.getSuperclass()) {
            getMethodsSet(curToken, Class::getDeclaredMethods, methods);
        }

        removeFinalMethods(token, Class::getMethods, methods);
        for (Class<?> curToken = token; curToken != null; curToken = curToken.getSuperclass()) {
            removeFinalMethods(curToken, Class::getDeclaredMethods, methods);
        }
        return methods;
    }

    /**
     * Class wrap over the classic {@link Method}.
     * Allows you to compare methods for equivalence by name, arguments and return type.
     *
     * @see Method#hashCode()
     * @see Method#equals(Object)
     */
    private static class MethodWrapper {
        /**
         * A method being stored inside a wrapper
         */
        private final Method method;

        /**
         * Constructs a method wrapper by a given method
         *
         * @param method method to store
         */
        public MethodWrapper(final Method method) {
            this.method = method;
        }

        /**
         * Gets a method stored
         *
         * @return a method stored
         */
        public Method getMethod() {
            return method;
        }

        /**
         * Overridden method for comparing the contents of wrapper classes.
         * It returns {@code true} if and only if the argument passed is a method and the name,
         * parameters and return type of the methods are the same, and {@code false} otherwise.
         *
         * @param obj a method wrapper to compare with
         * @return {@code true} if objects are equal, {@code false} otherwise
         */
        @Override
        public boolean equals(final Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (obj instanceof MethodWrapper) {
                final MethodWrapper otherMethod = (MethodWrapper) obj;
                return (method.getName().equals(otherMethod.method.getName())) &&
                        Arrays.equals(method.getParameterTypes(), otherMethod.method.getParameterTypes()) &&
                        method.getReturnType().equals(otherMethod.method.getReturnType());
            }
            return false;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public int hashCode() {
            return Objects.hash(method.getName(),
                    Arrays.hashCode(method.getParameterTypes()),
                    method.getReturnType());
        }
    }

    /**
     * Writes class implementation using a <var>writer</var>.
     * A package is wrivaren at the beginning of the file, if it exists, obtained by the {@link #getPackageLine(Class)} method.
     * An implementation class contains one constructor (see {@link #writeConstructor(Class, Writer)},
     * and an implementation of inherited methods (see {@link #writeMethods(Class, Writer)}).
     *
     * @param token  the {@link Class} object of a parent class or an interface that is being implemented
     * @param writer where to write the class implementation
     * @throws IOException     if an error occurred trying to write with a given writer
     * @throws ImplerException if there are no appropriate constructors in a class given
     * @see #getPackageLine(Class)
     * @see #getDeclarationLine(Class)
     * @see #writeConstructor(Class, Writer)
     * @see #writeMethods(Class, Writer)
     */
    private void writeClass(final Class<?> token, final Writer writer) throws IOException, ImplerException {
        write(writer, String.format("%s%n%s {%n",
                getPackageLine(token),
                getDeclarationLine(token)));

        writeConstructor(token, writer);
        writeMethods(token, writer);

        writer.write("}");
    }

    /**
     * Checks if all arguments passed are non-null.
     *
     * @param args arguments to check
     * @throws ImplerException if at least one argument is null
     */
    private void nullAssertion(final Object... args) throws ImplerException {
        for (final Object o : args) {
            if (Objects.isNull(o)) {
                throw new ImplerException("Non null arguments expected");
            }
        }
    }


    @Override
    public void implement(final Class<?> token, Path root) throws ImplerException {
        nullAssertion(token, root);
        if (token.isPrimitive()
                || token.isArray()
                || token == Enum.class
                || Modifier.isFinal(token.getModifiers())
                || Modifier.isPrivate(token.getModifiers())) {
            throw new ImplerException("Unsupported class token given");
        }

        root = getPath(token, root, ".java");

        try (final Writer writer = Files.newBufferedWriter(root)) {
            writeClass(token, writer);
        } catch (final IOException e) {
            throw new ImplerException("Error during writing in file: " + e.getMessage(), e);
        }
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
        final String[] args = new String[]{
                "-cp",
                System.getProperty("java.class.path") + File.pathSeparator + path.toString(),
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
     * Encodes text. Escapes all Unicode characters greater than or equal to 128.
     *
     * @param text the {@link String} to be converted
     * @return Encoded {@link String}
     */
    private String encode(final String text) {
        final StringBuilder sb = new StringBuilder();
        final char[] charArray = text.toCharArray();
        for (final char c : charArray) {
            sb.append((c < 128) ? c : String.format("\\u%04x", (int) c));
        }
        return sb.toString();
    }

    /**
     * Writes a string to a <var>writer</var>, Escaping all Unicode characters greater than or equal to 128.
     *
     * @param writer where to write
     * @param string the {@link String} to be encoded and written to the <var>writer</var>
     * @throws IOException if an error occurred trying to write with a given writer
     * @see #encode(String)
     */
    private void write(final Writer writer, final String string) throws IOException {
        writer.write(encode(string));
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
    private static void createJarFile(final Class<?> token, final Path tmpDir, final Path jarFile) throws ImplerException {
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

    /**
     * {@inheritDoc}
     */
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
                new Implementor().implement(Class.forName(args[0]), Paths.get(args[1]));
            } else if (args[0].equals("-jar")) {
                new Implementor().implementJar(Class.forName(args[1]), Paths.get(args[2]));
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
