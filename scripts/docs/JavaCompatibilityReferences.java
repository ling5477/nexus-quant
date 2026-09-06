import com.sun.source.tree.*;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import javax.tools.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Parse source references without compiling or executing application code.
 * The analysis deliberately over-approximates ambiguous simple names and variable uses:
 * ambiguity must not authorize a new caller. Comments and string literals are not edges.
 * Output binds target type/member to exact source path and enclosing declaration signature.
 */
class JavaCompatibilityReferences {
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Map<String, Set<String>> names = new TreeMap<>();
        List<String> files = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            boolean readingFiles = false;
            while ((line = input.readLine()) != null) {
                if (line.isEmpty()) { readingFiles = true; continue; }
                if (readingFiles) { files.add(line); continue; }
                String[] parts = line.split("\t", -1);
                names.computeIfAbsent(parts[0], key -> new TreeSet<>()).add(parts[1]);
            }
        }
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("JDK_REQUIRED");
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            var inputs = manager.getJavaFileObjectsFromPaths(files.stream().map(root::resolve).toList());
            JavacTask task = (JavacTask) compiler.getTask(null, manager, diagnostics,
                    List.of("-proc:none", "--release", "21"), null, inputs);
            Set<String> edges = new TreeSet<>();
            for (CompilationUnitTree unit : task.parse()) {
                String path = root.relativize(Path.of(unit.getSourceFile().toUri())).toString().replace('\\', '/');
                Map<String, Set<String>> visibleNames = new TreeMap<>();
                names.forEach((name, targets) -> targets.forEach(target -> {
                    String type = target.substring(target.lastIndexOf('.') + 1);
                    boolean visible = name.equals(type) || path.endsWith("/" + type + ".java")
                            || unit.getTypeDecls().stream().filter(t -> t instanceof ClassTree)
                                .map(t -> ((ClassTree) t).getExtendsClause()).filter(Objects::nonNull)
                                .anyMatch(t -> t.toString().equals(target) || t.toString().equals(type))
                            || unit.getImports().stream().anyMatch(i -> {
                                String imported = i.getQualifiedIdentifier().toString();
                                return imported.equals(target) || imported.startsWith(target + ".");
                            });
                    if (visible) visibleNames.computeIfAbsent(name, key -> new TreeSet<>()).add(target);
                }));
                Map<String, Set<String>> bindings = new TreeMap<>();
                visibleNames.forEach((key, value) -> bindings.put(key, new TreeSet<>(value)));
                // Track uses of parameters, fields, locals and getters typed as retained contracts.
                new TreeScanner<Void, Void>() {
                    void bind(String name, Tree type) {
                        if (type == null) return;
                        new TreeScanner<Void, Void>() {
                            @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                                Set<String> targets = visibleNames.get(node.getName().toString());
                                if (targets != null) bindings.computeIfAbsent(name, key -> new TreeSet<>()).addAll(targets);
                                return super.visitIdentifier(node, unused);
                            }
                            @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                                Set<String> targets = visibleNames.get(node.getIdentifier().toString());
                                if (targets != null) bindings.computeIfAbsent(name, key -> new TreeSet<>()).addAll(targets);
                                return super.visitMemberSelect(node, unused);
                            }
                        }.scan(type, null);
                    }
                    @Override public Void visitVariable(VariableTree node, Void unused) {
                        bind(node.getName().toString(), node.getType());
                        return super.visitVariable(node, unused);
                    }
                    @Override public Void visitMethod(MethodTree node, Void unused) {
                        bind(node.getName().toString(), node.getReturnType());
                        return super.visitMethod(node, unused);
                    }
                }.scan(unit, null);
                new TreeScanner<Void, Void>() {
                    String owner = "<unit>";
                    String member = "<type>";
                    void emit(String name) {
                        for (String target : bindings.getOrDefault(name, Set.of())) {
                            String reference = visibleNames.containsKey(name) ? name : "<typed-use>";
                            edges.add(target + "\t" + path + "\t" + owner + "#" + member + "\t" + reference);
                        }
                    }
                    @Override public Void visitClass(ClassTree node, Void unused) {
                        String priorOwner = owner, priorMember = member;
                        owner = owner.equals("<unit>") ? node.getSimpleName().toString() : owner + "." + node.getSimpleName();
                        member = "<type>";
                        super.visitClass(node, unused);
                        owner = priorOwner; member = priorMember;
                        return null;
                    }
                    @Override public Void visitMethod(MethodTree node, Void unused) {
                        String prior = member;
                        member = node.getName() + "(" + String.join(",", node.getParameters().stream()
                                .map(parameter -> parameter.getType().toString().replaceAll("\\s+", " ")).toList()) + ")";
                        super.visitMethod(node, unused); member = prior;
                        return null;
                    }
                    @Override public Void visitVariable(VariableTree node, Void unused) {
                        String prior = member;
                        if (member.equals("<type>")) member = node.getName().toString();
                        super.visitVariable(node, unused); member = prior;
                        return null;
                    }
                    @Override public Void visitIdentifier(IdentifierTree node, Void unused) {
                        emit(node.getName().toString()); return super.visitIdentifier(node, unused);
                    }
                    @Override public Void visitMemberSelect(MemberSelectTree node, Void unused) {
                        String selected = node.getIdentifier().toString();
                        String expression = node.getExpression().toString();
                        // A fully qualified selector needs no import. Still emit the member
                        // edge, even when this same caller already has a type-level grant.
                        for (String target : names.getOrDefault(selected, Set.of())) {
                            String type = target.substring(target.lastIndexOf('.') + 1);
                            if (expression.equals(target) || expression.startsWith(target + ".")
                                    || expression.equals(type) || expression.startsWith(type + ".")) {
                                edges.add(target + "\t" + path + "\t" + owner + "#" + member + "\t" + selected);
                            }
                        }
                        emit(node.getIdentifier().toString()); return super.visitMemberSelect(node, unused);
                    }
                }.scan(unit, null);
            }
            if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                throw new IllegalArgumentException("JAVA_SOURCE_PARSE_FAILED");
            }
            for (String edge : edges) System.out.println(edge);
        }
    }
}
