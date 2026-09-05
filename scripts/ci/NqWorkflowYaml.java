import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.nodes.MappingNode;
import org.yaml.snakeyaml.nodes.Node;
import org.yaml.snakeyaml.nodes.ScalarNode;
import org.yaml.snakeyaml.nodes.SequenceNode;
import org.yaml.snakeyaml.nodes.Tag;

/**
 * Offline CI adapter for the backend BOM's SnakeYAML dependency. SnakeYAML owns parsing,
 * escape decoding and node structure; this adapter projects its nodes to JSON, without object
 * construction or expression evaluation. Workflow scalars remain decoded strings (including
 * the YAML 1.1 boolean spelling "on"); callers enforce types of mappings/sequences explicitly.
 * Aliases, merge keys, duplicate decoded keys and non-core tags are outside this CI contract.
 * Errors deliberately omit input values. No network, application startup or production access.
 */
class NqWorkflowYaml {
    private final Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
    private final List<Map<String, Object>> actions = new ArrayList<>();
    private final String[] lines;

    private NqWorkflowYaml(String source) { lines = source.split("\\r?\\n", -1); }

    private Object project(Node node, List<String> path) {
        if (node == null || !visited.add(node)) throw new IllegalArgumentException();
        if (node instanceof MappingNode mapping && Tag.MAP.equals(node.getTag())) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (var tuple : mapping.getValue()) {
                if (!(tuple.getKeyNode() instanceof ScalarNode keyNode) || !visited.add(keyNode))
                    throw new IllegalArgumentException();
                String key = keyNode.getValue(); // Already decoded by SnakeYAML, never raw YAML text.
                if (key.equals("<<") || result.containsKey(key)) throw new IllegalArgumentException();
                if (!Set.of(Tag.STR, Tag.BOOL, Tag.INT, Tag.FLOAT, Tag.NULL).contains(keyNode.getTag()))
                    throw new IllegalArgumentException();
                List<String> childPath = new ArrayList<>(path); childPath.add(key);
                Object value = project(tuple.getValueNode(), childPath);
                result.put(key, value);
                if (key.equals("uses") && path.size() == 4 && path.get(0).equals("jobs")
                        && path.get(2).equals("steps") && tuple.getValueNode() instanceof ScalarNode scalar) {
                    var mark = scalar.getEndMark();
                    String line = lines[mark.getLine()];
                    int offset = line.offsetByCodePoints(0, mark.getColumn());
                    actions.add(Map.of("job", path.get(1), "index", path.get(3),
                            "value", value, "annotation", line.substring(offset).trim()));
                }
            }
            return result;
        }
        if (node instanceof SequenceNode sequence && Tag.SEQ.equals(node.getTag())) {
            List<Object> result = new ArrayList<>();
            for (int i = 0; i < sequence.getValue().size(); i++) {
                List<String> childPath = new ArrayList<>(path); childPath.add(Integer.toString(i));
                result.add(project(sequence.getValue().get(i), childPath));
            }
            return result;
        }
        if (node instanceof ScalarNode scalar && Set.of(Tag.STR, Tag.BOOL, Tag.INT, Tag.FLOAT,
                Tag.NULL, Tag.TIMESTAMP).contains(node.getTag())) {
            return scalar.getValue();
        }
        throw new IllegalArgumentException();
    }

    // JSON transport only; all YAML syntax and escape processing are handled above by SnakeYAML.
    private static String json(Object value) {
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            map.forEach((key, item) -> fields.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof List<?> list) return "[" + String.join(",", list.stream().map(NqWorkflowYaml::json).toList()) + "]";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') out.append('\\').append(c);
            else if (c < 32 || c > 126) out.append(String.format("\\u%04x", (int)c));
            else out.append(c);
        }
        return out.append('"').toString();
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1 || Files.size(Path.of(args[0])) > 1_000_000) throw new IllegalArgumentException();
            String source = Files.readString(Path.of(args[0]));
            LoaderOptions options = new LoaderOptions();
            options.setAllowDuplicateKeys(false);
            options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(64);
            options.setCodePointLimit(1_000_000);
            NqWorkflowYaml adapter = new NqWorkflowYaml(source);
            Node node = new Yaml(new SafeConstructor(options)).compose(new StringReader(source));
            Object document = adapter.project(node, List.of());
            System.out.println(json(Map.of("document", document, "actions", adapter.actions)));
        } catch (Exception error) {
            System.err.println("YAML_SEMANTIC_PARSE_REJECTED");
            System.exit(1);
        }
    }
}
