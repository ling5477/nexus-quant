package com.guidinglight.nexusquant.app.smoke;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.TreeSet;

/** 运行前及运行中绑定实际源码和权威输入；证据目录不进入候选集合。 */
final class L6BFingerprint {
    private final ObjectNode entry;
    private final boolean formal;
    L6BFingerprint(boolean formal) throws Exception { this.formal=formal;entry=capture(); }
    ObjectNode evidence() { return entry.deepCopy(); }
    void verify() throws Exception {
        if(!entry.equals(capture()))throw new IllegalStateException("L6_B_CANDIDATE_DRIFT");
    }
    private ObjectNode capture() throws Exception {
        Path root=B0Processes.root();
        String[] areas={"backend","scripts",".github",".agents","AGENTS.md","pom.xml",".gitattributes"};
        var command=new java.util.ArrayList<String>(java.util.List.of("git","-C",root.toString(),"ls-files","--cached","--others","--exclude-standard","--"));
        command.addAll(java.util.List.of(areas));
        var paths=new TreeSet<String>();
        for(String name:B0Processes.command(command.toArray(String[]::new)).split("\\R"))if(!name.isBlank())paths.add(name);
        paths.add(L6BContract.planPath(new ObjectMapper().readTree(Files.readAllBytes(root.resolve(L6BContract.CANONICAL)))).toString().replace('\\','/'));
        paths.add(L6BContract.CANONICAL.toString().replace('\\','/'));
        paths.add(L6PgCapacityContract.CANONICAL.toString().replace('\\','/'));
        paths.add(L6BContract.CANONICAL.resolveSibling(".gitattributes").toString().replace('\\','/'));
        paths.add(L6FormalManifest.CANONICAL.toString().replace('\\','/'));
        paths.add(L6BContract.CANONICAL.resolveSibling("L6_B_ACCEPTED_A_INPUT.json").toString().replace('\\','/'));
        paths.add(L6BContract.CANONICAL.getParent().resolve("runs/L6_B_RUNNER_CLOSURE_20260915/evidence-volume-analysis.json").toString().replace('\\','/'));
        if(formal) {
            var diff=new java.util.ArrayList<String>(java.util.List.of("git","-C",root.toString(),"diff","--exit-code","HEAD","--"));
            diff.addAll(java.util.List.of(areas));B0Processes.command(diff.toArray(String[]::new));
            B0Processes.command("git","-C",root.toString(),"diff","--cached","--exit-code");
        }
        var result=new ObjectMapper().createObjectNode();
        result.put("HEAD",B0Processes.command("git","-C",root.toString(),"rev-parse","HEAD").trim());
        var hashes=result.putObject("files");
        // 新代JVM加载同一编译输出；运行中重新编译也会使指纹失效。
        try(var modules=Files.list(root.resolve("backend"))) {
            for(var module:modules.filter(Files::isDirectory).toList())for(String output:java.util.List.of("target/classes","target/test-classes")) {
                Path tree=module.resolve(output);
                if(Files.isDirectory(tree))try(var compiled=Files.walk(tree)) {
                    for(var file:compiled.filter(Files::isRegularFile).toList())paths.add(root.relativize(file).toString().replace('\\','/'));
                }
            }
        }
        for(String name:paths)hashes.put(name,L6PgCapacityContract.hash(Files.readAllBytes(root.resolve(name))));
        return result;
    }
}
